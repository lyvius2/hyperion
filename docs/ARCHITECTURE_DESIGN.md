# Hyperion, NL-to-SQL Data Platform — Architecture Design v7

> Date: 2026-06-12  
> Target: Kotlin + Spring Boot 4, Ollama (Local LLM), Monolithic SPA  
> v7 Changes:
> - **Embedding pipeline detailed design merged into main document**
> - **Docker Compose configuration added** (Ollama + Spring Boot + ChromaDB + MySQL)
> - **LLM model file storage location and initial setup procedure added**

---

[한국어(Korean) 문서](ARCHITECTURE_DESIGN_KR.md)

## Table of Contents

1. [Requirements Review](#1-requirements-review)
2. [Key Design Decisions](#2-key-design-decisions)
3. [Overall Domain Model Overview](#3-overall-domain-model-overview)
4. [Domain 1 — Member](#4-domain-1--member)
5. [Domain 2 — Target System](#5-domain-2--target-system)
6. [Domain 3 — Result Board (QueryResult)](#6-domain-3--result-board-queryresult)
7. [Domain 4 — Job History](#7-domain-4--job-history)
8. [SQL Pre-validation Strategy — Single PROD DB Connection](#8-sql-pre-validation-strategy--single-prod-db-connection)
9. [Embedding Pipeline (RAG Pre-work)](#9-embedding-pipeline-rag-pre-work)
10. [RAG Runtime Search Flow](#10-rag-runtime-search-flow)
11. [Docker Compose Configuration](#11-docker-compose-configuration)
12. [Software Infrastructure Definition](#12-software-infrastructure-definition)
13. [EC2 Instance Spec Recommendations](#13-ec2-instance-spec-recommendations)
14. [System Architecture](#14-system-architecture)
15. [Technology Stack and Dependencies](#15-technology-stack-and-dependencies)
16. [Prompt Language Strategy](#16-prompt-language-strategy)
17. [Functional Design Detail](#17-functional-design-detail)
18. [Security Considerations](#18-security-considerations)
19. [Non-Functional Requirements and Limitations](#19-non-functional-requirements-and-limitations)
20. [Development Roadmap](#20-development-roadmap)

---

## 1. Requirements Review

| # | Requirement | Status | Notes |
|---|-------------|:------:|-------|
| 2 | SQL/coding-specialized LLM model | ✅ | `gpt-oss:20b` (tentative); finalized via the Phase 2-end benchmark gate (§20-A) |
| 3 | Markdown + SQL DDL + source code RAG | ✅ | Dedicated chunking per type + embedding pipeline |
| 4~7 | Natural language → SQL → Excel, async + WebSocket | ✅ | Spring WebSocket + Kotlin Coroutine |
| 8~10 | Natural language → SQL → d3.js HTML → ZIP | ✅ | Two-stage LLM call |
| 11 | Kotlin / Spring Boot 4 / Mustache monolithic | ✅ | |
| v4 | Target system selection and management | ✅ | Per-system directory + ChromaDB isolation |
| v5 | Result board (2-day retention) | ✅ | Soft delete + physical file deletion separated |
| v6 | Single PROD DB connection (EXPLAIN pre-validation) | ✅ | |
| v6 | Multi-DBMS support / Member / JobHistory domains | ✅ | |
| **v7** | **Embedding pipeline detailed implementation design** | ✅ | 4 trigger scenarios, incremental update, batch processing |
| **v7** | **Docker Compose** (Ollama+App+ChromaDB+MySQL) | ✅ | Model files on host volume mount |

---

## 2. Key Design Decisions

### 2-1. Fine-tuning vs RAG — Why We Chose RAG

This platform generates SQL against the **live schemas of multiple operational systems**.
For this workload, fine-tuning is rejected not because it is "too difficult," but because
**RAG is structurally superior**.

| Concern | RAG (chosen) | Fine-tuning |
|---------|--------------|-------------|
| Schema drift | Re-embed only the changed file (seconds) | Retrain model (hours to days) |
| Multi-system isolation | Separate ChromaDB collection per system | Per-system adapter required → ops nightmare |
| Onboarding a new system | Immediately usable on registration | Cold start (training queue) |
| Hallucination control | Original DDL injected verbatim → exact column names | Hallucinations persist even after weight absorption |
| Auditability / debugging | Used chunks logged → traceable | Weights are opaque |
| Hardware | T4 16GB is enough for inference | Fine-tuning a 20B model needs A100/H100 |
| Change blast radius | Reindex only | Separate training pipeline + MLOps |

> Fine-tuning may eventually have a supplementary role (e.g., a small LoRA adapter for in-house
> SQL style, or distillation into a smaller model), but only **after RAG hits a quality ceiling**.
> Until then, improving retrieval quality has a far higher ROI.

#### RAG Two-Stage Flow

```
━━━ Stage 1: Embedding (Pre-work — once + on every change) ━━━━━━━

Documents/DDL/Code → Chunking → nomic-embed-text → Vectors → ChromaDB storage

  · The generation model is NOT involved in this stage
  · This is NOT "memorizing" documents. It converts text into numerical coordinates
  · When documents change, only re-embedding is needed — no retraining required

━━━ Stage 2: RAG Runtime (on every user request) ━━━━━━━━━━━━━━━━━

Question → nomic-embed-text → Vector → ChromaDB similarity (+ BM25 hybrid)
         → Cross-encoder reranker re-ranks top-K
         → Retrieve 5~6 relevant chunks → Insert into prompt
         → Call generation model → Generate SQL

  · The generation model reads the context fresh on every request
  · Per-system ChromaDB collection isolation → No cross-system data contamination
```

### 2-2. DB Connection Architecture — Single PROD DB

In most production environments, the PROD server cannot connect to a Dev DB.  
`EXPLAIN` (no data reads) is used to pre-validate on the PROD DB, then execute on the same DB.

---

## 3. Overall Domain Model Overview

```
members ──(creator)──▶ target_systems ──1:N──▶ system_files
   │                         │
   └──(requester)──▶ query_results

job_history  (history of all async operations, stack trace on failure)
```

### Full ERD (Summary)

```
members                    target_systems              system_files
───────                    ──────────────              ────────────
id PK                      id PK              ◀─1:N─  id PK
username (unique)          name (unique)               system_id FK
email (unique)             root_path                   original_filename
password_hash (BCrypt)     chroma_collection            stored_path
display_name               db_url                      file_type
role (ADMIN|USER|VIEWER)   db_type                     file_size
status                     db_username_enc (AES-GCM)   source_hash
failed_login_count         db_password_enc (AES-GCM)   last_embedded_at
locked_until               git_url                     embedded_chunk_count
last_login_at              git_access_token_enc         uploaded_by FK
created_at / updated_at    slack_webhook_url            uploaded_at
                           slack_enabled
                           ingestion_status
                           last_ingested_at
                           total_chunk_count
                           created_by FK / created_at / updated_at

query_results                               job_history
─────────────                               ───────────
id PK (serial number)                       id PK
system_id FK                                job_type
requested_by FK                             reference_id / reference_type
dataset_name (LLM-named)                    system_id FK
natural_language                            triggered_by FK (NULL=scheduler)
generated_sql                               status (RUNNING|SUCCESS|FAILED|SKIPPED)
result_type (EXTRACT|VISUALIZE)             started_at / finished_at / duration_ms
status (PROCESSING|COMPLETED|FAILED)        input_summary / output_summary
file_path                                   error_code / error_message
expires_at (requested_at + 2 days)          stack_trace (full text on failure)
unused / file_deleted / file_deleted_at     created_at
slack_sent / slack_sent_at
error_message
requested_at / updated_at
```

---

## 4. Domain 1 — Member

### 4-1. DDL

```sql
CREATE TABLE members (
    id                  BIGINT       NOT NULL AUTO_INCREMENT   COMMENT 'Member PK',
    username            VARCHAR(50)  NOT NULL                  COMMENT 'Login ID',
    email               VARCHAR(200) NOT NULL                  COMMENT 'Email',
    password_hash       VARCHAR(255) NOT NULL                  COMMENT 'BCrypt hash',
    display_name        VARCHAR(100) NOT NULL                  COMMENT 'Display name',
    role                VARCHAR(20)  NOT NULL DEFAULT 'USER'   COMMENT 'ADMIN|USER|VIEWER',
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|INACTIVE|LOCKED|WITHDRAWN',
    profile_image_url   VARCHAR(500) NULL,
    failed_login_count  INT          NOT NULL DEFAULT 0        COMMENT 'Consecutive login failure count',
    locked_until        DATETIME     NULL                      COMMENT 'Lock release time (NULL=not locked)',
    password_changed_at DATETIME     NULL,
    last_login_at       DATETIME     NULL,
    last_login_ip       VARCHAR(45)  NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_members          PRIMARY KEY (id),
    CONSTRAINT uq_members_username UNIQUE (username),
    CONSTRAINT uq_members_email    UNIQUE (email)
) COMMENT = 'Member information';

CREATE TABLE member_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    token_type VARCHAR(30)  NOT NULL  COMMENT 'PASSWORD_RESET',
    token_hash VARCHAR(255) NOT NULL  COMMENT 'SHA-256 hash (original not stored)',
    expires_at DATETIME     NOT NULL,
    used_at    DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_member_tokens PRIMARY KEY (id),
    CONSTRAINT fk_member_tokens_member FOREIGN KEY (member_id) REFERENCES members(id)
) COMMENT = 'Member tokens (password reset only — auth uses Session, email verification not implemented)';
```

### 4-2. Authentication Design — Session + Redis

Authentication uses **HTTP Session backed by Spring Session Data Redis**.  
Access tokens (JWT/Bearer) are **not issued**. This API is internal-only and never exposed publicly.

#### Auth Flow

```
[Login]
  POST /auth/login {username, password}
  → BCrypt password verification + account status check
  → On success: create HttpSession → Spring Session stores it in Redis
  → Return 200 (session cookie: JSESSIONID set in response)

[Every Authenticated Request]
  → Spring Security reads JSESSIONID from cookie
  → Spring Session checks Redis for session data
  → Session found  → Set SecurityContext → proceed
  → Session not found → 401 Unauthorized

[Logout]
  POST /auth/logout
  → session.invalidate() → delete from Redis immediately
  → Clear JSESSIONID cookie

[Heartbeat — keep-alive while the user is active]
  POST /api/auth/heartbeat   (requires auth)
  → Touching an authenticated endpoint resets the session TTL in Redis
  → Client calls this every 10 minutes
  → Session TTL resets to 15 minutes from the time of the call
```

#### Session Lifecycle

| Item | Value |
|------|-------|
| Session TTL (max-inactive-interval) | **15 minutes** |
| Heartbeat API call interval (client-side) | **every 10 minutes** |
| TTL reset on heartbeat | Reset to **15 minutes** from call time |
| TTL on logout | Deleted immediately |

#### Why Redis (Redundancy Readiness)

In a single-node setup the session exists only in Redis.  
When scaling to multiple nodes (이중화), each node reads from the same Redis instance — no sticky sessions required.

```yaml
# application.yaml
spring:
  session:
    store-type: redis
    timeout: 15m
    redis:
      flush-mode: on-save
      namespace: hyperion:session
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: 6379
      password: ${REDIS_PASSWORD}
```

### 4-3. Role-Based Access Control

| Feature | ADMIN | USER | VIEWER |
|---------|:-----:|:----:|:------:|
| Register/edit/delete systems | ✅ | ❌ | ❌ |
| Upload files / Git Sync / run embedding | ✅ | ❌ | ❌ |
| Data extract / visualize requests | ✅ | ✅ | ❌ |
| Board view | ✅ | ✅ | ✅ |
| Member management | ✅ | ❌ | ❌ |

---

## 5. Domain 2 — Target System

### 5-1. DDL

```sql
CREATE TABLE target_systems (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(100) NOT NULL                  COMMENT 'System name (URL-safe)',
    description          VARCHAR(500) NULL,
    root_path            VARCHAR(500) NOT NULL                  COMMENT '/data/systems/{name}_{hash}/',
    chroma_collection    VARCHAR(100) NOT NULL                  COMMENT 'sys_{name}_{hash}',
    db_url               VARCHAR(500) NOT NULL                  COMMENT 'JDBC URL',
    db_type              VARCHAR(20)  NOT NULL                  COMMENT 'MYSQL|MARIADB|ORACLE|POSTGRESQL|MSSQL',
    db_username_enc      VARCHAR(500) NOT NULL                  COMMENT 'AES-GCM encrypted',
    db_password_enc      VARCHAR(500) NOT NULL                  COMMENT 'AES-GCM encrypted',
    git_url              VARCHAR(500) NULL,
    git_access_token_enc VARCHAR(500) NULL                      COMMENT 'AES-GCM encrypted',
    last_git_sync_at     DATETIME     NULL,
    last_commit_hash     VARCHAR(40)  NULL,
    slack_webhook_url    VARCHAR(500) NULL,
    slack_enabled        CHAR(1)      NOT NULL DEFAULT 'N',
    ingestion_status     VARCHAR(20)  NOT NULL DEFAULT 'NONE'   COMMENT 'NONE|RUNNING|COMPLETED|FAILED',
    last_ingested_at     DATETIME     NULL,
    total_chunk_count    INT          NOT NULL DEFAULT 0,
    created_by           BIGINT       NOT NULL,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_target_systems    PRIMARY KEY (id),
    CONSTRAINT uq_target_systems_name   UNIQUE (name),
    CONSTRAINT uq_target_systems_chroma UNIQUE (chroma_collection),
    CONSTRAINT fk_target_systems_member FOREIGN KEY (created_by) REFERENCES members(id)
) COMMENT = 'Target analysis system';

CREATE TABLE system_files (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    system_id            BIGINT       NOT NULL,
    original_filename    VARCHAR(255) NOT NULL,
    stored_path          VARCHAR(500) NOT NULL,
    file_type            VARCHAR(20)  NOT NULL   COMMENT 'MARKDOWN|SQL_DDL',
    file_size            BIGINT       NOT NULL,
    source_hash          VARCHAR(64)  NULL        COMMENT 'SHA-256 for change detection',
    last_embedded_at     DATETIME     NULL,
    embedded_chunk_count INT          NOT NULL DEFAULT 0,
    uploaded_by          BIGINT       NOT NULL,
    uploaded_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_system_files PRIMARY KEY (id),
    CONSTRAINT fk_system_files_system FOREIGN KEY (system_id)   REFERENCES target_systems(id),
    CONSTRAINT fk_system_files_member FOREIGN KEY (uploaded_by) REFERENCES members(id)
) COMMENT = 'System uploaded files';
```

### 5-2. Server Directory Structure

```
/data/systems/
├── hexa_a3f2b1c4/
│   ├── docs/           ← uploaded .md files
│   ├── ddl/            ← uploaded .sql files
│   └── sourcetree/     ← git clone target
│       └── .ingestion-ignore
└── kooroo-bss_7d1e3a9b/
    ├── docs/ / ddl/ / sourcetree/
```

### 5-3. Dynamic DataSource Creation

```kotlin
@Component
class DynamicDataSourceFactory(private val tokenEncryptor: TokenEncryptor) {
    private val cache = ConcurrentHashMap<Long, DataSource>()

    fun getDataSource(system: TargetSystem): DataSource =
        cache.getOrPut(system.id) { createDataSource(system) }

    fun invalidate(systemId: Long) = cache.remove(systemId)

    private fun createDataSource(system: TargetSystem) = HikariDataSource(HikariConfig().apply {
        jdbcUrl         = system.dbUrl
        driverClassName = system.dbType.toDriverClassName()
        username        = tokenEncryptor.decrypt(system.dbUsernameEnc)
        password        = tokenEncryptor.decrypt(system.dbPasswordEnc)
        maximumPoolSize = 5
        minimumIdle     = 1
        connectionTimeout = 10_000
        poolName        = "pool-${system.name}"
    })
}

enum class DbType {
    MYSQL, MARIADB, ORACLE, POSTGRESQL, MSSQL;
    fun toDriverClassName() = when (this) {
        MYSQL      -> "com.mysql.cj.jdbc.Driver"
        MARIADB    -> "org.mariadb.jdbc.Driver"
        ORACLE     -> "oracle.jdbc.OracleDriver"
        POSTGRESQL -> "org.postgresql.Driver"
        MSSQL      -> "com.microsoft.sqlserver.jdbc.SQLServerDriver"
    }
}
```

---

## 6. Domain 3 — Result Board (QueryResult)

### 6-1. DDL

```sql
CREATE TABLE query_results (
    id               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Serial number',
    system_id        BIGINT       NOT NULL,
    requested_by     BIGINT       NOT NULL,
    dataset_name     VARCHAR(200) NOT NULL                 COMMENT 'LLM-named dataset (max 30 chars)',
    natural_language TEXT         NOT NULL,
    generated_sql    TEXT         NULL,
    result_type      VARCHAR(20)  NOT NULL                 COMMENT 'EXTRACT|VISUALIZE',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING'  COMMENT 'PROCESSING|COMPLETED|FAILED',
    file_path        VARCHAR(500) NULL,
    expires_at       DATETIME     NOT NULL                 COMMENT 'requested_at + 2 days',
    unused           CHAR(1)      NOT NULL DEFAULT 'N'     COMMENT 'Y=hidden (record not deleted from DB)',
    file_deleted     CHAR(1)      NOT NULL DEFAULT 'N',
    file_deleted_at  DATETIME     NULL,
    slack_sent       CHAR(1)      NOT NULL DEFAULT 'N',
    slack_sent_at    DATETIME     NULL,
    error_message    TEXT         NULL,
    requested_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_query_results PRIMARY KEY (id),
    CONSTRAINT fk_qr_system FOREIGN KEY (system_id)    REFERENCES target_systems(id),
    CONSTRAINT fk_qr_member FOREIGN KEY (requested_by) REFERENCES members(id)
) COMMENT = 'Query request result board (2-day retention)';

CREATE INDEX idx_qr_system  ON query_results (system_id, requested_at DESC);
CREATE INDEX idx_qr_expires ON query_results (expires_at, file_deleted);
```

---

## 7. Domain 4 — Job History

### 7-1. DDL

```sql
CREATE TABLE job_history (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    job_type        VARCHAR(50)   NOT NULL  COMMENT 'QUERY_EXTRACT|QUERY_VISUALIZE|SQL_EXPLAIN|INGESTION_FULL|INGESTION_INCREMENTAL|INGESTION_SINGLE_FILE|GIT_CLONE|GIT_PULL|FILE_CLEANUP|SLACK_NOTIFY',
    reference_id    BIGINT        NULL,
    reference_type  VARCHAR(50)   NULL,
    system_id       BIGINT        NULL,
    triggered_by    BIGINT        NULL      COMMENT 'NULL=scheduler',
    status          VARCHAR(20)   NOT NULL DEFAULT 'RUNNING'  COMMENT 'RUNNING|SUCCESS|FAILED|SKIPPED',
    started_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     DATETIME      NULL,
    duration_ms     BIGINT        NULL,
    input_summary   VARCHAR(1000) NULL,
    output_summary  VARCHAR(1000) NULL,
    error_code      VARCHAR(100)  NULL      COMMENT 'Exception class name',
    error_message   VARCHAR(2000) NULL,
    stack_trace     TEXT          NULL      COMMENT 'Full stack trace on failure',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_job_history PRIMARY KEY (id),
    CONSTRAINT fk_jh_system FOREIGN KEY (system_id)    REFERENCES target_systems(id) ON DELETE SET NULL,
    CONSTRAINT fk_jh_member FOREIGN KEY (triggered_by) REFERENCES members(id)        ON DELETE SET NULL
) COMMENT = 'Job execution history';

CREATE INDEX idx_jh_type   ON job_history (job_type, started_at DESC);
CREATE INDEX idx_jh_system ON job_history (system_id, started_at DESC);
CREATE INDEX idx_jh_status ON job_history (status, started_at DESC);
```

### 7-2. Helper Pattern

```kotlin
// Used consistently across all async operations
val job = jobHistoryService.start(JobType.QUERY_EXTRACT, system, member, inputSummary = nl.take(200))
runCatching {
    // ... perform work ...
    jobHistoryService.complete(job, "rows=1234")
}.onFailure { e ->
    jobHistoryService.fail(job, e)  // error_code, error_message, stack_trace saved automatically
    throw e
}
```

---

## 8. SQL Pre-validation Strategy — Single PROD DB Connection

### 8-1. Three-Layer Defense

```
LLM-generated SQL
      │
      ▼ Layer 1 — Static Analysis (no execution)
        · Enforce SELECT only    · Block forbidden keywords
        · Block multiple statements (;)  · Enforce LIMIT 10,000
      │ Pass
      ▼ Layer 2 — EXPLAIN (PROD DB, no data reads)
        · Extract query_cost / rows_examined / access_type
        · Detect Full Table Scan, filesort, temporary table
      │
      ├── REJECT → QueryTooExpensiveException
      ├── WARN   → Re-query LLM for optimization
      └── PASS   → Execute on PROD DB
```

### 8-2. EXPLAIN Syntax per DBMS and Thresholds

| DBMS | Syntax | Cost Path |
|------|--------|---------|
| MySQL / MariaDB | `EXPLAIN FORMAT=JSON {sql}` | `$.query_block.cost_info.query_cost` |
| PostgreSQL | `EXPLAIN (FORMAT JSON, COSTS TRUE) {sql}` | `$[0].Plan."Total Cost"` |
| Oracle | `EXPLAIN PLAN FOR {sql}` → DBMS_XPLAN | Text parsing |
| MS SQL Server | `SET SHOWPLAN_XML ON` then execute | XML parsing |

| Metric | PASS | WARN | REJECT |
|--------|:----:|:----:|:------:|
| `query_cost` | < 10,000 | ~50,000 | > 50,000 |
| `rows_examined` | < 100,000 | ~1,000,000 | > 1,000,000 |
| `access_type` | range or better | — | ALL (Full Scan) |

---

## 9. Embedding Pipeline (RAG Pre-work)

### 9-1. Separation of Roles Between Two Models

| Model (default) | Stage | Role |
|-----------------|-------|------|
| `nomic-embed-text` v1.5 | Pre-embedding + runtime query conversion | Converts text → **768-dimension** vectors only |
| `gpt-oss:20b` (tentative) | Runtime SQL/HTML generation | Reads prompt context and generates SQL on the spot |

> **Model names are never hardcoded.** They are injected via `application.yaml`
> (`app.ollama.embedding-model`, `app.ollama.generation-model`) and finalized after the
> benchmark gate at the end of Phase 2 (§20). Candidates: `gpt-oss:20b`,
> `qwen2.5-coder:7b`, `deepseek-coder:6.7b`.

### 9-2. Full Pipeline Flow

```
/data/systems/{name}_{hash}/docs/ ddl/ sourcetree/
          │
          ▼ 1. Enumerate files + apply .ingestion-ignore
          ▼ 2. Type-specific chunking
               .md   → MarkdownChunker  (## heading boundary, re-split by paragraph if >512 tokens)
               .sql  → SqlDdlChunker    (entire CREATE TABLE = 1 chunk, synthesize DDL + description)
               .java/.kt → SourceCodeChunker (JavaParser AST, method-level)
          ▼ 3. SHA-256 hash → SKIP if unchanged
          ▼ 4. Ollama `/api/embed` true batch call (N inputs per request)
               text → FloatArray(768 dimensions)
          ▼ 5. ChromaDB upsert (batches of 100)
               collection: sys_{name}_{hash}
               stored: vector + original text + metadata (type, source_path, table_name, etc.)
          ▼ 6. Update SystemFile.lastEmbeddedAt + complete JobHistory
```

### 9-3. Four Trigger Scenarios

| Scenario | Trigger | Scope |
|----------|---------|-------|
| A. After file upload | Automatically after `POST /admin/systems/{id}/files` | That file only |
| B. After Git Sync | Automatically after `POST /admin/systems/{id}/git/sync` | Changed files only |
| C. Manual admin | `POST /admin/systems/{id}/ingest` | FULL or INCREMENTAL |
| D. On app startup | `@EventListener(ApplicationReadyEvent)` | Systems with `ingestionStatus=NONE` only |

### 9-4. Source Type Chunking Detail

#### MarkdownChunker

```kotlin
@Component
class MarkdownChunker : DocumentChunker {
    override fun chunk(file: File, system: TargetSystem): List<DocumentChunk> {
        val content      = file.readText(Charsets.UTF_8)
        val relativePath = file.relativeTo(File(system.rootPath)).path
        return splitByHeadings(content).flatMapIndexed { idx, section ->
            val subChunks = if (estimateTokens(section.text) > 512)
                splitByParagraph(section.text).mapIndexed { i, t -> "${idx}_$i" to t }
            else listOf("$idx" to section.text)
            subChunks.map { (subIdx, text) ->
                DocumentChunk(
                    id   = generateId(system.id, relativePath, subIdx.hashCode()),
                    text = text.trim(),
                    metadata = mapOf("system_id" to system.id.toString(),
                                     "type" to "markdown",
                                     "source_path" to relativePath,
                                     "heading" to (section.headingPath ?: ""),
                                     "category" to inferCategory(relativePath)),
                    sourceHash = sha256(text)
                )
            }
        }
    }
    // splitByHeadings: splits sections by ## or # heading boundaries
    // inferCategory: infers from whether path contains schema/business/glossary/architecture
}
```

#### SqlDdlChunker

```kotlin
@Component
class SqlDdlChunker : DocumentChunker {
    private val DDL_PATTERN = Regex(
        """(CREATE\s+TABLE[\s\S]+?;|ALTER\s+TABLE[\s\S]+?;|CREATE\s+(?:UNIQUE\s+)?INDEX[\s\S]+?;)""",
        RegexOption.IGNORE_CASE
    )
    override fun chunk(file: File, system: TargetSystem): List<DocumentChunk> {
        val relativePath = file.relativeTo(File(system.rootPath)).path
        return DDL_PATTERN.findAll(file.readText()).mapIndexed { i, match ->
            val ddl = match.value.trim()
            val tableName = extractTableName(ddl)
            // Synthesize DDL + description → maps natural language queries to English DDL column names
            val embeddingText = """
                Table name: $tableName
                Description: ${extractTableComment(ddl) ?: tableName}
                Key columns: ${extractColumnSummary(ddl)}
                DDL:
                $ddl
            """.trimIndent()
            DocumentChunk(
                id   = generateId(system.id, relativePath, i),
                text = embeddingText,
                metadata = mapOf("system_id" to system.id.toString(),
                                 "type" to "sql_ddl",
                                 "source_path" to relativePath,
                                 "table_name" to tableName,
                                 "columns" to extractColumnNames(ddl).joinToString(",")),
                sourceHash = sha256(ddl)
            )
        }.toList()
    }
}
```

#### SourceCodeChunker (JavaParser-based, method-level)

```kotlin
@Component
class SourceCodeChunker(
    private val javaExtractor: JavaMethodExtractor,
    private val kotlinExtractor: KotlinMethodExtractor
) : DocumentChunker {
    override fun chunk(file: File, system: TargetSystem): List<DocumentChunk> {
        if (!shouldIngest(file, system)) return emptyList()
        val relativePath = file.relativeTo(File(system.rootPath)).path
        val methods = when (file.extension.lowercase()) {
            "java" -> javaExtractor.extract(file)
            "kt"   -> kotlinExtractor.extract(file)
            else   -> return emptyList()
        }
        return methods.mapIndexed { i, method ->
            // Synthesize class context + annotations + method body
            val enrichedText = buildString {
                appendLine("// File: $relativePath")
                appendLine("// Class: ${method.className}  Package: ${method.packageName}")
                if (method.annotations.isNotEmpty())
                    appendLine("// Annotations: ${method.annotations.joinToString(", ")}")
                appendLine("// Method: ${method.signature}")
                append(method.fullText)
            }
            DocumentChunk(
                id   = generateId(system.id, "$relativePath#${method.name}", i),
                text = enrichedText,
                metadata = mapOf("system_id" to system.id.toString(),
                                 "type" to "source_code",
                                 "source_path" to relativePath,
                                 "class_name" to method.className,
                                 "method_name" to method.name,
                                 "layer" to inferLayer(relativePath)),
                sourceHash = sha256(method.fullText)
            )
        }
    }
    // shouldIngest: applies .ingestion-ignore + prioritizes Service/Repository/Domain/DTO layers
    // inferLayer: infers from whether path contains /service/ /repository/ /domain/ /dto/
}
```

### 9-5. OllamaClient — Embedding + Inference

**Model names are injected via `OllamaProperties`.** The client uses Ollama's newer
`/api/embed` endpoint for true batch embedding (the older `/api/embeddings` accepts only a
single input).

```kotlin
@ConfigurationProperties(prefix = "app.ollama")
data class OllamaProperties(
    val baseUrl: String,
    val embeddingModel: String = "nomic-embed-text",
    val generationModel: String = "gpt-oss:20b",
    val embeddingBatchSize: Int = 64,         // inputs per batched call
    val generationTemperature: Double = 0.1,
    val generationMaxTokens: Int = 1024
)

@Component
class OllamaClient(
    private val props: OllamaProperties,
    private val webClient: WebClient
) {
    // ── Single embedding (used for runtime query conversion) ─────────────
    suspend fun embed(text: String): FloatArray = embedBatch(listOf(text)).first()

    // ── True batch embedding via `/api/embed` (input: List<String>) ─────
    suspend fun embedBatch(texts: List<String>): List<FloatArray> =
        texts.chunked(props.embeddingBatchSize).flatMap { batch ->
            val res = webClient.post().uri("${props.baseUrl}/api/embed")
                .bodyValue(mapOf(
                    "model" to props.embeddingModel,
                    "input" to batch
                ))
                .retrieve().awaitBody<EmbedResponse>()
            res.embeddings.map { it.toFloatArray() }
        }

    // ── SQL/HTML generation (runtime) ──────────────────────────────────
    suspend fun generate(
        prompt: String,
        temperature: Double = props.generationTemperature
    ): String {
        val res = webClient.post().uri("${props.baseUrl}/api/generate")
            .bodyValue(mapOf(
                "model"   to props.generationModel,
                "prompt"  to prompt,
                "stream"  to false,
                "options" to mapOf(
                    "temperature" to temperature,
                    "num_predict" to props.generationMaxTokens
                )
            ))
            .retrieve().awaitBody<GenerateResponse>()
        return res.response.trim()
    }

    data class EmbedResponse(val embeddings: List<List<Float>>)
    data class GenerateResponse(val response: String)
}
```

```yaml
# application.yaml — swap models by editing YAML, no code changes
app:
  ollama:
    base-url: http://ollama:11434
    embedding-model: nomic-embed-text          # 768-dim
    generation-model: gpt-oss:20b              # may be replaced after the benchmark gate
    embedding-batch-size: 64
    generation-temperature: 0.1
    generation-max-tokens: 1024
```

> **Forbidden:** writing model names as string literals inside `OllamaClient`. See AGENTS.md §5-7.

### 9-6. ChromaDbClient — Storage and Search

```kotlin
@Component
class ChromaDbClient(@Value("\${app.chromadb.base-url}") private val baseUrl: String,
                     private val webClient: WebClient) {

    // upsert (batches of 100)
    suspend fun upsert(collection: String, chunks: List<DocumentChunk>, embeddings: List<FloatArray>) {
        chunks.zip(embeddings).chunked(100).forEach { batch ->
            webClient.post().uri("$baseUrl/api/v1/collections/$collection/upsert")
                .bodyValue(mapOf(
                    "ids"        to batch.map { it.first.id },
                    "embeddings" to batch.map { it.second.toList() },
                    "documents"  to batch.map { it.first.text },
                    "metadatas"  to batch.map {
                        it.first.metadata + mapOf("source_hash" to it.first.sourceHash) }
                )).retrieve().awaitBodilessEntity()
        }
    }

    // Similarity search (runtime — called on every request)
    suspend fun query(collection: String, queryEmbedding: FloatArray,
                      topK: Int = 6, typeFilter: List<String>? = null): List<RetrievedChunk> {
        val body = mutableMapOf<String, Any>(
            "query_embeddings" to listOf(queryEmbedding.toList()),
            "n_results"        to topK,
            "include"          to listOf("documents", "metadatas", "distances")
        )
        if (typeFilter != null) body["where"] = mapOf("type" to mapOf("\$in" to typeFilter))

        val res = webClient.post().uri("$baseUrl/api/v1/collections/$collection/query")
            .bodyValue(body).retrieve().awaitBody<QueryResponse>()

        return res.documents.first().zip(res.metadatas.first()).zip(res.distances.first())
            .map { (dm, dist) -> RetrievedChunk(dm.first, dm.second, 1.0 - dist) }
            .filter { it.similarity > 0.3 }   // Filter out low-similarity chunks
    }

    // Delete existing chunks before re-ingestion
    suspend fun deleteBySourcePath(collection: String, sourcePath: String) {
        webClient.post().uri("$baseUrl/api/v1/collections/$collection/delete")
            .bodyValue(mapOf("where" to mapOf("source_path" to mapOf("\$eq" to sourcePath))))
            .retrieve().awaitBodilessEntity()
    }

    data class QueryResponse(val documents: List<List<String>>,
                             val metadatas: List<List<Map<String,String>>>,
                             val distances: List<List<Double>>)
    data class RetrievedChunk(val text: String, val metadata: Map<String,String>,
                               val similarity: Double)
}
```

### 9-7. DocumentIngestionPipeline — Pipeline Assembly

> **`GlobalScope.launch` is forbidden.** Background work runs on the lifecycle-managed
> `ApplicationCoroutineScope` bean. (see §9-7-a)

#### 9-7-a. ApplicationCoroutineScope bean

```kotlin
@Configuration
class CoroutineConfig {
    /**
     * Explicit scope so in-flight jobs are cancelled cleanly at shutdown.
     * SupervisorJob keeps sibling jobs alive when one fails.
     */
    @Bean(destroyMethod = "close")
    fun applicationCoroutineScope(): ApplicationCoroutineScope =
        ApplicationCoroutineScope()
}

class ApplicationCoroutineScope : CoroutineScope, AutoCloseable {
    private val job = SupervisorJob()
    override val coroutineContext = job + Dispatchers.IO +
        CoroutineName("hyperion-app")
    override fun close() { job.cancel() }   // invoked on @PreDestroy
}
```

#### 9-7-b. Pipeline code

```kotlin
@Service
class DocumentIngestionPipeline(
    private val markdownChunker: MarkdownChunker,
    private val sqlDdlChunker: SqlDdlChunker,
    private val sourceCodeChunker: SourceCodeChunker,
    private val ollamaClient: OllamaClient,
    private val chromaDbClient: ChromaDbClient,
    private val systemRepo: TargetSystemRepository,
    private val fileRepo: SystemFileRepository,
    private val jobHistoryService: JobHistoryService,
    private val appScope: ApplicationCoroutineScope   // ★ injected
) {
    fun triggerAsync(system: TargetSystem, mode: IngestionMode, triggeredBy: Member?) {
        appScope.launch { run(system, mode, triggeredBy) }
    }

    suspend fun run(system: TargetSystem, mode: IngestionMode, triggeredBy: Member?) {
        val job = jobHistoryService.start(
            if (mode == IngestionMode.FULL) JobType.INGESTION_FULL
            else JobType.INGESTION_INCREMENTAL, system, triggeredBy)
        systemRepo.save(system.copy(ingestionStatus = IngestionStatus.RUNNING))

        runCatching {
            val files = collectFiles(File(system.rootPath), mode, system)
            var totalChunks = 0
            files.forEach { file ->
                val chunks = chunkFile(file, system)
                if (chunks.isEmpty()) return@forEach
                val relPath = file.relativeTo(File(system.rootPath)).path
                chromaDbClient.deleteBySourcePath(system.chromaCollection, relPath) // delete before re-ingest
                val embeddings = ollamaClient.embedBatch(chunks.map { it.text })
                chromaDbClient.upsert(system.chromaCollection, chunks, embeddings)
                totalChunks += chunks.size
                // Update SystemFile status
                fileRepo.findBySystemIdAndStoredPath(system.id, relPath)?.let {
                    fileRepo.save(it.copy(lastEmbeddedAt = LocalDateTime.now(),
                                          embeddedChunkCount = chunks.size,
                                          sourceHash = sha256(file.readText())))
                }
            }
            systemRepo.save(system.copy(ingestionStatus = IngestionStatus.COMPLETED,
                                         lastIngestedAt = LocalDateTime.now(),
                                         totalChunkCount = totalChunks))
            jobHistoryService.complete(job, "${files.size} files, $totalChunks chunks")
        }.onFailure { e ->
            systemRepo.save(system.copy(ingestionStatus = IngestionStatus.FAILED))
            jobHistoryService.fail(job, e)
        }
    }

    // Single file ingestion (called automatically after file upload)
    suspend fun ingestSingleFile(file: File, system: TargetSystem, triggeredBy: Member?) {
        val job = jobHistoryService.start(JobType.INGESTION_SINGLE_FILE, system, triggeredBy,
                                          inputSummary = file.name)
        runCatching {
            val chunks = chunkFile(file, system)
            val relPath = file.relativeTo(File(system.rootPath)).path
            chromaDbClient.deleteBySourcePath(system.chromaCollection, relPath)
            chromaDbClient.upsert(system.chromaCollection, chunks,
                                  ollamaClient.embedBatch(chunks.map { it.text }))
            jobHistoryService.complete(job, "${chunks.size} chunks")
        }.onFailure { jobHistoryService.fail(job, it) }
    }

    private fun collectFiles(root: File, mode: IngestionMode, system: TargetSystem): List<File> {
        val all = root.walkTopDown().filter { it.isFile }
            .filter { it.extension.lowercase() in setOf("md","sql","java","kt") }
            .filter { !it.path.contains("/.git/") }.toList()
        if (mode == IngestionMode.FULL) return all
        val since = system.lastIngestedAt ?: return all
        return all.filter { it.lastModified() > since.toInstant(ZoneOffset.UTC).toEpochMilli() }
    }

    private fun chunkFile(file: File, system: TargetSystem) = when (file.extension.lowercase()) {
        "md"         -> markdownChunker.chunk(file, system)
        "sql"        -> sqlDdlChunker.chunk(file, system)
        "java", "kt" -> sourceCodeChunker.chunk(file, system)
        else         -> emptyList()
    }
}
```

### 9-8. Incremental Update — SHA-256 Double Check

```
File modification time > lastIngestedAt?
  No  → SKIP
  Yes → Calculate SHA-256 → Compare with ChromaDB source_hash
           Same → SKIP (time differs but content identical)
           Different → Delete existing chunks → Re-chunk → Re-embed → upsert
```

---

## 10. RAG Runtime Search Flow

### 10-1. Three-Stage Retrieval (Dense + Sparse Hybrid → Reranker → Context Assembly)

Cosine similarity alone often pulls in chunks whose table/column names are *similar but
from a different domain*. Since the next ceiling of SQL-generation accuracy is almost
always retrieval quality, we apply three stages.

```
User question
  │
  ▼ ① Query embedding (nomic-embed-text, "search_query:" prefix)
  │
  ├──▶ Dense search  (ChromaDB cosine, topK=10 per type) ──┐
  │                                                         ├─▶ ② Candidate union (≤ ~30)
  └──▶ Sparse search (BM25, table/column keyword match)  ──┘
                                                              │
                                                              ▼ ③ Cross-encoder rerank
                                                                (bge-reranker-base)
                                                              │
                                                              ▼ Pick top-6
                                                              │
                                                              ▼ Type ratio (DDL≥3, code≥2, MD≥1)
                                                              │
                                                              ▼ Assemble context → generation
```

| Stage | Component | Responsibility |
|-------|-----------|----------------|
| Dense | `ChromaDbClient.query()` | Semantic similarity |
| Sparse | `BM25Index` (in-process Lucene) | Exact identifier match |
| Rerank | `RerankerClient` (e.g. `bge-reranker-base` hosted on Ollama/Triton) | Question-chunk fit |
| Compose | `ContextAssembler` | Type ratios, dedupe, token-budget |

### 10-2. Implementation

```kotlin
@Service
class LlmOrchestrationService(
    private val ollamaClient: OllamaClient,
    private val chromaDbClient: ChromaDbClient,
    private val bm25Index: BM25Index,
    private val reranker: RerankerClient,
    private val promptBuilder: PromptBuilder
) {
    suspend fun generateSql(naturalLanguage: String, system: TargetSystem): String {
        // ① Query embedding (task prefix is mandatory — AGENTS §11-6)
        val queryVector = ollamaClient.embed("search_query: $naturalLanguage")

        // ② Dense + Sparse candidates in parallel
        val denseDeferred  = coroutineScope { async {
            chromaDbClient.query(system.chromaCollection, queryVector, topK = 10)
        } }
        val sparseDeferred = coroutineScope { async {
            bm25Index.search(system.id, naturalLanguage, topK = 10)
        } }
        val candidates = (denseDeferred.await() + sparseDeferred.await())
            .distinctBy { it.id }

        // ③ Cross-encoder reranker selects top-6
        val reranked = reranker.rerank(naturalLanguage, candidates, topK = 6)

        // ④ Enforce type ratios + assemble context
        val context = ContextAssembler.assemble(
            chunks = reranked,
            ratios = mapOf("sql_ddl" to 3, "source_code" to 2, "markdown" to 1)
        )

        // ⑤ Externalised prompt template (AGENTS §16)
        val prompt = promptBuilder.buildSqlGenerationPrompt(
            nl = naturalLanguage,
            dbType = system.dbType.name,
            systemName = system.name,
            context = context
        )
        return ollamaClient.generate(prompt)
    }
}
```

### 10-3. Reranker Hosting — Prefer Light Models

- Default: `bge-reranker-base` (~278 MB) — loaded on the same GPU as the generation model
- Alternative: `bge-reranker-v2-m3` (multilingual, ~1.5 GB) — pick this if Korean queries dominate
- Latency target: < 200 ms on T4 for 6–30 candidates

> **Phased rollout:** Phase 3 ships Dense-only retrieval. Phase 4 enables Hybrid + Reranker,
> each guarded by a feature flag (`app.retrieval.hybrid.enabled`, `app.retrieval.rerank.enabled`).

---

## 11. Docker Compose Configuration

### 11-1. LLM Model File Storage Location

> **Ollama model files must be stored in the host directory `/data/ollama`.**  
> Even if containers are recreated or updated, models do not need to be re-downloaded.

```
/data/ollama/                     ← host directory (mounted into container)
└── models/
    ├── manifests/
    │   └── registry.ollama.ai/library/
    │       ├── gpt-oss/          ← generation model (tentative, finalized after §20-A bench)
    │       ├── qwen2.5-coder/    ← benchmark candidate
    │       ├── deepseek-coder/   ← benchmark candidate
    │       ├── nomic-embed-text/ ← embeddings (768-dim)
    │       └── bge-reranker-base/ ← reranker (Phase 4)
    └── blobs/                    ← actual weight files
        ├── sha256-xxxx...        ← gpt-oss:20b (~13GB)
        └── sha256-yyyy...        ← nomic-embed-text (~274MB)
```

**Initial model download — run only once after first container startup:**

```bash
# Run inside the Ollama container
docker compose exec ollama ollama pull gpt-oss:20b              # ~13GB, 30~60 min (tentative — finalize via §20-A)
docker compose exec ollama ollama pull nomic-embed-text         # ~274MB, 1~2 min
# Optional candidates for the benchmark gate (§20-A):
# docker compose exec ollama ollama pull qwen2.5-coder:7b       # ~4.7GB
# docker compose exec ollama ollama pull deepseek-coder:6.7b    # ~3.8GB
# Phase 4 reranker:
# docker compose exec ollama ollama pull bge-reranker-base      # ~278MB (or HF/Triton)

# Verify
docker compose exec ollama ollama list
```

After this, `docker compose down` → `docker compose up -d` will reuse the files stored in `/data/ollama`.

### 11-2. Pre-create Directories (Once on First Server Setup)

```bash
sudo mkdir -p /data/{ollama,chromadb,mysql,redis,systems,results} /tmp/nl-platform
sudo chmod 755 /data/ollama /data/chromadb /data/mysql /data/redis \
               /data/systems /data/results /tmp/nl-platform
```

### 11-3. docker-compose.yml

```yaml
services:

  # ── MySQL (Platform Meta DB) ──────────────────────────────────────────
  mysql:
    image: mysql:8.0
    container_name: nlp-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE:      ${MYSQL_DATABASE:-nlplatform}
      MYSQL_USER:          ${MYSQL_USER:-nlpuser}
      MYSQL_PASSWORD:      ${MYSQL_PASSWORD}
      TZ: Asia/Seoul
    ports:
      - "127.0.0.1:3306:3306"         # loopback only
    volumes:
      - /data/mysql:/var/lib/mysql     # persistent data
      - ./init-sql:/docker-entrypoint-initdb.d  # auto-run initial DDL
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --default-time-zone=+09:00
      - --max_connections=200
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost",
             "-u", "root", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── ChromaDB (Vector DB) ─────────────────────────────────────────────
  chromadb:
    image: chromadb/chroma:0.5.23   # pinned version (no :latest — reproducibility / no surprise regressions)
    container_name: nlp-chromadb
    restart: unless-stopped
    ports:
      - "127.0.0.1:8000:8000"
    volumes:
      - /data/chromadb:/chroma/chroma  # persistent vector data
    environment:
      - ANONYMIZED_TELEMETRY=false
      - CHROMA_SERVER_HOST=0.0.0.0
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/api/v1/heartbeat"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Ollama (LLM Runtime) ─────────────────────────────────────────────
  ollama:
    image: ollama/ollama:0.5.11     # pinned version (no :latest — reproducibility / no surprise regressions)
    container_name: nlp-ollama
    restart: unless-stopped
    ports:
      - "127.0.0.1:11434:11434"
    volumes:
      - /data/ollama:/root/.ollama    # ★ KEY: persistent model file storage
    environment:
      - OLLAMA_HOST=0.0.0.0
      - OLLAMA_MODELS=/root/.ollama
    # Uncomment below to enable GPU (requires NVIDIA Container Toolkit)
    # deploy:
    #   resources:
    #     reservations:
    #       devices:
    #         - driver: nvidia
    #           count: 1
    #           capabilities: [gpu]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:11434/api/tags"]
      interval: 15s
      timeout: 10s
      retries: 5

  # ── Redis (Session Store) ────────────────────────────────────────────
  redis:
    image: redis:7-alpine
    container_name: nlp-redis
    restart: unless-stopped
    ports:
      - "127.0.0.1:6379:6379"    # loopback only
    command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 256mb --maxmemory-policy allkeys-lru
    volumes:
      - /data/redis:/data         # persistent session data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Spring Boot 4 Application ────────────────────────────────────────
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: nlp-app
    restart: unless-stopped
    ports:
      - "127.0.0.1:5542:5542"
    depends_on:
      mysql:
        condition: service_healthy
      chromadb:
        condition: service_healthy
      ollama:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      # Platform meta DB
      SPRING_DATASOURCE_URL:      jdbc:mysql://mysql:3306/${MYSQL_DATABASE:-nlplatform}?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER:-nlpuser}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      # Redis (Spring Session)
      SPRING_DATA_REDIS_HOST:     redis
      SPRING_DATA_REDIS_PORT:     6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      # Ollama / ChromaDB
      APP_OLLAMA_BASE_URL:        http://ollama:11434
      APP_CHROMADB_BASE_URL:      http://chromadb:8000
      # AES-GCM encryption key (for DB credentials / Git Token encryption)
      SECURITY_TOKEN_ENCRYPTION_KEY: ${TOKEN_ENCRYPTION_KEY}
      # File storage paths
      APP_RESULTS_BASE_DIR: /data/results
      APP_SYSTEMS_BASE_DIR: /data/systems
      # JVM
      JAVA_OPTS: "-Xms512m -Xmx2g -XX:+UseG1GC"
      TZ: Asia/Seoul
    volumes:
      - /data/systems:/data/systems
      - /data/results:/data/results
      - /tmp/nl-platform:/tmp/nl-platform

networks:
  default:
    name: nlp-network
```

### 11-4. .env File

```dotenv
# .env — NEVER commit to git (add to .gitignore)

MYSQL_ROOT_PASSWORD=change_this_root_password
MYSQL_DATABASE=nlplatform
MYSQL_USER=nlpuser
MYSQL_PASSWORD=change_this_db_password

# Redis session store password
REDIS_PASSWORD=change_this_redis_password

# 32-byte AES key (generate: openssl rand -base64 32)
TOKEN_ENCRYPTION_KEY=REPLACE_WITH_32_BYTE_BASE64_KEY_HERE
```

```bash
chmod 600 .env   # restrict file permissions
```

### 11-5. Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew . && COPY gradle gradle && COPY build.gradle.kts .
COPY settings.gradle.kts . && COPY src src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache git curl tzdata \
    && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 5542
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

### 11-6. Automatic Initial DDL Execution

```
init-sql/
├── 01_members.sql
├── 02_target_systems.sql
├── 03_system_files.sql
├── 04_query_results.sql
├── 05_job_history.sql
└── 06_member_tokens.sql
```

MySQL container automatically executes `/docker-entrypoint-initdb.d/*.sql` on first startup.

### 11-7. Quick Command Reference

```bash
# 1. Create directories (once on first setup)
sudo mkdir -p /data/{ollama,chromadb,mysql,systems,results} /tmp/nl-platform

# 2. Write .env and set permissions
vi .env && chmod 600 .env

# 3. Start all services
docker compose up -d

# 4. Check status
docker compose ps

# 5. Download models (once after first startup)
docker compose exec ollama ollama pull gpt-oss:20b
docker compose exec ollama ollama pull nomic-embed-text

# 6. Verify models
docker compose exec ollama ollama list

# 7. View logs
docker compose logs -f app
docker compose logs -f ollama

# 8. Restart / stop
docker compose restart app
docker compose down           # preserve data
```

### 11-8. Nginx Configuration (SSL + WebSocket)

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;
    ssl_certificate     /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:5542;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket (STOMP) — long timeout for LLM response wait
    location /ws {
        proxy_pass http://127.0.0.1:5542;
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

---

## 12. Software Infrastructure Definition

| Software | Role | Runtime | Required |
|----------|------|---------|:--------:|
| Spring Boot 4 App | Core business logic | Docker | ✅ |
| Ollama | LLM inference + embedding API | Docker | ✅ |
| ChromaDB | Vector DB | Docker | ✅ |
| MySQL 8.0 | Platform meta DB | Docker | ✅ |
| Redis 7 | Session store (Spring Session Data Redis) | Docker | ✅ |
| Git | Source code clone/pull | Inside App container | ✅ |
| NVIDIA Driver + CUDA | GPU acceleration | Host OS | GPU conditional |
| Nginx | Reverse Proxy / SSL | Host OS | Recommended |

---

## 13. EC2 Instance Spec Recommendations

| Scenario | Instance | RAM | GPU | Monthly Cost (Seoul) | Recommended Use |
|----------|---------|-----|-----|---------------------|----------------|
| A. Development | `m7i.2xlarge` | 32GB | — | ~$140 | Dev/PoC |
| B. Minimal production | `g4dn.xlarge` | 16GB | T4 16GB | ~$430 | Small-scale |
| **C. Production (recommended)** ⭐ | **`g4dn.2xlarge`** | **32GB** | **T4 16GB** | **~$620** | **Production** |
| D. High performance | `g5.xlarge` | 16GB | A10G 22GB | ~$830 | 13B models |

```
EBS gp3 300 GB

/                       30 GB  OS + Docker images
/data/
  ├── ollama/           60 GB  LLM models (gpt-oss:20b ~13GB + nomic-embed-text ~274MB
  │                              + reranker ~1.5GB + candidate-model buffer)
  ├── chromadb/         20 GB  persistent vector DB data
  ├── mysql/            10 GB  platform meta DB
  ├── redis/             5 GB  session store (room for AOF)
  ├── systems/         150 GB  per-system docs/ddl/sourcetree
  └── results/          10 GB  generated Excel/HTML (2-day TTL)
```

> Keeping `/data/ollama` at 60 GB allows holding the chosen generation model
> alongside candidates (`qwen2.5-coder:7b` ~4.7 GB, `deepseek-coder:6.7b` ~3.8 GB) during
> benchmarking and rollback.

---

## 14. System Architecture

```
Client (Browser)  ─── HTTPS/WSS ───▶  Nginx :443 (host OS)
                                            │
                               ┌────────────▼──────────────────────────────┐
                               │  Docker Network: nlp-network               │
                               │                                            │
                               │  nlp-app :5542 (Spring Boot 4)            │
                               │  nlp-mysql :3306  nlp-chromadb :8000       │
                               │  nlp-ollama :11434  nlp-redis :6379        │
                               │                                            │
                               │  /data/ollama   → nlp-ollama (mount)       │
                               │  /data/chromadb → nlp-chromadb (mount)     │
                               │  /data/mysql    → nlp-mysql (mount)        │
                               │  /data/redis    → nlp-redis (mount)        │
                               │  /data/systems  → nlp-app (mount)          │
                               │  /data/results  → nlp-app (mount)          │
                               └───────────────────────────────────────────┘

Target system DB:
  → DynamicDataSourceFactory → runtime JDBC connection
  → External to Docker network (connects directly to production server DB)
  → EXPLAIN validation + SELECT execution both on same PROD DB
```

---

## 15. Technology Stack and Dependencies

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mustache")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    // Platform meta DB driver
    runtimeOnly("com.mysql:mysql-connector-j")
    // Target system DB drivers (multi-DBMS)
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.oracle.database.jdbc:ojdbc11")
    runtimeOnly("com.microsoft.sqlserver:mssql-jdbc")
    implementation("com.zaxxer:HikariCP")
    // Feature libraries
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.0.0.202409031743-r")
    implementation("com.github.javaparser:javaparser-core:3.26.1")
    implementation("org.jooq:jooq:3.19.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
```

---

## 16. Prompt Language Strategy

**Prompts are written in English; the rejection response is explicitly instructed in Korean.**
SQL-specialised and code-trained models are dominated by English training data, so English
system prompts yield better SQL quality. All prompts are externalised as `.md` templates in
`src/main/resources/prompts/` (see AGENTS.md §16).

```
[SYSTEM - English]
You are an expert SQL generator for the "{{system_name}}" system.
Target DBMS: {{db_type}}
Use ONLY the context below. Generate ONLY a valid SQL SELECT statement.
If not about data extraction, respond EXACTLY: "데이터 추출 요구 아님"

[CONTEXT — retrieved from knowledge base]
{{context}}    ← DDL ≥3 + source code ≥2 + Markdown ≥1 (post-reranker)

[USER REQUEST]
{{natural_language}}
```

---

## 17. Functional Design Detail

### 17-1. Data Extraction

```
POST /api/query/extract {systemId, naturalLanguage}
→ Verify Member authentication
→ Create QueryResult (status=PROCESSING)
→ Async coroutine:
   → [Parallel] SQL generation + dataset naming (2 LLM calls in parallel)
   → SqlExecutionGuard (static analysis → EXPLAIN validation)
   → Execute on PROD DB → Apache POI Excel
   → Save to /data/results/{id}/result.xlsx
   → Update QueryResult (COMPLETED)
   → Slack file attachment (if slackEnabled=Y)
   → WebSocket: {type:BOARD_READY, url:/board/{id}}
```

### 17-2. Data Visualization

```
POST /api/query/visualize {systemId, naturalLanguage}
→ [Stage 1] SQL generation + EXPLAIN + PROD DB execution
→ [Stage 2] Generate d3.js HTML → /data/results/{id}/visualization.html
→ Send HTML via WebSocket → iframe rendering
→ [Download] html2canvas PNG + JSZip
```

### 17-3. Full Admin API List

```
Auth:        POST /auth/login           {username, password} → set JSESSIONID cookie
             POST /auth/logout          invalidate session + clear cookie
             POST /api/auth/heartbeat   reset session TTL (call every 10 min)
             GET  /auth/me              return current member info

Members:     GET/POST /admin/members
             PUT /admin/members/{id}/role|status

Systems:     GET/POST /admin/systems
             PUT/DELETE /admin/systems/{id}

Files:       GET/POST   /admin/systems/{id}/files
             DELETE     /admin/systems/{id}/files/{fid}

Git:         POST /admin/systems/{id}/git/sync
             GET  /admin/systems/{id}/git/status

Ingestion:   POST /admin/systems/{id}/ingest  {mode:FULL|INCREMENTAL}
             GET  /admin/systems/{id}/ingest/status

Job History: GET /admin/jobs
             GET /admin/jobs/{id}  (includes stack_trace)

Board:       GET /board                list
             GET /board/{id}           detail
             GET /board/{id}/download  Excel download
             GET /board/{id}/html      HTML serving (for iframe)
```

---

## 18. Security Considerations

| Threat | Countermeasure |
|--------|---------------|
| SQL Injection | SELECT only + forbidden keywords + EXPLAIN REJECT |
| DB overload | Block execution when EXPLAIN threshold exceeded + enforce LIMIT |
| DB credential exposure | AES-GCM encrypted storage, mask in API responses |
| Git Token exposure | AES-GCM encrypted storage |
| .env file exposure | Add to .gitignore + chmod 600 |
| File upload abuse | Only .md/.sql allowed, 10MB limit, Path Traversal blocked |
| Unauthorized admin access | ADMIN role + Spring Security |
| Container port exposure | Docker ports → 127.0.0.1 binding (block external) |
| Brute-force login | 5 failures → set lockedUntil |
| Session hijacking | HTTPS only (Secure cookie), HttpOnly cookie, SameSite=Strict |
| Session fixation | Spring Security invalidates old session on login (default behavior) |
| Redis exposure | Password protected, loopback-only port (127.0.0.1:6379) |
| iframe XSS | sandbox="allow-scripts" only |

---

## 19. Non-Functional Requirements and Limitations

| Item | T4 16GB | A10G 22GB |
|------|:-------:|:---------:|
| LLM SQL generation (gpt-oss:20b) | 8~20 sec | 4~10 sec |
| LLM SQL generation (qwen2.5-coder:7b) | 2~5 sec | 1~3 sec |
| EXPLAIN validation | < 500 ms | < 500 ms |
| Dense search (ChromaDB) | < 25 ms | < 25 ms |
| Hybrid (Dense+BM25) search | < 60 ms | < 60 ms |
| Reranker (bge-reranker-base, 30 candidates) | < 200 ms | < 80 ms |
| Excel generation (10,000 rows) | 1~3 sec | 1~3 sec |
| Embedding (1,000 chunks, batch=64) | ~10 sec | ~6 sec |
| Git clone (large repo) | Several min (async) | Several min (async) |

> Embedding latency drops from prior ~50 s to ~10 s thanks to the true `/api/embed` batch
> endpoint with `batch=64`.

**Known Limitations:**
- Oracle/MSSQL EXPLAIN parsing is significantly more complex than MySQL/PostgreSQL —
  incremental addition is targeted for Phase 5.
- `gpt-oss:20b` is not SQL-specialised. If accuracy is insufficient, switch to
  `qwen2.5-coder:7b` or `deepseek-coder:6.7b` per the §20-A benchmark gate.
- The cross-encoder reranker consumes additional GPU memory (~1.5 GB headroom required on T4
  when sharing the GPU with the generation model).

---

## 20. Development Roadmap

```
Phase 1 — Infrastructure + Domain Foundation (3 weeks)
  ├── Docker Compose environment setup + model download (incl. Redis)
  ├── Member entity + Spring Security + Spring Session Data Redis
  ├── Session-based authentication (login/logout/heartbeat API)
  ├── TargetSystem + SystemFile entities
  ├── DynamicDataSourceFactory + TokenEncryptor
  ├── ApplicationCoroutineScope bean (§9-7-a)
  └── SystemDirectoryManager + ChromaDB collection management

Phase 2 — Embedding Pipeline (2 weeks)
  ├── JobHistory entity + JobHistoryService
  ├── OllamaClient (OllamaProperties-injected, /api/embed batch)
  ├── ChromaDbClient (upsert + query, 768-dim)
  ├── MarkdownChunker + SqlDdlChunker + SourceCodeChunker
  ├── DocumentIngestionPipeline (FULL/INCREMENTAL/SINGLE)
  └── Git Sync API + automatic embedding trigger

[End of Phase 2] A. Generation-model Benchmark Gate (3~5 days)
  ├── Candidates: gpt-oss:20b vs qwen2.5-coder:7b vs deepseek-coder:6.7b
  ├── Eval set: 100 internal NL-SQL pairs (covering ≥ 3 systems)
  ├── Metrics: (1) SQL exact-match (2) execution-match (3) p50/p95 latency
  │           (4) GPU memory (5) quality drop under 4-bit quantisation
  └── Pass bar: p95 latency ≤ 8 s (T4) or ≤ 4 s (A10G) AND exec-match ≥ 70%
             → pin the lightest model that passes into application.yaml

Phase 3 — SQL Validation + Extraction + Board (3 weeks)
  ├── SqlValidator + ExplainAnalyzer (MySQL/MariaDB/PostgreSQL)
  ├── LlmOrchestrationService (Dense RAG + SQL generation + naming)
  ├── NLQueryService async coroutine (uses ApplicationCoroutineScope)
  ├── Apache POI Excel + QueryResult board
  ├── WebSocket + ResultFileCleanupScheduler (2-day TTL)
  └── Slack file attachment delivery

Phase 4 — Hybrid Retrieval + Visualization + Admin UI (3 weeks)
  ├── BM25Index (in-process Lucene) + hybrid candidate union
  ├── RerankerClient (bge-reranker-base) + ContextAssembler
  ├── Regression run against the Phase 2-A eval set (must improve accuracy)
  ├── VisualizationService (two-stage LLM)
  ├── HTML serving API + iframe rendering
  └── Complete admin UI (Mustache)

Phase 5 — Oracle/MSSQL + Stabilization (1 week)
  ├── ExplainAnalyzer Oracle/MSSQL parser addition
  ├── Concurrent request Semaphore queuing
  ├── Nginx + SSL (Certbot)
  └── Monitoring (Micrometer + CloudWatch)
```

---

*This document is the v7 final consolidated version and supersedes v6 and `embedding-pipeline-design.md`.*
