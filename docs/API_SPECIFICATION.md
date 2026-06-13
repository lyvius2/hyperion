# Hyperion, NL-to-SQL Data Platform — API Specification

> Date: 2026-06-12  
> Base URL: `https://hyperion.furaiki-lifelog.com`  
> Authentication: HTTP Session (JSESSIONID Cookie)  
> Response Format: `application/json` (except file downloads)  
> Character Encoding: UTF-8

---

[한국어(Korean) 문서](API_SPECIFICATION_KR.md)

## Table of Contents

1. [Common Conventions](#1-common-conventions)
2. [Authentication API](#2-authentication-api)
3. [Query API (User)](#3-query-api-user)
4. [Board API](#4-board-api)
5. [WebSocket Protocol](#5-websocket-protocol)
6. [Admin — Member API](#6-admin--member-api)
7. [Admin — System API](#7-admin--system-api)
8. [Admin — File API](#8-admin--file-api)
9. [Admin — Git API](#9-admin--git-api)
10. [Admin — Ingestion API](#10-admin--ingestion-api)
11. [Admin — Job History API](#11-admin--job-history-api)
12. [Error Code Definitions](#12-error-code-definitions)

---

## 1. Common Conventions

### 1-1. Authentication

All APIs (except `/auth/login`) require a valid session.  
Authentication is handled via the `JSESSIONID` cookie set at login — **no Authorization header is needed**.  
The browser sends the cookie automatically on every request.

**Session lifecycle:**

| Item | Value |
|------|-------|
| Session TTL | 15 minutes (max-inactive-interval) |
| Keep-alive | Call `POST /api/auth/heartbeat` every 10 minutes |
| On logout | Session invalidated and cookie cleared immediately |

**Role-based access control:**

| Role | Accessible Paths |
|------|----------------|
| `ADMIN` | All paths |
| `USER` | `/api/**`, `/board/**` |
| `VIEWER` | `/board/**` (read-only) |

### 1-2. Common Response Structure

**Success Response**

```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "timestamp": "2026-06-12T14:30:00+09:00"
  }
}
```

**Paginated Response**

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

**Error Response**

```json
{
  "success": false,
  "error": {
    "code": "SYSTEM_NOT_FOUND",
    "message": "System not found. (id=99)",
    "details": null
  },
  "meta": {
    "timestamp": "2026-06-12T14:30:00+09:00"
  }
}
```

### 1-3. Date Format

All dates and times use ISO 8601 format with KST offset.

```
"2026-06-12T14:30:00+09:00"
```

### 1-4. Pagination Parameters

Common query parameters for GET APIs that support pagination:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 20 | Items per page (max 100) |
| `sort` | string | varies | Sort criteria (e.g. `requestedAt,desc`) |

---

## 2. Authentication API

### 2-1. Login

```
POST /auth/login
```

**Request Body**

```json
{
  "username": "sherlock",
  "password": "P@ssw0rd!"
}
```

**Response 200**

A `Set-Cookie: JSESSIONID=...` header is included in the response.  
The browser stores and sends this cookie automatically on all subsequent requests.

```json
{
  "success": true,
  "data": {
    "member": {
      "id": 1,
      "username": "sherlock",
      "displayName": "Sherlock Holmes",
      "role": "ADMIN",
      "profileImageUrl": null
    }
  }
}
```

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 401 | `INVALID_CREDENTIALS` | Username or password mismatch |
| 423 | `ACCOUNT_LOCKED` | Account is locked |
| 403 | `ACCOUNT_INACTIVE` | Inactive account |

---

### 2-2. Heartbeat (Session Keep-Alive)

Resets the session TTL to prevent expiry while the user is active.  
The client must call this endpoint every **10 minutes**.

```
POST /api/auth/heartbeat
```

> Authentication required (JSESSIONID cookie)

**Response 200**

```json
{
  "success": true,
  "data": {
    "sessionExpiresIn": 900
  }
}
```

| Field | Description |
|-------|-------------|
| `sessionExpiresIn` | Remaining TTL in seconds after reset (always 900 = 15 min) |

---

### 2-3. Logout

```
POST /auth/logout
```

> Authentication required (JSESSIONID cookie)

**Response 200**

The `JSESSIONID` cookie is cleared in the response (`Set-Cookie: JSESSIONID=; Max-Age=0`).

```json
{
  "success": true,
  "data": null
}
```

---

### 2-4. Get My Profile

```
GET /auth/me
```

> Authentication required (JSESSIONID cookie)

**Response 200**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "sherlock",
    "email": "sherlock@example.com",
    "displayName": "Sherlock Holmes",
    "role": "ADMIN",
    "status": "ACTIVE",
    "lastLoginAt": "2026-06-12T09:00:00+09:00",
    "emailVerified": "Y"
  }
}
```

---

### 2-5. Change Password

```
PUT /auth/password
```

> Authentication required (JSESSIONID cookie)

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

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 400 | `PASSWORD_MISMATCH` | New password confirmation mismatch |
| 401 | `INVALID_CURRENT_PASSWORD` | Current password mismatch |

---

## 3. Query API (User)

### 3-1. List Target Systems

API for users to select a system before submitting a query.

```
GET /api/systems
```

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `status` | string | N | Filter by ingestion status (e.g. `COMPLETED`) |

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "hexa",
      "description": "Hexa Service — BSS Main",
      "ingestionStatus": "COMPLETED",
      "lastIngestedAt": "2026-06-12T14:30:00+09:00",
      "totalChunkCount": 3241,
      "dbType": "MYSQL"
    },
    {
      "id": 2,
      "name": "kooroo-bss",
      "description": "KooRoo BSS System",
      "ingestionStatus": "COMPLETED",
      "lastIngestedAt": "2026-06-11T09:15:00+09:00",
      "totalChunkCount": 1872,
      "dbType": "POSTGRESQL"
    }
  ]
}
```

> DB connection details (URL, username, password) are not included in user responses.

---

### 3-2. Data Extraction Request

Accepts natural language input, generates SQL, and creates an Excel file.  
Processing is asynchronous; results are received via WebSocket.

```
POST /api/query/extract
Content-Type: application/json
```

**Request Body**

```json
{
  "systemId": 1,
  "naturalLanguage": "Show me the top 10 customers by revenue in Seoul for 2024, sorted by amount descending"
}
```

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| `systemId` | long | ✅ | Target system ID |
| `naturalLanguage` | string | ✅ | Natural language request (max 1,000 chars) |

**Response 202** — Returns immediately (async processing begins)

```json
{
  "success": true,
  "data": {
    "resultId": 42,
    "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "message": "Processing started. Subscribe to the WebSocket session ID to receive status updates.",
    "wsSubscribePath": "/topic/session/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
}
```

| Field | Description |
|-------|-------------|
| `resultId` | Board record ID (accessible at `/board/{resultId}` after completion) |
| `sessionId` | WebSocket subscription session ID |
| `wsSubscribePath` | STOMP path the client should subscribe to |

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 400 | `INVALID_SYSTEM_ID` | System does not exist |
| 400 | `INGESTION_NOT_COMPLETED` | System embedding not yet completed |
| 403 | `ACCESS_DENIED` | VIEWER role cannot make requests |

---

### 3-3. Data Visualization Request

Accepts natural language input and generates a d3.js HTML visualization.

```
POST /api/query/visualize
Content-Type: application/json
```

**Request Body**

```json
{
  "systemId": 1,
  "naturalLanguage": "Show monthly battery swap counts for 2024 as a line chart"
}
```

**Response 202**

```json
{
  "success": true,
  "data": {
    "resultId": 43,
    "sessionId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "message": "Processing started. Subscribe to the WebSocket session ID to receive status updates.",
    "wsSubscribePath": "/topic/session/b2c3d4e5-f6a7-8901-bcde-f12345678901"
  }
}
```

*Request/error response structure is identical to 3-2.*

---

## 4. Board API

### 4-1. Result Board List

```
GET /board
```

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Page number |
| `size` | int | 20 | Items per page |
| `systemId` | long | — | Filter by specific system |
| `resultType` | string | — | `EXTRACT` \| `VISUALIZE` |
| `requestedBy` | long | — | Filter by requester (ADMIN only) |

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 42,
      "systemName": "hexa",
      "datasetName": "Top 10 Seoul Customers by Revenue 2024",
      "resultType": "EXTRACT",
      "status": "COMPLETED",
      "requestedBy": {
        "id": 1,
        "displayName": "Sherlock Holmes"
      },
      "requestedAt": "2026-06-12T14:30:00+09:00",
      "expiresAt": "2026-06-14T14:30:00+09:00",
      "expiresInHours": 47,
      "fileAvailable": true
    },
    {
      "id": 41,
      "systemName": "hexa",
      "datasetName": "Monthly Battery Swap Count Trend 2024",
      "resultType": "VISUALIZE",
      "status": "COMPLETED",
      "requestedBy": {
        "id": 1,
        "displayName": "Sherlock Holmes"
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

### 4-2. Result Board Detail

```
GET /board/{id}
```

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | long | Board serial number |

**Response 200 — EXTRACT (Data Extraction)**

```json
{
  "success": true,
  "data": {
    "id": 42,
    "systemName": "hexa",
    "datasetName": "Top 10 Seoul Customers by Revenue 2024",
    "naturalLanguage": "Show me the top 10 customers by revenue in Seoul for 2024, sorted by amount descending",
    "generatedSql": "SELECT c.customer_name, SUM(o.total_amount) AS total ...",
    "resultType": "EXTRACT",
    "status": "COMPLETED",
    "requestedBy": {
      "id": 1,
      "displayName": "Sherlock Holmes"
    },
    "requestedAt": "2026-06-12T14:30:00+09:00",
    "expiresAt": "2026-06-14T14:30:00+09:00",
    "expiresInHours": 47,
    "fileAvailable": true,
    "downloadUrl": "/board/42/download"
  }
}
```

**Response 200 — VISUALIZE (Data Visualization)**

```json
{
  "success": true,
  "data": {
    "id": 43,
    "systemName": "hexa",
    "datasetName": "Monthly Battery Swap Count Trend 2024",
    "naturalLanguage": "Show monthly battery swap counts for 2024 as a line chart",
    "generatedSql": "SELECT DATE_FORMAT(swap_date, '%Y-%m') AS month ...",
    "resultType": "VISUALIZE",
    "status": "COMPLETED",
    "requestedBy": {
      "id": 1,
      "displayName": "Sherlock Holmes"
    },
    "requestedAt": "2026-06-12T13:00:00+09:00",
    "expiresAt": "2026-06-14T13:00:00+09:00",
    "expiresInHours": 46,
    "fileAvailable": true,
    "htmlUrl": "/board/43/html"
  }
}
```

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 404 | `RESULT_NOT_FOUND` | Result does not exist or has been hidden |
| 403 | `ACCESS_DENIED` | Accessing another user's result (except ADMIN) |

---

### 4-3. Excel File Download

```
GET /board/{id}/download
```

**Response 200**

```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename*=UTF-8''dataset-name.xlsx
```

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 404 | `RESULT_NOT_FOUND` | Result not found |
| 410 | `FILE_EXPIRED` | File has expired and been deleted |
| 400 | `NOT_EXTRACT_TYPE` | Not an EXTRACT type result |

---

### 4-4. Visualization HTML Serving

Endpoint used directly as the `src` attribute of an iframe.

```
GET /board/{id}/html
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
  <!-- LLM-generated d3.js visualization HTML -->
</body>
</html>
```

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 410 | `FILE_EXPIRED` | File has expired and been deleted |
| 400 | `NOT_VISUALIZE_TYPE` | Not a VISUALIZE type result |

---

## 5. WebSocket Protocol

Channel for receiving async processing results after query requests (extract/visualize).

### 5-1. Connection

```
WSS /ws
Sec-WebSocket-Protocol: stomp
```

Authentication is via the `JSESSIONID` cookie — no Authorization header required.  
The browser sends the cookie automatically during the WebSocket handshake.

SockJS fallback URL: `https://your-domain.com/ws`

### 5-2. STOMP Subscription

Subscribe using the `wsSubscribePath` from the `POST /api/query/extract` (or visualize) response.

```javascript
const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  // No connectHeaders needed — JSESSIONID cookie is sent automatically
  onConnect: () => {
    client.subscribe(wsSubscribePath, (message) => {
      const payload = JSON.parse(message.body);
      handleMessage(payload);
    });
  }
});
client.activate();
```

### 5-3. Server → Client Message Types

#### PROGRESS — Processing status update

```json
{
  "type": "PROGRESS",
  "resultId": 42,
  "step": "Generating SQL...",
  "stepIndex": 1,
  "totalSteps": 4
}
```

| `step` value | Description |
|-------------|-------------|
| `"Generating SQL..."` | After LLM prompt is sent |
| `"Validating query..."` | During EXPLAIN execution |
| `"Querying data..."` | During PROD DB SELECT execution |
| `"Generating file..."` | During Excel or HTML generation |

#### BOARD_READY — Processing complete (common for extract/visualize)

```json
{
  "type": "BOARD_READY",
  "resultId": 42,
  "resultType": "EXTRACT",
  "datasetName": "Top 10 Seoul Customers by Revenue 2024",
  "boardUrl": "/board/42"
}
```

#### ERROR — Processing failed

```json
{
  "type": "ERROR",
  "resultId": 42,
  "errorCode": "NOT_DATA_EXTRACTION",
  "message": "This request is not related to data extraction. Please try again."
}
```

| `errorCode` | Description |
|------------|-------------|
| `NOT_DATA_EXTRACTION` | LLM responded with "데이터 추출 요구 아님" |
| `QUERY_TOO_EXPENSIVE` | EXPLAIN cost threshold exceeded |
| `SQL_GENERATION_FAILED` | LLM SQL generation failed |
| `DB_EXECUTION_FAILED` | DB execution error |
| `FILE_GENERATION_FAILED` | Excel/HTML generation error |

---

## 6. Admin — Member API

> All `/admin/**` APIs require the `ADMIN` role.

### 6-1. Member List

```
GET /admin/members
```

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `role` | string | `ADMIN` \| `USER` \| `VIEWER` |
| `status` | string | `ACTIVE` \| `INACTIVE` \| `LOCKED` \| `WITHDRAWN` |
| `keyword` | string | Search by username or displayName |
| `page` / `size` | int | Pagination |

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "username": "sherlock",
      "email": "sherlock@example.com",
      "displayName": "Sherlock Holmes",
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

### 6-2. Register Member

```
POST /admin/members
```

**Request Body**

```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "password": "Temp@1234!",
  "displayName": "New User",
  "role": "USER"
}
```

| Field | Type | Required | Validation |
|-------|------|:--------:|------------|
| `username` | string | ✅ | 5~50 chars, alphanumeric + underscore |
| `email` | string | ✅ | Valid email format |
| `password` | string | ✅ | Min 8 chars, upper+lower+number+special char |
| `displayName` | string | ✅ | 2~100 chars |
| `role` | string | ✅ | `ADMIN` \| `USER` \| `VIEWER` |

**Response 201**

```json
{
  "success": true,
  "data": {
    "id": 5,
    "username": "newuser",
    "email": "newuser@example.com",
    "displayName": "New User",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-06-12T15:00:00+09:00"
  }
}
```

---

### 6-3. Change Member Role

```
PUT /admin/members/{id}/role
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

### 6-4. Change Member Status

```
PUT /admin/members/{id}/status
```

**Request Body**

```json
{
  "status": "LOCKED",
  "reason": "Security policy violation"
}
```

| `status` | Description |
|---------|-------------|
| `ACTIVE` | Activate (includes unlock) |
| `INACTIVE` | Deactivate |
| `LOCKED` | Lock |
| `WITHDRAWN` | Process withdrawal |

**Response 200**

```json
{
  "success": true,
  "data": { "id": 5, "status": "LOCKED" }
}
```

---

## 7. Admin — System API

### 7-1. System List

```
GET /admin/systems
```

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "hexa",
      "description": "Hexa Service — BSS Main",
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

> `dbUsername` is masked. `dbPassword` is never included in responses.

---

### 7-2. Register System

```
POST /admin/systems
```

**Request Body**

```json
{
  "name": "hexa",
  "description": "Hexa Service — BSS Main",
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

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| `name` | string | ✅ | Alphanumeric + hyphen, 3~100 chars, must be unique |
| `dbUrl` | string | ✅ | JDBC URL |
| `dbType` | string | ✅ | `MYSQL` \| `MARIADB` \| `ORACLE` \| `POSTGRESQL` \| `MSSQL` |
| `dbUsername` | string | ✅ | DB login ID (AES-GCM encrypted on save) |
| `dbPassword` | string | ✅ | DB login password (AES-GCM encrypted on save) |
| `gitUrl` | string | N | Git repository URL |
| `gitAccessToken` | string | N | Git Access Token (AES-GCM encrypted on save) |
| `slackWebhookUrl` | string | N | Slack Incoming Webhook URL |
| `slackEnabled` | string | N | `Y` \| `N` (default: `N`) |

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

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 409 | `SYSTEM_NAME_DUPLICATE` | System name already exists |
| 400 | `INVALID_DB_CONNECTION` | DB connection validation failed |

---

### 7-3. System Detail

```
GET /admin/systems/{id}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "hexa",
    "description": "Hexa Service",
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
    "createdBy": { "id": 1, "displayName": "Sherlock Holmes" },
    "createdAt": "2026-01-15T09:00:00+09:00"
  }
}
```

---

### 7-4. Update System

```
PUT /admin/systems/{id}
```

**Request Body** — Include only fields to change (Partial Update)

```json
{
  "description": "Hexa Service v2",
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
    "description": "Hexa Service v2",
    "updatedAt": "2026-06-12T16:00:00+09:00"
  }
}
```

---

### 7-5. Delete System

```
DELETE /admin/systems/{id}
```

The following operations are executed in order on deletion:

1. Physical deletion of associated `QueryResult` files + set `unused=Y`
2. Delete ChromaDB collection
3. Delete `/data/systems/{name}_{hash}/` directory
4. Delete `SystemFile` records
5. Delete `TargetSystem` record

**Response 200**

```json
{
  "success": true,
  "data": null
}
```

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 404 | `SYSTEM_NOT_FOUND` | System not found |
| 409 | `INGESTION_RUNNING` | Cannot delete a system with ingestion in progress |

---

## 8. Admin — File API

### 8-1. File List

```
GET /admin/systems/{systemId}/files
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
      "uploadedBy": { "id": 1, "displayName": "Sherlock Holmes" },
      "uploadedAt": "2026-06-12T10:00:00+09:00"
    }
  ]
}
```

---

### 8-2. Upload File

Embedding is automatically triggered immediately after upload.

```
POST /admin/systems/{systemId}/files
Content-Type: multipart/form-data
```

**Form Data**

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| `file` | file | ✅ | `.md` or `.sql` file (max 10MB) |

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
    "message": "File uploaded successfully. Embedding ingestion has started in the background."
  }
}
```

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 400 | `INVALID_FILE_TYPE` | File type other than `.md` / `.sql` |
| 400 | `FILE_TOO_LARGE` | Exceeds 10MB |
| 409 | `FILE_ALREADY_EXISTS` | File with same name already exists (delete and re-upload to overwrite) |

---

### 8-3. Delete File

```
DELETE /admin/systems/{systemId}/files/{fileId}
```

When a file is deleted, the associated chunks are also removed from ChromaDB.

**Response 200**

```json
{
  "success": true,
  "data": null
}
```

---

## 9. Admin — Git API

### 9-1. Git Sync (Clone / Pull)

Executes `git clone` on the first call and `git pull` on subsequent calls.  
Incremental embedding is automatically triggered based on changed files after completion.

```
POST /admin/systems/{systemId}/git/sync
```

**Response 202** — Async processing

```json
{
  "success": true,
  "data": {
    "jobId": 101,
    "message": "Git synchronization started.",
    "statusUrl": "/admin/systems/1/git/status"
  }
}
```

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 400 | `GIT_URL_NOT_SET` | Git URL not configured for this system |
| 409 | `GIT_SYNC_RUNNING` | Synchronization already in progress |

---

### 9-2. Git Status

```
GET /admin/systems/{systemId}/git/status
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "gitUrl": "https://github.com/org/hexa.git",
    "lastSyncAt": "2026-06-12T12:00:00+09:00",
    "lastCommitHash": "a1b2c3d4e5f6789012345678901234567890abcd",
    "lastCommitMessage": "feat: improve BSS battery swap logic",
    "sourcetreeExists": true,
    "syncStatus": "SUCCESS"
  }
}
```

---

## 10. Admin — Ingestion API

### 10-1. Trigger Ingestion

```
POST /admin/systems/{systemId}/ingest
```

**Request Body**

```json
{
  "mode": "INCREMENTAL"
}
```

| `mode` | Description |
|--------|-------------|
| `FULL` | Re-ingest all files (replace all existing ChromaDB chunks) |
| `INCREMENTAL` | Ingest only changed files based on modification time + SHA-256 comparison |

**Response 202**

```json
{
  "success": true,
  "data": {
    "jobId": 102,
    "mode": "INCREMENTAL",
    "message": "Ingestion started.",
    "statusUrl": "/admin/systems/1/ingest/status"
  }
}
```

**Error Responses**

| HTTP | Error Code | Description |
|------|-----------|-------------|
| 409 | `INGESTION_ALREADY_RUNNING` | Ingestion already in progress |

---

### 10-2. Ingestion Status

```
GET /admin/systems/{systemId}/ingest/status
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

## 11. Admin — Job History API

### 11-1. Job History List

```
GET /admin/jobs
```

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `systemId` | long | Filter by specific system |
| `jobType` | string | `QUERY_EXTRACT` \| `INGESTION_FULL` etc. |
| `status` | string | `RUNNING` \| `SUCCESS` \| `FAILED` \| `SKIPPED` |
| `from` | datetime | Start datetime (ISO 8601) |
| `to` | datetime | End datetime (ISO 8601) |
| `page` / `size` | int | Pagination |

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 102,
      "jobType": "INGESTION_INCREMENTAL",
      "systemName": "hexa",
      "triggeredBy": { "id": 1, "displayName": "Sherlock Holmes" },
      "status": "SUCCESS",
      "startedAt": "2026-06-12T14:25:00+09:00",
      "finishedAt": "2026-06-12T14:30:00+09:00",
      "durationMs": 300000,
      "inputSummary": "mode=INCREMENTAL, system=hexa",
      "outputSummary": "5 files, 320 chunks"
    },
    {
      "id": 101,
      "jobType": "QUERY_EXTRACT",
      "systemName": "hexa",
      "triggeredBy": { "id": 1, "displayName": "Sherlock Holmes" },
      "status": "FAILED",
      "startedAt": "2026-06-12T14:00:00+09:00",
      "finishedAt": "2026-06-12T14:00:15+09:00",
      "durationMs": 15000,
      "inputSummary": "Top 10 Seoul customers by revenue 2024...",
      "outputSummary": null,
      "errorCode": "QueryTooExpensiveException",
      "errorMessage": "Query execution cost exceeds the allowed threshold."
    }
  ],
  "meta": { "page": 0, "size": 20, "totalElements": 234, "totalPages": 12 }
}
```

---

### 11-2. Job History Detail (with Stack Trace)

```
GET /admin/jobs/{id}
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
    "triggeredBy": { "id": 1, "displayName": "Sherlock Holmes" },
    "status": "FAILED",
    "startedAt": "2026-06-12T14:00:00+09:00",
    "finishedAt": "2026-06-12T14:00:15+09:00",
    "durationMs": 15000,
    "inputSummary": "Top 10 Seoul customers by revenue 2024...",
    "outputSummary": null,
    "errorCode": "QueryTooExpensiveException",
    "errorMessage": "Query execution cost exceeds the allowed threshold. (cost=72453.2, rows=2100000, access=ALL)",
    "stackTrace": "com.yourcompany.nlplatform.domain.exception.QueryTooExpensiveException: ...\n\tat com.yourcompany..."
  }
}
```

---

### 11-3. Job History by System

```
GET /admin/systems/{systemId}/jobs
```

*Same response structure as 11-1, but returns only jobs for the specified system.*

---

## 12. Error Code Definitions

### 12-1. HTTP Status Code Usage Principles

| HTTP | When Used |
|------|----------|
| 200 | Successful read/update/delete |
| 201 | Successful creation |
| 202 | Async processing started (query, Git Sync, ingestion) |
| 400 | Request parameter/body validation error |
| 401 | Authentication failure or token expired |
| 403 | Insufficient permissions |
| 404 | Resource not found |
| 409 | State conflict (duplicate, already in progress, etc.) |
| 410 | Resource expired (file TTL exceeded) |
| 423 | Account locked |
| 500 | Internal server error |

### 12-2. Full Error Code List

| Error Code | HTTP | Description |
|-----------|------|-------------|
| `INVALID_CREDENTIALS` | 401 | Username/password mismatch |
| `TOKEN_EXPIRED` | 401 | Access Token expired |
| `TOKEN_INVALID` | 401 | Invalid token |
| `ACCOUNT_LOCKED` | 423 | Account is locked |
| `ACCOUNT_INACTIVE` | 403 | Inactive account |
| `ACCESS_DENIED` | 403 | Insufficient permissions |
| `INVALID_CURRENT_PASSWORD` | 401 | Current password mismatch |
| `PASSWORD_MISMATCH` | 400 | New password confirmation mismatch |
| `MEMBER_NOT_FOUND` | 404 | Member not found |
| `MEMBER_USERNAME_DUPLICATE` | 409 | Duplicate username |
| `MEMBER_EMAIL_DUPLICATE` | 409 | Duplicate email |
| `SYSTEM_NOT_FOUND` | 404 | System not found |
| `SYSTEM_NAME_DUPLICATE` | 409 | Duplicate system name |
| `INVALID_DB_CONNECTION` | 400 | DB connection validation failed |
| `INGESTION_NOT_COMPLETED` | 400 | Query attempted on system with incomplete ingestion |
| `INGESTION_ALREADY_RUNNING` | 409 | Ingestion already in progress |
| `INGESTION_RUNNING` | 409 | Attempted to delete a system with ingestion running |
| `GIT_URL_NOT_SET` | 400 | Git URL not configured |
| `GIT_SYNC_RUNNING` | 409 | Git synchronization already in progress |
| `GIT_CLONE_FAILED` | 500 | Git clone failed |
| `INVALID_FILE_TYPE` | 400 | File type not allowed |
| `FILE_TOO_LARGE` | 400 | File size exceeds limit (10MB) |
| `FILE_ALREADY_EXISTS` | 409 | File with same name already exists |
| `FILE_NOT_FOUND` | 404 | File not found |
| `RESULT_NOT_FOUND` | 404 | Board result not found |
| `FILE_EXPIRED` | 410 | File has expired and been deleted |
| `NOT_EXTRACT_TYPE` | 400 | Excel download — not an EXTRACT type |
| `NOT_VISUALIZE_TYPE` | 400 | HTML serving — not a VISUALIZE type |
| `NOT_DATA_EXTRACTION` | 400 | LLM rejection ("데이터 추출 요구 아님") |
| `QUERY_TOO_EXPENSIVE` | 400 | EXPLAIN cost threshold exceeded |
| `SQL_GENERATION_FAILED` | 500 | LLM SQL generation failed |
| `DB_EXECUTION_FAILED` | 500 | DB execution error |
| `FILE_GENERATION_FAILED` | 500 | Excel/HTML generation error |
| `JOB_NOT_FOUND` | 404 | Job history not found |
| `INTERNAL_SERVER_ERROR` | 500 | Internal server error |

---

*This document is an appendix to `architecture-design-v7.md` — API Specification.*
