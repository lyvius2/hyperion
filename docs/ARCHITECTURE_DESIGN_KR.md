# NL-to-SQL 데이터 플랫폼 — 아키텍처 설계 v7

> 작성일: 2026-06-12  
> 대상: Kotlin + Spring Boot 4, Ollama (Local LLM), Monolithic SPA  
> v7 변경사항:
> - **임베딩 파이프라인 전체 상세 설계 본문 통합**
> - **Docker Compose 구성 추가** (Ollama + Spring Boot + ChromaDB + MySQL)
> - **LLM 모델 파일 저장 위치 및 초기 설정 절차 추가**

---

## 목차

1. [요건 검토](#1-요건-검토)
2. [핵심 설계 결정](#2-핵심-설계-결정)
3. [전체 도메인 모델 개요](#3-전체-도메인-모델-개요)
4. [도메인 1 — 회원 (Member)](#4-도메인-1--회원-member)
5. [도메인 2 — 분析 대상 시스템 (TargetSystem)](#5-도메인-2--분析-대상-시스템-targetsystem)
6. [도메인 3 — 결과 게시판 (QueryResult)](#6-도메인-3--결과-게시판-queryresult)
7. [도메인 4 — 작업 이력 (JobHistory)](#7-도메인-4--작업-이력-jobhistory)
8. [SQL 사전 검증 전략 — PROD DB 단일 접속](#8-sql-사전-검증-전략--prod-db-단일-접속)
9. [임베딩 파이프라인 (RAG 사전 작업)](#9-임베딩-파이프라인-rag-사전-작업)
10. [RAG 런타임 검색 흐름](#10-rag-런타임-검색-흐름)
11. [Docker Compose 구성](#11-docker-compose-구성)
12. [소프트웨어 인프라 정의](#12-소프트웨어-인프라-정의)
13. [EC2 인스턴스 스펙 권고](#13-ec2-인스턴스-스펙-권고)
14. [시스템 아키텍처](#14-시스템-아키텍처)
15. [기술 스택 및 의존성](#15-기술-스택-및-의존성)
16. [프롬프트 언어 전략](#16-프롬프트-언어-전략)
17. [기능 상세 설계](#17-기능-상세-설계)
18. [보안 고려사항](#18-보안-고려사항)
19. [비기능 요건 및 한계](#19-비기능-요건-및-한계)
20. [개발 로드맵](#20-개발-로드맵)

---

## 1. 요건 검토

| # | 요건 | 판정 | 비고 |
|---|------|:----:|------|
| 2 | SQL/코딩 특화 LLM 모델 | ✅ | `gpt-oss:20b` 사용 (초기에는 `deepseek-coder:6.7b` 검토) |
| 3 | Markdown + SQL DDL + 소스 코드 RAG | ✅ | 타입별 전용 청킹 + 임베딩 파이프라인 |
| 4~7 | 자연어 → SQL → Excel, 비동기 + WebSocket | ✅ | Spring WebSocket + Kotlin Coroutine |
| 8~10 | 자연어 → SQL → d3.js HTML → ZIP | ✅ | 2단계 LLM 호출 |
| 11 | Kotlin / Spring Boot 4 / Mustache 모놀리식 | ✅ | |
| v4 | 분析 대상 시스템 선택 및 관리 | ✅ | 시스템별 디렉토리 + ChromaDB 격리 |
| v5 | 결과 게시판 (2일 보관) | ✅ | 소프트 삭제 + 파일 물리 삭제 분리 |
| v6 | PROD DB 단독 접속 (EXPLAIN 사전 검증) | ✅ | |
| v6 | 다중 DBMS 지원 / 회원 / 작업이력 도메인 | ✅ | |
| **v7** | **임베딩 파이프라인 상세 구현 설계** | ✅ | 트리거 4종, 증분 업데이트, 배치 처리 |
| **v7** | **Docker Compose** (Ollama+App+ChromaDB+MySQL) | ✅ | 모델 파일 호스트 볼륨 마운트 |

---

## 2. 핵심 설계 결정

### 2-1. Fine-tuning vs RAG — RAG 두 단계 이해

```
━━━ 단계 1: 임베딩 (사전 작업 — 1회 + 변경 시마다) ━━━━━━━━━

문서/DDL/코드 → 청킹 → nomic-embed-text → 벡터 → ChromaDB 저장

  · gpt-oss:20b는 이 단계에 전혀 관여하지 않음
  · 문서를 "암기"하는 것이 아님. 텍스트를 수치 좌표로 변환해 저장하는 것
  · 문서 변경 시 재임베딩만으로 즉시 반영 (재학습 불필요)

━━━ 단계 2: RAG 런타임 (사용자 요청마다) ━━━━━━━━━━━━━━━━━

질문 → nomic-embed-text → 벡터 → ChromaDB 유사도 검색
     → 관련 청크 5~6개 꺼냄 → 프롬프트에 삽입
     → gpt-oss:20b 호출 → SQL 생성

  · gpt-oss:20b는 매 요청마다 context를 처음 읽고 SQL 생성
  · 시스템별 ChromaDB 컬렉션 격리 → 타 시스템 데이터 오염 없음
```

### 2-2. DB 접속 구조 — PROD DB 단독

운영 환경에서 PROD 서버가 Dev DB에 접속할 수 없는 경우가 대부분이므로,  
`EXPLAIN`(데이터 읽기 없음)으로 PROD DB에서 사전 검증 후 동일 DB에서 실행합니다.

---

## 3. 전체 도메인 모델 개요

```
members ──(등록자)──▶ target_systems ──1:N──▶ system_files
   │                       │
   └──(요청자)──▶ query_results
   
job_history  (모든 비동기 작업 이력, 실패 시 스택 트레이스)
```

### 전체 ERD (요약)

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
email_verified             slack_webhook_url            uploaded_at
created_at / updated_at    slack_enabled
                           ingestion_status
                           last_ingested_at
                           total_chunk_count
                           created_by FK / created_at / updated_at

query_results                               job_history
─────────────                               ───────────
id PK (일련번호)                             id PK
system_id FK                                job_type
requested_by FK                             reference_id / reference_type
dataset_name (LLM 작명)                     system_id FK
natural_language                            triggered_by FK (NULL=스케줄러)
generated_sql                               status (RUNNING|SUCCESS|FAILED|SKIPPED)
result_type (EXTRACT|VISUALIZE)             started_at / finished_at / duration_ms
status (PROCESSING|COMPLETED|FAILED)        input_summary / output_summary
file_path                                   error_code / error_message
expires_at (요청일+2일)                      stack_trace (실패 시 전문)
unused / file_deleted / file_deleted_at     created_at
slack_sent / slack_sent_at
error_message
requested_at / updated_at
```

---

## 4. 도메인 1 — 회원 (Member)

### 4-1. DDL

```sql
CREATE TABLE members (
    id                  BIGINT       NOT NULL AUTO_INCREMENT   COMMENT '회원 PK',
    username            VARCHAR(50)  NOT NULL                  COMMENT '로그인 ID',
    email               VARCHAR(200) NOT NULL                  COMMENT '이메일',
    password_hash       VARCHAR(255) NOT NULL                  COMMENT 'BCrypt 해시',
    display_name        VARCHAR(100) NOT NULL                  COMMENT '표시 이름',
    role                VARCHAR(20)  NOT NULL DEFAULT 'USER'   COMMENT 'ADMIN|USER|VIEWER',
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|INACTIVE|LOCKED|WITHDRAWN',
    profile_image_url   VARCHAR(500) NULL,
    failed_login_count  INT          NOT NULL DEFAULT 0        COMMENT '연속 로그인 실패 횟수',
    locked_until        DATETIME     NULL                      COMMENT '잠금 해제 일시 (NULL=잠금없음)',
    password_changed_at DATETIME     NULL,
    last_login_at       DATETIME     NULL,
    last_login_ip       VARCHAR(45)  NULL,
    email_verified      CHAR(1)      NOT NULL DEFAULT 'N',
    email_verified_at   DATETIME     NULL,
    oauth_provider      VARCHAR(20)  NULL     COMMENT 'google|kakao|github',
    oauth_provider_id   VARCHAR(200) NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_members          PRIMARY KEY (id),
    CONSTRAINT uq_members_username UNIQUE (username),
    CONSTRAINT uq_members_email    UNIQUE (email)
) COMMENT = '회원 정보';

CREATE TABLE member_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    token_type VARCHAR(30)  NOT NULL  COMMENT 'EMAIL_VERIFY|PASSWORD_RESET',
    token_hash VARCHAR(255) NOT NULL  COMMENT 'SHA-256 해시 (원본 미저장)',
    expires_at DATETIME     NOT NULL,
    used_at    DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_member_tokens PRIMARY KEY (id),
    CONSTRAINT fk_member_tokens_member FOREIGN KEY (member_id) REFERENCES members(id)
) COMMENT = '회원 토큰 (이메일 인증, 비밀번호 재설정 전용 — 인증은 Session 사용)';
```

### 4-2. 인증 설계 — Session + Redis

인증은 **Spring Session Data Redis**로 백업되는 **HTTP Session**을 사용합니다.  
액세스 토큰(JWT/Bearer)은 **발급하지 않습니다**. 이 API는 내부 전용이며 외부에 공개하지 않습니다.

#### 인증 흐름

```
[로그인]
  POST /auth/login {username, password}
  → BCrypt 비밀번호 검증 + 계정 상태 확인
  → 성공 시: HttpSession 생성 → Spring Session이 Redis에 저장
  → 응답에 JSESSIONID 쿠키 설정 후 200 반환

[인증이 필요한 모든 요청]
  → 쿠키의 JSESSIONID를 Spring Security가 읽음
  → Spring Session이 Redis에서 세션 데이터 조회
  → 세션 있음  → SecurityContext 설정 → 처리 진행
  → 세션 없음  → 401 Unauthorized

[로그아웃]
  POST /auth/logout
  → session.invalidate() → Redis에서 즉시 삭제
  → JSESSIONID 쿠키 삭제

[Heartbeat — 사용 중 세션 유지]
  POST /api/auth/heartbeat   (인증 필요)
  → 인증된 요청이 Redis의 세션 TTL을 초기화
  → 클라이언트가 10분마다 호출
  → 호출 시점부터 TTL이 15분으로 재설정됨
```

#### 세션 생명 주기

| 항목 | 값 |
|------|-----|
| 세션 TTL (max-inactive-interval) | **15분** |
| Heartbeat API 호출 주기 (클라이언트) | **10분마다** |
| Heartbeat 호출 시 TTL 재설정 | 호출 시점부터 **15분** |
| 로그아웃 시 TTL | 즉시 삭제 |

#### Redis를 사용하는 이유 (이중화 대비)

단일 노드 환경에서도 세션은 Redis에만 저장됩니다.  
이중화(멀티 노드)로 확장 시, 모든 노드가 동일한 Redis 인스턴스를 참조하므로 Sticky Session이 불필요합니다.

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

### 4-3. 역할별 권한

| 기능 | ADMIN | USER | VIEWER |
|------|:-----:|:----:|:------:|
| 시스템 등록/수정/삭제 | ✅ | ❌ | ❌ |
| 파일 업로드 / Git Sync / 임베딩 실행 | ✅ | ❌ | ❌ |
| 데이터 추출 / 시각화 요청 | ✅ | ✅ | ❌ |
| 게시판 조회 | ✅ | ✅ | ✅ |
| 회원 관리 | ✅ | ❌ | ❌ |

---

## 5. 도메인 2 — 분析 대상 시스템 (TargetSystem)

### 5-1. DDL

```sql
CREATE TABLE target_systems (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(100) NOT NULL                  COMMENT '시스템 이름 (URL-safe)',
    description          VARCHAR(500) NULL,
    root_path            VARCHAR(500) NOT NULL                  COMMENT '/data/systems/{name}_{hash}/',
    chroma_collection    VARCHAR(100) NOT NULL                  COMMENT 'sys_{name}_{hash}',
    db_url               VARCHAR(500) NOT NULL                  COMMENT 'JDBC URL',
    db_type              VARCHAR(20)  NOT NULL                  COMMENT 'MYSQL|MARIADB|ORACLE|POSTGRESQL|MSSQL',
    db_username_enc      VARCHAR(500) NOT NULL                  COMMENT 'AES-GCM 암호화',
    db_password_enc      VARCHAR(500) NOT NULL                  COMMENT 'AES-GCM 암호화',
    git_url              VARCHAR(500) NULL,
    git_access_token_enc VARCHAR(500) NULL                      COMMENT 'AES-GCM 암호화',
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
) COMMENT = '분析 대상 시스템';

CREATE TABLE system_files (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    system_id            BIGINT       NOT NULL,
    original_filename    VARCHAR(255) NOT NULL,
    stored_path          VARCHAR(500) NOT NULL,
    file_type            VARCHAR(20)  NOT NULL   COMMENT 'MARKDOWN|SQL_DDL',
    file_size            BIGINT       NOT NULL,
    source_hash          VARCHAR(64)  NULL        COMMENT 'SHA-256 변경 감지용',
    last_embedded_at     DATETIME     NULL,
    embedded_chunk_count INT          NOT NULL DEFAULT 0,
    uploaded_by          BIGINT       NOT NULL,
    uploaded_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_system_files PRIMARY KEY (id),
    CONSTRAINT fk_system_files_system FOREIGN KEY (system_id)   REFERENCES target_systems(id),
    CONSTRAINT fk_system_files_member FOREIGN KEY (uploaded_by) REFERENCES members(id)
) COMMENT = '시스템 업로드 파일';
```

### 5-2. 서버 디렉토리 구조

```
/data/systems/
├── hexa_a3f2b1c4/
│   ├── docs/           ← 업로드된 .md 파일
│   ├── ddl/            ← 업로드된 .sql 파일
│   └── sourcetree/     ← git clone 대상
│       └── .ingestion-ignore
└── kooroo-bss_7d1e3a9b/
    ├── docs/ / ddl/ / sourcetree/
```

### 5-3. 동적 DataSource 생성

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

## 6. 도메인 3 — 결과 게시판 (QueryResult)

### 6-1. DDL

```sql
CREATE TABLE query_results (
    id               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '일련번호',
    system_id        BIGINT       NOT NULL,
    requested_by     BIGINT       NOT NULL,
    dataset_name     VARCHAR(200) NOT NULL                 COMMENT 'LLM 작명 (최대 30자)',
    natural_language TEXT         NOT NULL,
    generated_sql    TEXT         NULL,
    result_type      VARCHAR(20)  NOT NULL                 COMMENT 'EXTRACT|VISUALIZE',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING'  COMMENT 'PROCESSING|COMPLETED|FAILED',
    file_path        VARCHAR(500) NULL,
    expires_at       DATETIME     NOT NULL                 COMMENT '요청일시+2일',
    unused           CHAR(1)      NOT NULL DEFAULT 'N'     COMMENT 'Y=비노출 (DB삭제안함)',
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
) COMMENT = '쿼리 요청 결과 게시판 (2일 보관)';

CREATE INDEX idx_qr_system  ON query_results (system_id, requested_at DESC);
CREATE INDEX idx_qr_expires ON query_results (expires_at, file_deleted);
```

---

## 7. 도메인 4 — 작업 이력 (JobHistory)

### 7-1. DDL

```sql
CREATE TABLE job_history (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    job_type        VARCHAR(50)   NOT NULL  COMMENT 'QUERY_EXTRACT|QUERY_VISUALIZE|SQL_EXPLAIN|INGESTION_FULL|INGESTION_INCREMENTAL|INGESTION_SINGLE_FILE|GIT_CLONE|GIT_PULL|FILE_CLEANUP|SLACK_NOTIFY',
    reference_id    BIGINT        NULL,
    reference_type  VARCHAR(50)   NULL,
    system_id       BIGINT        NULL,
    triggered_by    BIGINT        NULL      COMMENT 'NULL=스케줄러',
    status          VARCHAR(20)   NOT NULL DEFAULT 'RUNNING'  COMMENT 'RUNNING|SUCCESS|FAILED|SKIPPED',
    started_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     DATETIME      NULL,
    duration_ms     BIGINT        NULL,
    input_summary   VARCHAR(1000) NULL,
    output_summary  VARCHAR(1000) NULL,
    error_code      VARCHAR(100)  NULL      COMMENT '예외 클래스명',
    error_message   VARCHAR(2000) NULL,
    stack_trace     TEXT          NULL      COMMENT '실패 시 스택 트레이스 전문',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_job_history PRIMARY KEY (id),
    CONSTRAINT fk_jh_system FOREIGN KEY (system_id)    REFERENCES target_systems(id) ON DELETE SET NULL,
    CONSTRAINT fk_jh_member FOREIGN KEY (triggered_by) REFERENCES members(id)        ON DELETE SET NULL
) COMMENT = '작업 실행 이력';

CREATE INDEX idx_jh_type   ON job_history (job_type, started_at DESC);
CREATE INDEX idx_jh_system ON job_history (system_id, started_at DESC);
CREATE INDEX idx_jh_status ON job_history (status, started_at DESC);
```

### 7-2. 헬퍼 패턴

```kotlin
// 모든 비동기 작업에서 동일하게 사용
val job = jobHistoryService.start(JobType.QUERY_EXTRACT, system, member, inputSummary = nl.take(200))
runCatching {
    // ... 작업 수행 ...
    jobHistoryService.complete(job, "rows=1234")
}.onFailure { e ->
    jobHistoryService.fail(job, e)  // error_code, error_message, stack_trace 자동 저장
    throw e
}
```

---

## 8. SQL 사전 검증 전략 — PROD DB 단일 접속

### 8-1. 3단계 방어 레이어

```
LLM 생성 SQL
      │
      ▼ 레이어 1 — 정적 분석 (실행 없음)
        · SELECT 전용 강제    · 금지 키워드 차단
        · 다중 구문(;) 차단   · LIMIT 10,000 강제
      │ 통과
      ▼ 레이어 2 — EXPLAIN (PROD DB, 데이터 읽기 없음)
        · query_cost / rows_examined / access_type 추출
        · Full Table Scan, filesort, temporary 탐지
      │
      ├── REJECT → QueryTooExpensiveException
      ├── WARN   → LLM 최적화 재질의
      └── PASS   → PROD DB 실행
```

### 8-2. DBMS별 EXPLAIN 문법 및 임계값

| DBMS | 문법 | 비용 경로 |
|------|------|---------|
| MySQL / MariaDB | `EXPLAIN FORMAT=JSON {sql}` | `$.query_block.cost_info.query_cost` |
| PostgreSQL | `EXPLAIN (FORMAT JSON, COSTS TRUE) {sql}` | `$[0].Plan."Total Cost"` |
| Oracle | `EXPLAIN PLAN FOR {sql}` → DBMS_XPLAN | 텍스트 파싱 |
| MS SQL Server | `SET SHOWPLAN_XML ON` 후 실행 | XML 파싱 |

| 지표 | PASS | WARN | REJECT |
|------|:----:|:----:|:------:|
| `query_cost` | < 10,000 | ~50,000 | > 50,000 |
| `rows_examined` | < 100,000 | ~1,000,000 | > 1,000,000 |
| `access_type` | range 이상 | — | ALL (Full Scan) |

---

## 9. 임베딩 파이프라인 (RAG 사전 작업)

### 9-1. 두 모델의 역할 분리

| 모델 | 단계 | 역할 |
|------|------|------|
| `nomic-embed-text` | 사전 임베딩 + 런타임 질문 변환 | 텍스트 → 384차원 벡터만 담당 |
| `gpt-oss:20b` | 런타임 SQL/HTML 생성 | 프롬프트 context를 읽고 그 자리에서 SQL 생성 (초기에는 `deepseek-coder:6.7b` 검토) |

### 9-2. 전체 파이프라인 흐름

```
/data/systems/{name}_{hash}/docs/ ddl/ sourcetree/
          │
          ▼ 1. 파일 열거 + .ingestion-ignore 적용
          ▼ 2. 타입별 청킹
               .md   → MarkdownChunker  (## 헤딩 기준, 512토큰 초과 시 단락 재분할)
               .sql  → SqlDdlChunker    (CREATE TABLE 전체 = 1청크, DDL+한국어설명 합성)
               .java/.kt → SourceCodeChunker (JavaParser AST, 메서드 단위)
          ▼ 3. SHA-256 해시 → 기존과 동일하면 SKIP
          ▼ 4. Ollama nomic-embed-text (배치 10개씩)
               텍스트 → FloatArray(384차원)
          ▼ 5. ChromaDB upsert (100건씩 배치)
               컬렉션: sys_{name}_{hash}
               저장: 벡터 + 원문 + 메타데이터(type, source_path, table_name 등)
          ▼ 6. SystemFile.lastEmbeddedAt 업데이트 + JobHistory 완료
```

### 9-3. 트리거 시나리오 4가지

| 시나리오 | 트리거 | 수집 범위 |
|---------|--------|---------|
| A. 파일 업로드 직후 | `POST /admin/systems/{id}/files` 처리 후 자동 | 해당 파일만 |
| B. Git Sync 후 | `POST /admin/systems/{id}/git/sync` 후 자동 | 변경 파일만 |
| C. 관리자 수동 | `POST /admin/systems/{id}/ingest` | FULL 또는 INCREMENTAL |
| D. 앱 기동 시 | `@EventListener(ApplicationReadyEvent)` | `ingestionStatus=NONE`인 시스템만 |

### 9-4. 소스 타입별 청킹 상세

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
    // splitByHeadings: ## 또는 # 기준으로 섹션 분리
    // inferCategory: 경로에 schema/business/glossary/architecture 포함 여부로 추론
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
            // DDL + 한국어 설명 합성 → 한국어 자연어 "주문 테이블의 금액"이 영문 DDL에 매핑
            val embeddingText = """
                테이블명: $tableName
                설명: ${extractTableComment(ddl) ?: tableName}
                주요 컬럼: ${extractColumnSummary(ddl)}
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

#### SourceCodeChunker (JavaParser 기반 메서드 단위)

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
            // 클래스 컨텍스트 + 어노테이션 + 메서드 본문 합성
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
    // shouldIngest: .ingestion-ignore 적용 + Service/Repository/Domain/DTO 레이어 우선
    // inferLayer: 경로에 /service/ /repository/ /domain/ /dto/ 포함 여부로 추론
}
```

### 9-5. OllamaClient — 임베딩 + 추론

```kotlin
@Component
class OllamaClient(@Value("\${app.ollama.base-url}") private val baseUrl: String,
                   private val webClient: WebClient) {

    // ── 임베딩 (사전 작업 + 런타임 질문 변환) ──────────────────
    suspend fun embed(text: String): FloatArray {
        val res = webClient.post().uri("$baseUrl/api/embeddings")
            .bodyValue(mapOf("model" to "nomic-embed-text", "prompt" to text))
            .retrieve().awaitBody<EmbeddingResponse>()
        return res.embedding.toFloatArray()
    }

    // Ollama는 배치 API 미지원 → 10개씩 순차 처리
    suspend fun embedBatch(texts: List<String>): List<FloatArray> =
        texts.chunked(10).flatMap { batch -> batch.map { embed(it) } }

    // ── SQL/HTML 생성 (런타임) ──────────────────────────────────
    suspend fun generate(prompt: String, temperature: Double = 0.1): String {
        val res = webClient.post().uri("$baseUrl/api/generate")
            .bodyValue(mapOf(
                "model"   to "gpt-oss:20b",  // deepseek-coder:6.7b에서 변경
                "prompt"  to prompt,
                "stream"  to false,
                "options" to mapOf("temperature" to temperature, "num_predict" to 1024)
            ))
            .retrieve().awaitBody<GenerateResponse>()
        return res.response.trim()
    }

    data class EmbeddingResponse(val embedding: List<Float>)
    data class GenerateResponse(val response: String)
}
```

### 9-6. ChromaDbClient — 저장 및 검색

```kotlin
@Component
class ChromaDbClient(@Value("\${app.chromadb.base-url}") private val baseUrl: String,
                     private val webClient: WebClient) {

    // upsert (100건 단위 배치)
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

    // 유사도 검색 (런타임 — 요청마다 호출)
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
            .filter { it.similarity > 0.3 }   // 낮은 유사도 청크 필터링
    }

    // 파일 재수집 전 기존 청크 삭제
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

### 9-7. DocumentIngestionPipeline — 파이프라인 조립

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
    private val jobHistoryService: JobHistoryService
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun triggerAsync(system: TargetSystem, mode: IngestionMode, triggeredBy: Member?) {
        GlobalScope.launch(Dispatchers.IO) { run(system, mode, triggeredBy) }
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
                chromaDbClient.deleteBySourcePath(system.chromaCollection, relPath) // 재수집 전 삭제
                val embeddings = ollamaClient.embedBatch(chunks.map { it.text })
                chromaDbClient.upsert(system.chromaCollection, chunks, embeddings)
                totalChunks += chunks.size
                // SystemFile 상태 업데이트
                fileRepo.findBySystemIdAndStoredPath(system.id, relPath)?.let {
                    fileRepo.save(it.copy(lastEmbeddedAt = LocalDateTime.now(),
                                          embeddedChunkCount = chunks.size,
                                          sourceHash = sha256(file.readText())))
                }
            }
            systemRepo.save(system.copy(ingestionStatus = IngestionStatus.COMPLETED,
                                         lastIngestedAt = LocalDateTime.now(),
                                         totalChunkCount = totalChunks))
            jobHistoryService.complete(job, "파일 ${files.size}개, 청크 $totalChunks개")
        }.onFailure { e ->
            systemRepo.save(system.copy(ingestionStatus = IngestionStatus.FAILED))
            jobHistoryService.fail(job, e)
        }
    }

    // 단일 파일 수집 (파일 업로드 직후 자동 호출)
    suspend fun ingestSingleFile(file: File, system: TargetSystem, triggeredBy: Member?) {
        val job = jobHistoryService.start(JobType.INGESTION_SINGLE_FILE, system, triggeredBy,
                                          inputSummary = file.name)
        runCatching {
            val chunks = chunkFile(file, system)
            val relPath = file.relativeTo(File(system.rootPath)).path
            chromaDbClient.deleteBySourcePath(system.chromaCollection, relPath)
            chromaDbClient.upsert(system.chromaCollection, chunks,
                                  ollamaClient.embedBatch(chunks.map { it.text }))
            jobHistoryService.complete(job, "${chunks.size}청크")
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

### 9-8. 증분 업데이트 — SHA-256 이중 확인

```
파일 수정 시각 > lastIngestedAt?
  No  → SKIP
  Yes → SHA-256 계산 → ChromaDB source_hash와 비교
           동일 → SKIP (시각은 달라도 내용 동일)
           다름 → 기존 청크 삭제 → 재청킹 → 재임베딩 → upsert
```

---

## 10. RAG 런타임 검색 흐름

```kotlin
suspend fun generateSql(naturalLanguage: String, system: TargetSystem): String {
    // ① 질문을 벡터로 변환
    val queryVector = ollamaClient.embed(naturalLanguage)

    // ② 타입별 가중 검색 (SQL 생성: DDL 우선)
    val chunks = chromaDbClient.query(system.chromaCollection, queryVector, topK=3,
                    typeFilter=listOf("sql_ddl")) +
                 chromaDbClient.query(system.chromaCollection, queryVector, topK=2,
                    typeFilter=listOf("source_code")) +
                 chromaDbClient.query(system.chromaCollection, queryVector, topK=1,
                    typeFilter=listOf("markdown"))

    // ③ 유사도 낮은 청크 제거 후 context 조립
    val context = chunks.sortedByDescending { it.similarity }
                        .joinToString("\n\n---\n\n") { it.text }

    // ④ 영문 프롬프트 + gpt-oss:20b 호출
    return ollamaClient.generate("""
        [SYSTEM]
        You are an expert SQL generator for the "${system.name}" system.
        Target DBMS: ${system.dbType}
        Use ONLY the context below. Generate ONLY a valid SQL SELECT statement.
        If not about data extraction, respond EXACTLY: "데이터 추출 요구 아님"

        [CONTEXT — retrieved from knowledge base]
        $context

        [USER REQUEST]
        $naturalLanguage
    """.trimIndent())
}
```

---

## 11. Docker Compose 구성

### 11-1. LLM 모델 파일 저장 위치

> **Ollama 모델 파일은 반드시 호스트 디렉토리 `/data/ollama`에 저장해야 합니다.**  
> 컨테이너를 재생성·업데이트해도 수 GB짜리 모델을 다시 받지 않습니다.

```
/data/ollama/                     ← 호스트 디렉토리 (컨테이너에 마운트)
└── models/
    ├── manifests/
    │   └── registry.ollama.ai/library/
    │       ├── gpt-oss/          ← 모델 메타 정보 (deepseek-coder에서 변경)
    │       └── nomic-embed-text/
    └── blobs/                    ← 실제 가중치 파일
        ├── sha256-xxxx...        ← gpt-oss:20b (~11GB)
        └── sha256-yyyy...        ← nomic-embed-text (~274MB)
```

**모델 초기 다운로드 — 컨테이너 최초 기동 후 단 1회 실행:**

```bash
# Ollama 컨테이너에서 실행
docker compose exec ollama ollama pull gpt-oss:20b           # ~11GB, 30~60분 (deepseek-coder:6.7b에서 변경)
docker compose exec ollama ollama pull nomic-embed-text       # ~274MB, 1~2분

# 확인
docker compose exec ollama ollama list
```

이후 `docker compose down` → `docker compose up -d` 해도 `/data/ollama`에 저장된 파일을 그대로 씁니다.

### 11-2. 디렉토리 사전 생성 (서버 최초 1회)

```bash
sudo mkdir -p /data/{ollama,chromadb,mysql,redis,systems,results} /tmp/nl-platform
sudo chmod 755 /data/ollama /data/chromadb /data/mysql /data/redis \
               /data/systems /data/results /tmp/nl-platform
```

### 11-3. docker-compose.yml

```yaml
services:

  # ── MySQL (플랫폼 메타 DB) ────────────────────────────────────────────
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
      - "127.0.0.1:3306:3306"         # 루프백만 노출
    volumes:
      - /data/mysql:/var/lib/mysql     # 데이터 영구 보존
      - ./init-sql:/docker-entrypoint-initdb.d  # 초기 DDL 자동 실행
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

  # ── ChromaDB (벡터 DB) ───────────────────────────────────────────────
  chromadb:
    image: chromadb/chroma:latest
    container_name: nlp-chromadb
    restart: unless-stopped
    ports:
      - "127.0.0.1:8000:8000"
    volumes:
      - /data/chromadb:/chroma/chroma  # 벡터 데이터 영구 보존
    environment:
      - ANONYMIZED_TELEMETRY=false
      - CHROMA_SERVER_HOST=0.0.0.0
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/api/v1/heartbeat"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Ollama (LLM 런타임) ──────────────────────────────────────────────
  ollama:
    image: ollama/ollama:latest
    container_name: nlp-ollama
    restart: unless-stopped
    ports:
      - "127.0.0.1:11434:11434"
    volumes:
      - /data/ollama:/root/.ollama    # ★ 모델 파일 영구 보존 핵심
    environment:
      - OLLAMA_HOST=0.0.0.0
      - OLLAMA_MODELS=/root/.ollama
    # GPU 사용 시 아래 주석 해제 (NVIDIA Container Toolkit 필요)
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

  # ── Redis (세션 저장소) ──────────────────────────────────────────────
  redis:
    image: redis:7-alpine
    container_name: nlp-redis
    restart: unless-stopped
    ports:
      - "127.0.0.1:6379:6379"    # loopback 전용
    command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 256mb --maxmemory-policy allkeys-lru
    volumes:
      - /data/redis:/data         # 세션 데이터 영속성
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
      - "127.0.0.1:8080:8080"
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
      # 플랫폼 메타 DB
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
      # AES-GCM 암호화 키 (DB접속정보/Git Token 암호화)
      SECURITY_TOKEN_ENCRYPTION_KEY: ${TOKEN_ENCRYPTION_KEY}
      # 파일 저장 경로
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

### 11-4. .env 파일

```dotenv
# .env — git에 절대 커밋하지 말 것 (.gitignore에 추가)

MYSQL_ROOT_PASSWORD=change_this_root_password
MYSQL_DATABASE=nlplatform
MYSQL_USER=nlpuser
MYSQL_PASSWORD=change_this_db_password

# Redis 세션 저장소 비밀번호
REDIS_PASSWORD=change_this_redis_password

# 32바이트 AES 키 (생성: openssl rand -base64 32)
TOKEN_ENCRYPTION_KEY=REPLACE_WITH_32_BYTE_BASE64_KEY_HERE
```

```bash
chmod 600 .env   # 파일 권한 제한
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
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

### 11-6. 초기 DDL 자동 실행

```
init-sql/
├── 01_members.sql
├── 02_target_systems.sql
├── 03_system_files.sql
├── 04_query_results.sql
├── 05_job_history.sql
└── 06_member_tokens.sql
```

MySQL 컨테이너는 `/docker-entrypoint-initdb.d/*.sql`을 최초 기동 시 자동 실행합니다.

### 11-7. 실행 명령 요약

```bash
# 1. 디렉토리 생성 (최초 1회)
sudo mkdir -p /data/{ollama,chromadb,mysql,systems,results} /tmp/nl-platform

# 2. .env 작성 후 권한 설정
vi .env && chmod 600 .env

# 3. 전체 서비스 기동
docker compose up -d

# 4. 기동 상태 확인
docker compose ps

# 5. 모델 다운로드 (최초 1회, 기동 후 실행)
docker compose exec ollama ollama pull gpt-oss:20b
docker compose exec ollama ollama pull nomic-embed-text

# 6. 모델 확인
docker compose exec ollama ollama list

# 7. 로그 확인
docker compose logs -f app
docker compose logs -f ollama

# 8. 재시작 / 중지
docker compose restart app
docker compose down           # 데이터 보존
```

### 11-8. Nginx 설정 (SSL + WebSocket)

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;
    ssl_certificate     /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket (STOMP) — LLM 응답 대기 시간 고려
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

---

## 12. 소프트웨어 인프라 정의

| 소프트웨어 | 역할 | 실행 방식 | 필수 |
|-----------|------|---------|:----:|
| Spring Boot 4 App | 핵심 비즈니스 로직 | Docker | ✅ |
| Ollama | LLM 추론 + 임베딩 API | Docker | ✅ |
| ChromaDB | 벡터 DB | Docker | ✅ |
| MySQL 8.0 | 플랫폼 메타 DB | Docker | ✅ |
| Redis 7 | 세션 저장소 (Spring Session Data Redis) | Docker | ✅ |
| Git | 소스 코드 clone/pull | App 컨테이너 내 | ✅ |
| NVIDIA Driver + CUDA | GPU 가속 | 호스트 OS | GPU 조건부 |
| Nginx | Reverse Proxy / SSL | 호스트 OS | 권장 |

---

## 13. EC2 인스턴스 스펙 권고

| 시나리오 | 인스턴스 | RAM | GPU | 월 비용(서울) | 권장 용도 |
|---------|---------|-----|-----|------------|---------|
| A. 개발 | `m7i.2xlarge` | 32GB | — | ~$140 | 개발/PoC |
| B. 최소 운영 | `g4dn.xlarge` | 16GB | T4 16GB | ~$430 | 소규모 |
| **C. 운영 권장** ⭐ | **`g4dn.2xlarge`** | **32GB** | **T4 16GB** | **~$620** | **운영** |
| D. 고성능 | `g5.xlarge` | 16GB | A10G 22GB | ~$830 | 13B 모델 |

```
EBS gp3 250 GB

/                       30 GB  OS + Docker 이미지
/data/
  ├── ollama/           50 GB  LLM 모델 (deepseek ~3.8GB + nomic ~274MB + 여유)
  ├── chromadb/         20 GB  벡터 DB 영구 데이터
  ├── mysql/            10 GB  플랫폼 메타 DB
  ├── systems/         130 GB  시스템별 docs/ddl/sourcetree
  └── results/          10 GB  생성된 Excel/HTML (2일 TTL)
```

---

## 14. 시스템 아키텍처

```
Client (Browser)  ─── HTTPS/WSS ───▶  Nginx :443 (호스트)
                                            │
                               ┌────────────▼──────────────────────────────┐
                               │  Docker Network: nlp-network               │
                               │                                            │
                               │  nlp-app :8080 (Spring Boot 4)            │
                               │  nlp-mysql :3306  nlp-chromadb :8000       │
                               │  nlp-ollama :11434  nlp-redis :6379        │
                               │                                            │
                               │  /data/ollama   → nlp-ollama (마운트)      │
                               │  /data/chromadb → nlp-chromadb (마운트)    │
                               │  /data/mysql    → nlp-mysql (마운트)       │
                               │  /data/redis    → nlp-redis (마운트)       │
                               │  /data/systems  → nlp-app (마운트)         │
                               │  /data/results  → nlp-app (마운트)         │
                               └───────────────────────────────────────────┘

분析 대상 시스템 DB:
  → DynamicDataSourceFactory → 런타임 JDBC 연결
  → Docker 네트워크 외부 (운영 서버 DB에 직접 연결)
  → EXPLAIN 검증 + SELECT 실행 모두 동일 PROD DB
```

---

## 15. 기술 스택 및 의존성

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
    // 플랫폼 메타 DB
    runtimeOnly("com.mysql:mysql-connector-j")
    // 분析 대상 시스템 DB (다중 DBMS)
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.oracle.database.jdbc:ojdbc11")
    runtimeOnly("com.microsoft.sqlserver:mssql-jdbc")
    implementation("com.zaxxer:HikariCP")
    // 기능 라이브러리
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.0.0.202409031743-r")
    implementation("com.github.javaparser:javaparser-core:3.26.1")
    implementation("org.jooq:jooq:3.19.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
```

---

## 16. 프롬프트 언어 전략

**프롬프트는 영어로, 거절 응답은 한국어로 명시 지시합니다.**

```
[SYSTEM - English]
You are an expert SQL generator for the "{system.name}" system.
Target DBMS: {system.dbType}
Use ONLY the context below. Generate ONLY a valid SQL SELECT statement.
If not about data extraction, respond EXACTLY: "데이터 추출 요구 아님"

[CONTEXT — retrieved from knowledge base]
{DDL 3개 + 소스코드 2개 + Markdown 1개}

[USER REQUEST]
{사용자 자연어 입력}
```

---

## 17. 기능 상세 설계

### 17-1. 데이터 추출

```
POST /api/query/extract {systemId, naturalLanguage}
→ Member 인증 확인
→ QueryResult 생성 (status=PROCESSING)
→ 비동기 코루틴:
   → [병렬] SQL 생성 + 데이터셋 작명 (LLM 2회 병렬)
   → SqlExecutionGuard (정적분석 → EXPLAIN 검증)
   → PROD DB 실행 → Apache POI Excel
   → /data/results/{id}/result.xlsx 저장
   → QueryResult 업데이트 (COMPLETED)
   → Slack 파일 첨부 전송 (slackEnabled=Y)
   → WebSocket: {type:BOARD_READY, url:/board/{id}}
```

### 17-2. 데이터 시각화

```
POST /api/query/visualize {systemId, naturalLanguage}
→ [1단계] SQL 생성 + EXPLAIN + PROD DB 실행
→ [2단계] d3.js HTML 생성 → /data/results/{id}/visualization.html
→ WebSocket HTML 전송 → iframe 렌더링
→ [다운로드] html2canvas PNG + JSZip
```

### 17-3. 관리자 API 전체 목록

```
인증:     POST /auth/login           {username, password} → JSESSIONID 쿠키 설정
          POST /auth/logout          세션 무효화 + 쿠키 삭제
          POST /api/auth/heartbeat   세션 TTL 초기화 (10분마다 호출)
          GET  /auth/me              현재 로그인 회원 정보 반환

회원:     GET/POST /admin/members
          PUT /admin/members/{id}/role|status

시스템:   GET/POST /admin/systems
          PUT/DELETE /admin/systems/{id}

파일:     GET/POST   /admin/systems/{id}/files
          DELETE     /admin/systems/{id}/files/{fid}

Git:      POST /admin/systems/{id}/git/sync
          GET  /admin/systems/{id}/git/status

임베딩:   POST /admin/systems/{id}/ingest  {mode:FULL|INCREMENTAL}
          GET  /admin/systems/{id}/ingest/status

작업이력: GET /admin/jobs
          GET /admin/jobs/{id}  (stack_trace 포함)

게시판:   GET /board                 목록
          GET /board/{id}            상세
          GET /board/{id}/download   Excel 다운로드
          GET /board/{id}/html       iframe용 HTML 서빙
```

---

## 18. 보안 고려사항

| 위협 | 대응 방안 |
|------|-----------|
| SQL Injection | SELECT 전용 + 금지 키워드 + EXPLAIN REJECT |
| DB 과부하 | EXPLAIN 임계값 초과 시 실행 차단 + LIMIT 강제 |
| DB 접속 정보 노출 | AES-GCM 암호화 저장, API 응답 마스킹 |
| Git Token 노출 | AES-GCM 암호화 저장 |
| .env 파일 노출 | .gitignore 등록 + chmod 600 |
| 파일 업로드 악용 | .md/.sql만 허용, 10MB 제한, Path Traversal 차단 |
| 무단 관리자 접근 | ADMIN 역할 + Spring Security |
| 컨테이너 포트 노출 | Docker ports → 127.0.0.1 바인딩 (외부 차단) |
| 브루트포스 로그인 | 5회 실패 → lockedUntil 설정 |
| 세션 탈취 | HTTPS 전용(Secure 쿠키), HttpOnly 쿠키, SameSite=Strict |
| 세션 고정 | Spring Security 로그인 시 기존 세션 무효화 (기본 동작) |
| Redis 노출 | 비밀번호 보호, loopback 전용 포트 (127.0.0.1:6379) |
| iframe XSS | sandbox="allow-scripts" only |

---

## 19. 비기능 요건 및 한계

| 항목 | GPU 인스턴스 기준 |
|------|:----------------:|
| LLM SQL 생성 | 5~15초 |
| EXPLAIN 검증 | < 500ms |
| ChromaDB 검색 | < 25ms |
| Excel 생성 (10,000행) | 1~3초 |
| 임베딩 (1,000청크) | ~50초 |
| Git clone (대형 레포) | 수분 (비동기) |

**알려진 한계:**
Oracle/MSSQL EXPLAIN 파싱은 MySQL/PostgreSQL 대비 복잡합니다. Phase 5에서 단계적으로 추가를 권장합니다.

---

## 20. 개발 로드맵

```
Phase 1 — 인프라 + 도메인 기반 (3주)
  ├── Docker Compose 환경 구성 + 모델 다운로드 (Redis 포함)
  ├── Member 엔티티 + Spring Security + Spring Session Data Redis
  ├── 세션 기반 인증 (로그인/로그아웃/Heartbeat API)
  ├── TargetSystem + SystemFile 엔티티
  ├── DynamicDataSourceFactory + TokenEncryptor
  └── SystemDirectoryManager + ChromaDB 컬렉션 관리

Phase 2 — 임베딩 파이프라인 (2주)
  ├── JobHistory 엔티티 + JobHistoryService
  ├── OllamaClient (embed + generate)
  ├── ChromaDbClient (upsert + query)
  ├── MarkdownChunker + SqlDdlChunker + SourceCodeChunker
  ├── DocumentIngestionPipeline (FULL/INCREMENTAL/SINGLE)
  └── Git Sync API + 자동 임베딩 트리거

Phase 3 — SQL 검증 + 추출 + 게시판 (3주)
  ├── SqlValidator + ExplainAnalyzer (MySQL/MariaDB/PostgreSQL)
  ├── LlmOrchestrationService (RAG + SQL 생성 + 작명)
  ├── NLQueryService 비동기 코루틴
  ├── Apache POI Excel + QueryResult 게시판
  ├── WebSocket + ResultFileCleanupScheduler (2일 TTL)
  └── Slack 파일 첨부 전송

Phase 4 — 시각화 + 관리자 UI (2주)
  ├── VisualizationService (2단계 LLM)
  ├── HTML 서빙 API + iframe 렌더링
  └── 관리자 UI 완성 (Mustache)

Phase 5 — Oracle/MSSQL + 안정화 (1주)
  ├── ExplainAnalyzer Oracle/MSSQL 파서 추가
  ├── 동시 요청 Semaphore 큐잉
  ├── Nginx + SSL (Certbot)
  └── 모니터링 (Micrometer + CloudWatch)
```

---

*이 문서는 v7 최종 통합본이며, v6 및 `embedding-pipeline-design.md`를 대체합니다.*
