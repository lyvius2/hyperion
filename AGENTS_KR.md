# AGENTS.md — Hyperion, NL-to-SQL 데이터 플랫폼

> 이 파일은 AI 에이전트(Claude, Cursor, Copilot 등)와 개발자가 이 프로젝트에서  
> 코드를 작성하거나 리뷰할 때 반드시 준수해야 할 규칙과 설계 원칙을 정의합니다.  
> 모든 코드 생성 및 수정은 이 문서를 최우선 기준으로 삼습니다.

---

## 참조 문서

| 문서 | 설명 |
|-|-|
| [ARCHITECTURE_DESIGN_KR.md](./docs/ARCHITECTURE_DESIGN_KR.md)| 전체 시스템 아키텍처, 도메인 모델, 인프라, 임베딩 파이프라인 설계 |
| [DATABASE_SCHEMA_KR.md](./docs/DATABASE_SCHEMA_KR.md)| NL-to-SQL 데이터 플랫폼의 플랫폼 메타 DB 스키마를 정의 |
| [API_SPECIFICATION_KR.md](./docs/API_SPECIFICATION_KR.md)| 전체 REST API 및 WebSocket 프로토콜 명세 |
| [UI_UX_DESIGN_KR.md](./docs/UI_UX_DESIGN_KR.md) | UI/UX 설계 |

코드를 작성하기 전에 반드시 두 문서를 확인합니다. 설계 문서와 충돌하는 구현은 허용하지 않습니다.

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [개발 언어 및 프레임워크](#2-개발-언어-및-프레임워크)
3. [레이어 구조와 책임](#3-레이어-구조와-책임)
4. [패키지 구조](#4-패키지-구조)
5. [코딩 규칙](#5-코딩-규칙)
6. [의존성 규칙](#6-의존성-규칙)
7. [비동기 처리 규칙](#7-비동기-처리-규칙)
8. [도메인 및 엔티티 규칙](#8-도메인-및-엔티티-규칙)
9. [보안 규칙](#9-보안-규칙)
10. [에러 처리 규칙](#10-에러-처리-규칙)
11. [임베딩 파이프라인 규칙](#11-임베딩-파이프라인-규칙)
12. [SQL 검증 규칙](#12-sql-검증-규칙)
13. [테스트 규칙](#13-테스트-규칙)
14. [금지 사항](#14-금지-사항)
15. [커밋 및 브랜치 규칙](#15-커밋-및-브랜치-규칙)
16. [프롬프트 관리 규칙](#16-프롬프트-관리-규칙)

---

## 1. 프로젝트 개요

이 프로젝트는 **자연어 입력 → SQL 생성 → 데이터 추출/시각화**를 제공하는 모놀리식 플랫폼입니다.

핵심 흐름:

```
사용자 자연어 입력
    → RAG(ChromaDB 유사도 검색)로 관련 스키마/코드 청크 추출
    → LLM(gpt-oss:20b) 프롬프트에 context 삽입
    → SQL 생성
    → EXPLAIN 비용 검증 (PROD DB, 데이터 읽기 없음)
    → PROD DB 실행
    → Excel 또는 d3.js HTML 생성
    → WebSocket으로 게시판 URL 반환
```

RAG는 학습이 아닙니다. 매 요청마다 ChromaDB에서 관련 청크를 꺼내 프롬프트에 삽입합니다.

---

## 2. 개발 언어 및 프레임워크

| 항목 | 선택 | 비고 |
|------|------|------|
| 언어 | **Kotlin 2.x** | Java 혼용 최소화 |
| 런타임 | **JDK 21** | Virtual Thread 지원 |
| 프레임워크 | **Spring Boot 4.x** | Spring Framework 7 기반 |
| 비동기 | **Kotlin Coroutines** | `suspend fun`, `CoroutineScope` |
| HTTP | Spring MVC + WebClient | WebFlux는 선택적 사용 |
| WebSocket | Spring WebSocket (STOMP) | SockJS fallback |
| ORM | Spring Data JPA | 동적 쿼리는 jOOQ 병행 |
| 템플릿 | Mustache | 서버 사이드 렌더링 |
| 빌드 | Gradle Kotlin DSL | |

### 언어 규칙

- **Kotlin이 기본 언어**입니다. Java로 작성된 파일이 추가될 경우 반드시 이유를 주석으로 명시합니다.
- `data class`를 적극 활용합니다. 불변 데이터 구조를 우선합니다.
- `val`을 기본으로 사용하고, `var`는 불가피한 경우에만 사용합니다.
- Kotlin의 null-safety를 최대한 활용합니다. `!!` 연산자 사용을 지양합니다.
- 주석과 문서는 **한국어**로 작성합니다. (코드 내 변수명/함수명은 영어)

---

## 3. 레이어 구조와 책임

이 프로젝트는 다음 레이어로 구성됩니다. 각 레이어의 책임 범위를 엄격하게 지킵니다.

```
Controller
    ├─ Facade                            ← 여러 Service 오케스트레이션
    │    ├─ ServiceA
    │    ├─ ServiceB
    │    └─ ServiceC
    └─ Service (단순 케이스는 직접 호출 허용)
         ├─ LlmOrchestrationService
         ├─ DocumentIngestionPipeline
         ├─ JobHistoryService
         └─ (각 도메인 Service)
              ├─ OllamaClient
              ├─ ChromaDbClient
              └─ DynamicDataSourceFactory
```

### 레이어별 책임

| 레이어 | 역할 | 금지 |
|--------|------|------|
| **Controller** | HTTP 요청/응답, 파라미터 검증, 인증 확인 | 비즈니스 로직 작성 |
| **Facade** | 단일 유스케이스를 위한 여러 Service 오케스트레이션, 트랜잭션 경계 관리 | DB 직접 접근, 다른 Facade 호출, infra 클라이언트 직접 호출 |
| **Service** | 단일 도메인 비즈니스 로직 | 타 Service 직접 호출 |
| **LlmOrchestrationService** | RAG 검색 + 프롬프트 조립 + LLM 호출 오케스트레이션 | DB 직접 접근 |
| **DocumentIngestionPipeline** | 임베딩 파이프라인 전체 흐름 조율 | HTTP 요청 처리 |
| **Chunker** (`MarkdownChunker` 등) | 단일 파일 타입 청킹만 담당 | 임베딩 호출 |
| **OllamaClient** | Ollama REST API 호출만 담당 | 비즈니스 판단 |
| **ChromaDbClient** | ChromaDB REST API 호출만 담당 | 비즈니스 판단 |
| **DynamicDataSourceFactory** | HikariCP DataSource 생성/캐싱만 담당 | SQL 실행 |
| **Scheduler** | TTL 만료 파일 삭제 등 정기 작업 | 비즈니스 로직 직접 구현 |
| **Entity/Domain** | 데이터 구조 정의 | 외부 서비스 호출 |

### Facade 패턴 — 여러 Service 오케스트레이션

하나의 유스케이스에서 여러 Service의 협력이 필요한 경우 **Facade** 클래스를 도입합니다.  
Facade는 여러 Service에 동시에 의존할 수 있는 유일한 레이어입니다.  
Facade가 존재하더라도 Service → Service 직접 호출은 여전히 금지입니다.

```kotlin
// ❌ 금지: Service → Service 직접 호출
@Service
class NLQueryService(private val ingestionService: DocumentIngestionPipeline) { ... }

// ✅ 허용: Facade가 여러 Service를 오케스트레이션
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

// ✅ 허용: Controller → Service 직접 호출 (Service 하나만 필요한 경우)
@RestController
class JobHistoryController(private val jobHistoryService: JobHistoryService) {
    @GetMapping("/admin/jobs/{id}")
    fun getJob(@PathVariable id: Long) = jobHistoryService.findById(id)
}
```

### Facade vs. 직접 Service 호출 기준

| 상황 | 패턴 |
|------|------|
| Service 하나로 충분한 경우 | `Controller → Service` |
| 여러 Service의 협력이 필요한 경우 | `Controller → Facade → Service(들)` |
| 비동기 작업 + WebSocket 알림 + 이력 기록 | 반드시 Facade 사용 |
| 단순 CRUD 조회 | Controller → Service 직접 호출 허용 |

---

## 4. 패키지 구조

```
com.yourcompany.nlplatform
├── config/                         # Spring 설정 클래스
│   ├── WebSocketConfig.kt
│   ├── DataSourceConfig.kt         # 플랫폼 메타 DB DataSource
│   ├── OllamaConfig.kt
│   └── ChromaDbConfig.kt
│
├── web/                            # Controller 레이어
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
├── domain/                         # 도메인 엔티티 및 Repository
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
├── facade/                         # Facade 레이어 (여러 Service 오케스트레이션)
│   ├── query/
│   │   └── NLQueryFacade.kt        # NLQueryService + JobHistoryService + WebSocketNotifier 조율
│   ├── ingestion/
│   │   └── IngestionFacade.kt      # DocumentIngestionPipeline + JobHistoryService 조율
│   └── admin/
│       └── SystemAdminFacade.kt    # TargetSystemService + FileUploadService + GitSyncService 조율
│
├── service/                        # Service 레이어
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
├── llm/                            # LLM 오케스트레이션
│   ├── LlmOrchestrationService.kt
│   ├── PromptBuilder.kt
│   └── ResponseParser.kt
│
├── ingestion/                      # 임베딩 파이프라인
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
├── infra/                          # 외부 시스템 클라이언트
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
├── scheduler/                      # 정기 작업
│   └── ResultFileCleanupScheduler.kt
│
└── util/                           # 공통 유틸리티
    ├── ExcelGenerator.kt
    ├── TempFileStorage.kt
    └── HashUtils.kt
```

### 패키지 배치 원칙

- 도메인 엔티티는 반드시 `domain/` 하위에 위치합니다.
- 외부 API 클라이언트(Ollama, ChromaDB, Slack)는 `infra/` 하위에 위치합니다.
- 비즈니스 로직은 `service/` 또는 `llm/`, `ingestion/`에 위치합니다.
- 새 파일을 만들기 전에 적절한 패키지가 이미 존재하는지 확인합니다.

---

## 5. 코딩 규칙

### 5-1. 함수 및 클래스 설계

```kotlin
// ✅ 권장: 단일 책임, 명확한 함수명
@Service
class SqlDdlChunker : DocumentChunker {
    override fun chunk(file: File, system: TargetSystem): List<DocumentChunk> { ... }
    private fun extractTableName(ddl: String): String { ... }
    private fun extractColumnSummary(ddl: String): String { ... }
}

// ❌ 금지: 여러 책임을 한 함수에 몰아넣기
fun processEverything(file: File, system: TargetSystem, ollamaClient: OllamaClient) { ... }
```

### 5-2. data class 활용

```kotlin
// ✅ 권장: data class 사용, 불변 구조
data class DocumentChunk(
    val id: String,
    val text: String,
    val metadata: Map<String, String>,
    val sourceHash: String
)

// ❌ 금지: 가변 필드를 가진 DTO
class DocumentChunk {
    var id: String = ""
    var text: String = ""
}
```

### 5-3. Null 처리

```kotlin
// ✅ 권장: Elvis 연산자, let 스코프
val tableName = extractTableName(ddl) ?: "unknown"
system.gitUrl?.let { url -> gitClient.clone(url) }

// ❌ 금지: !! 강제 언박싱
val tableName = extractTableName(ddl)!!
```

### 5-4. 함수 길이 제한

- 단일 함수는 **40줄** 이하를 원칙으로 합니다.
- 40줄을 초과하면 private 함수로 분리합니다.

### 5-5. 상수 정의

```kotlin
// ✅ 권장: companion object에 상수 정의
companion object {
    const val MAX_CHUNK_TOKENS = 512
    const val EMBEDDING_BATCH_SIZE = 10
    const val SIMILARITY_THRESHOLD = 0.3
    const val COST_REJECT_THRESHOLD = 50_000.0
}

// ❌ 금지: 매직 넘버 하드코딩
if (tokens > 512) { ... }
if (similarity < 0.3) { ... }
```

### 5-6. 로깅

```kotlin
// ✅ 권장: LoggerFactory 사용, 구조적 로그
private val log = LoggerFactory.getLogger(javaClass)

log.info("[${system.name}] 청킹 완료: 파일=${file.name}, 청크=${chunks.size}개")
log.error("[${system.name}] 임베딩 실패: path=${relPath}", e)

// ❌ 금지: println, System.out.println
println("청킹 완료")

// ❌ 금지: 민감 정보 로깅
log.info("DB 접속 비밀번호: ${system.dbPasswordEnc}")
log.info("Git Token: ${system.gitAccessTokenEnc}")
```

### 5-7. LLM 모델명 설정 관리

LLM 모델명은 소스 코드에 하드코딩하지 않습니다. 반드시 `application.yaml`에서 관리하고 `@ConfigurationProperties`를 통해 주입합니다.

```kotlin
// ✅ 권장: application.yaml에서 모델명 주입
@ConfigurationProperties(prefix = "app.ollama")
data class OllamaProperties(
    val baseUrl: String,
    val embeddingModel: String = "nomic-embed-text",
    val generationModel: String = "gpt-oss:20b"   // 잠정; Phase 2 말 벤치마크 게이트로 확정
)

// OllamaClient에서 사용
suspend fun generate(prompt: String): String {
    val res = webClient.post().uri("$baseUrl/api/generate")
        .bodyValue(mapOf("model" to ollamaProperties.generationModel, ...))
        ...
}

// ❌ 금지: 모델명 하드코딩
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

### 5-8. 한국어 주석 원칙

```kotlin
// ✅ 권장: 한국어로 설계 의도 설명
// DDL + 한국어 설명 합성 → 한국어 자연어 검색 품질 향상
val embeddingText = """
    테이블명: $tableName
    설명: $comment
""".trimIndent()

// ✅ 권장: KDoc 형식 문서화
/**
 * SHA-256 해시를 이용한 증분 변경 감지.
 * 파일 수정 시각과 해시를 이중으로 비교하여 불필요한 재임베딩을 방지한다.
 */
private fun hasChanged(file: File, storedHash: String?): Boolean { ... }
```

---

## 6. 의존성 규칙

### 허용

```
Controller → Facade
Controller → Service (Service 하나만 필요한 경우)
Facade → Service (여러 Service 동시 의존 허용)
Facade → JobHistoryService
Service → infra/* (OllamaClient, ChromaDbClient, QueryExecutor 등)
Service → domain/* (Repository, Entity)
LlmOrchestrationService → OllamaClient, ChromaDbClient
DocumentIngestionPipeline → Chunker, OllamaClient, ChromaDbClient, JobHistoryService
Scheduler → Service (단방향)
```

### 금지

```
Service → Service (직접 호출 금지 — 필요 시 Facade 도입)
Facade → Facade (Facade 간 교차 호출 금지)
Service → Facade (역방향 금지)
infra/* → Service (역방향 금지)
domain/Entity → Service (역방향 금지)
Chunker → OllamaClient (청킹과 임베딩은 분리)
Controller → infra/* (Controller는 Service 또는 Facade를 통해서만)
```

### 순환 의존성 금지

```kotlin
// ❌ 절대 금지: 순환 의존
@Service
class ServiceA(private val serviceB: ServiceB)
@Service
class ServiceB(private val serviceA: ServiceA)
```

---

## 7. 비동기 처리 규칙

### Kotlin Coroutines 기반

이 프로젝트의 비동기 처리는 **Kotlin Coroutines**를 사용합니다.

```kotlin
// ✅ 권장: suspend fun 기반 비동기
@Service
class NLQueryService(...) {
    suspend fun processExtract(system: TargetSystem, member: Member, nl: String) {
        val sql = llmService.generateSql(nl, system)   // suspend
        val result = queryExecutor.execute(sql, system) // suspend
    }
}

// ✅ 권장: 병렬 처리
val namingDeferred = async { llmService.generateDatasetName(naturalLanguage) }
val sqlDeferred    = async { llmService.generateSql(naturalLanguage, system) }
val datasetName    = namingDeferred.await()
val sql            = sqlDeferred.await()
```

### 비동기 트리거 (Controller → 백그라운드)

`GlobalScope` 대신 애플리케이션 스코프 빈인 `ApplicationCoroutineScope`를 주입받아 사용합니다.
`GlobalScope`는 JVM 종료 시 cancel을 보장하지 않아, 컨테이너 재기동 시
"좀비" 작업(JobHistory가 `RUNNING`인 채로 남음, ChromaDB 부분 upsert 등)을 남깁니다.

```kotlin
// ✅ 필수: 라이프사이클이 관리되는 스코프 주입
@Component
class DocumentIngestionPipeline(
    private val appScope: ApplicationCoroutineScope,
    // ... 다른 의존성
) {
    fun triggerAsync(system: TargetSystem, mode: IngestionMode, triggeredBy: Member?) {
        appScope.launch { run(system, mode, triggeredBy) }   // 즉시 202 반환
    }
}
```

```kotlin
// ✅ 빈 정의 (전체 코드는 ARCHITECTURE_DESIGN §9-7-a 참조)
@Configuration
class CoroutineConfig {
    @Bean(destroyMethod = "close")
    fun applicationCoroutineScope(): ApplicationCoroutineScope = ApplicationCoroutineScope()
}
```

```kotlin
// ❌ 금지: GlobalScope는 종료 시점에 누수됨
@OptIn(DelicateCoroutinesApi::class)
GlobalScope.launch(Dispatchers.IO) { ... }
```

### 금지

```kotlin
// ❌ 금지: 블로킹 호출
val result = someService.doSomething().get()  // Future.get()
Thread.sleep(1000)
runBlocking { ... }  // 서비스 레이어에서의 runBlocking 사용 금지
```

### WebClient 사용 (외부 HTTP 호출)

```kotlin
// ✅ 권장: WebClient + awaitBody (Coroutines 통합)
val response = webClient.post()
    .uri("$baseUrl/api/embeddings")
    .bodyValue(body)
    .retrieve()
    .awaitBody<EmbeddingResponse>()

// ❌ 금지: RestTemplate, HttpClient 동기 호출
val response = restTemplate.postForObject(url, body, EmbeddingResponse::class.java)
```

---

## 8. 도메인 및 엔티티 규칙

### 8-1. 엔티티 설계

```kotlin
// ✅ 권장: data class + JPA 어노테이션
@Entity
@Table(name = "target_systems")
data class TargetSystem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    val name: String,

    // ... 나머지 필드
)

// ❌ 금지: 엔티티에 비즈니스 로직 메서드 작성
@Entity
data class TargetSystem(...) {
    fun connectToDatabase() { ... }   // 금지: 인프라 로직
    fun embed(chunker: Chunker) { ... } // 금지: 서비스 로직
}
```

### 8-2. 엔티티 수정 패턴

JPA entity는 불변(val)으로 설계하고, 수정이 필요하면 `copy()`를 사용합니다.

```kotlin
// ✅ 권장: copy()를 이용한 불변 업데이트
systemRepo.save(system.copy(
    ingestionStatus = IngestionStatus.COMPLETED,
    lastIngestedAt  = LocalDateTime.now(),
    totalChunkCount = totalChunks
))
```

### 8-3. 암호화 필드

DB 접속 정보, Git Token은 반드시 암호화하여 저장합니다.

```kotlin
// ✅ 권장: 저장 전 암호화, 사용 전 복호화
val encryptedUsername = tokenEncryptor.encrypt(request.dbUsername)
val encryptedPassword = tokenEncryptor.encrypt(request.dbPassword)

// 사용 시
val username = tokenEncryptor.decrypt(system.dbUsernameEnc)

// ❌ 금지: 평문 저장
@Column
val dbPassword: String  // 암호화 없이 평문 저장
```

### 8-4. 응답 DTO에서 민감 정보 제외

```kotlin
// ✅ 권장: 응답 DTO에서 민감 정보 제외
data class TargetSystemResponse(
    val id: Long,
    val name: String,
    val dbType: DbType,
    val dbUrl: String,
    val dbUsername: String,      // 마스킹 처리: "he***r"
    // dbPassword: 응답에 절대 포함 금지
    // gitAccessToken: 응답에 절대 포함 금지
)
```

---

## 9. 보안 규칙

### 9-1. SQL 검증 — 반드시 SqlValidator를 거쳐야 함

LLM이 생성한 SQL은 절대로 검증 없이 실행하지 않습니다.

```kotlin
// ✅ 필수: SqlValidator → ExplainAnalyzer → 실행
val safeSql = sqlValidator.validate(generatedSql)   // SELECT 전용, 금지 키워드 차단
val explain = explainAnalyzer.analyze(safeSql, system) // EXPLAIN으로 비용 검증
val finalSql = when (explain.verdict) {
    Verdict.REJECT -> throw QueryTooExpensiveException(...)
    Verdict.WARN   -> llmService.optimizeSql(safeSql, explain, system)
    Verdict.PASS   -> safeSql
}
queryExecutor.execute(finalSql, system)
```

### 9-2. 파일 업로드 검증

```kotlin
// ✅ 필수: 파일 타입 및 크기 검증
val allowedExtensions = setOf("md", "sql")
require(file.originalFilename?.substringAfterLast('.') in allowedExtensions) {
    "허용되지 않는 파일 형식입니다. (.md, .sql만 허용)"
}
require(file.size <= 10 * 1024 * 1024) { "파일 크기는 10MB를 초과할 수 없습니다." }

// ✅ 필수: Path Traversal 방어
val safeName = file.originalFilename
    ?.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")  // 특수문자 치환
    ?: throw IllegalArgumentException("파일명이 없습니다.")
```

### 9-3. 민감 정보 로그 금지

```kotlin
// ❌ 절대 금지: 민감 정보 로그 출력
log.info("Token: ${tokenEncryptor.decrypt(system.gitAccessTokenEnc)}")
log.debug("DB PW: $dbPassword")
log.error("API Key: $apiKey", e)
```

### 9-4. .env 파일 관리

```kotlin
// .gitignore에 반드시 포함
// .env
// **/application-prod.yml
// **/*-secret.yml
```

### 9-5. 세션 인증 규칙

이 프로젝트는 인증에 **Spring Session Data Redis**로 백업되는 **HTTP Session**을 사용합니다.  
액세스 토큰(JWT/Bearer)은 **발급하지 않습니다**. 이 API는 내부 전용이며 외부에 공개하지 않습니다.

```kotlin
// ✅ 필수: Spring Session + Redis 설정
// application.yaml
// spring.session.store-type: redis
// spring.session.timeout: 15m

// ✅ 필수: 세션 기반 인증 흐름
// 로그인  → HttpSession 생성 → Spring Session이 Redis에 저장 → JSESSIONID 쿠키 반환
// 요청    → JSESSIONID 읽기 → Spring Session이 Redis에서 조회 → 있으면 인증 / 없으면 401
// 로그아웃 → session.invalidate() → Redis에서 즉시 삭제

// ✅ 필수: 세션 유지를 위한 Heartbeat API
// 클라이언트가 10분마다 POST /api/auth/heartbeat 호출
// 인증된 요청이면 Redis의 TTL을 15분으로 재설정

// ✅ 필수: 보안 쿠키 설정
// server.servlet.session.cookie.http-only=true
// server.servlet.session.cookie.secure=true   (HTTPS 전용)
// server.servlet.session.cookie.same-site=Strict
```

```
❌ 금지: 인증 목적으로 JWT 또는 Bearer 액세스 토큰 발급
❌ 금지: Redis 백업 없이 JVM 힙에만 세션 데이터 저장
❌ 금지: Redis 포트를 loopback 외부에 노출 (반드시 127.0.0.1 바인딩)
❌ 금지: Redis를 비밀번호 없이 운영
❌ 금지: member_tokens에 REFRESH_TOKEN 타입 사용 (세션이 토큰 인증을 대체)
```

---

## 10. 에러 처리 규칙

### 10-1. 예외 계층 구조

```kotlin
// 모든 도메인 예외는 이 계층에서 확장
open class NlPlatformException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause)

class DataExtractionNotRequestedException(message: String)
    : NlPlatformException(message)

class QueryTooExpensiveException(message: String)
    : NlPlatformException(message)

class SqlValidationException(message: String)
    : NlPlatformException(message)

class SystemNotFoundException(id: Long)
    : NlPlatformException("시스템을 찾을 수 없습니다. (id=$id)")
```

### 10-2. JobHistory 연동 — 모든 비동기 작업에서 필수

```kotlin
// ✅ 필수 패턴: 모든 비동기 작업은 JobHistory를 기록
val job = jobHistoryService.start(
    jobType      = JobType.QUERY_EXTRACT,
    system       = system,
    triggeredBy  = member,
    inputSummary = naturalLanguage.take(200)
)
runCatching {
    // ... 작업 수행
    jobHistoryService.complete(job, "rows=${result.size}")
}.onFailure { e ->
    jobHistoryService.fail(job, e)  // stack_trace 자동 저장
    throw e
}
```

### 10-3. WebSocket 에러 응답

```kotlin
// ✅ 권장: 에러 발생 시 WebSocket으로 클라이언트에 알림
}.onFailure { e ->
    jobHistoryService.fail(job, e)
    webSocketNotifier.sendError(
        sessionId = sessionId,
        resultId  = resultId,
        errorCode = e.toErrorCode(),
        message   = e.toUserMessage()   // 사용자 친화적 메시지
    )
}
```

### 10-4. Controller 전역 예외 처리

```kotlin
// ✅ 권장: @ControllerAdvice로 중앙 집중 처리
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

## 11. 임베딩 파이프라인 규칙

임베딩 파이프라인의 설계 원칙입니다. 자세한 구현은 [architecture-design-v7.md](./docs/architecture-design-v7.md) 9장을 참조합니다.

### 11-1. 청킹 — 타입별 전용 Chunker만 사용

```kotlin
// ✅ 필수: DocumentChunker 인터페이스 구현
interface DocumentChunker {
    fun chunk(file: File, system: TargetSystem): List<DocumentChunk>
}

// 각 파일 타입은 전용 Chunker를 사용
.md   → MarkdownChunker   (헤딩 기준 분리)
.sql  → SqlDdlChunker     (CREATE TABLE 전체를 1청크, DDL+한국어설명 합성)
.java → JavaMethodExtractor + SourceCodeChunker  (메서드 단위 AST 청킹)
.kt   → KotlinMethodExtractor + SourceCodeChunker
```

### 11-2. 소스 코드 청킹 — 고정 크기 절대 금지

```kotlin
// ❌ 절대 금지: 고정 크기 청킹 (함수 중간에서 잘림)
content.chunked(500).map { DocumentChunk(it) }

// ✅ 필수: 메서드/함수 단위 AST 청킹
javaExtractor.extract(file).map { method ->
    DocumentChunk(text = buildEnrichedText(method), ...)
}
```

### 11-3. 기존 청크 삭제 후 재수집

```kotlin
// ✅ 필수: 재수집 전 기존 청크 삭제 (중복 방지)
chromaDbClient.deleteBySourcePath(system.chromaCollection, relPath)
val embeddings = ollamaClient.embedBatch(chunks.map { it.text })
chromaDbClient.upsert(system.chromaCollection, chunks, embeddings)
```

### 11-4. 증분 업데이트 — SHA-256 이중 확인

```kotlin
// ✅ 권장: 수정 시각 + SHA-256 이중 확인
if (file.lastModified() <= sinceEpochMs) return  // 시각 동일 → SKIP
val currentHash = sha256(file.readText())
if (currentHash == storedHash) return            // 내용 동일 → SKIP
// 이 경우만 재임베딩
```

### 11-5. 컬렉션 격리 — 시스템 혼용 금지

```kotlin
// ✅ 필수: 항상 system.chromaCollection을 명시
chromaDbClient.upsert(collection = system.chromaCollection, ...)
chromaDbClient.query(collection = system.chromaCollection, ...)

// ❌ 절대 금지: 하드코딩된 컬렉션명
chromaDbClient.query(collection = "business-context", ...)
```

### 11-6. nomic-embed-text — 태스크 prefix 필수 적용

`nomic-embed-text` 모델로 Ollama 임베딩 API를 호출할 때는 반드시 태스크에 맞는 prefix를 텍스트 앞에 붙여야 합니다.

| 상황 | Prefix | 설명 |
|------|--------|------|
| 문서 청크 저장 시 | `search_document:` | 인제스천 시 청크를 임베딩할 때 적용 |
| 쿼리 검색 시 | `search_query:` | RAG 검색을 위해 자연어 쿼리를 임베딩할 때 적용 |

```kotlin
// ✅ 필수: 임베딩 전 태스크 prefix 추가

// 문서 청크 인제스천 시 (DocumentIngestionPipeline)
val embeddingInputs = chunks.map { "search_document: ${it.text}" }
val embeddings = ollamaClient.embedBatch(embeddingInputs)

// RAG 검색 쿼리 임베딩 시 (LlmOrchestrationService)
val queryEmbedding = ollamaClient.embed("search_query: $naturalLanguage")

// ❌ 금지: prefix 없이 원문 그대로 전송
val embeddings = ollamaClient.embedBatch(chunks.map { it.text })
val queryEmbedding = ollamaClient.embed(naturalLanguage)
```

> **이유:** `nomic-embed-text`는 문서와 쿼리에 따라 내부 표현을 다르게 생성합니다. prefix를 생략하면 검색 정확도가 크게 저하됩니다.

### 11-7. 임베딩 차원 및 배치

- `nomic-embed-text` v1.5는 **768차원** 벡터를 반환합니다. ChromaDB 컬렉션은 반드시
  `dimension=768`로 생성합니다. (Matryoshka 256/512/768 모드는 설정 가능하나 현재 파이프라인의
  기본값과 가정은 768입니다.)
- 대량 임베딩에는 Ollama `/api/embed` 엔드포인트(`input: List<String>`)를 사용합니다.
  구 `/api/embeddings`는 단건 입력만 지원하므로 인제스천에서는 사용하지 않습니다.
- 배치 크기는 `app.ollama.embedding-batch-size`(기본 64)로 주입하며, 코드에 하드코딩하지 않습니다.

```kotlin
// ✅ 필수: /api/embed로 진짜 배치
webClient.post().uri("${props.baseUrl}/api/embed")
    .bodyValue(mapOf("model" to props.embeddingModel, "input" to batch))
    .retrieve().awaitBody<EmbedResponse>()

// ❌ 금지: /api/embeddings를 "가짜 배치" 루프로 직렬 호출
texts.chunked(10).flatMap { it.map { single -> embedSingle(single) } }
```

### 11-8. Hybrid Retrieval 및 Reranker (Phase 4 이후)

`app.retrieval.hybrid.enabled=true`일 때 RAG retrieval은 다음 단계를 **모두** 거쳐야 합니다.

1. Dense 검색 — `ChromaDbClient.query(...)` (의미적 유사도)
2. Sparse 검색 — `BM25Index.search(...)` (식별자/키워드 정확 매칭)
3. Cross-encoder rerank — `RerankerClient.rerank(question, candidates, topK)`

플래그가 켜진 상태에서 단계를 생략하는 것은 금지합니다. 전체 설계는 ARCHITECTURE_DESIGN §10 참조.
플래그가 꺼진 경로(Phase 3 출시)는 Dense 단독으로 동작합니다.

---

## 12. SQL 검증 규칙

PROD DB에서 EXPLAIN을 실행하여 사전 검증합니다. Dev DB는 사용하지 않습니다.

### 12-1. 검증 3단계는 반드시 순서대로 실행

```
레이어 1: SqlValidator   → SELECT 전용, 금지 키워드, LIMIT 강제
레이어 2: ExplainAnalyzer → EXPLAIN FORMAT=JSON (PROD DB, 데이터 읽기 없음)
레이어 3: 판정           → PASS / WARN(재질의) / REJECT(차단)
```

### 12-2. 금지 키워드 목록 (임의 축소 금지)

```kotlin
private val FORBIDDEN = setOf(
    "DROP","DELETE","UPDATE","INSERT","TRUNCATE",
    "ALTER","CREATE","GRANT","REVOKE",
    "EXEC","EXECUTE","XP_","SP_","--","/*","*/"
)
```

### 12-3. 임계값은 설정 파일에서 관리

```kotlin
// ❌ 금지: 임계값 하드코딩
if (cost > 50_000.0) throw QueryTooExpensiveException(...)

// ✅ 권장: application.yml에서 주입
@ConfigurationProperties(prefix = "app.query.explain")
data class ExplainProperties(
    val costRejectThreshold: Double = 50_000.0,
    val costWarnThreshold: Double   = 10_000.0,
    val rowsRejectThreshold: Long   = 1_000_000L
)
```

---

## 13. 테스트 규칙

### 13-1. 테스트 대상 레이어

아래 레이어는 **단위 테스트가 필수**입니다.

| 레이어 | 필수 여부 | 비고 |
|--------|:--------:|------|
| `util/` 전체 | ✅ 필수 | HashUtils, ExcelGenerator 등 |
| `service/` 전체 | ✅ 필수 | |
| `llm/` | ✅ 필수 | LlmOrchestrationService, PromptBuilder 등 |
| `ingestion/chunker/` | ✅ 필수 | 각 Chunker |
| `infra/db/SqlValidator` | ✅ 필수 | |
| `infra/db/explain/ExplainAnalyzer` | ✅ 필수 | |
| Controller | 🔵 권장 | MockMvc 사용 |

### 13-2. 테스트 작성 원칙

```kotlin
// ✅ 권장: KotlinTest + Mockito 조합
@ExtendWith(MockitoExtension::class)
class SqlValidatorTest {

    private val validator = SqlValidator()

    @Test
    @DisplayName("SELECT 문은 정상 통과한다")
    fun `SELECT statement should pass validation`() {
        val sql = "SELECT * FROM orders WHERE order_id = 1"
        val result = validator.validate(sql)
        assertThat(result).contains("LIMIT")  // LIMIT 강제 추가 확인
    }

    @Test
    @DisplayName("DROP 포함 SQL은 SqlValidationException을 던진다")
    fun `SQL with DROP keyword should throw SqlValidationException`() {
        val sql = "SELECT * FROM orders; DROP TABLE orders"
        assertThrows<SqlValidationException> { validator.validate(sql) }
    }
}
```

```kotlin
// ✅ 권장: 비동기 서비스 테스트
@ExtendWith(MockitoExtension::class)
class NLQueryServiceTest {

    @Mock private lateinit var llmService: LlmOrchestrationService
    @Mock private lateinit var queryExecutor: QueryExecutor
    @InjectMocks private lateinit var service: NLQueryService

    @Test
    @DisplayName("데이터 추출 요구 아님 응답 시 예외를 던진다")
    fun `should throw exception when LLM responds with not data extraction`() = runTest {
        whenever(llmService.generateSql(any(), any()))
            .thenReturn("데이터 추출 요구 아님")

        assertThrows<DataExtractionNotRequestedException> {
            service.processExtract(mockSystem, mockMember, "날씨 알려줘")
        }
    }
}
```

### 13-3. 테스트 명명 규칙

```kotlin
@DisplayName("한국어로 테스트 의도를 설명한다")
fun `영어 스네이크케이스로 함수명`() { ... }
```

### 13-4. 외부 의존성 처리

```kotlin
// ✅ 권장: OllamaClient, ChromaDbClient 등 외부 의존성은 Mock
@Mock private lateinit var ollamaClient: OllamaClient
@Mock private lateinit var chromaDbClient: ChromaDbClient

// ✅ 권장: 실제 DB가 필요한 테스트는 @DataJpaTest + H2 인메모리 DB
@DataJpaTest
class MemberRepositoryTest { ... }
```

### 13-5. 테스트 커버리지 목표

- 비즈니스 로직 클래스 (`service/`, `llm/`, `ingestion/`): **80% 이상**
- 유틸 클래스 (`util/`): **90% 이상**
- `infra/` 클라이언트 클래스: Mock 테스트로 대체 가능

---

## 14. 금지 사항

다음 사항은 어떤 이유로도 허용하지 않습니다.

### 절대 금지 (하드 룰)

```
❌ LLM 생성 SQL을 SqlValidator 없이 실행
❌ DB 비밀번호, Git Token 평문 저장 또는 로그 출력
❌ ChromaDB 쿼리 시 컬렉션명 하드코딩 (시스템 격리 위반)
❌ Service 간 직접 호출 (순환 의존 및 결합도 증가)
❌ !! 강제 언박싱 (NullPointerException 위험)
❌ 소스 코드 고정 크기 청킹 (의미 파괴)
❌ 암호화 키를 코드에 하드코딩
❌ 블로킹 I/O (RestTemplate, Thread.sleep 등)
❌ println, System.out.println 사용
❌ 임베딩 모델(nomic-embed-text)로 SQL 생성 시도
❌ LLM 모델명을 소스 코드에 하드코딩 (반드시 application.yaml에서 관리)
```

### 강력 권고 금지 (리뷰에서 지적 대상)

```
⚠️ 40줄 초과 단일 함수
⚠️ var 남발 (val 우선 원칙 위반)
⚠️ Magic Number 하드코딩 (상수화 필요)
⚠️ 테스트 없는 Service 클래스 추가
⚠️ 영어가 아닌 변수명/함수명
⚠️ JobHistory 없이 비동기 작업 실행
⚠️ WebSocket 응답 없이 비동기 작업 종료
```

---

## 15. 커밋 및 브랜치 규칙

### 브랜치 전략

```
main          ← 운영 배포 브랜치 (직접 push 금지)
develop       ← 개발 통합 브랜치
feature/{name} ← 기능 개발 브랜치
fix/{name}     ← 버그 수정 브랜치
```

### 커밋 메시지 형식

```
{type}: {한국어 설명}

타입:
  feat     새 기능 추가
  fix      버그 수정
  refactor 리팩토링 (기능 변경 없음)
  test     테스트 추가/수정
  docs     문서 수정
  chore    빌드, 설정, 의존성 변경
  style    코드 스타일 변경 (포맷팅 등)

예:
  feat: SqlDdlChunker에 한국어 설명 합성 로직 추가
  fix: ChromaDB upsert 시 컬렉션명 격리 오류 수정
  refactor: MarkdownChunker 헤딩 분리 로직 단순화
  test: ExplainAnalyzer MySQL 파서 단위 테스트 추가
  docs: AGENTS.md 임베딩 파이프라인 규칙 추가
```

### PR 규칙

- PR 제목은 커밋 메시지 형식을 따릅니다.
- PR 설명에는 변경 사항, 테스트 방법, 관련 이슈를 포함합니다.
- 모든 테스트를 통과해야 머지할 수 있습니다.
- Self-merge는 금지합니다 (최소 1인 리뷰 필요).

---

## 부록: 자주 묻는 질문

**Q. RAG는 Fine-tuning과 무엇이 다릅니까?**  
A. RAG는 LLM이 문서를 학습하지 않습니다. 매 요청마다 ChromaDB에서 관련 청크를 검색해 프롬프트에 삽입합니다. Fine-tuning은 모델 가중치를 변경하지만, RAG는 프롬프트 엔지니어링입니다.

**Q. 새로운 DBMS를 추가하려면 어떻게 합니까?**  
A. `DbType` enum에 새 항목을 추가하고, `ExplainAnalyzer`에 해당 DBMS의 EXPLAIN 파서를 구현합니다. `DynamicDataSourceFactory`의 `toDriverClassName()`에도 드라이버를 추가합니다.

**Q. 임베딩 파이프라인이 실패하면 어떻게 됩니까?**  
A. `JobHistory`에 `FAILED` 상태와 스택 트레이스가 저장됩니다. `TargetSystem.ingestionStatus`가 `FAILED`로 업데이트됩니다. 관리자 API `GET /admin/jobs/{id}`로 원인을 확인할 수 있습니다.

**Q. 임베딩 모델과 생성 모델의 역할 차이는 무엇입니까?**
A. `nomic-embed-text` v1.5는 텍스트를 **768차원** 벡터로 변환하는 임베딩 전용 모델입니다. SQL을 이해하거나 생성하지 않습니다. 생성 모델(현재 잠정 `gpt-oss:20b`, Phase 2 말 벤치마크 게이트로 확정 — 후보: `qwen2.5-coder:7b`, `deepseek-coder:6.7b`)은 프롬프트 context를 읽고 SQL을 그 자리에서 생성하는 언어 모델입니다.

---

---

## 16. 프롬프트 관리 규칙

### 16-1. 클래스 안 String 상수로 관리하면 안 되는 이유

이 프로젝트의 프롬프트는 스키마 컨텍스트, SQL 규칙, 출력 포맷 지시 등 여러 섹션으로 구성된 긴 텍스트입니다.  
Kotlin 소스 파일 안에 `String` 리터럴이나 `companion object` 상수로 삽입하면 다음 문제가 발생합니다.

- `\n`, `"""`, `$` 등 Kotlin 이스케이프 문법이 섞여 프롬프트 자체를 읽기 어려움
- 프롬프트를 수정할 때마다 프로젝트를 재컴파일해야 함
- PR 리뷰 시 들여쓰기·이스케이프 문자와 로직이 뒤섞여 diff가 지저분함
- 도메인 전문가(프롬프트 튜너) 등 비개발자가 안전하게 편집하기 어려움

### 16-2. 권장 방식 — 리소스 템플릿 파일 + PromptBuilder

프롬프트 **템플릿**을 `src/main/resources/prompts/` 하위에 `.md` 파일로 저장합니다.  
`PromptBuilder`는 이 템플릿을 시작 시 로드하고, 런타임에 변수를 치환하여 최종 프롬프트를 조립합니다.

```
src/main/resources/prompts/
├── sql-generation.md           # SQL 생성 메인 프롬프트
├── sql-optimization.md         # EXPLAIN WARN 시 재질의 프롬프트
├── dataset-naming.md           # 데이터셋 이름 생성 프롬프트
└── visualization-type.md       # 시각화 타입 선택 프롬프트
```

```kotlin
// PromptBuilder.kt — 시작 시 템플릿 로드, 호출 시 변수 치환
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

### 16-3. 템플릿 파일 형식

플레이스홀더 규칙은 `{{변수명}}`을 사용합니다. 가독성을 위해 Markdown 형식으로 작성합니다.

```markdown
<!-- prompts/sql-generation.md -->
당신은 {{db_type}} 데이터베이스 전문 SQL 생성 어시스턴트입니다.

## 스키마 컨텍스트
{{schema_context}}

## 관련 소스 코드
{{code_context}}

## 작업
아래 자연어 요청을 단일 SELECT SQL 문으로 변환하십시오.
- 위 스키마 컨텍스트에 존재하는 테이블과 컬럼만 사용하십시오.
- 반드시 LIMIT 절을 포함하십시오 (최대 10,000건).
- 전체 테이블을 스캔하는 서브쿼리는 사용하지 마십시오.

## 요청
{{natural_language}}

## 출력
SQL 문만 반환하십시오. 설명이나 마크다운 코드 블록은 포함하지 마십시오.
```

### 16-4. 규칙

```
✅ 모든 프롬프트는 src/main/resources/prompts/ 하위 .md 파일로 저장
✅ PromptBuilder만이 템플릿을 로드하고 조립할 수 있음
✅ 플레이스홀더는 반드시 {{변수명}} 규칙을 따름
✅ 각 템플릿 파일은 PromptType enum 항목과 1:1 대응
✅ PromptBuilder는 단위 테스트 필수 — 플레이스홀더 치환이 정확한지 검증

❌ String 리터럴 또는 companion object 상수로 프롬프트 텍스트 하드코딩
❌ PromptBuilder 외부에서 resourceLoader로 프롬프트 파일 직접 로드
❌ 목적이 다른 여러 프롬프트를 단일 템플릿 파일에 혼재
❌ 조립 완료된 프롬프트에 미치환 {{플레이스홀더}} 잔존
```

### 16-5. PromptBuilder 테스트

```kotlin
@ExtendWith(MockitoExtension::class)
class PromptBuilderTest {

    private val promptBuilder = PromptBuilder(DefaultResourceLoader())

    @Test
    @DisplayName("SQL 생성 프롬프트는 모든 플레이스홀더를 치환해야 한다")
    fun `sql generation prompt should substitute all placeholders`() {
        val prompt = promptBuilder.buildSqlGenerationPrompt(
            nl = "지역별 총 매출을 보여줘",
            schemaContext = "CREATE TABLE orders ...",
            codeContext = "fun getOrdersByRegion() ..."
        )
        assertThat(prompt).doesNotContain("{{")   // 미치환 플레이스홀더 없음 확인
        assertThat(prompt).contains("지역별 총 매출을 보여줘")
        assertThat(prompt).contains("CREATE TABLE orders")
    }
}
```

---

*이 문서는 프로젝트 진행에 따라 지속적으로 업데이트됩니다.*  
*최신 버전은 항상 이 파일을 기준으로 합니다.*
