# Hyperion, NL-to-SQL 데이터 플랫폼 — 데이터베이스 스키마 및 테이블 관계 명세

> 작성일: 2026-06-12  
> DBMS: MySQL 8.0 (플랫폼 메타 DB)  
> 문자셋: utf8mb4 / utf8mb4_unicode_ci  
> 시간대: Asia/Seoul (UTC+9)

---

## 1. 전체 ERD

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
│ token_hash  (SHA-256, 원본 미저장)                │
│ expires_at / used_at                             │
│ created_at                                       │
└──────────────────────────────────────────────────┘
```

---

## 2. 테이블 관계 정의

| 관계 | 카디널리티 | 설명 |
|------|:--------:|------|
| `members` → `target_systems` | 1:N | 회원이 여러 시스템을 등록 (`created_by`) |
| `members` → `query_results` | 1:N | 회원이 여러 쿼리를 요청 (`requested_by`) |
| `members` → `system_files` | 1:N | 회원이 여러 파일을 업로드 (`uploaded_by`) |
| `members` → `job_history` | 1:N | 회원이 여러 작업을 트리거 (`triggered_by`, NULL 허용) |
| `members` → `member_tokens` | 1:N | 회원이 여러 토큰 보유 |
| `target_systems` → `system_files` | 1:N | 시스템에 여러 파일 등록 |
| `target_systems` → `query_results` | 1:N | 시스템에 여러 쿼리 결과 존재 |
| `target_systems` → `job_history` | 1:N | 시스템에 여러 작업 이력 존재 (NULL 허용: SET NULL on DELETE) |

---

## 3. 테이블 상세 스펙

### 3-1. members (회원)

| 컬럼명 | 타입 | NULL | 기본값 | 설명 |
|--------|------|:----:|--------|------|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK |
| `username` | VARCHAR(50) | NO | — | 로그인 ID (UNIQUE) |
| `email` | VARCHAR(200) | NO | — | 이메일 (UNIQUE) |
| `password_hash` | VARCHAR(255) | NO | — | BCrypt 해시 |
| `display_name` | VARCHAR(100) | NO | — | 화면 표시 이름 |
| `role` | VARCHAR(20) | NO | `'USER'` | `ADMIN` \| `USER` \| `VIEWER` |
| `status` | VARCHAR(20) | NO | `'ACTIVE'` | `ACTIVE` \| `INACTIVE` \| `LOCKED` \| `WITHDRAWN` |
| `profile_image_url` | VARCHAR(500) | YES | NULL | 프로필 이미지 URL |
| `failed_login_count` | INT | NO | `0` | 연속 로그인 실패 횟수 |
| `locked_until` | DATETIME | YES | NULL | 잠금 해제 일시 (NULL=미잠금) |
| `password_changed_at` | DATETIME | YES | NULL | 마지막 비밀번호 변경 일시 |
| `last_login_at` | DATETIME | YES | NULL | 마지막 로그인 일시 |
| `last_login_ip` | VARCHAR(45) | YES | NULL | 마지막 로그인 IP (IPv6 포함) |
| `email_verified` | CHAR(1) | NO | `'N'` | 이메일 인증 여부 `Y`/`N` |
| `email_verified_at` | DATETIME | YES | NULL | 이메일 인증 완료 일시 |
| `oauth_provider` | VARCHAR(20) | YES | NULL | `google` \| `kakao` \| `github` |
| `oauth_provider_id` | VARCHAR(200) | YES | NULL | 소셜 제공자 고유 ID |
| `created_at` | DATETIME | NO | CURRENT_TIMESTAMP | 가입 일시 |
| `updated_at` | DATETIME | NO | CURRENT_TIMESTAMP ON UPDATE | 수정 일시 |

**인덱스**
- PK: `id`
- UNIQUE: `username`, `email`

---

### 3-2. member_tokens (회원 토큰)

| 컬럼명 | 타입 | NULL | 기본값 | 설명 |
|--------|------|:----:|--------|------|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK |
| `member_id` | BIGINT | NO | — | FK → members(id) |
| `token_type` | VARCHAR(30) | NO | — | `EMAIL_VERIFY` \| `PASSWORD_RESET` \| `REFRESH_TOKEN` |
| `token_hash` | VARCHAR(255) | NO | — | SHA-256 해시 (원본 토큰 미저장) |
| `expires_at` | DATETIME | NO | — | 만료 일시 |
| `used_at` | DATETIME | YES | NULL | 사용 일시 (NULL=미사용) |
| `created_at` | DATETIME | NO | CURRENT_TIMESTAMP | 생성 일시 |

**인덱스**
- PK: `id`
- FK: `member_id`

---

### 3-3. target_systems (분析 대상 시스템)

| 컬럼명 | 타입 | NULL | 기본값 | 설명 |
|--------|------|:----:|--------|------|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK |
| `name` | VARCHAR(100) | NO | — | 시스템 이름, URL-safe (UNIQUE) |
| `description` | VARCHAR(500) | YES | NULL | 시스템 설명 |
| `root_path` | VARCHAR(500) | NO | — | 서버 내 루트 경로 `/data/systems/{name}_{hash}/` |
| `chroma_collection` | VARCHAR(100) | NO | — | ChromaDB 컬렉션명 `sys_{name}_{hash}` (UNIQUE) |
| `db_url` | VARCHAR(500) | NO | — | JDBC URL |
| `db_type` | VARCHAR(20) | NO | — | `MYSQL` \| `MARIADB` \| `ORACLE` \| `POSTGRESQL` \| `MSSQL` |
| `db_username_enc` | VARCHAR(500) | NO | — | DB 접속 ID (AES-GCM 암호화) |
| `db_password_enc` | VARCHAR(500) | NO | — | DB 접속 PW (AES-GCM 암호화) |
| `git_url` | VARCHAR(500) | YES | NULL | Git 저장소 URL |
| `git_access_token_enc` | VARCHAR(500) | YES | NULL | Git Access Token (AES-GCM 암호화) |
| `last_git_sync_at` | DATETIME | YES | NULL | 마지막 Git 동기화 일시 |
| `last_commit_hash` | VARCHAR(40) | YES | NULL | 마지막 커밋 해시 (SHA-1, 40자) |
| `slack_webhook_url` | VARCHAR(500) | YES | NULL | Slack Incoming Webhook URL |
| `slack_enabled` | CHAR(1) | NO | `'N'` | Slack 전송 여부 `Y`/`N` |
| `ingestion_status` | VARCHAR(20) | NO | `'NONE'` | `NONE` \| `RUNNING` \| `COMPLETED` \| `FAILED` |
| `last_ingested_at` | DATETIME | YES | NULL | 마지막 수집 완료 일시 |
| `total_chunk_count` | INT | NO | `0` | 전체 임베딩 청크 수 |
| `created_by` | BIGINT | NO | — | FK → members(id) |
| `created_at` | DATETIME | NO | CURRENT_TIMESTAMP | 등록 일시 |
| `updated_at` | DATETIME | NO | CURRENT_TIMESTAMP ON UPDATE | 수정 일시 |

**인덱스**
- PK: `id`
- UNIQUE: `name`, `chroma_collection`
- FK: `created_by`

---

### 3-4. system_files (시스템 업로드 파일)

| 컬럼명 | 타입 | NULL | 기본값 | 설명 |
|--------|------|:----:|--------|------|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK |
| `system_id` | BIGINT | NO | — | FK → target_systems(id) |
| `original_filename` | VARCHAR(255) | NO | — | 원본 파일명 |
| `stored_path` | VARCHAR(500) | NO | — | 저장 경로 (루트 기준 상대경로, 예: `docs/schema.md`) |
| `file_type` | VARCHAR(20) | NO | — | `MARKDOWN` \| `SQL_DDL` |
| `file_size` | BIGINT | NO | — | 파일 크기 (bytes) |
| `source_hash` | VARCHAR(64) | YES | NULL | SHA-256 해시 (증분 변경 감지용) |
| `last_embedded_at` | DATETIME | YES | NULL | 마지막 임베딩 일시 |
| `embedded_chunk_count` | INT | NO | `0` | 임베딩된 청크 수 |
| `uploaded_by` | BIGINT | NO | — | FK → members(id) |
| `uploaded_at` | DATETIME | NO | CURRENT_TIMESTAMP | 업로드 일시 |

**인덱스**
- PK: `id`
- FK: `system_id`, `uploaded_by`

---

### 3-5. query_results (결과 게시판)

| 컬럼명 | 타입 | NULL | 기본값 | 설명 |
|--------|------|:----:|--------|------|
| `id` | BIGINT | NO | AUTO_INCREMENT | 일련번호 (게시판 표시용) |
| `system_id` | BIGINT | NO | — | FK → target_systems(id) |
| `requested_by` | BIGINT | NO | — | FK → members(id) |
| `dataset_name` | VARCHAR(200) | NO | — | LLM이 작명한 데이터셋 명칭 (최대 30자 권장) |
| `natural_language` | TEXT | NO | — | 원본 자연어 입력 |
| `generated_sql` | TEXT | YES | NULL | LLM이 생성한 SQL (감사 로그용) |
| `result_type` | VARCHAR(20) | NO | — | `EXTRACT` \| `VISUALIZE` |
| `status` | VARCHAR(20) | NO | `'PROCESSING'` | `PROCESSING` \| `COMPLETED` \| `FAILED` |
| `file_path` | VARCHAR(500) | YES | NULL | 결과 파일 경로 (예: `/data/results/42/result.xlsx`) |
| `expires_at` | DATETIME | NO | — | 만료 일시 (`requested_at + 2일`) |
| `unused` | CHAR(1) | NO | `'N'` | 비노출 여부 `Y`/`N` (DB 레코드는 삭제하지 않음) |
| `file_deleted` | CHAR(1) | NO | `'N'` | 파일 물리 삭제 여부 `Y`/`N` |
| `file_deleted_at` | DATETIME | YES | NULL | 파일 삭제 일시 |
| `slack_sent` | CHAR(1) | NO | `'N'` | Slack 전송 여부 `Y`/`N` |
| `slack_sent_at` | DATETIME | YES | NULL | Slack 전송 일시 |
| `error_message` | TEXT | YES | NULL | 오류 메시지 (`status=FAILED` 시) |
| `requested_at` | DATETIME | NO | CURRENT_TIMESTAMP | 요청 일시 |
| `updated_at` | DATETIME | NO | CURRENT_TIMESTAMP ON UPDATE | 수정 일시 |

**인덱스**
- PK: `id`
- FK: `system_id`, `requested_by`
- INDEX: `(system_id, requested_at DESC)` — 시스템별 최신 결과 조회
- INDEX: `(expires_at, file_deleted)` — 만료 파일 정리 스케줄러용

---

### 3-6. job_history (작업 이력)

| 컬럼명 | 타입 | NULL | 기본값 | 설명 |
|--------|------|:----:|--------|------|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK |
| `job_type` | VARCHAR(50) | NO | — | 아래 JobType 참조 |
| `reference_id` | BIGINT | YES | NULL | 연관 레코드 ID |
| `reference_type` | VARCHAR(50) | YES | NULL | `QUERY_RESULT` \| `TARGET_SYSTEM` \| `SYSTEM_FILE` |
| `system_id` | BIGINT | YES | NULL | FK → target_systems(id), ON DELETE SET NULL |
| `triggered_by` | BIGINT | YES | NULL | FK → members(id), ON DELETE SET NULL (NULL=스케줄러) |
| `status` | VARCHAR(20) | NO | `'RUNNING'` | `RUNNING` \| `SUCCESS` \| `FAILED` \| `SKIPPED` |
| `started_at` | DATETIME | NO | CURRENT_TIMESTAMP | 작업 시작 일시 |
| `finished_at` | DATETIME | YES | NULL | 작업 종료 일시 |
| `duration_ms` | BIGINT | YES | NULL | 실행 시간 (밀리초) |
| `input_summary` | VARCHAR(1000) | YES | NULL | 입력 요약 (자연어 앞 200자 등) |
| `output_summary` | VARCHAR(1000) | YES | NULL | 출력 요약 (청크 수, 행 수 등) |
| `error_code` | VARCHAR(100) | YES | NULL | 에러 코드 (예외 클래스명) |
| `error_message` | VARCHAR(2000) | YES | NULL | 에러 메시지 |
| `stack_trace` | TEXT | YES | NULL | 스택 트레이스 전문 (실패 시) |
| `created_at` | DATETIME | NO | CURRENT_TIMESTAMP | 생성 일시 |

**JobType 값 목록**

| 값 | 설명 |
|----|------|
| `QUERY_EXTRACT` | 데이터 추출 쿼리 실행 |
| `QUERY_VISUALIZE` | 데이터 시각화 쿼리 실행 |
| `SQL_EXPLAIN` | EXPLAIN 비용 검증 |
| `INGESTION_FULL` | 전체 임베딩 수집 |
| `INGESTION_INCREMENTAL` | 증분 임베딩 수집 |
| `INGESTION_SINGLE_FILE` | 단일 파일 임베딩 수집 |
| `GIT_CLONE` | Git clone 실행 |
| `GIT_PULL` | Git pull 실행 |
| `FILE_CLEANUP` | 만료 파일 삭제 (스케줄러) |
| `SLACK_NOTIFY` | Slack 알림 발송 |

**인덱스**
- PK: `id`
- FK: `system_id` (SET NULL), `triggered_by` (SET NULL)
- INDEX: `(job_type, started_at DESC)` — 타입별 최신 이력 조회
- INDEX: `(system_id, started_at DESC)` — 시스템별 이력 조회
- INDEX: `(status, started_at DESC)` — 상태별 이력 조회

---

## 4. ENUM 값 정의

### 4-1. MemberRole

| 값 | 설명 | 권한 |
|----|------|------|
| `ADMIN` | 관리자 | 전체 기능 |
| `USER` | 일반 사용자 | 쿼리 요청, 게시판 조회 |
| `VIEWER` | 뷰어 | 게시판 조회만 |

### 4-2. MemberStatus

| 값 | 설명 |
|----|------|
| `ACTIVE` | 활성 |
| `INACTIVE` | 비활성 |
| `LOCKED` | 잠금 (로그인 실패 5회 초과 또는 관리자 처리) |
| `WITHDRAWN` | 탈퇴 처리 |

### 4-3. DbType

| 값 | JDBC 드라이버 |
|----|--------------|
| `MYSQL` | `com.mysql.cj.jdbc.Driver` |
| `MARIADB` | `org.mariadb.jdbc.Driver` |
| `ORACLE` | `oracle.jdbc.OracleDriver` |
| `POSTGRESQL` | `org.postgresql.Driver` |
| `MSSQL` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |

### 4-4. IngestionStatus

| 값 | 설명 |
|----|------|
| `NONE` | 수집 전 (초기 상태) |
| `RUNNING` | 수집 진행 중 |
| `COMPLETED` | 수집 완료 |
| `FAILED` | 수집 실패 |

### 4-5. FileType

| 값 | 파일 확장자 | 청커 |
|----|------------|------|
| `MARKDOWN` | `.md` | `MarkdownChunker` |
| `SQL_DDL` | `.sql` | `SqlDdlChunker` |

### 4-6. ResultType

| 값 | 설명 | 결과 파일 |
|----|------|----------|
| `EXTRACT` | 데이터 추출 | `result.xlsx` |
| `VISUALIZE` | 데이터 시각화 | `visualization.html` |

### 4-7. ResultStatus

| 값 | 설명 |
|----|------|
| `PROCESSING` | 처리 중 |
| `COMPLETED` | 완료 |
| `FAILED` | 실패 |

### 4-8. JobType / JobStatus

JobType: 위 3-6 참조  

| JobStatus 값 | 설명 |
|-------------|------|
| `RUNNING` | 실행 중 |
| `SUCCESS` | 성공 |
| `FAILED` | 실패 |
| `SKIPPED` | 건너뜀 (증분 수집에서 변경 없음 등) |

---

## 5. 초기 DDL 스크립트

> 파일 순서대로 실행해야 FK 제약 조건 오류가 발생하지 않습니다.

```
init-sql/
├── 01_members.sql          ← members, member_tokens
├── 02_target_systems.sql   ← target_systems
├── 03_system_files.sql     ← system_files (target_systems, members 선행 필요)
├── 04_query_results.sql    ← query_results (target_systems, members 선행 필요)
├── 05_job_history.sql      ← job_history (target_systems, members 선행 필요)
└── 06_indexes.sql          ← 추가 인덱스 (선택)
```

---

## 6. 보안 설계 원칙

| 컬럼 | 처리 방식 | 이유 |
|------|----------|------|
| `password_hash` | BCrypt 해시 (단방향) | 원문 비밀번호 복원 불가 |
| `db_username_enc` | AES-256-GCM 암호화 (양방향) | 런타임 복호화 필요 |
| `db_password_enc` | AES-256-GCM 암호화 (양방향) | 런타임 복호화 필요 |
| `git_access_token_enc` | AES-256-GCM 암호화 (양방향) | 런타임 복호화 필요 |
| `token_hash` | SHA-256 해시 (단방향) | 이메일 인증/리셋 토큰 원문 미저장 |
| `slack_webhook_url` | 평문 저장 | Slack Webhook은 추측 불가한 랜덤 URL |

---

## 7. 데이터 생명주기

| 테이블 | 삭제 정책 | 보존 기간 |
|--------|----------|----------|
| `members` | 물리 삭제 (탈퇴 시) 또는 `status=WITHDRAWN` 소프트 삭제 | — |
| `member_tokens` | 만료 후 스케줄러 물리 삭제 | 토큰 유형별 상이 (Refresh: 30일 등) |
| `target_systems` | 물리 삭제 | — |
| `system_files` | 물리 삭제 (시스템 삭제 시 연쇄) | — |
| `query_results` | **DB 레코드 삭제 안 함** / `unused=Y` 소프트 삭제 | 영구 |
| `query_results` 파일 | 스케줄러 물리 삭제 (`file_deleted=Y`) | 요청일 + **2일** |
| `job_history` | 물리 삭제 안 함 (감사 로그 목적) | 영구 |

---

*이 문서는 플랫폼 메타 DB(members, target_systems 등) 스키마 전용입니다.*  
*각 TargetSystem이 접속하는 분析 대상 DB의 스키마는 별도 관리됩니다.*
