# Hyperion — UI/UX 설계 명세서

> 작성일: 2026-06-13  
> 프로젝트: hyperion (NL-to-SQL 데이터 분석 플랫폼)  
> 기술 스택: Mustache SPA, Vanilla JS, SockJS + STOMP, d3.js, html2canvas

---

## 목차

1. [전체 설계 원칙](#1-전체-설계-원칙)
2. [디자인 시스템](#2-디자인-시스템)
3. [레이아웃 구조](#3-레이아웃-구조)
4. [헤더](#4-헤더)
5. [메인 화면 — 채팅 인터페이스](#5-메인-화면--채팅-인터페이스)
6. [입력 컨트롤 상세](#6-입력-컨트롤-상세)
7. [전송 후 모달 흐름](#7-전송-후-모달-흐름)
8. [WebSocket 진행 상황 표시](#8-websocket-진행-상황-표시)
9. [결과 렌더링 영역](#9-결과-렌더링-영역)
10. [사이드바](#10-사이드바)
11. [URL 라우팅](#11-url-라우팅)
12. [상태 전환 다이어그램](#12-상태-전환-다이어그램)
13. [반응형 처리](#13-반응형-처리)
14. [접근성](#14-접근성)

---

## 1. 전체 설계 원칙

### 1-1. SPA 구성

- 전체 애플리케이션은 단일 페이지로 구성합니다.
- 화면 전환은 History API(`pushState`)로 URL을 변경하되 페이지를 새로 불러오지 않습니다.
- 모든 팝업과 다이얼로그는 **레이어 팝업(Layer Popup)** 으로 구현합니다. 별도 창(`window.open`) 사용을 금지합니다.
- 기본 표시 언어는 **영어**입니다.

### 1-2. 디자인 레퍼런스

- Spring Cloud Eureka 대시보드의 레이아웃·카드 스타일을 참고합니다.
- Eureka의 초록·회색 계열 색상 대신 **연한 블루(Light Blue)** 계열을 전체 테마로 사용합니다.
- 기술적이고 간결한(clean & technical) 인터페이스를 지향합니다.

---

## 2. 디자인 시스템

### 2-1. 컬러 팔레트

| 역할 | 변수명 | HEX | 사용처 |
|------|--------|-----|--------|
| Primary | `--color-primary` | `#2563EB` | 버튼, 강조 링크, 활성 상태 |
| Primary Hover | `--color-primary-hover` | `#1D4ED8` | 버튼 호버 |
| Primary Light | `--color-primary-light` | `#DBEAFE` | 배경 강조, 선택 상태 |
| Primary Muted | `--color-primary-muted` | `#BFDBFE` | 테두리, 구분선 |
| Header BG | `--color-header-bg` | `#1E40AF` | 헤더 배경 |
| Surface | `--color-surface` | `#FFFFFF` | 카드, 입력창, 사이드바 |
| Background | `--color-bg` | `#F0F4FF` | 전체 페이지 배경 |
| Text Primary | `--color-text-primary` | `#111827` | 본문 텍스트 |
| Text Secondary | `--color-text-secondary` | `#6B7280` | 보조 텍스트, 힌트 |
| Text Muted | `--color-text-muted` | `#9CA3AF` | 비활성, 타임스탬프 |
| Text On Primary | `--color-text-on-primary` | `#FFFFFF` | 헤더·버튼 위 텍스트 |
| Success | `--color-success` | `#16A34A` | 완료 아이콘·메시지 |
| Error | `--color-error` | `#DC2626` | 오류 상태 |

### 2-2. 타이포그래피

| 역할 | 폰트 패밀리 | 크기 | 굵기 | 비고 |
|------|-----------|------|------|------|
| 로고 `hyperion` | `'Courier New', Courier, monospace` | 22px | 700 | 모두 소문자 |
| 본문 | `'Inter', -apple-system, sans-serif` | 14px | 400 | |
| 코드 / SQL | `'Courier New', Courier, monospace` | 13px | 400 | |
| 진행 메시지 | `'Inter', sans-serif` | 13px | 400 | |
| 버튼 | `'Inter', sans-serif` | 14px | 500 | |
| 모달 제목 | `'Inter', sans-serif` | 16px | 600 | |
| 사이드바 항목 | `'Inter', sans-serif` | 13px | 400 | |

> `hyperion` 로고는 Windows 명령 프롬프트·Linux 콘솔 스타일의 고정폭(Monospace) 폰트를 사용하며 **반드시 소문자**로 표기합니다.

### 2-3. 간격 체계 (스페이싱)

```
4px  — xs    (미세 여백)
8px  — sm    (요소 내부)
12px — md-   (컴포넌트 내)
16px — md    (기본 패딩)
24px — lg    (섹션 간)
32px — xl    (모달 패딩)
48px — 2xl   (대형 여백)
```

### 2-4. 공통 컴포넌트 스타일

```css
:root {
  --color-primary:       #2563EB;
  --color-primary-hover: #1D4ED8;
  --color-primary-light: #DBEAFE;
  --color-primary-muted: #BFDBFE;
  --color-header-bg:     #1E40AF;
  --color-surface:       #FFFFFF;
  --color-bg:            #F0F4FF;
  --color-text-primary:  #111827;
  --color-text-secondary:#6B7280;
  --color-text-muted:    #9CA3AF;
  --color-success:       #16A34A;
  --color-error:         #DC2626;
  --radius-sm:  6px;
  --radius-md:  10px;
  --radius-lg:  16px;
  --shadow-sm:  0 1px 4px rgba(0,0,0,0.08);
  --shadow-md:  0 4px 16px rgba(37,99,235,0.10);
  --shadow-lg:  0 20px 60px rgba(0,0,0,0.15);
}

/* Primary 버튼 */
.btn-primary {
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: 8px 18px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-primary:hover    { background: var(--color-primary-hover); }
.btn-primary:disabled { background: var(--color-primary-muted); cursor: not-allowed; }

/* Outline 버튼 */
.btn-outline {
  background: transparent;
  color: var(--color-primary);
  border: 1.5px solid var(--color-primary);
  border-radius: var(--radius-sm);
  padding: 7px 18px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.btn-outline:hover { background: var(--color-primary-light); }

/* 모달 오버레이 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 모달 박스 */
.modal-box {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 32px;
  min-width: 380px;
  max-width: 480px;
  box-shadow: var(--shadow-lg);
}
```

---

## 3. 레이아웃 구조

```
┌─────────────────────────────────────────────────────────────────┐
│  HEADER  (고정, height: 56px)                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  MAIN FRAME  (height: calc(100vh - 56px), overflow: hidden)     │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  CHAT AREA  (flex-grow: 1, overflow-y: auto)              │  │
│  │  - 사용자 메시지 말풍선                                    │  │
│  │  - 진행 상황 메시지 (No 선택 시)                           │  │
│  │  - 결과 렌더링 (Excel 버튼 / iframe / Markdown)            │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │  INPUT CONTROL  (하단 고정)                               │  │
│  │  [System ▼]  [□ Visualize] [□ Analyze Result]            │  │
│  │  ─────────────────────────────────────────────────────    │  │
│  │  textarea                                           [↑]   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                                           ┌──────────────────────┐
                                           │  SIDEBAR (오른쪽, 슬라이딩) │
                                           │  width: 300px         │
                                           └──────────────────────┘
```

- 헤더는 `position: fixed`로 항상 상단에 고정됩니다.
- 메인 프레임은 헤더 아래부터 뷰포트 전체를 차지하며, 내부가 Flex Column으로 구성됩니다.
- 사이드바는 오른쪽에서 슬라이딩으로 등장하고, 메인 프레임을 밀지 않고 **오버레이** 형태로 덮습니다.

---

## 4. 헤더

### 4-1. 구조

```
┌────────────────────────────────────────────────────────────────────────┐
│  hyperion               Ask Data. Discover Insight.          [≡]       │
│  (로고 / 홈 링크)        (슬로건 / GitHub 링크)              (사이드바)  │
└────────────────────────────────────────────────────────────────────────┘
```

### 4-2. 요소 명세

| 요소 | 위치 | 스타일 | 동작 |
|------|------|--------|------|
| `hyperion` 로고 | 왼쪽 | monospace, 흰색, 22px, bold | 클릭 → `/` (홈 초기화) |
| `Ask Data. Discover Insight.` | 오른쪽 (아이콘 왼쪽) | 흰색, 14px, italic, underline on hover | 클릭 → `https://github.com/lyvius2/hyperion` 새 탭 |
| 사이드바 토글 버튼 | 오른쪽 끝 | 흰색 햄버거/목록 아이콘, 24px | 클릭 → 우측 사이드바 슬라이딩 토글 |

### 4-3. CSS

```css
.header {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 56px;
  background: var(--color-header-bg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 900;
  box-shadow: 0 2px 8px rgba(0,0,0,0.18);
}

.header-logo {
  font-family: 'Courier New', Courier, monospace;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  text-decoration: none;
  letter-spacing: 1px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-slogan {
  color: var(--color-primary-muted);
  font-size: 14px;
  font-style: italic;
  text-decoration: none;
  transition: color 0.15s;
}
.header-slogan:hover { color: #fff; }

.header-sidebar-btn {
  background: none;
  border: none;
  color: #fff;
  font-size: 22px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  line-height: 1;
  transition: background 0.15s;
}
.header-sidebar-btn:hover { background: rgba(255,255,255,0.15); }
```

---

## 5. 메인 화면 — 채팅 인터페이스

### 5-1. 초기 상태 (채팅 내역 없음)

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│                                                              │
│              What data would you like to explore?            │
│                                                              │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  [Select a system ▼]  [□ Visualize] [□ Analyze Result] │  │
│  │  ────────────────────────────────────────────────────  │  │
│  │  Type your data request here...                        │  │
│  │  (Shift+Enter for new line)                       [↑]  │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

중앙 안내 문구 `"What data would you like to explore?"` 는 채팅 내역이 생기면 사라집니다.

### 5-2. 메시지 스타일

```css
/* 채팅 영역 */
.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 사용자 메시지 (오른쪽) */
.msg-user {
  align-self: flex-end;
  background: var(--color-primary);
  color: #fff;
  border-radius: 18px 18px 4px 18px;
  padding: 12px 16px;
  max-width: 70%;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: var(--shadow-sm);
}

/* 시스템 응답 (왼쪽) */
.msg-system {
  align-self: flex-start;
  background: var(--color-surface);
  border: 1px solid var(--color-primary-muted);
  border-radius: 18px 18px 18px 4px;
  padding: 12px 16px;
  max-width: 85%;
  font-size: 14px;
  box-shadow: var(--shadow-sm);
}
```

---

## 6. 입력 컨트롤 상세

### 6-1. 전체 구조

```
┌──────────────────────────────────────────────────────────────────┐
│  [Select a system ▼]           [□ Visualize] [□ Analyze Result]  │
├──────────────────────────────────────────────────────────────────┤
│  Type your data request here...                                  │
│  (여러 줄 입력 가능)                                              │
│                                                             [↑]  │
└──────────────────────────────────────────────────────────────────┘
```

### 6-2. 시스템 선택 드롭다운

| 항목 | 내용 |
|------|------|
| 위치 | 입력 컨트롤 상단 왼쪽 |
| 기본값 | `Select a system...` (플레이스홀더) |
| 데이터 | `GET /api/systems` — `ingestionStatus = COMPLETED` 시스템만 목록 표시 |
| 필수 | 미선택 상태로 전송 시 에러 토스트 표시 (`"Please select a system first."`) |

### 6-3. 체크박스 옵션

| 체크박스 | 영문 라벨 | 동작 |
|---------|----------|------|
| Visualize | `Visualize` | 체크 시 LLM에 d3.js HTML 시각화 생성 요청 포함 |
| Analyze Result | `Analyze Result` | 체크 시 LLM에 통계·비즈니스 의미 분석 요청 포함 |

두 체크박스는 독립적으로 선택 가능하며 동시 선택도 허용합니다.

### 6-4. 텍스트 입력 영역

| 항목 | 내용 |
|------|------|
| 타입 | `<textarea>` |
| 최소 높이 | 80px |
| 최대 높이 | 240px (초과 시 내부 스크롤) |
| 자동 높이 | 입력 내용에 따라 자동으로 높이 증가 |
| 줄 바꿈 | `Shift + Enter` → 줄 바꿈 |
| 전송 | `Enter` 단독 → 전송 |
| 플레이스홀더 | `Type your data request here... (Shift+Enter for new line)` |
| 비활성화 조건 | 처리 대기 중(No 선택 후), 히스토리 조회 모드 |

### 6-5. 전송 버튼 상태

| 상태 | 표시 내용 | 클릭 가능 |
|------|----------|:--------:|
| 입력 없음 | 버튼 숨김 | — |
| 입력 있음 | 위 방향 화살표 ↑ | ✅ |
| 처리 중 | 화살표 사라짐, 회전 스피너 | ❌ |

```css
.input-wrapper {
  position: relative;
  background: var(--color-surface);
  border: 1.5px solid var(--color-primary-muted);
  border-radius: var(--radius-md);
  padding: 12px 16px;
  box-shadow: var(--shadow-md);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.input-wrapper:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37,99,235,0.15);
}

.input-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  line-height: 1.6;
  min-height: 80px;
  max-height: 240px;
  overflow-y: auto;
  background: transparent;
  color: var(--color-text-primary);
  font-family: 'Inter', sans-serif;
}
.input-textarea:disabled { color: var(--color-text-muted); }
.input-textarea::placeholder { color: var(--color-text-muted); }

.send-btn {
  position: absolute;
  bottom: 12px;
  right: 12px;
  width: 34px;
  height: 34px;
  background: var(--color-primary);
  border: none;
  border-radius: var(--radius-sm);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: background 0.15s;
}
.send-btn:hover { background: var(--color-primary-hover); }

/* 로딩 스피너 */
.spinner {
  width: 18px;
  height: 18px;
  border: 2.5px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.75s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
```

---

## 7. 전송 후 모달 흐름

### 7-1. 전체 흐름 다이어그램

```
[전송 버튼 클릭]
      │
      ▼
┌─────────────────────────────────┐
│  [모달 1] 대기 확인              │
│  "Data analysis may take        │
│   a while.                      │
│   Would you like to grab        │
│   a coffee?"                    │
│                                 │
│       [No]        [Yes]         │
└──────┬──────────────┬───────────┘
       │              │
       ▼              ▼
  [모달 2-No]    [모달 2-Yes]
  "Please stay   "The result will
   on the screen  appear in chat.
   and wait."     Slack will notify
                  you when done!"
       │              │
       ▼              ▼
  [Confirm]       [Confirm]
       │              │
       ▼              ▼
  대기 모드       초기 화면 복귀
  (진행 상황      (백그라운드 처리)
   표시)
```

---

### 7-2. 모달 1 — 대기 확인

**트리거**: 전송 버튼 클릭 즉시 표시

```
┌────────────────────────────────────────────────┐
│                                                │
│   Data analysis may take a while.             │
│   Would you like to grab a coffee?            │
│                                               │
│                    [ No ]     [ Yes ]         │
│                                               │
└────────────────────────────────────────────────┘
```

| 버튼 | 다음 동작 |
|------|----------|
| **Yes** | 모달 1 닫힘 → 모달 2-Yes 표시 |
| **No** | 모달 1 닫힘 → 모달 2-No 표시 |

---

### 7-3. 모달 2-Yes — 백그라운드 처리 안내

```
┌────────────────────────────────────────────────┐
│                                                │
│   The analysis result will appear in your     │
│   chat history, and we'll notify you via      │
│   Slack when it's ready!                      │
│   (if your Slack account is configured)       │
│                                               │
│                           [ Confirm ]         │
│                                               │
└────────────────────────────────────────────────┘
```

**Confirm 클릭 후**:
- 모달 닫힘
- 입력창 내용 초기화
- URL `/` 로 복귀
- 백그라운드에서 처리 계속 진행

---

### 7-4. 모달 2-No — 화면 대기 안내

```
┌────────────────────────────────────────────────┐
│                                                │
│   Please stay on the screen and wait.         │
│   We'll show you the progress right here!     │
│                                               │
│                           [ Confirm ]         │
│                                               │
└────────────────────────────────────────────────┘
```

**Confirm 클릭 후**:
1. 모달 닫힘
2. 텍스트 입력 영역 **비활성화**
3. 전송 버튼 화살표(↑) → **로딩 스피너** 교체
4. 입력 컨트롤 아래 **진행 상황 영역** 나타남
5. WebSocket 구독 시작 (`/topic/session/{sessionId}`)

---

### 7-5. 모달 공통 규칙

- 배경 클릭으로 닫히지 않습니다.
- `ESC` 키로 닫히지 않습니다.
- 항상 화면 정중앙에 표시됩니다.
- 닫기(✕) 버튼이 없습니다.
- 오버레이 배경: `rgba(0, 0, 0, 0.45)`

---

## 8. WebSocket 진행 상황 표시

### 8-1. 표시 조건

- **No 선택 → Confirm** 후에만 표시됩니다.
- 현재 로그인 사용자와 요청자가 동일할 때만 표시됩니다.
- 입력 컨트롤 바로 아래에 위치합니다.

### 8-2. 진행 단계 메시지

메시지는 이전 항목을 교체하지 않고 **아래로 누적** 표시됩니다.

| 단계 | 메시지 (영어) | 아이콘 |
|------|--------------|--------|
| 1 | `AI is analyzing your request...` | ⟳ 진행 중 |
| 2 | `SQL query has been generated.` | ✓ 완료 |
| 3 | `Executing query on the database...` | ⟳ |
| 4 | `Generating EXCEL file...` | ⟳ |
| 5 | `EXCEL file is ready.` | ✓ |
| 6-A *(옵션 선택 시)* | `Analyzing result data...` | ⟳ |
| 6-B *(옵션 선택 시)* | `Analysis complete. Displaying results.` | ✓ |

### 8-3. 분기 흐름

**분기 1 — Visualize / Analyze Result 모두 미선택**

```
EXCEL 파일 생성 완료 (단계 5)
  └─▶ [⬇ Download Excel] 버튼 표시
       └─▶ 입력 컨트롤 활성화 + 스피너 → 화살표 복원
```

**분기 2 — Visualize 또는 Analyze Result 선택**

```
EXCEL 파일 생성 완료 (단계 5)
  └─▶ [⬇ Download Excel] 버튼 표시
       └─▶ LLM 시각화/분석 요청 (단계 6-A)
            └─▶ 결과 수신 (단계 6-B)
                 └─▶ 결과 렌더링
                      └─▶ 입력 컨트롤 활성화 + 스피너 → 화살표 복원
```

### 8-4. 스타일

```css
.progress-area {
  margin-top: 8px;
  padding: 14px 16px;
  background: var(--color-primary-light);
  border: 1px solid var(--color-primary-muted);
  border-radius: var(--radius-sm);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.progress-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.progress-item.done   { color: var(--color-success); }
.progress-item.active { color: var(--color-primary); font-weight: 500; }

.progress-spinner {
  width: 14px; height: 14px;
  border: 2px solid var(--color-primary-muted);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.75s linear infinite;
  flex-shrink: 0;
}
.progress-check { color: var(--color-success); font-size: 14px; }
```

---

## 9. 결과 렌더링 영역

입력 컨트롤 아래, 진행 상황 영역 아래에 표시됩니다.  
새 요청이 시작되면 이 영역은 **초기화**됩니다.

### 9-1. Excel 다운로드 버튼

```
[⬇ Download Excel]
```

- EXCEL 파일 생성 완료(단계 5) 시 표시
- 클릭 → `GET /board/{resultId}/download`
- 파일 만료 시 버튼 대신 아래 문구 표시:
  - `"The download period has expired and the EXCEL file has been deleted."`

### 9-2. 시각화 영역 (Visualize 선택 시)

```
┌──────────────────────────────────────────────────────┐
│                       [⬇ Download Graph (PNG)] ←     │  ← 오른쪽 상단 오버레이 버튼
│                                                      │
│    (d3.js 시각화 그래프 — iframe 렌더링)               │
│                                                      │
└──────────────────────────────────────────────────────┘
```

| 항목 | 내용 |
|------|------|
| 렌더링 방식 | `<iframe src="/board/{resultId}/html" sandbox="allow-scripts">` |
| 기본 높이 | 500px |
| 그래프 다운로드 버튼 | iframe 오른쪽 상단 오버레이, 클릭 시 `html2canvas` → PNG 다운로드 |
| PNG 파일명 | `{datasetName}.png` |

### 9-3. 결과 분석 영역 (Analyze Result 선택 시)

- LLM이 Markdown 형식으로 반환
- `marked.js` 등으로 HTML 변환 후 렌더링
- 시각화 결과가 있다면 그 **아래에** 표시

### 9-4. 결과 표시 순서

```
① [⬇ Download Excel] 버튼  (또는 만료 문구)
② 시각화 iframe            (Visualize 선택 시)
③ 결과 분석 Markdown       (Analyze Result 선택 시)
```

```css
.result-area {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 시각화 컨테이너 */
.viz-container {
  position: relative;
  border: 1px solid var(--color-primary-muted);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.viz-iframe {
  width: 100%;
  height: 500px;
  border: none;
  display: block;
}
.viz-download-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 10;
  background: rgba(255,255,255,0.92);
  border: 1px solid var(--color-primary-muted);
  border-radius: var(--radius-sm);
  padding: 5px 12px;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
  backdrop-filter: blur(2px);
}

/* 결과 분석 */
.analysis-area {
  padding: 20px 24px;
  background: var(--color-surface);
  border: 1px solid var(--color-primary-muted);
  border-radius: var(--radius-sm);
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-text-primary);
}
```

---

## 10. 사이드바

### 10-1. 구조 및 애니메이션

```
┌─────────────────────────────┐
│  [+ New Chat]               │  ← 상단 고정
├─────────────────────────────┤
│  Recent                     │  ← 섹션 제목
│─────────────────────────────│
│  2024년 서울 매출 상위...    │  ← 히스토리 항목
│  hexa · Jun 12, 14:30  📊  │
│─────────────────────────────│
│  월별 배터리 교환 건수...    │
│  hexa · Jun 11, 09:15  📋  │
└─────────────────────────────┘
```

```css
.sidebar {
  position: fixed;
  top: 56px;
  right: 0;
  width: 300px;
  height: calc(100vh - 56px);
  background: var(--color-surface);
  border-left: 1px solid var(--color-primary-muted);
  transform: translateX(100%);
  transition: transform 0.25s ease-in-out;
  z-index: 800;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.sidebar.open { transform: translateX(0); }

.sidebar-overlay {
  position: fixed;
  inset: 56px 0 0 0;
  background: rgba(0,0,0,0.28);
  z-index: 799;
  display: none;
}
.sidebar-overlay.visible { display: block; }
```

### 10-2. 새 채팅 버튼

- 사이드바 최상단 고정
- 클릭 시: 사이드바 닫힘 → 메인 화면 초기화 → URL `/` 이동

### 10-3. 히스토리 항목

| 표시 정보 | 내용 |
|---------|------|
| 제목 | `dataset_name` (1줄, 말줄임 처리) |
| 부제 | `{시스템명} · {날짜시간}` |
| 아이콘 | 📊 Visualize 선택 여부, 📋 Analyze Result 선택 여부 |
| 정렬 | `requested_at DESC` |

### 10-4. 히스토리 항목 클릭 시 동작

1. 사이드바 닫힘
2. URL → `/history/{resultId}`
3. 입력 컨트롤 **비활성화** (읽기 전용)
4. 텍스트 영역에 당시 자연어 입력 내용 표시
5. 결과 영역에 당시 결과 표시:
   - Excel 다운로드 버튼 또는 만료 문구
   - 시각화 iframe (해당 시)
   - 결과 분석 Markdown (해당 시)
6. 상단에 **"← New Chat"** 버튼 표시 → 클릭 시 초기 화면 이동

### 10-5. 만료 파일 처리

| 조건 | 처리 |
|------|------|
| `file_deleted = 'Y'` | 다운로드 버튼 대신 만료 문구 표시 |
| 시각화 HTML | 만료 여부 무관, **항상 표시** |
| 결과 분석 Markdown | 만료 여부 무관, **항상 표시** |

만료 문구 (영어):
> `"The download period has expired and the EXCEL file has been deleted."`

---

## 11. URL 라우팅

| URL | 화면 상태 |
|-----|----------|
| `/` | 메인 채팅 초기 화면 |
| `/chat/{resultId}` | No 선택 후 처리 중·완료 결과 화면 |
| `/history/{resultId}` | 히스토리에서 과거 결과 조회 |

---

## 12. 상태 전환 다이어그램

```
[초기 화면]
    │  텍스트 입력 + 전송
    ▼
[모달 1: 대기 확인]
    │ Yes                    │ No
    ▼                        ▼
[모달 2-Yes]           [모달 2-No]
    │ Confirm                │ Confirm
    ▼                        ▼
[초기 화면 복귀]        [대기 모드]
(백그라운드 처리)        입력 비활성화
                         스피너 표시
                              │ WebSocket 진행 메시지 수신
                              ▼
                         [결과 표시]
                         Excel 버튼
                         iframe (선택 시)
                         Markdown (선택 시)
                              │ 완료
                              ▼
                         [입력 컨트롤 활성화]
                         스피너 → 화살표 복원
                              │ 새 입력 + 전송
                              ▼
                         [결과 영역 초기화]
                         → [모달 1] 반복
```

---

## 13. 반응형 처리

| 뷰포트 폭 | 처리 |
|----------|------|
| ≥ 1024px | 전체 레이아웃 표시 |
| 768px ~ 1023px | 사이드바 오버레이 유지, 입력 컨트롤 100% 너비 |
| < 768px | 헤더 슬로건 텍스트 숨김, 모달 너비 `90vw` |

---

## 14. 접근성

| 항목 | 처리 방법 |
|------|----------|
| 버튼 레이블 | 아이콘 전용 버튼에 `aria-label` 필수 (`aria-label="Send"`, `aria-label="Toggle sidebar"`) |
| 모달 포커스 | 모달 열릴 때 첫 번째 버튼으로 포커스 이동, Tab 키 모달 내부로 제한 |
| 모달 숨김 | 배경 콘텐츠에 `aria-hidden="true"` 적용 |
| 로딩 상태 | `aria-busy="true"`, 진행 메시지 영역에 `aria-live="polite"` |
| 색상 대비 | WCAG AA 기준 (4.5:1) 준수 |
| 비활성 입력 | `disabled` 속성 + `aria-disabled="true"` 병행 적용 |

---

*이 문서는 hyperion UI/UX 설계 초안을 기반으로 작성된 명세서입니다.*  
*구현 과정에서 세부 사항은 조정될 수 있습니다.*
