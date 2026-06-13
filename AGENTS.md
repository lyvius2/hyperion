# AGENTS.md — Hyperion, NL-to-SQL Data Platform

> This file defines the rules and design principles that AI agents (Claude, Cursor, Copilot, etc.)  
> and human developers must follow when writing or reviewing code in this project.  
> All code generation and modification must treat this document as the highest authority.

---

[한국어(Korean) 문서](AGENTS_KR.md)

## Reference Documents

| Document | Description |
|-|-|
| [ARCHITECTURE_DESIGN.md](./docs/ARCHITECTURE_DESIGN.md) | Full system architecture, domain models, infrastructure, and embedding pipeline design |
| [DATABASE_SCHEMA.md](./docs/DATABASE_SCHEMA.md) | Defines the platform meta DB schema for the NL-to-SQL Data Platform |
| [API_SPECIFICATION.md](./docs/API_SPECIFICATION.md) | Complete REST API and WebSocket protocol specification |

Always read both documents before writing code. Any implementation that conflicts with the design documents is not permitted.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Language and Framework](#2-language-and-framework)
3. [Layer Structure and Responsibilities](#3-layer-structure-and-responsibilities)
4. [Package Structure](#4-package-structure)
5. [Coding Rules](#5-coding-rules)
6. [Dependency Rules](#6-dependency-rules)
7. [Async Processing Rules](#7-async-processing-rules)
8. [Domain and Entity Rules](#8-domain-and-entity-rules)
9. [Security Rules](#9-security-rules)
10. [Error Handling Rules](#10-error-handling-rules)
11. [Embedding Pipeline Rules](#11-embedding-pipeline-rules)
12. [SQL Validation Rules](#12-sql-validation-rules)
13. [Testing Rules](#13-testing-rules)
14. [Prohibited Practices](#14-prohibited-practices)
15. [Commit and Branch Rules](#15-commit-and-branch-rules)
16. [Prompt Management Rules](#16-prompt-management-rules)

---

## 1. Project Overview

This project is a monolithic platform that provides **natural language input → SQL generation → data extraction/visualization**.

Core flow:

```
User natural language input
    → Extract relevant schema/code chunks via RAG (ChromaDB similarity search)
    → Insert context into LLM (gpt-oss:20b) prompt
    → Generate SQL
    → EXPLAIN cost validation (PROD DB, no data reads)
    → Execute on PROD DB
    → Generate Excel or d3.js HTML
    → Return board URL via WebSocket
```

RAG is not training. On every request, relevant chunks are retrieved from ChromaDB and inserted into the prompt.

---

## 2. Language and Framework

| Item | Choice | Notes |
|------|--------|-------|
| Language | **Kotlin 2.x** | Minimize Java mixing |
| Runtime | **JDK 21** | Virtual Thread support |
| Framework | **Spring Boot 4.x** | Based on Spring Framework 7 |
| Async | **Kotlin Coroutines** | `suspend fun`, `CoroutineScope` |
| HTTP | Spring MVC + WebClient | WebFlux used selectively |
| WebSocket | Spring WebSocket (STOMP) | SockJS fallback |
| ORM | Spring Data JPA | jOOQ for dynamic queries |
| Template | Mustache | Server-side rendering |
| Build | Gradle Kotlin DSL | |

### Language Rules

- **Kotlin is the default language.** If a Java file is added, the reason must be explicitly stated in a comment.
- Use `data class` actively. Prefer immutable data structures.
- Use `val` by default. Use `var` only when unavoidable.
- Leverage Kotlin's null-safety to the fullest. Avoid the `!!` operator.
- Comments and documentation must be written in **English**. (Variable names and function names in code are always English.)

---

## 3. Layer Structure and Responsibilities

This project is composed of the following layers. Each layer's scope of responsibility must be strictly observed.

```
Controller
    ├─ Facade                            ← multi-service orchestration
    │    ├─ ServiceA
    │    ├─ ServiceB
    │    └─ ServiceC
    └─ Service (direct, for simple cases)
         ├─ LlmOrchestrationService
         ├─ DocumentIngestionPipeline
         ├─ JobHistoryService
         └─ (each domain Service)
              ├─ OllamaClient
              ├─ ChromaDbClient
              └─ DynamicDataSourceFactory
```

### Layer Responsibilities

| Layer | Role | Prohibited |
|-------|------|-----------|
| **Controller** | HTTP request/response, parameter validation, auth check | Writing business logic |
| **Facade** | Orchestrate multiple Services for a single use-case; manage transaction boundary | Direct DB access, calling other Facades, calling infra clients directly |
| **Service** | Single-domain business logic | Directly calling other Services |
| **LlmOrchestrationService** | RAG search + prompt assembly + LLM call orchestration | Direct DB access |
| **DocumentIngestionPipeline** | Orchestrating the full embedding pipeline flow | Handling HTTP requests |
| **Chunker** (`MarkdownChunker`, etc.) | Responsible only for chunking a single file type | Calling embedding APIs |
| **OllamaClient** | Responsible only for calling Ollama REST API | Making business decisions |
| **ChromaDbClient** | Responsible only for calling ChromaDB REST API | Making business decisions |
| **DynamicDataSourceFactory** | Responsible only for creating/caching HikariCP DataSource | Executing SQL |
| **Scheduler** | Periodic tasks such as TTL-expired file deletion | Implementing business logic directly |
| **Entity/Domain** | Defining data structures | Calling external services |

### Facade Pattern — Multi-Service Orchestration

When a use-case requires cooperation between multiple Services, introduce a **Facade** class.  
A Facade is the only layer allowed to depend on multiple Services simultaneously.  
Direct Service → Service calls remain prohibited even when a Facade exists.

```kotlin
// ❌ Prohibited: Service → Service direct call
@Service
class NLQueryService(private val ingestionService: DocumentIngestionPipeline) { ... }

// ✅ Allowed: Facade orchestrates multiple Services
@Component
class NLQueryFacade(
    private val nlQueryService: NLQueryService,
    private val jobHistoryService: JobHistoryService,
    private val webSocketNotifier: WebSocketNotifier
) {
    suspend fun processExtract(system: TargetSystem, member: Member, nl: String) {
        val job = jobHistoryService.start(JobType.QUERY_EXTRACT, system, member, nl.take(200))
        runCatching {
            val result = nlQueryService.processExtract(system, member, nl)
            jobHistoryService.complete(job, "rows=${result.size}")
            webSocketNotifier.sendResult(member.sessionId, result)
        }.onFailure { e ->
            jobHistoryService.fail(job, e)
            webSocketNotifier.sendError(member.sessionId, e.toErrorCode(), e.toUserMessage())
        }
    }
}

// ✅ Also allowed: Controller → Service directly (when only one Service is needed)
@RestController
class JobHistoryController(private val jobHistoryService: JobHistoryService) {
    @GetMapping("/admin/jobs/{id}")
    fun getJob(@PathVariable id: Long) = jobHistoryService.findById(id)
}
```

### When to Use Facade vs. Direct Service Call

| Situation | Pattern |
|-----------|---------|
| Single Service is sufficient | `Controller → Service` |
| Multiple Services must cooperate | `Controller → Facade → Service(s)` |
| Async job + WebSocket notification + history logging | Always use Facade |
| Simple CRUD query | Direct Controller → Service is acceptable |

---

## 4. Package Structure

```
com.yourcompany.nlplatform
├── config/                         # Spring configuration classes
│   ├── WebSocketConfig.kt
│   ├── DataSourceConfig.kt         # Platform meta DB DataSource
│   ├── OllamaConfig.kt
│   └── ChromaDbConfig.kt
│
├── web/                            # Controller layer
│   ├── admin/
│   │   ├── SystemController.kt
│   │   ├── FileUploadController.kt
│   │   ├── GitSyncController.kt
│   │   ├── IngestionController.kt
│   │   ├── MemberController.kt
│   │   └── JobHistoryController.kt
│   ├── query/
│   │   ├── ExtractController.kt
│   │   └── VisualizeController.kt
│   ├── board/
│   │   ├── BoardController.kt
│   │   └── BoardApiController.kt
│   ├── auth/
│   │   └── AuthController.kt
│   └── PageController.kt
│
├── domain/                         # Domain entities and Repositories
│   ├── member/
│   │   ├── Member.kt               # @Entity
│   │   ├── MemberToken.kt
│   │   ├── MemberRole.kt           # enum
│   │   ├── MemberStatus.kt         # enum
│   │   ├── MemberRepository.kt
│   │   └── MemberTokenRepository.kt
│   ├── system/
│   │   ├── TargetSystem.kt
│   │   ├── SystemFile.kt
│   │   ├── DbType.kt               # enum
│   │   ├── IngestionStatus.kt      # enum
│   │   ├── FileType.kt             # enum
│   │   ├── TargetSystemRepository.kt
│   │   └── SystemFileRepository.kt
│   ├── result/
│   │   ├── QueryResult.kt
│   │   ├── ResultType.kt           # enum
│   │   ├── ResultStatus.kt         # enum
│   │   └── QueryResultRepository.kt
│   ├── job/
│   │   ├── JobHistory.kt
│   │   ├── JobType.kt              # enum
│   │   ├── JobStatus.kt            # enum
│   │   └── JobHistoryRepository.kt
│   └── exception/
│       ├── DataExtractionNotRequestedException.kt
│       ├── QueryTooExpensiveException.kt
│       ├── SqlValidationException.kt
│       └── SystemNotFoundException.kt
│
├── facade/                         # Facade layer (multi-service orchestration)
│   ├── query/
│   │   └── NLQueryFacade.kt        # Orchestrates NLQueryService + JobHistoryService + WebSocketNotifier
│   ├── ingestion/
│   │   └── IngestionFacade.kt      # Orchestrates DocumentIngestionPipeline + JobHistoryService
│   └── admin/
│       └── SystemAdminFacade.kt    # Orchestrates TargetSystemService + FileUploadService + GitSyncService
│
├── service/                        # Service layer
│   ├── member/
│   │   └── MemberService.kt
│   ├── system/
│   │   ├── TargetSystemService.kt
│   │   ├── FileUploadService.kt
│   │   └── GitSyncService.kt
│   ├── query/
│   │   ├── NLQueryService.kt
│   │   └── VisualizationService.kt
│   ├── board/
│   │   └── QueryResultService.kt
│   └── job/
│       └── JobHistoryService.kt
│
├── llm/                            # LLM orchestration
│   ├── LlmOrchestrationService.kt
│   ├── PromptBuilder.kt
│   └── ResponseParser.kt
│
├── ingestion/                      # Embedding pipeline
│   ├── DocumentIngestionPipeline.kt
│   ├── IncrementalIngestionStrategy.kt
│   ├── chunker/
│   │   ├── DocumentChunker.kt      # interface
│   │   ├── MarkdownChunker.kt
│   │   ├── SqlDdlChunker.kt
│   │   └── SourceCodeChunker.kt
│   └── extractor/
│       ├── JavaMethodExtractor.kt
│       └── KotlinMethodExtractor.kt
│
├── infra/                          # External system clients
│   ├── ollama/
│   │   └── OllamaClient.kt
│   ├── vectordb/
│   │   └── ChromaDbClient.kt
│   ├── db/
│   │   ├── DynamicDataSourceFactory.kt
│   │   ├── QueryExecutor.kt
│   │   └── SqlValidator.kt
│   ├── db/explain/
│   │   └── ExplainAnalyzer.kt
│   ├── crypto/
│   │   └── TokenEncryptor.kt
│   ├── storage/
│   │   └── SystemDirectoryManager.kt
│   ├── slack/
│   │   └── SlackNotifier.kt
│   └── websocket/
│       └── WebSocketNotifier.kt
│
├── scheduler/                      # Scheduled tasks
│   └── ResultFileCleanupScheduler.kt
│
└── util/                           # Common utilities
    ├── ExcelGenerator.kt
    ├── TempFileStorage.kt
    └── HashUtils.kt
```

### Package Placement Principles

- Domain entities must always be located under `domain/`.
- External API clients (Ollama, ChromaDB, Slack) must be located under `infra/`.
- Business logic belongs in `service/`, `llm/`, or `ingestion/`.
- Before creating a new file, verify that an appropriate package does not already exist.

---

## 5. Coding Rules

### 5-1. Function and Class Design

```kotlin
// ✅ Recommended: Single responsibility, clear function names
@Service
class SqlDdlChunker : DocumentChunker {
    override fun chunk(file: File, system: TargetSystem): List<DocumentChunk> { ... }
    private fun extractTableName(ddl: String): String { ... }
    private fun extractColumnSummary(ddl: String): String { ... }
}

// ❌ Prohibited: Cramming multiple responsibilities into one function
fun processEverything(file: File, system: TargetSystem, ollamaClient: OllamaClient) { ... }
```

### 5-2. Using data class

```kotlin
// ✅ Recommended: Use data class, immutable structure
data class DocumentChunk(
    val id: String,
    val text: String,
    val metadata: Map<String, String>,
    val sourceHash: String
)

// ❌ Prohibited: DTO with mutable fields
class DocumentChunk {
    var id: String = ""
    var text: String = ""
}
```

### 5-3. Null Handling

```kotlin
// ✅ Recommended: Elvis operator, let scope
val tableName = extractTableName(ddl) ?: "unknown"
system.gitUrl?.let { url -> gitClient.clone(url) }

// ❌ Prohibited: Forced non-null assertion
val tableName = extractTableName(ddl)!!
```

### 5-4. Function Length Limit

- A single function should be **40 lines or fewer** as a principle.
- If it exceeds 40 lines, extract into private functions.

### 5-5. Constant Definitions

```kotlin
// ✅ Recommended: Define constants in companion object
companion object {
    const val MAX_CHUNK_TOKENS = 512
    const val EMBEDDING_BATCH_SIZE = 10
    const val SIMILARITY_THRESHOLD = 0.3
    const val COST_REJECT_THRESHOLD = 50_000.0
}

// ❌ Prohibited: Hardcoded magic numbers
if (tokens > 512) { ... }
if (similarity < 0.3) { ... }
```

### 5-6. Logging

```kotlin
// ✅ Recommended: Use LoggerFactory, structured logs
private val log = LoggerFactory.getLogger(javaClass)

log.info("[${system.name}] Chunking complete: file=${file.name}, chunks=${chunks.size}")
log.error("[${system.name}] Embedding failed: path=$relPath", e)

// ❌ Prohibited: println, System.out.println
println("Chunking complete")

// ❌ Prohibited: Logging sensitive information
log.info("DB password: ${system.dbPasswordEnc}")
log.info("Git Token: ${system.gitAccessTokenEnc}")
```

### 5-7. LLM Model Name Configuration

LLM model names must never be hardcoded in source code. They must be managed via `application.yaml` and injected through `@ConfigurationProperties`.

```kotlin
// ✅ Recommended: Inject model names from application.yaml
@ConfigurationProperties(prefix = "app.ollama")
data class OllamaProperties(
    val baseUrl: String,
    val embeddingModel: String = "nomic-embed-text",
    val generationModel: String = "gpt-oss:20b"   // initially considered: deepseek-coder:6.7b
)

// Usage in OllamaClient
suspend fun generate(prompt: String): String {
    val res = webClient.post().uri("$baseUrl/api/generate")
        .bodyValue(mapOf("model" to ollamaProperties.generationModel, ...))
        ...
}

// ❌ Prohibited: Hardcoded model name
.bodyValue(mapOf("model" to "gpt-oss:20b", ...))
```

```yaml
# application.yaml
app:
  ollama:
    base-url: http://localhost:11434
    embedding-model: nomic-embed-text
    generation-model: gpt-oss:20b
```

### 5-8. Comment Principles

```kotlin
// ✅ Recommended: Explain design intent in comments
// Synthesize DDL + description text to improve natural language search quality
val embeddingText = """
    Table name: $tableName
    Description: $comment
""".trimIndent()

// ✅ Recommended: KDoc-style documentation
/**
 * Incremental change detection using SHA-256 hashing.
 * Double-checks file modification time and hash to avoid unnecessary re-embedding.
 */
private fun hasChanged(file: File, storedHash: String?): Boolean { ... }
```

---

## 6. Dependency Rules

### Allowed

```
Controller → Facade
Controller → Service (when a single Service is sufficient)
Facade → Service (multiple Services allowed)
Facade → JobHistoryService
Service → infra/* (OllamaClient, ChromaDbClient, QueryExecutor, etc.)
Service → domain/* (Repository, Entity)
LlmOrchestrationService → OllamaClient, ChromaDbClient
DocumentIngestionPipeline → Chunker, OllamaClient, ChromaDbClient, JobHistoryService
Scheduler → Service (one-directional)
```

### Prohibited

```
Service → Service (direct calls prohibited — use Facade instead)
Facade → Facade (cross-facade calls prohibited)
Service → Facade (reverse direction prohibited)
infra/* → Service (reverse direction prohibited)
domain/Entity → Service (reverse direction prohibited)
Chunker → OllamaClient (chunking and embedding are separated)
Controller → infra/* (Controller must go through Service or Facade)
```

### Circular Dependencies Prohibited

```kotlin
// ❌ Absolutely prohibited: Circular dependency
@Service
class ServiceA(private val serviceB: ServiceB)
@Service
class ServiceB(private val serviceA: ServiceA)
```

---

## 7. Async Processing Rules

### Kotlin Coroutines-Based

Async processing in this project uses **Kotlin Coroutines**.

```kotlin
// ✅ Recommended: suspend fun-based async
@Service
class NLQueryService(...) {
    suspend fun processExtract(system: TargetSystem, member: Member, nl: String) {
        val sql = llmService.generateSql(nl, system)    // suspend
        val result = queryExecutor.execute(sql, system) // suspend
    }
}

// ✅ Recommended: Parallel processing
val namingDeferred = async { llmService.generateDatasetName(naturalLanguage) }
val sqlDeferred    = async { llmService.generateSql(naturalLanguage, system) }
val datasetName    = namingDeferred.await()
val sql            = sqlDeferred.await()
```

### Async Trigger (Controller → Background)

```kotlin
// ✅ Recommended: Async execution, return 202 immediately
@OptIn(DelicateCoroutinesApi::class)
fun triggerAsync(system: TargetSystem, mode: IngestionMode, triggeredBy: Member?) {
    GlobalScope.launch(Dispatchers.IO) { run(system, mode, triggeredBy) }
}
```

### Prohibited

```kotlin
// ❌ Prohibited: Blocking calls
val result = someService.doSomething().get()  // Future.get()
Thread.sleep(1000)
runBlocking { ... }  // Prohibited in service layer
```

### WebClient Usage (External HTTP Calls)

```kotlin
// ✅ Recommended: WebClient + awaitBody (Coroutines integration)
val response = webClient.post()
    .uri("$baseUrl/api/embeddings")
    .bodyValue(body)
    .retrieve()
    .awaitBody<EmbeddingResponse>()

// ❌ Prohibited: RestTemplate, synchronous HttpClient calls
val response = restTemplate.postForObject(url, body, EmbeddingResponse::class.java)
```

---

## 8. Domain and Entity Rules

### 8-1. Entity Design

```kotlin
// ✅ Recommended: data class + JPA annotations
@Entity
@Table(name = "target_systems")
data class TargetSystem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    val name: String,

    // ... remaining fields
)

// ❌ Prohibited: Writing business logic methods in entities
@Entity
data class TargetSystem(...) {
    fun connectToDatabase() { ... }    // Prohibited: infrastructure logic
    fun embed(chunker: Chunker) { ... } // Prohibited: service logic
}
```

### 8-2. Entity Modification Pattern

Design JPA entities as immutable (val), and use `copy()` when modification is needed.

```kotlin
// ✅ Recommended: Immutable update using copy()
systemRepo.save(system.copy(
    ingestionStatus = IngestionStatus.COMPLETED,
    lastIngestedAt  = LocalDateTime.now(),
    totalChunkCount = totalChunks
))
```

### 8-3. Encrypted Fields

DB connection credentials and Git tokens must always be encrypted before storage.

```kotlin
// ✅ Recommended: Encrypt before saving, decrypt before use
val encryptedUsername = tokenEncryptor.encrypt(request.dbUsername)
val encryptedPassword = tokenEncryptor.encrypt(request.dbPassword)

// When using
val username = tokenEncryptor.decrypt(system.dbUsernameEnc)

// ❌ Prohibited: Storing in plain text
@Column
val dbPassword: String  // Plain text storage without encryption
```

### 8-4. Exclude Sensitive Information from Response DTOs

```kotlin
// ✅ Recommended: Exclude sensitive information from response DTOs
data class TargetSystemResponse(
    val id: Long,
    val name: String,
    val dbType: DbType,
    val dbUrl: String,
    val dbUsername: String,      // Masked: "he***r"
    // dbPassword: absolutely must not be included in response
    // gitAccessToken: absolutely must not be included in response
)
```

---

## 9. Security Rules

### 9-1. SQL Validation — Must Always Go Through SqlValidator

LLM-generated SQL must never be executed without validation.

```kotlin
// ✅ Required: SqlValidator → ExplainAnalyzer → Execute
val safeSql = sqlValidator.validate(generatedSql)      // SELECT only, block forbidden keywords
val explain = explainAnalyzer.analyze(safeSql, system) // Cost validation via EXPLAIN
val finalSql = when (explain.verdict) {
    Verdict.REJECT -> throw QueryTooExpensiveException(...)
    Verdict.WARN   -> llmService.optimizeSql(safeSql, explain, system)
    Verdict.PASS   -> safeSql
}
queryExecutor.execute(finalSql, system)
```

### 9-2. File Upload Validation

```kotlin
// ✅ Required: File type and size validation
val allowedExtensions = setOf("md", "sql")
require(file.originalFilename?.substringAfterLast('.') in allowedExtensions) {
    "File type not allowed. Only .md and .sql files are permitted."
}
require(file.size <= 10 * 1024 * 1024) { "File size must not exceed 10MB." }

// ✅ Required: Path Traversal defense
val safeName = file.originalFilename
    ?.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")  // Replace special characters
    ?: throw IllegalArgumentException("Filename is missing.")
```

### 9-3. Sensitive Information Logging Prohibited

```kotlin
// ❌ Absolutely prohibited: Logging sensitive information
log.info("Token: ${tokenEncryptor.decrypt(system.gitAccessTokenEnc)}")
log.debug("DB PW: $dbPassword")
log.error("API Key: $apiKey", e)
```

### 9-4. .env File Management

```kotlin
// Must be included in .gitignore
// .env
// **/application-prod.yml
// **/*-secret.yml
```

---

## 10. Error Handling Rules

### 10-1. Exception Hierarchy

```kotlin
// All domain exceptions extend from this hierarchy
open class NlPlatformException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause)

class DataExtractionNotRequestedException(message: String)
    : NlPlatformException(message)

class QueryTooExpensiveException(message: String)
    : NlPlatformException(message)

class SqlValidationException(message: String)
    : NlPlatformException(message)

class SystemNotFoundException(id: Long)
    : NlPlatformException("System not found. (id=$id)")
```

### 10-2. JobHistory Integration — Required for All Async Operations

```kotlin
// ✅ Required pattern: All async operations must record JobHistory
val job = jobHistoryService.start(
    jobType      = JobType.QUERY_EXTRACT,
    system       = system,
    triggeredBy  = member,
    inputSummary = naturalLanguage.take(200)
)
runCatching {
    // ... perform work
    jobHistoryService.complete(job, "rows=${result.size}")
}.onFailure { e ->
    jobHistoryService.fail(job, e)  // stack_trace saved automatically
    throw e
}
```

### 10-3. WebSocket Error Response

```kotlin
// ✅ Recommended: Notify client via WebSocket when an error occurs
}.onFailure { e ->
    jobHistoryService.fail(job, e)
    webSocketNotifier.sendError(
        sessionId = sessionId,
        resultId  = resultId,
        errorCode = e.toErrorCode(),
        message   = e.toUserMessage()   // User-friendly message
    )
}
```

### 10-4. Global Exception Handling in Controller

```kotlin
// ✅ Recommended: Centralized handling with @ControllerAdvice
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(SystemNotFoundException::class)
    fun handleNotFound(e: SystemNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("SYSTEM_NOT_FOUND", e.message))

    @ExceptionHandler(QueryTooExpensiveException::class)
    fun handleTooExpensive(e: QueryTooExpensiveException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest()
            .body(ErrorResponse("QUERY_TOO_EXPENSIVE", e.message))
}
```

---

## 11. Embedding Pipeline Rules

These are the design principles for the embedding pipeline. For detailed implementation, refer to Chapter 9 of [architecture-design-v7.md](./docs/architecture-design-v7.md).

### 11-1. Chunking — Use Only Dedicated Chunker per Type

```kotlin
// ✅ Required: Implement DocumentChunker interface
interface DocumentChunker {
    fun chunk(file: File, system: TargetSystem): List<DocumentChunk>
}

// Each file type uses its dedicated Chunker
.md   → MarkdownChunker    (split by heading boundaries)
.sql  → SqlDdlChunker      (entire CREATE TABLE = 1 chunk, synthesize DDL + description)
.java → JavaMethodExtractor + SourceCodeChunker  (AST-based method-level chunking)
.kt   → KotlinMethodExtractor + SourceCodeChunker
```

### 11-2. Source Code Chunking — Fixed-Size Chunking Is Absolutely Prohibited

```kotlin
// ❌ Absolutely prohibited: Fixed-size chunking (cuts in the middle of a function)
content.chunked(500).map { DocumentChunk(it) }

// ✅ Required: AST-based method/function-level chunking
javaExtractor.extract(file).map { method ->
    DocumentChunk(text = buildEnrichedText(method), ...)
}
```

### 11-3. Delete Existing Chunks Before Re-ingestion

```kotlin
// ✅ Required: Delete existing chunks before re-ingestion (prevent duplicates)
chromaDbClient.deleteBySourcePath(system.chromaCollection, relPath)
val embeddings = ollamaClient.embedBatch(chunks.map { it.text })
chromaDbClient.upsert(system.chromaCollection, chunks, embeddings)
```

### 11-4. Incremental Update — SHA-256 Double Check

```kotlin
// ✅ Recommended: Double check with modification time + SHA-256
if (file.lastModified() <= sinceEpochMs) return  // Same time → SKIP
val currentHash = sha256(file.readText())
if (currentHash == storedHash) return            // Same content → SKIP
// Re-embed only in this case
```

### 11-5. Collection Isolation — Mixing Systems Is Prohibited

```kotlin
// ✅ Required: Always specify system.chromaCollection explicitly
chromaDbClient.upsert(collection = system.chromaCollection, ...)
chromaDbClient.query(collection = system.chromaCollection, ...)

// ❌ Absolutely prohibited: Hardcoded collection name
chromaDbClient.query(collection = "business-context", ...)
```

### 11-6. nomic-embed-text — Task Prefix Is Required

When calling the Ollama embedding API with `nomic-embed-text`, a task-specific prefix **must** be prepended to the prompt text.

| Context | Prefix | Description |
|---------|--------|-------------|
| Storing a document chunk | `search_document:` | Applied when embedding chunks during ingestion |
| Searching with a query | `search_query:` | Applied when embedding the user's natural language query for RAG retrieval |

```kotlin
// ✅ Required: Prepend task prefix before embedding

// When ingesting document chunks (DocumentIngestionPipeline)
val embeddingInputs = chunks.map { "search_document: ${it.text}" }
val embeddings = ollamaClient.embedBatch(embeddingInputs)

// When querying via RAG (LlmOrchestrationService)
val queryEmbedding = ollamaClient.embed("search_query: $naturalLanguage")

// ❌ Prohibited: Sending raw text without prefix
val embeddings = ollamaClient.embedBatch(chunks.map { it.text })
val queryEmbedding = ollamaClient.embed(naturalLanguage)
```

> **Why:** `nomic-embed-text` uses different internal representations depending on whether the text is a document or a query. Omitting the prefix degrades retrieval accuracy.

---

## 12. SQL Validation Rules

Pre-validation is performed by running EXPLAIN on the PROD DB. Dev DB is not used.

### 12-1. Three Validation Layers Must Be Executed in Order

```
Layer 1: SqlValidator    → SELECT only, forbidden keywords, enforce LIMIT
Layer 2: ExplainAnalyzer → EXPLAIN FORMAT=JSON (PROD DB, no data reads)
Layer 3: Verdict         → PASS / WARN (re-query LLM) / REJECT (block)
```

### 12-2. Forbidden Keyword List (Must Not Be Arbitrarily Reduced)

```kotlin
private val FORBIDDEN = setOf(
    "DROP","DELETE","UPDATE","INSERT","TRUNCATE",
    "ALTER","CREATE","GRANT","REVOKE",
    "EXEC","EXECUTE","XP_","SP_","--","/*","*/"
)
```

### 12-3. Thresholds Must Be Managed in Configuration Files

```kotlin
// ❌ Prohibited: Hardcoded thresholds
if (cost > 50_000.0) throw QueryTooExpensiveException(...)

// ✅ Recommended: Inject from application.yml
@ConfigurationProperties(prefix = "app.query.explain")
data class ExplainProperties(
    val costRejectThreshold: Double = 50_000.0,
    val costWarnThreshold: Double   = 10_000.0,
    val rowsRejectThreshold: Long   = 1_000_000L
)
```

---

## 13. Testing Rules

### 13-1. Layers That Require Tests

The following layers require **mandatory unit tests**.

| Layer | Required | Notes |
|-------|:--------:|-------|
| `util/` (all) | ✅ Required | HashUtils, ExcelGenerator, etc. |
| `service/` (all) | ✅ Required | |
| `llm/` | ✅ Required | LlmOrchestrationService, PromptBuilder, etc. |
| `ingestion/chunker/` | ✅ Required | Each Chunker |
| `infra/db/SqlValidator` | ✅ Required | |
| `infra/db/explain/ExplainAnalyzer` | ✅ Required | |
| Controller | 🔵 Recommended | Use MockMvc |

### 13-2. Test Writing Principles

```kotlin
// ✅ Recommended: KotlinTest + Mockito combination
@ExtendWith(MockitoExtension::class)
class SqlValidatorTest {

    private val validator = SqlValidator()

    @Test
    @DisplayName("A SELECT statement should pass validation")
    fun `SELECT statement should pass validation`() {
        val sql = "SELECT * FROM orders WHERE order_id = 1"
        val result = validator.validate(sql)
        assertThat(result).contains("LIMIT")  // Verify LIMIT is enforced
    }

    @Test
    @DisplayName("SQL containing DROP should throw SqlValidationException")
    fun `SQL with DROP keyword should throw SqlValidationException`() {
        val sql = "SELECT * FROM orders; DROP TABLE orders"
        assertThrows<SqlValidationException> { validator.validate(sql) }
    }
}
```

```kotlin
// ✅ Recommended: Async service testing
@ExtendWith(MockitoExtension::class)
class NLQueryServiceTest {

    @Mock private lateinit var llmService: LlmOrchestrationService
    @Mock private lateinit var queryExecutor: QueryExecutor
    @InjectMocks private lateinit var service: NLQueryService

    @Test
    @DisplayName("Should throw exception when LLM responds with not-data-extraction")
    fun `should throw exception when LLM responds with not data extraction`() = runTest {
        whenever(llmService.generateSql(any(), any()))
            .thenReturn("데이터 추출 요구 아님")

        assertThrows<DataExtractionNotRequestedException> {
            service.processExtract(mockSystem, mockMember, "What is the weather?")
        }
    }
}
```

### 13-3. Test Naming Convention

```kotlin
@DisplayName("Describe the test intent in a readable sentence")
fun `english_snake_case_function_name`() { ... }
```

### 13-4. Handling External Dependencies

```kotlin
// ✅ Recommended: Mock external dependencies like OllamaClient, ChromaDbClient
@Mock private lateinit var ollamaClient: OllamaClient
@Mock private lateinit var chromaDbClient: ChromaDbClient

// ✅ Recommended: Tests requiring a real DB use @DataJpaTest + H2 in-memory DB
@DataJpaTest
class MemberRepositoryTest { ... }
```

### 13-5. Test Coverage Goals

- Business logic classes (`service/`, `llm/`, `ingestion/`): **80% or above**
- Utility classes (`util/`): **90% or above**
- `infra/` client classes: Replaceable with Mock tests

---

## 14. Prohibited Practices

The following are not permitted under any circumstances.

### Absolutely Prohibited (Hard Rules)

```
❌ Executing LLM-generated SQL without SqlValidator
❌ Storing DB passwords or Git tokens in plain text, or logging them
❌ Hardcoding collection names in ChromaDB queries (violates system isolation)
❌ Direct calls between Services (increases circular dependency and coupling)
❌ Using !! force non-null assertion (risk of NullPointerException)
❌ Fixed-size chunking of source code (destroys semantic meaning)
❌ Hardcoding encryption keys in code
❌ Blocking I/O (RestTemplate, Thread.sleep, etc.)
❌ Using println or System.out.println
❌ Attempting SQL generation with the embedding model (nomic-embed-text)
❌ Hardcoding LLM model names in source code (must be managed via application.yaml)
```

### Strongly Discouraged (Flagged in Code Review)

```
⚠️ Single function exceeding 40 lines
⚠️ Overuse of var (violates val-first principle)
⚠️ Hardcoded magic numbers (must be constants)
⚠️ Adding a Service class without tests
⚠️ Non-English variable or function names
⚠️ Executing async operations without JobHistory
⚠️ Terminating async operations without a WebSocket response
```

---

## 15. Commit and Branch Rules

### Branch Strategy

```
main            ← Production deployment branch (direct push prohibited)
develop         ← Development integration branch
feature/{name}  ← Feature development branch
fix/{name}      ← Bug fix branch
```

### Commit Message Format

```
{type}: {description}

Types:
  feat     Add new feature
  fix      Fix a bug
  refactor Refactor code (no functional change)
  test     Add or modify tests
  docs     Update documentation
  chore    Build, configuration, or dependency changes
  style    Code style changes (formatting, etc.)

Examples:
  feat: Add Korean description synthesis to SqlDdlChunker
  fix: Fix collection name isolation bug in ChromaDB upsert
  refactor: Simplify heading split logic in MarkdownChunker
  test: Add unit tests for ExplainAnalyzer MySQL parser
  docs: Add embedding pipeline rules to AGENTS.md
```

### PR Rules

- PR titles must follow the commit message format.
- PR descriptions must include the changes made, how to test them, and related issues.
- All tests must pass before merging.
- Self-merging is prohibited (at least 1 reviewer required).

---

## Appendix: Frequently Asked Questions

**Q. How is RAG different from Fine-tuning?**  
A. RAG does not train the LLM on documents. On every request, it retrieves relevant chunks from ChromaDB and inserts them into the prompt. Fine-tuning modifies model weights, whereas RAG is prompt engineering.

**Q. How do I add support for a new DBMS?**  
A. Add a new entry to the `DbType` enum, implement an EXPLAIN parser for that DBMS in `ExplainAnalyzer`, and add the corresponding driver to `toDriverClassName()` in `DynamicDataSourceFactory`.

**Q. What happens if the embedding pipeline fails?**  
A. The `JobHistory` record is updated to `FAILED` status with the full stack trace saved. `TargetSystem.ingestionStatus` is updated to `FAILED`. The cause can be inspected via the admin API `GET /admin/jobs/{id}`.

**Q. What is the difference in roles between `nomic-embed-text` and `gpt-oss:20b`?**  
A. `nomic-embed-text` is an embedding-only model that converts text into 384-dimensional vectors. It does not understand or generate SQL. `gpt-oss:20b` (formerly considered: `deepseek-coder:6.7b`) is a language model that reads the prompt context and generates SQL.

---

---

## 16. Prompt Management Rules

### 16-1. Why Not String Constants in a Class?

Prompts in this project are long, multi-section texts that include schema context, SQL rules,
and output format instructions. Embedding them as `String` literals or `companion object` constants
inside Kotlin source files causes the following problems:

- Prompts are not readable without understanding Kotlin string escaping (`\n`, `"""`, `$`)
- Editing a prompt requires recompiling the project
- Diff reviews in PRs are noisy with indentation and escape characters mixed with logic
- Non-developer team members (e.g., domain experts tuning prompts) cannot edit safely

### 16-2. Recommended Approach — Resource Template Files + PromptBuilder

Store prompt **templates** as `.md` files under `src/main/resources/prompts/`.  
`PromptBuilder` loads these templates at startup and performs runtime variable substitution.

```
src/main/resources/prompts/
├── sql-generation.md           # Main SQL generation prompt
├── sql-optimization.md         # Prompt for re-querying when EXPLAIN returns WARN
├── dataset-naming.md           # Prompt for generating a dataset name
└── visualization-type.md       # Prompt for selecting visualization type
```

```kotlin
// PromptBuilder.kt — load templates at startup, substitute at call time
@Component
class PromptBuilder(resourceLoader: ResourceLoader) {

    private val templates: Map<PromptType, String> = PromptType.entries.associateWith { type ->
        resourceLoader.getResource("classpath:prompts/${type.filename}").inputStream
            .bufferedReader().readText()
    }

    fun buildSqlGenerationPrompt(nl: String, schemaContext: String, codeContext: String): String =
        templates[PromptType.SQL_GENERATION]!!
            .replace("{{natural_language}}", nl)
            .replace("{{schema_context}}", schemaContext)
            .replace("{{code_context}}", codeContext)
}

enum class PromptType(val filename: String) {
    SQL_GENERATION("sql-generation.md"),
    SQL_OPTIMIZATION("sql-optimization.md"),
    DATASET_NAMING("dataset-naming.md"),
    VISUALIZATION_TYPE("visualization-type.md")
}
```

### 16-3. Template File Format

Use `{{variable_name}}` as the placeholder convention. Write templates in Markdown for readability.

```markdown
<!-- prompts/sql-generation.md -->
You are a SQL generation assistant for a {{db_type}} database.

## Schema Context
{{schema_context}}

## Related Source Code
{{code_context}}

## Task
Convert the following natural language request into a single SELECT SQL statement.
- Use only the tables and columns present in the schema context above.
- Always include a LIMIT clause (maximum 10,000 rows).
- Do not use subqueries that scan the full table.

## Request
{{natural_language}}

## Output
Return only the SQL statement with no explanation or markdown fences.
```

### 16-4. Rules

```
✅ All prompts must be stored as .md files under src/main/resources/prompts/
✅ PromptBuilder is the only class that may load and assemble prompts
✅ Placeholders must use the {{variable_name}} convention
✅ Each prompt file must correspond to exactly one PromptType enum entry
✅ PromptBuilder requires unit tests — test that placeholders are substituted correctly

❌ Hardcoding prompt text as a String literal or companion object constant
❌ Calling resourceLoader or reading prompt files from outside PromptBuilder
❌ Sharing a single template file for multiple distinct prompt purposes
❌ Leaving unused {{placeholder}} variables in the final assembled prompt
```

### 16-5. Testing PromptBuilder

```kotlin
@ExtendWith(MockitoExtension::class)
class PromptBuilderTest {

    private val promptBuilder = PromptBuilder(DefaultResourceLoader())

    @Test
    @DisplayName("SQL generation prompt should substitute all placeholders")
    fun `sql generation prompt should substitute all placeholders`() {
        val prompt = promptBuilder.buildSqlGenerationPrompt(
            nl = "Show me total sales by region",
            schemaContext = "CREATE TABLE orders ...",
            codeContext = "fun getOrdersByRegion() ..."
        )
        assertThat(prompt).doesNotContain("{{")   // No unresolved placeholders
        assertThat(prompt).contains("Show me total sales by region")
        assertThat(prompt).contains("CREATE TABLE orders")
    }
}
```

---

*This document is continuously updated as the project progresses.*  
*This file is always the authoritative latest version.*
