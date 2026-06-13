# Hyperion, NL-to-SQL Data Platform — Database Schema and Table Relationship Specification

> Date: 2026-06-12  
> DBMS: MySQL 8.0 (Platform Meta DB)  
> Character Set: utf8mb4 / utf8mb4_unicode_ci  
> Timezone: Asia/Seoul (UTC+9)

---

[한국어(Korean) 문서](DATABASE_SCHEMA_KR.md)

## 1. Full ERD

```
┌─────────────────┐         ┌──────────────────────┐         ┌──────────────────┐
│    members      │         │   target_systems      │         │   system_files   │
│─────────────────│         │──────────────────────│         │──────────────────│
│ id         PK   │◀──┐     │ id             PK    │◀──1:N──▶│ id          PK   │
│ username        │   │     │ name (unique)        │         │ system_id   FK   │
│ email           │   │     │ root_path            │         │ original_filename│
│ password_hash   │   └─FK──│ chroma_collection    │         │ stored_path      │
│ display_name    │   ┌─FK──│ db_url               │         │ file_type        │
│ role            │   │     │ db_type              │         │ file_size        │
│ status          │   │     │ db_username_enc      │         │ source_hash      │
│ ...             │   │     │ db_password_enc      │         │ last_embedded_at │
└─────────────────┘   │     │ git_url              │         │ embedded_chunk.. │
                       │     │ git_access_token_enc │         │ uploaded_by FK   │
                       │     │ slack_webhook_url    │         │ uploaded_at      │
                       │     │ slack_enabled        │         └──────────────────┘
                       │     │ ingestion_status     │
                       │     │ last_ingested_at     │
                       │     │ total_chunk_count    │
                       │     │ created_by      FK   │
                       │     └──────────────────────┘
                       │               │
                       │       ┌───────┘ 1:N
                       │       ▼
                       │  ┌──────────────────────┐
                       │  │   query_results       │
                       │  │──────────────────────│
                       └──│ id              PK   │
                      FK  │ system_id       FK   │
                          │ requested_by    FK   │
                          │ dataset_name         │
                          │ natural_language     │
                          │ generated_sql        │
                          │ result_type          │
                          │ status               │
                          │ file_path            │
                          │ expires_at           │
                          │ unused               │
                          │ file_deleted         │
                          │ slack_sent           │
                          │ ...                  │
                          └──────────────────────┘

┌──────────────────────────────────────────────────┐
│   job_history                                    │
│──────────────────────────────────────────────────│
│ id               PK                              │
│ job_type                                         │
│ reference_id  / reference_type                   │
│ system_id       FK → target_systems (SET NULL)   │
│ triggered_by    FK → members        (SET NULL)   │
│ status                                           │
│ started_at / finished_at / duration_ms           │
│ input_summary / output_summary                   │
│ error_code / error_message / stack_trace         │
│ created_at                                       │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│   member_tokens                                  │
│──────────────────────────────────────────────────│
│ id               PK                              │
│ member_id        FK → members                    │
│ token_type                                       │
│ token_hash  (SHA-256, original not stored)       │
│ expires_at / used_at                             │
│ created_at                                       │
└──────────────────────────────────────────────────┘
```

---

## 2. Table Relationship Definitions

| Relationship | Cardinality | Description |
|--------------|:-----------:|-------------|
| `members` → `target_systems` | 1:N | A member registers multiple systems (`created_by`) |
| `members` → `query_results` | 1:N | A member submits multiple queries (`requested_by`) |
| `members` → `system_files` | 1:N | A member uploads multiple files (`uploaded_by`) |
| `members` → `job_history` | 1:N | A member triggers multiple jobs (`triggered_by`, nullable) |
| `members` → `member_tokens` | 1:N | A member holds multiple one-time tokens (password reset only — email verification not implemented) |
| `target_systems` → `system_files` | 1:N | A system has multiple registered files |
| `target_systems` → `query_results` | 1:N | A system has multiple query results |
| `target_systems` → `job_history` | 1:N | A system has multiple job history records (nullable: SET NULL on DELETE) |

---

## 3. Table Column Specifications

### 3-1. members

| Column | Type | NULL | Default | Description |
|--------|------|:----:|---------|-------------|
| `id` | BIGINT | NO | AUTO_INCREMENT | Primary key |
| `username` | VARCHAR(50) | NO | — | Login ID (UNIQUE) |
| `email` | VARCHAR(200) | NO | — | Email address (UNIQUE) |
| `password_hash` | VARCHAR(255) | NO | — | BCrypt hash |
| `display_name` | VARCHAR(100) | NO | — | Display name |
| `role` | VARCHAR(20) | NO | `'USER'` | `ADMIN` \| `USER` \| `VIEWER` |
| `status` | VARCHAR(20) | NO | `'ACTIVE'` | `ACTIVE` \| `INACTIVE` \| `LOCKED` \| `WITHDRAWN` |
| `failed_login_count` | INT | NO | `0` | Consecutive login failure count |
| `locked_until` | DATETIME | YES | NULL | Lock release time (NULL = not locked) |
| `password_changed_at` | DATETIME | YES | NULL | Last password change time |
| `last_login_at` | DATETIME | YES | NULL | Last login time |
| `last_login_ip` | VARCHAR(45) | YES | NULL | Last login IP (supports IPv6) |
| `created_at` | DATETIME | NO | CURRENT_TIMESTAMP | Registration time |
| `updated_at` | DATETIME | NO | CURRENT_TIMESTAMP ON UPDATE | Last updated time |

**Indexes**
- PK: `id`
- UNIQUE: `username`, `email`

---

### 3-2. member_tokens

> **Authentication sessions are NOT stored here.**  
> HTTP session-based authentication uses **Redis** (via Spring Session Data Redis), not this table.  
> This table stores one-time tokens for **password reset only**.  
> Email verification is not implemented.

| Column | Type | NULL | Default | Description |
|--------|------|:----:|---------|-------------|
| `id` | BIGINT | NO | AUTO_INCREMENT | Primary key |
| `member_id` | BIGINT | NO | — | FK → members(id) |
| `token_type` | VARCHAR(30) | NO | — | `PASSWORD_RESET` |
| `token_hash` | VARCHAR(255) | NO | — | SHA-256 hash (original token not stored) |
| `expires_at` | DATETIME | NO | — | Expiration time |
| `used_at` | DATETIME | YES | NULL | Usage time (NULL = not yet used) |
| `created_at` | DATETIME | NO | CURRENT_TIMESTAMP | Creation time |

**Indexes**
- PK: `id`
- FK: `member_id`

---

### 3-3. target_systems

| Column | Type | NULL | Default | Description |
|--------|------|:----:|---------|-------------|
| `id` | BIGINT | NO | AUTO_INCREMENT | Primary key |
| `name` | VARCHAR(100) | NO | — | System name, URL-safe (UNIQUE) |
| `description` | VARCHAR(500) | YES | NULL | System description |
| `root_path` | VARCHAR(500) | NO | — | Server root path `/data/systems/{name}_{hash}/` |
| `chroma_collection` | VARCHAR(100) | NO | — | ChromaDB collection name `sys_{name}_{hash}` (UNIQUE) |
| `db_url` | VARCHAR(500) | NO | — | JDBC connection URL |
| `db_type` | VARCHAR(20) | NO | — | `MYSQL` \| `MARIADB` \| `ORACLE` \| `POSTGRESQL` \| `MSSQL` |
| `db_username_enc` | VARCHAR(500) | NO | — | DB login ID (AES-GCM encrypted) |
| `db_password_enc` | VARCHAR(500) | NO | — | DB login password (AES-GCM encrypted) |
| `git_url` | VARCHAR(500) | YES | NULL | Git repository URL |
| `git_access_token_enc` | VARCHAR(500) | YES | NULL | Git Access Token (AES-GCM encrypted) |
| `last_git_sync_at` | DATETIME | YES | NULL | Last Git sync time |
| `last_commit_hash` | VARCHAR(40) | YES | NULL | Last commit hash (SHA-1, 40 chars) |
| `slack_webhook_url` | VARCHAR(500) | YES | NULL | Slack Incoming Webhook URL |
| `slack_enabled` | CHAR(1) | NO | `'N'` | Slack notification enabled `Y`/`N` |
| `ingestion_status` | VARCHAR(20) | NO | `'NONE'` | `NONE` \| `RUNNING` \| `COMPLETED` \| `FAILED` |
| `last_ingested_at` | DATETIME | YES | NULL | Last ingestion completion time |
| `total_chunk_count` | INT | NO | `0` | Total embedded chunk count |
| `created_by` | BIGINT | NO | — | FK → members(id) |
| `created_at` | DATETIME | NO | CURRENT_TIMESTAMP | Registration time |
| `updated_at` | DATETIME | NO | CURRENT_TIMESTAMP ON UPDATE | Last updated time |

**Indexes**
- PK: `id`
- UNIQUE: `name`, `chroma_collection`
- FK: `created_by`

---

### 3-4. system_files

| Column | Type | NULL | Default | Description |
|--------|------|:----:|---------|-------------|
| `id` | BIGINT | NO | AUTO_INCREMENT | Primary key |
| `system_id` | BIGINT | NO | — | FK → target_systems(id) |
| `original_filename` | VARCHAR(255) | NO | — | Original file name |
| `stored_path` | VARCHAR(500) | NO | — | Storage path relative to root (e.g. `docs/schema.md`) |
| `file_type` | VARCHAR(20) | NO | — | `MARKDOWN` \| `SQL_DDL` |
| `file_size` | BIGINT | NO | — | File size in bytes |
| `source_hash` | VARCHAR(64) | YES | NULL | SHA-256 hash (for incremental change detection) |
| `last_embedded_at` | DATETIME | YES | NULL | Last embedding time |
| `embedded_chunk_count` | INT | NO | `0` | Number of embedded chunks |
| `uploaded_by` | BIGINT | NO | — | FK → members(id) |
| `uploaded_at` | DATETIME | NO | CURRENT_TIMESTAMP | Upload time |

**Indexes**
- PK: `id`
- FK: `system_id`, `uploaded_by`

---

### 3-5. query_results

| Column | Type | NULL | Default | Description |
|--------|------|:----:|---------|-------------|
| `id` | BIGINT | NO | AUTO_INCREMENT | Serial number (displayed on board) |
| `system_id` | BIGINT | NO | — | FK → target_systems(id) |
| `requested_by` | BIGINT | NO | — | FK → members(id) |
| `dataset_name` | VARCHAR(200) | NO | — | LLM-named dataset title (max 30 chars recommended) |
| `natural_language` | TEXT | NO | — | Original natural language input |
| `generated_sql` | TEXT | YES | NULL | LLM-generated SQL (audit log purpose) |
| `result_type` | VARCHAR(20) | NO | — | `EXTRACT` \| `VISUALIZE` |
| `status` | VARCHAR(20) | NO | `'PROCESSING'` | `PROCESSING` \| `COMPLETED` \| `FAILED` |
| `sql_result` | JSON | YES | NULL | SQL Query Result JSON |
| `file_path` | VARCHAR(500) | YES | NULL | Result file path (e.g. `/data/results/42/result.xlsx`) |
| `graph_markup` | MEDIUMTEXT | YES | NULL | Analysis Result Visualized HTML Mark Up |
| `analysis_result` | TEXT | YES | NULL | Analysis Result |
| `expires_at` | DATETIME | NO | — | Expiration time (`requested_at + 2 days`) |
| `unused` | CHAR(1) | NO | `'N'` | Hidden flag `Y`/`N` (DB record is never deleted) |
| `file_deleted` | CHAR(1) | NO | `'N'` | Physical file deleted flag `Y`/`N` |
| `file_deleted_at` | DATETIME | YES | NULL | File deletion time |
| `slack_sent` | CHAR(1) | NO | `'N'` | Slack notification sent `Y`/`N` |
| `slack_sent_at` | DATETIME | YES | NULL | Slack notification sent time |
| `error_message` | TEXT | YES | NULL | Error message (when `status=FAILED`) |
| `requested_at` | DATETIME | NO | CURRENT_TIMESTAMP | Request time |
| `updated_at` | DATETIME | NO | CURRENT_TIMESTAMP ON UPDATE | Last updated time |

**Indexes**
- PK: `id`
- FK: `system_id`, `requested_by`
- INDEX: `(system_id, requested_at DESC)` — latest results per system
- INDEX: `(expires_at, file_deleted)` — expired file cleanup scheduler

---

### 3-6. job_history

| Column | Type | NULL | Default | Description |
|--------|------|:----:|---------|-------------|
| `id` | BIGINT | NO | AUTO_INCREMENT | Primary key |
| `job_type` | VARCHAR(50) | NO | — | See JobType values below |
| `reference_id` | BIGINT | YES | NULL | Associated record ID |
| `reference_type` | VARCHAR(50) | YES | NULL | `QUERY_RESULT` \| `TARGET_SYSTEM` \| `SYSTEM_FILE` |
| `system_id` | BIGINT | YES | NULL | FK → target_systems(id), ON DELETE SET NULL |
| `triggered_by` | BIGINT | YES | NULL | FK → members(id), ON DELETE SET NULL (NULL = scheduler) |
| `status` | VARCHAR(20) | NO | `'RUNNING'` | `RUNNING` \| `SUCCESS` \| `FAILED` \| `SKIPPED` |
| `started_at` | DATETIME | NO | CURRENT_TIMESTAMP | Job start time |
| `finished_at` | DATETIME | YES | NULL | Job end time |
| `duration_ms` | BIGINT | YES | NULL | Execution duration in milliseconds |
| `input_summary` | VARCHAR(1000) | YES | NULL | Input summary (e.g. first 200 chars of natural language) |
| `output_summary` | VARCHAR(1000) | YES | NULL | Output summary (e.g. chunk count, row count) |
| `error_code` | VARCHAR(100) | YES | NULL | Error code (exception class name) |
| `error_message` | VARCHAR(2000) | YES | NULL | Error message |
| `stack_trace` | TEXT | YES | NULL | Full stack trace (on failure) |
| `created_at` | DATETIME | NO | CURRENT_TIMESTAMP | Creation time |

**JobType Values**

| Value | Description |
|-------|-------------|
| `QUERY_EXTRACT` | Data extraction query execution |
| `QUERY_VISUALIZE` | Data visualization query execution |
| `SQL_EXPLAIN` | EXPLAIN cost validation |
| `INGESTION_FULL` | Full embedding ingestion |
| `INGESTION_INCREMENTAL` | Incremental embedding ingestion |
| `INGESTION_SINGLE_FILE` | Single file embedding ingestion |
| `GIT_CLONE` | Git clone execution |
| `GIT_PULL` | Git pull execution |
| `FILE_CLEANUP` | Expired file deletion (scheduler) |
| `SLACK_NOTIFY` | Slack notification dispatch |

**Indexes**
- PK: `id`
- FK: `system_id` (SET NULL), `triggered_by` (SET NULL)
- INDEX: `(job_type, started_at DESC)` — latest history by type
- INDEX: `(system_id, started_at DESC)` — history by system
- INDEX: `(status, started_at DESC)` — history by status

---

## 4. Enum Value Definitions

### 4-1. MemberRole

| Value | Description | Permissions |
|-------|-------------|-------------|
| `ADMIN` | Administrator | Full access |
| `USER` | Regular user | Submit queries, view board |
| `VIEWER` | Viewer | View board only |

### 4-2. MemberStatus

| Value | Description |
|-------|-------------|
| `ACTIVE` | Active |
| `INACTIVE` | Inactive |
| `LOCKED` | Locked (exceeded 5 failed logins or manual admin action) |
| `WITHDRAWN` | Withdrawn (soft-deleted) |

### 4-3. DbType

| Value | JDBC Driver |
|-------|------------|
| `MYSQL` | `com.mysql.cj.jdbc.Driver` |
| `MARIADB` | `org.mariadb.jdbc.Driver` |
| `ORACLE` | `oracle.jdbc.OracleDriver` |
| `POSTGRESQL` | `org.postgresql.Driver` |
| `MSSQL` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |

### 4-4. IngestionStatus

| Value | Description |
|-------|-------------|
| `NONE` | Not yet ingested (initial state) |
| `RUNNING` | Ingestion in progress |
| `COMPLETED` | Ingestion completed |
| `FAILED` | Ingestion failed |

### 4-5. FileType

| Value | Extension | Chunker |
|-------|-----------|---------|
| `MARKDOWN` | `.md` | `MarkdownChunker` |
| `SQL_DDL` | `.sql` | `SqlDdlChunker` |

### 4-6. ResultType

| Value | Description | Output File |
|-------|-------------|------------|
| `EXTRACT` | Data extraction | `result.xlsx` |
| `VISUALIZE` | Data visualization | `visualization.html` |

### 4-7. ResultStatus

| Value | Description |
|-------|-------------|
| `PROCESSING` | Processing in progress |
| `COMPLETED` | Completed |
| `FAILED` | Failed |

### 4-8. JobType / JobStatus

JobType: see Section 3-6 above  

| JobStatus Value | Description |
|----------------|-------------|
| `RUNNING` | Executing |
| `SUCCESS` | Succeeded |
| `FAILED` | Failed |
| `SKIPPED` | Skipped (e.g. no changes in incremental ingestion) |

---

## 5. Initial DDL Script Execution Order

> Scripts must be executed in order to avoid FK constraint errors.

```
init-sql/
├── 01_members.sql          ← members, member_tokens
├── 02_target_systems.sql   ← target_systems
├── 03_system_files.sql     ← system_files (requires target_systems, members)
├── 04_query_results.sql    ← query_results (requires target_systems, members)
├── 05_job_history.sql      ← job_history (requires target_systems, members)
└── 06_indexes.sql          ← additional indexes (optional)
```

---

## 6. Security Design Principles

| Column | Storage Method | Reason |
|--------|---------------|--------|
| `password_hash` | BCrypt hash (one-way) | Original password cannot be recovered |
| `db_username_enc` | AES-256-GCM encryption (two-way) | Must be decryptable at runtime |
| `db_password_enc` | AES-256-GCM encryption (two-way) | Must be decryptable at runtime |
| `git_access_token_enc` | AES-256-GCM encryption (two-way) | Must be decryptable at runtime |
| `token_hash` | SHA-256 hash (one-way) | Original password-reset tokens never stored |
| `slack_webhook_url` | Plain text | Slack Webhooks are unguessable random URLs |

> **Authentication session data is not stored in MySQL.**  
> Login sessions are stored in **Redis** (Spring Session Data Redis, namespace `hyperion:session`).  
> Session TTL: 15 minutes, reset on every heartbeat call. Redis is password-protected and bound to loopback only.

---

## 7. Data Lifecycle

| Table | Deletion Policy | Retention |
|-------|----------------|-----------|
| `members` | Physical delete on withdrawal OR soft delete with `status=WITHDRAWN` | — |
| `member_tokens` | Physical delete by scheduler after expiration | `PASSWORD_RESET`: 1 hour |
| `target_systems` | Physical delete | — |
| `system_files` | Physical delete (cascades on system deletion) | — |
| `query_results` | **DB record never deleted** / soft delete with `unused=Y` | Permanent |
| `query_results` files | Physical delete by scheduler (`file_deleted=Y`) | requested_at + **2 days** |
| `job_history` | Never deleted (audit log purpose) | Permanent |

---

---

## 8. Session Store — Redis (Out-of-DB)

Authentication sessions are stored in **Redis**, not in MySQL.  
This section documents the Redis key structure for reference.

| Item | Value |
|------|-------|
| Store | Redis 7 (`nlp-redis` container) |
| Spring namespace | `hyperion:session` |
| Key pattern | `hyperion:session:sessions:{sessionId}` |
| TTL | 15 minutes (reset on each authenticated request) |
| Max memory | 256 MB (`allkeys-lru` eviction) |
| Password | Required (`REDIS_PASSWORD` env var) |
| Port | 6379 — bound to `127.0.0.1` only |

**Session payload (stored as Redis Hash):**

```
hyperion:session:sessions:{sessionId}
  ├── sessionAttr:SPRING_SECURITY_CONTEXT   → SecurityContext (serialized)
  ├── creationTime                           → epoch ms
  ├── lastAccessedTime                       → epoch ms
  └── maxInactiveInterval                    → 900 (seconds)
```

> Redis data does not participate in MySQL FK constraints or DDL scripts.  
> Session lifecycle is managed entirely by Spring Session Data Redis.

---

*This document covers only the platform meta DB schema (members, target_systems, etc.).*  
*The schema of each TargetSystem's analysis target DB is managed separately.*
