# NL-to-SQL 데이터 플랫폼 — API 명세서

> 작성일: 2026-06-12  
> Base URL: `https://your-domain.com`  
> 인증 방식: Bearer Token (JWT)  
> 응답 형식: `application/json` (파일 다운로드 제외)  
> 문자 인코딩: UTF-8

---

## 목차

1. [공통 규약](#1-공통-규약)
2. [인증 API](#2-인증-api)
3. [쿼리 API (사용자)](#3-쿼리-api-사용자)
4. [게시판 API](#4-게시판-api)
5. [WebSocket 프로토콜](#5-websocket-프로토콜)
6. [관리자 — 회원 API](#6-관리자--회원-api)
7. [관리자 — 시스템 API](#7-관리자--시스템-api)
8. [관리자 — 파일 API](#8-관리자--파일-api)
9. [관리자 — Git API](#9-관리자--git-api)
10. [관리자 — 임베딩 API](#10-관리자--임베딩-api)
11. [관리자 — 작업 이력 API](#11-관리자--작업-이력-api)
12. [에러 코드 정의](#12-에러-코드-정의)

---

## 1. 공통 규약

### 1-1. 인증

모든 API(인증 API 제외)는 HTTP 요청 헤더에 JWT 토큰이 필요합니다.

```
Authorization: Bearer {access_token}
```

| 역할 | 접근 가능 경로 |
|------|-------------|
| `ADMIN` | 전체 |
| `USER` | `/api/**`, `/board/**` |
| `VIEWER` | `/board/**` (조회만) |

### 1-2. 공통 응답 구조

**성공 응답**

```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "timestamp": "2026-06-12T14:30:00+09:00"
  }
}
```

**페이지네이션 응답**

```json
{
  "success": true,
  "data": [ ... ],
  "meta": {
    "timestamp": "2026-06-12T14:30:00+09:00",
    "page": 0,
    "size": 20,
    "totalElements": 153,
    "totalPages": 8
  }
}
```

**에러 응답**

```json
{
  "success": false,
  "error": {
    "code": "SYSTEM_NOT_FOUND",
    "message": "시스템을 찾을 수 없습니다. (id=99)",
    "details": null
  },
  "meta": {
    "timestamp": "2026-06-12T14:30:00+09:00"
  }
}
```

### 1-3. 날짜 형식

모든 날짜/시간은 ISO 8601 형식 + KST 오프셋을 사용합니다.

```
"2026-06-12T14:30:00+09:00"
```

### 1-4. 페이지네이션 파라미터

페이지네이션을 지원하는 GET API의 공통 쿼리 파라미터:

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|-------|------|
| `page` | int | 0 | 페이지 번호 (0부터 시작) |
| `size` | int | 20 | 페이지당 항목 수 (최대 100) |
| `sort` | string | 정의별 상이 | 정렬 기준 (예: `requestedAt,desc`) |

---

## 2. 인증 API

### 2-1. 로그인

```
POST /auth/login
```

**Request Body**

```json
{
  "username": "yunson",
  "password": "P@ssw0rd!"
}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "member": {
      "id": 1,
      "username": "yunson",
      "displayName": "황윤선",
      "role": "ADMIN",
      "profileImageUrl": null
    }
  }
}
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 401 | `INVALID_CREDENTIALS` | 아이디 또는 비밀번호 불일치 |
| 423 | `ACCOUNT_LOCKED` | 계정 잠금 상태 |
| 403 | `ACCOUNT_INACTIVE` | 비활성 계정 |

---

### 2-2. 토큰 갱신

```
POST /auth/refresh
```

**Request Body**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 3600
  }
}
```

---

### 2-3. 로그아웃

```
POST /auth/logout
Authorization: Bearer {access_token}
```

**Response 200**

```json
{
  "success": true,
  "data": null
}
```

---

### 2-4. 내 정보 조회

```
GET /auth/me
Authorization: Bearer {access_token}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "yunson",
    "email": "yunson@example.com",
    "displayName": "황윤선",
    "role": "ADMIN",
    "status": "ACTIVE",
    "lastLoginAt": "2026-06-12T09:00:00+09:00",
    "emailVerified": "Y"
  }
}
```

---

### 2-5. 비밀번호 변경

```
PUT /auth/password
Authorization: Bearer {access_token}
```

**Request Body**

```json
{
  "currentPassword": "OldP@ss1!",
  "newPassword": "NewP@ss2!",
  "newPasswordConfirm": "NewP@ss2!"
}
```

**Response 200**

```json
{
  "success": true,
  "data": null
}
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 400 | `PASSWORD_MISMATCH` | 새 비밀번호 확인 불일치 |
| 401 | `INVALID_CURRENT_PASSWORD` | 현재 비밀번호 불일치 |

---

## 3. 쿼리 API (사용자)

### 3-1. 분析 대상 시스템 목록 조회

사용자가 쿼리 전 시스템을 선택하기 위한 API입니다.

```
GET /api/systems
Authorization: Bearer {access_token}
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|:----:|------|
| `status` | string | N | 수집 상태 필터 (`COMPLETED` 등) |

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "hexa",
      "description": "헥사 서비스 — BSS 메인",
      "ingestionStatus": "COMPLETED",
      "lastIngestedAt": "2026-06-12T14:30:00+09:00",
      "totalChunkCount": 3241,
      "dbType": "MYSQL"
    },
    {
      "id": 2,
      "name": "kooroo-bss",
      "description": "쿠루 BSS 시스템",
      "ingestionStatus": "COMPLETED",
      "lastIngestedAt": "2026-06-11T09:15:00+09:00",
      "totalChunkCount": 1872,
      "dbType": "POSTGRESQL"
    }
  ]
}
```

> DB 접속 정보(URL, ID, PW)는 사용자 응답에 포함하지 않습니다.

---

### 3-2. 데이터 추출 요청

자연어를 입력받아 SQL을 생성하고 Excel 파일을 만듭니다.  
처리는 비동기로 수행되며 결과는 WebSocket으로 수신합니다.

```
POST /api/query/extract
Authorization: Bearer {access_token}
Content-Type: application/json
```

**Request Body**

```json
{
  "systemId": 1,
  "naturalLanguage": "2024년 서울 지역 매출 상위 10개 고객을 금액 내림차순으로 알려줘"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|:----:|------|
| `systemId` | long | ✅ | 분析 대상 시스템 ID |
| `naturalLanguage` | string | ✅ | 자연어 요청 (최대 1,000자) |

**Response 202** — 즉시 반환 (비동기 처리 시작)

```json
{
  "success": true,
  "data": {
    "resultId": 42,
    "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "message": "처리를 시작합니다. WebSocket 세션 ID로 진행 상태를 수신하세요.",
    "wsSubscribePath": "/topic/session/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
}
```

| 필드 | 설명 |
|------|------|
| `resultId` | 게시판 레코드 ID (완료 후 `/board/{resultId}` 접근 가능) |
| `sessionId` | WebSocket 구독 세션 ID |
| `wsSubscribePath` | 클라이언트가 구독해야 할 STOMP 경로 |

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 400 | `INVALID_SYSTEM_ID` | 존재하지 않는 시스템 |
| 400 | `INGESTION_NOT_COMPLETED` | 아직 임베딩 수집이 완료되지 않은 시스템 |
| 403 | `ACCESS_DENIED` | VIEWER 역할은 요청 불가 |

---

### 3-3. 데이터 시각화 요청

자연어를 입력받아 d3.js HTML 시각화를 생성합니다.

```
POST /api/query/visualize
Authorization: Bearer {access_token}
Content-Type: application/json
```

**Request Body**

```json
{
  "systemId": 1,
  "naturalLanguage": "2024년 월별 배터리 교환 건수를 라인 차트로 보여줘"
}
```

**Response 202**

```json
{
  "success": true,
  "data": {
    "resultId": 43,
    "sessionId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "message": "처리를 시작합니다. WebSocket 세션 ID로 진행 상태를 수신하세요.",
    "wsSubscribePath": "/topic/session/b2c3d4e5-f6a7-8901-bcde-f12345678901"
  }
}
```

*요청/에러 응답 구조는 3-2와 동일합니다.*

---

## 4. 게시판 API

### 4-1. 결과 게시판 목록

```
GET /board
Authorization: Bearer {access_token}
```

**Query Parameters**

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|-------|------|
| `page` | int | 0 | 페이지 번호 |
| `size` | int | 20 | 페이지당 항목 수 |
| `systemId` | long | — | 특정 시스템 필터 |
| `resultType` | string | — | `EXTRACT` \| `VISUALIZE` |
| `requestedBy` | long | — | 특정 요청자 필터 (ADMIN만) |

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 42,
      "systemName": "hexa",
      "datasetName": "2024년 서울 매출 상위 10개 고객",
      "resultType": "EXTRACT",
      "status": "COMPLETED",
      "requestedBy": {
        "id": 1,
        "displayName": "황윤선"
      },
      "requestedAt": "2026-06-12T14:30:00+09:00",
      "expiresAt": "2026-06-14T14:30:00+09:00",
      "expiresInHours": 47,
      "fileAvailable": true
    },
    {
      "id": 41,
      "systemName": "hexa",
      "datasetName": "2024년 월별 배터리 교환 건수 추이",
      "resultType": "VISUALIZE",
      "status": "COMPLETED",
      "requestedBy": {
        "id": 1,
        "displayName": "황윤선"
      },
      "requestedAt": "2026-06-12T13:00:00+09:00",
      "expiresAt": "2026-06-14T13:00:00+09:00",
      "expiresInHours": 46,
      "fileAvailable": true
    }
  ],
  "meta": {
    "timestamp": "2026-06-12T15:00:00+09:00",
    "page": 0,
    "size": 20,
    "totalElements": 85,
    "totalPages": 5
  }
}
```

---

### 4-2. 결과 게시판 상세

```
GET /board/{id}
Authorization: Bearer {access_token}
```

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `id` | long | 게시판 일련번호 |

**Response 200 — EXTRACT (데이터 추출)**

```json
{
  "success": true,
  "data": {
    "id": 42,
    "systemName": "hexa",
    "datasetName": "2024년 서울 매출 상위 10개 고객",
    "naturalLanguage": "2024년 서울 지역 매출 상위 10개 고객을 금액 내림차순으로 알려줘",
    "generatedSql": "SELECT c.customer_name, SUM(o.total_amount) AS total ...",
    "resultType": "EXTRACT",
    "status": "COMPLETED",
    "requestedBy": {
      "id": 1,
      "displayName": "황윤선"
    },
    "requestedAt": "2026-06-12T14:30:00+09:00",
    "expiresAt": "2026-06-14T14:30:00+09:00",
    "expiresInHours": 47,
    "fileAvailable": true,
    "downloadUrl": "/board/42/download"
  }
}
```

**Response 200 — VISUALIZE (데이터 시각화)**

```json
{
  "success": true,
  "data": {
    "id": 43,
    "systemName": "hexa",
    "datasetName": "2024년 월별 배터리 교환 건수 추이",
    "naturalLanguage": "2024년 월별 배터리 교환 건수를 라인 차트로 보여줘",
    "generatedSql": "SELECT DATE_FORMAT(swap_date, '%Y-%m') AS month ...",
    "resultType": "VISUALIZE",
    "status": "COMPLETED",
    "requestedBy": {
      "id": 1,
      "displayName": "황윤선"
    },
    "requestedAt": "2026-06-12T13:00:00+09:00",
    "expiresAt": "2026-06-14T13:00:00+09:00",
    "expiresInHours": 46,
    "fileAvailable": true,
    "htmlUrl": "/board/43/html"
  }
}
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 404 | `RESULT_NOT_FOUND` | 존재하지 않거나 비노출 처리된 결과 |
| 403 | `ACCESS_DENIED` | 타인의 결과 접근 (ADMIN 제외) |

---

### 4-3. Excel 파일 다운로드

```
GET /board/{id}/download
Authorization: Bearer {access_token}
```

**Response 200**

```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename*=UTF-8''2024%EB%85%84%20%EC%84%9C%EC%9A%B8%20%EB%A7%A4%EC%B6%9C.xlsx
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 404 | `RESULT_NOT_FOUND` | 결과 없음 |
| 410 | `FILE_EXPIRED` | 파일이 만료되어 삭제됨 |
| 400 | `NOT_EXTRACT_TYPE` | EXTRACT 타입이 아님 |

---

### 4-4. 시각화 HTML 서빙

iframe의 `src` 속성에 직접 사용하는 엔드포인트입니다.

```
GET /board/{id}/html
Authorization: Bearer {access_token}
```

**Response 200**

```
Content-Type: text/html; charset=UTF-8
X-Frame-Options: SAMEORIGIN
```

```html
<!DOCTYPE html>
<html>
<head><script src="https://cdn.jsdelivr.net/npm/d3@7"></script></head>
<body>
  <!-- LLM이 생성한 d3.js 시각화 HTML -->
</body>
</html>
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 410 | `FILE_EXPIRED` | 파일이 만료되어 삭제됨 |
| 400 | `NOT_VISUALIZE_TYPE` | VISUALIZE 타입이 아님 |

---

## 5. WebSocket 프로토콜

쿼리 요청(추출/시각화) 후 비동기 처리 결과를 수신하는 채널입니다.

### 5-1. 연결

```
WSS /ws
Sec-WebSocket-Protocol: stomp
Authorization: Bearer {access_token}
```

SockJS fallback URL: `https://your-domain.com/ws`

### 5-2. STOMP 구독

`POST /api/query/extract(또는 visualize)` 응답의 `wsSubscribePath`로 구독합니다.

```javascript
const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  connectHeaders: { Authorization: `Bearer ${accessToken}` },
  onConnect: () => {
    client.subscribe(wsSubscribePath, (message) => {
      const payload = JSON.parse(message.body);
      handleMessage(payload);
    });
  }
});
client.activate();
```

### 5-3. 서버 → 클라이언트 메시지 타입

#### PROGRESS — 처리 진행 상태

```json
{
  "type": "PROGRESS",
  "resultId": 42,
  "step": "SQL 생성 중...",
  "stepIndex": 1,
  "totalSteps": 4
}
```

| `step` 값 | 설명 |
|----------|------|
| `"SQL 생성 중..."` | LLM 프롬프트 전송 후 |
| `"쿼리 검증 중..."` | EXPLAIN 실행 중 |
| `"데이터 조회 중..."` | PROD DB SELECT 실행 중 |
| `"파일 생성 중..."` | Excel 또는 HTML 생성 중 |

#### BOARD_READY — 처리 완료 (추출/시각화 공통)

```json
{
  "type": "BOARD_READY",
  "resultId": 42,
  "resultType": "EXTRACT",
  "datasetName": "2024년 서울 매출 상위 10개 고객",
  "boardUrl": "/board/42"
}
```

#### ERROR — 처리 실패

```json
{
  "type": "ERROR",
  "resultId": 42,
  "errorCode": "NOT_DATA_EXTRACTION",
  "message": "데이터 추출과 관련된 요청이 아닙니다. 다시 입력해 주세요."
}
```

| `errorCode` | 설명 |
|------------|------|
| `NOT_DATA_EXTRACTION` | LLM이 "데이터 추출 요구 아님" 응답 |
| `QUERY_TOO_EXPENSIVE` | EXPLAIN 비용 임계값 초과 |
| `SQL_GENERATION_FAILED` | LLM SQL 생성 실패 |
| `DB_EXECUTION_FAILED` | DB 실행 오류 |
| `FILE_GENERATION_FAILED` | Excel/HTML 생성 오류 |

---

## 6. 관리자 — 회원 API

> 모든 `/admin/**` API는 `ADMIN` 역할 필요

### 6-1. 회원 목록

```
GET /admin/members
Authorization: Bearer {access_token}
```

**Query Parameters**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `role` | string | `ADMIN` \| `USER` \| `VIEWER` |
| `status` | string | `ACTIVE` \| `INACTIVE` \| `LOCKED` \| `WITHDRAWN` |
| `keyword` | string | username 또는 displayName 검색 |
| `page` / `size` | int | 페이지네이션 |

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "username": "yunson",
      "email": "yunson@example.com",
      "displayName": "황윤선",
      "role": "ADMIN",
      "status": "ACTIVE",
      "lastLoginAt": "2026-06-12T09:00:00+09:00",
      "createdAt": "2026-01-01T00:00:00+09:00"
    }
  ],
  "meta": { "page": 0, "size": 20, "totalElements": 5, "totalPages": 1 }
}
```

---

### 6-2. 회원 등록

```
POST /admin/members
Authorization: Bearer {access_token}
```

**Request Body**

```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "password": "Temp@1234!",
  "displayName": "신규사용자",
  "role": "USER"
}
```

| 필드 | 타입 | 필수 | 유효성 |
|------|------|:----:|-------|
| `username` | string | ✅ | 5~50자, 영숫자+언더스코어 |
| `email` | string | ✅ | 이메일 형식 |
| `password` | string | ✅ | 8자 이상, 대소문자+숫자+특수문자 |
| `displayName` | string | ✅ | 2~100자 |
| `role` | string | ✅ | `ADMIN` \| `USER` \| `VIEWER` |

**Response 201**

```json
{
  "success": true,
  "data": {
    "id": 5,
    "username": "newuser",
    "email": "newuser@example.com",
    "displayName": "신규사용자",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-06-12T15:00:00+09:00"
  }
}
```

---

### 6-3. 회원 역할 변경

```
PUT /admin/members/{id}/role
Authorization: Bearer {access_token}
```

**Request Body**

```json
{
  "role": "VIEWER"
}
```

**Response 200**

```json
{
  "success": true,
  "data": { "id": 5, "role": "VIEWER" }
}
```

---

### 6-4. 회원 상태 변경

```
PUT /admin/members/{id}/status
Authorization: Bearer {access_token}
```

**Request Body**

```json
{
  "status": "LOCKED",
  "reason": "보안 정책 위반"
}
```

| `status` | 설명 |
|---------|------|
| `ACTIVE` | 활성화 (잠금 해제 포함) |
| `INACTIVE` | 비활성화 |
| `LOCKED` | 잠금 |
| `WITHDRAWN` | 탈퇴 처리 |

**Response 200**

```json
{
  "success": true,
  "data": { "id": 5, "status": "LOCKED" }
}
```

---

## 7. 관리자 — 시스템 API

### 7-1. 시스템 목록

```
GET /admin/systems
Authorization: Bearer {access_token}
```

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "hexa",
      "description": "헥사 서비스 — BSS 메인",
      "dbType": "MYSQL",
      "dbUrl": "jdbc:mysql://prod-db:3306/hexadb",
      "dbUsername": "he***r",
      "ingestionStatus": "COMPLETED",
      "lastIngestedAt": "2026-06-12T14:30:00+09:00",
      "totalChunkCount": 3241,
      "gitUrl": "https://github.com/org/hexa.git",
      "lastGitSyncAt": "2026-06-12T12:00:00+09:00",
      "slackEnabled": "Y",
      "fileCount": 3,
      "createdAt": "2026-01-15T09:00:00+09:00"
    }
  ]
}
```

> `dbUsername`은 마스킹 처리됩니다. `dbPassword`는 절대 응답에 포함하지 않습니다.

---

### 7-2. 시스템 등록

```
POST /admin/systems
Authorization: Bearer {access_token}
```

**Request Body**

```json
{
  "name": "hexa",
  "description": "헥사 서비스 — BSS 메인",
  "dbUrl": "jdbc:mysql://prod-db:3306/hexadb",
  "dbType": "MYSQL",
  "dbUsername": "hexa_reader",
  "dbPassword": "db_password_here",
  "gitUrl": "https://github.com/org/hexa.git",
  "gitAccessToken": "ghp_xxxxxxxxxxxxxxxxxxxx",
  "slackWebhookUrl": "https://hooks.slack.com/services/T00/B00/xxx",
  "slackEnabled": "Y"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|:----:|------|
| `name` | string | ✅ | 영숫자+하이픈, 3~100자, 중복 불가 |
| `dbUrl` | string | ✅ | JDBC URL |
| `dbType` | string | ✅ | `MYSQL` \| `MARIADB` \| `ORACLE` \| `POSTGRESQL` \| `MSSQL` |
| `dbUsername` | string | ✅ | DB 접속 ID (저장 시 AES-GCM 암호화) |
| `dbPassword` | string | ✅ | DB 접속 PW (저장 시 AES-GCM 암호화) |
| `gitUrl` | string | N | Git 저장소 URL |
| `gitAccessToken` | string | N | Git Access Token (AES-GCM 암호화 저장) |
| `slackWebhookUrl` | string | N | Slack Incoming Webhook URL |
| `slackEnabled` | string | N | `Y` \| `N` (기본값: `N`) |

**Response 201**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "hexa",
    "rootPath": "/data/systems/hexa_a3f2b1c4",
    "chromaCollection": "sys_hexa_a3f2b1c4",
    "ingestionStatus": "NONE",
    "createdAt": "2026-06-12T15:00:00+09:00"
  }
}
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 409 | `SYSTEM_NAME_DUPLICATE` | 시스템 이름 중복 |
| 400 | `INVALID_DB_CONNECTION` | DB 접속 정보 검증 실패 |

---

### 7-3. 시스템 상세 조회

```
GET /admin/systems/{id}
Authorization: Bearer {access_token}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "hexa",
    "description": "헥사 서비스",
    "rootPath": "/data/systems/hexa_a3f2b1c4",
    "chromaCollection": "sys_hexa_a3f2b1c4",
    "dbType": "MYSQL",
    "dbUrl": "jdbc:mysql://prod-db:3306/hexadb",
    "dbUsername": "he***r",
    "gitUrl": "https://github.com/org/hexa.git",
    "lastGitSyncAt": "2026-06-12T12:00:00+09:00",
    "lastCommitHash": "a1b2c3d4e5f6789012345678901234567890abcd",
    "slackEnabled": "Y",
    "slackWebhookUrl": "https://hooks.slack.com/...",
    "ingestionStatus": "COMPLETED",
    "lastIngestedAt": "2026-06-12T14:30:00+09:00",
    "totalChunkCount": 3241,
    "files": [
      {
        "id": 1,
        "originalFilename": "schema.sql",
        "fileType": "SQL_DDL",
        "fileSize": 20480,
        "embeddedChunkCount": 48,
        "lastEmbeddedAt": "2026-06-12T14:28:00+09:00"
      },
      {
        "id": 2,
        "originalFilename": "architecture.md",
        "fileType": "MARKDOWN",
        "fileSize": 8192,
        "embeddedChunkCount": 12,
        "lastEmbeddedAt": "2026-06-12T14:28:00+09:00"
      }
    ],
    "createdBy": { "id": 1, "displayName": "황윤선" },
    "createdAt": "2026-01-15T09:00:00+09:00"
  }
}
```

---

### 7-4. 시스템 수정

```
PUT /admin/systems/{id}
Authorization: Bearer {access_token}
```

**Request Body** — 변경할 필드만 포함 (Partial Update)

```json
{
  "description": "헥사 서비스 v2",
  "slackEnabled": "N",
  "dbPassword": "new_password_here"
}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "hexa",
    "description": "헥사 서비스 v2",
    "updatedAt": "2026-06-12T16:00:00+09:00"
  }
}
```

---

### 7-5. 시스템 삭제

```
DELETE /admin/systems/{id}
Authorization: Bearer {access_token}
```

삭제 시 아래 작업이 순서대로 실행됩니다.

1. 연관 `QueryResult`의 파일 물리 삭제 + `unused=Y`
2. ChromaDB 컬렉션 삭제
3. `/data/systems/{name}_{hash}/` 디렉토리 삭제
4. `SystemFile` 레코드 삭제
5. `TargetSystem` 레코드 삭제

**Response 200**

```json
{
  "success": true,
  "data": null
}
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 404 | `SYSTEM_NOT_FOUND` | 시스템 없음 |
| 409 | `INGESTION_RUNNING` | 수집 중인 시스템은 삭제 불가 |

---

## 8. 관리자 — 파일 API

### 8-1. 파일 목록

```
GET /admin/systems/{systemId}/files
Authorization: Bearer {access_token}
```

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "originalFilename": "schema.sql",
      "storedPath": "ddl/schema.sql",
      "fileType": "SQL_DDL",
      "fileSize": 20480,
      "sourceHash": "a1b2c3d4...",
      "embeddedChunkCount": 48,
      "lastEmbeddedAt": "2026-06-12T14:28:00+09:00",
      "uploadedBy": { "id": 1, "displayName": "황윤선" },
      "uploadedAt": "2026-06-12T10:00:00+09:00"
    }
  ]
}
```

---

### 8-2. 파일 업로드

업로드 직후 자동으로 임베딩이 시작됩니다.

```
POST /admin/systems/{systemId}/files
Authorization: Bearer {access_token}
Content-Type: multipart/form-data
```

**Form Data**

| 필드 | 타입 | 필수 | 설명 |
|------|------|:----:|------|
| `file` | file | ✅ | `.md` 또는 `.sql` 파일 (최대 10MB) |

**Response 201**

```json
{
  "success": true,
  "data": {
    "id": 3,
    "originalFilename": "orders.sql",
    "storedPath": "ddl/orders.sql",
    "fileType": "SQL_DDL",
    "fileSize": 4096,
    "uploadedAt": "2026-06-12T16:00:00+09:00",
    "ingestionTriggered": true,
    "message": "파일 업로드가 완료되었습니다. 임베딩 수집이 백그라운드에서 시작됩니다."
  }
}
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 400 | `INVALID_FILE_TYPE` | `.md` / `.sql` 이외 파일 |
| 400 | `FILE_TOO_LARGE` | 10MB 초과 |
| 409 | `FILE_ALREADY_EXISTS` | 동일 파일명 존재 (덮어쓰기 필요 시 삭제 후 재업로드) |

---

### 8-3. 파일 삭제

```
DELETE /admin/systems/{systemId}/files/{fileId}
Authorization: Bearer {access_token}
```

파일 삭제 시 ChromaDB에서 해당 파일의 청크도 함께 삭제됩니다.

**Response 200**

```json
{
  "success": true,
  "data": null
}
```

---

## 9. 관리자 — Git API

### 9-1. Git 동기화 (Clone / Pull)

최초 호출 시 `git clone`, 이후 호출 시 `git pull`이 실행됩니다.  
완료 후 변경된 파일 기준으로 증분 임베딩이 자동 트리거됩니다.

```
POST /admin/systems/{systemId}/git/sync
Authorization: Bearer {access_token}
```

**Response 202** — 비동기 처리

```json
{
  "success": true,
  "data": {
    "jobId": 101,
    "message": "Git 동기화를 시작합니다.",
    "statusUrl": "/admin/systems/1/git/status"
  }
}
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 400 | `GIT_URL_NOT_SET` | Git URL이 등록되지 않은 시스템 |
| 409 | `GIT_SYNC_RUNNING` | 이미 동기화 진행 중 |

---

### 9-2. Git 상태 조회

```
GET /admin/systems/{systemId}/git/status
Authorization: Bearer {access_token}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "gitUrl": "https://github.com/org/hexa.git",
    "lastSyncAt": "2026-06-12T12:00:00+09:00",
    "lastCommitHash": "a1b2c3d4e5f6789012345678901234567890abcd",
    "lastCommitMessage": "feat: BSS 배터리 교환 로직 개선",
    "sourcetreeExists": true,
    "syncStatus": "SUCCESS"
  }
}
```

---

## 10. 관리자 — 임베딩 API

### 10-1. 수집 실행

```
POST /admin/systems/{systemId}/ingest
Authorization: Bearer {access_token}
```

**Request Body**

```json
{
  "mode": "INCREMENTAL"
}
```

| `mode` | 설명 |
|-------|------|
| `FULL` | 전체 파일 재수집 (기존 ChromaDB 청크 전체 교체) |
| `INCREMENTAL` | 수정 시각 + SHA-256 비교 후 변경분만 수집 |

**Response 202**

```json
{
  "success": true,
  "data": {
    "jobId": 102,
    "mode": "INCREMENTAL",
    "message": "수집을 시작합니다.",
    "statusUrl": "/admin/systems/1/ingest/status"
  }
}
```

**에러 응답**

| HTTP | 에러 코드 | 설명 |
|------|---------|------|
| 409 | `INGESTION_ALREADY_RUNNING` | 이미 수집 중 |

---

### 10-2. 수집 현황 조회

```
GET /admin/systems/{systemId}/ingest/status
Authorization: Bearer {access_token}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "systemId": 1,
    "systemName": "hexa",
    "ingestionStatus": "COMPLETED",
    "lastIngestedAt": "2026-06-12T14:30:00+09:00",
    "totalChunkCount": 3241,
    "fileStats": [
      {
        "fileId": 1,
        "filename": "schema.sql",
        "fileType": "SQL_DDL",
        "embeddedChunkCount": 48,
        "lastEmbeddedAt": "2026-06-12T14:28:00+09:00",
        "isEmbedded": true
      },
      {
        "fileId": 2,
        "filename": "architecture.md",
        "fileType": "MARKDOWN",
        "embeddedChunkCount": 12,
        "lastEmbeddedAt": "2026-06-12T14:28:00+09:00",
        "isEmbedded": true
      },
      {
        "fileId": null,
        "filename": "sourcetree (git clone)",
        "fileType": "SOURCE_CODE",
        "embeddedChunkCount": 3181,
        "lastEmbeddedAt": "2026-06-12T14:30:00+09:00",
        "isEmbedded": true
      }
    ]
  }
}
```

---

## 11. 관리자 — 작업 이력 API

### 11-1. 작업 이력 목록

```
GET /admin/jobs
Authorization: Bearer {access_token}
```

**Query Parameters**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `systemId` | long | 특정 시스템 필터 |
| `jobType` | string | `QUERY_EXTRACT` \| `INGESTION_FULL` 등 |
| `status` | string | `RUNNING` \| `SUCCESS` \| `FAILED` \| `SKIPPED` |
| `from` | datetime | 시작 일시 (ISO 8601) |
| `to` | datetime | 종료 일시 (ISO 8601) |
| `page` / `size` | int | 페이지네이션 |

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 102,
      "jobType": "INGESTION_INCREMENTAL",
      "systemName": "hexa",
      "triggeredBy": { "id": 1, "displayName": "황윤선" },
      "status": "SUCCESS",
      "startedAt": "2026-06-12T14:25:00+09:00",
      "finishedAt": "2026-06-12T14:30:00+09:00",
      "durationMs": 300000,
      "inputSummary": "mode=INCREMENTAL, system=hexa",
      "outputSummary": "파일 5개, 청크 320개"
    },
    {
      "id": 101,
      "jobType": "QUERY_EXTRACT",
      "systemName": "hexa",
      "triggeredBy": { "id": 1, "displayName": "황윤선" },
      "status": "FAILED",
      "startedAt": "2026-06-12T14:00:00+09:00",
      "finishedAt": "2026-06-12T14:00:15+09:00",
      "durationMs": 15000,
      "inputSummary": "2024년 서울 지역 매출...",
      "outputSummary": null,
      "errorCode": "QueryTooExpensiveException",
      "errorMessage": "쿼리 실행 비용이 허용 한도를 초과합니다."
    }
  ],
  "meta": { "page": 0, "size": 20, "totalElements": 234, "totalPages": 12 }
}
```

---

### 11-2. 작업 이력 상세 (스택 트레이스 포함)

```
GET /admin/jobs/{id}
Authorization: Bearer {access_token}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "id": 101,
    "jobType": "QUERY_EXTRACT",
    "referenceId": 42,
    "referenceType": "QUERY_RESULT",
    "systemName": "hexa",
    "triggeredBy": { "id": 1, "displayName": "황윤선" },
    "status": "FAILED",
    "startedAt": "2026-06-12T14:00:00+09:00",
    "finishedAt": "2026-06-12T14:00:15+09:00",
    "durationMs": 15000,
    "inputSummary": "2024년 서울 지역 매출 상위 10개...",
    "outputSummary": null,
    "errorCode": "QueryTooExpensiveException",
    "errorMessage": "쿼리 실행 비용이 허용 한도를 초과합니다. (cost=72453.2, rows=2100000, access=ALL)",
    "stackTrace": "com.yourcompany.nlplatform.domain.exception.QueryTooExpensiveException: ...\n\tat com.yourcompany..."
  }
}
```

---

### 11-3. 시스템별 작업 이력

```
GET /admin/systems/{systemId}/jobs
Authorization: Bearer {access_token}
```

*11-1과 동일한 응답 구조, 해당 시스템의 작업만 반환*

---

## 12. 에러 코드 정의

### 12-1. HTTP 상태 코드 사용 원칙

| HTTP | 사용 조건 |
|------|---------|
| 200 | 조회/수정/삭제 성공 |
| 201 | 생성 성공 |
| 202 | 비동기 처리 시작 (쿼리, Git Sync, 임베딩) |
| 400 | 요청 파라미터/바디 유효성 오류 |
| 401 | 인증 실패 또는 토큰 만료 |
| 403 | 권한 부족 |
| 404 | 리소스 없음 |
| 409 | 상태 충돌 (중복, 이미 진행 중 등) |
| 410 | 리소스 만료 (파일 TTL 초과) |
| 423 | 계정 잠금 |
| 500 | 서버 내부 오류 |

### 12-2. 전체 에러 코드 목록

| 에러 코드 | HTTP | 설명 |
|---------|------|------|
| `INVALID_CREDENTIALS` | 401 | 아이디/비밀번호 불일치 |
| `TOKEN_EXPIRED` | 401 | Access Token 만료 |
| `TOKEN_INVALID` | 401 | 유효하지 않은 토큰 |
| `ACCOUNT_LOCKED` | 423 | 계정 잠금 상태 |
| `ACCOUNT_INACTIVE` | 403 | 비활성 계정 |
| `ACCESS_DENIED` | 403 | 권한 부족 |
| `INVALID_CURRENT_PASSWORD` | 401 | 현재 비밀번호 불일치 |
| `PASSWORD_MISMATCH` | 400 | 새 비밀번호 확인 불일치 |
| `MEMBER_NOT_FOUND` | 404 | 회원 없음 |
| `MEMBER_USERNAME_DUPLICATE` | 409 | 중복 username |
| `MEMBER_EMAIL_DUPLICATE` | 409 | 중복 email |
| `SYSTEM_NOT_FOUND` | 404 | 시스템 없음 |
| `SYSTEM_NAME_DUPLICATE` | 409 | 중복 시스템 이름 |
| `INVALID_DB_CONNECTION` | 400 | DB 접속 정보 오류 |
| `INGESTION_NOT_COMPLETED` | 400 | 수집 미완료 시스템에 쿼리 |
| `INGESTION_ALREADY_RUNNING` | 409 | 이미 수집 중 |
| `INGESTION_RUNNING` | 409 | 수집 중인 시스템 삭제 시도 |
| `GIT_URL_NOT_SET` | 400 | Git URL 미등록 |
| `GIT_SYNC_RUNNING` | 409 | 이미 Git 동기화 중 |
| `GIT_CLONE_FAILED` | 500 | Git clone 실패 |
| `INVALID_FILE_TYPE` | 400 | 허용되지 않은 파일 유형 |
| `FILE_TOO_LARGE` | 400 | 파일 크기 초과 (10MB) |
| `FILE_ALREADY_EXISTS` | 409 | 동일 파일명 존재 |
| `FILE_NOT_FOUND` | 404 | 파일 없음 |
| `RESULT_NOT_FOUND` | 404 | 게시판 결과 없음 |
| `FILE_EXPIRED` | 410 | 파일 만료 삭제됨 |
| `NOT_EXTRACT_TYPE` | 400 | Excel 다운로드 — EXTRACT 타입 아님 |
| `NOT_VISUALIZE_TYPE` | 400 | HTML 서빙 — VISUALIZE 타입 아님 |
| `NOT_DATA_EXTRACTION` | 400 | LLM 거절 ("데이터 추출 요구 아님") |
| `QUERY_TOO_EXPENSIVE` | 400 | EXPLAIN 비용 임계값 초과 |
| `SQL_GENERATION_FAILED` | 500 | LLM SQL 생성 실패 |
| `DB_EXECUTION_FAILED` | 500 | DB 실행 오류 |
| `FILE_GENERATION_FAILED` | 500 | Excel/HTML 생성 오류 |
| `JOB_NOT_FOUND` | 404 | 작업 이력 없음 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |

---

*이 문서는 `architecture-design-v7.md` 부록 — API 명세서입니다.*
