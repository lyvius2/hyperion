# Hyperion — UI/UX Design Specification

> Date: 2026-06-13  
> Project: hyperion (NL-to-SQL Data Analysis Platform)  
> Tech Stack: Mustache SPA, Vanilla JS, SockJS + STOMP, d3.js, html2canvas

---

[한국어(Korean) 문서](UI_UX_DESIGN_KR.md)

## Table of Contents

1. [Overall Design Principles](#1-overall-design-principles)
2. [Design System](#2-design-system)
3. [Layout Structure](#3-layout-structure)
4. [Header](#4-header)
5. [Main Screen — Chat Interface](#5-main-screen--chat-interface)
6. [Input Control Detail](#6-input-control-detail)
7. [Post-Submission Modal Flow](#7-post-submission-modal-flow)
8. [WebSocket Progress Display](#8-websocket-progress-display)
9. [Result Rendering Area](#9-result-rendering-area)
10. [Sidebar](#10-sidebar)
11. [URL Routing](#11-url-routing)
12. [State Transition Diagram](#12-state-transition-diagram)
13. [Responsive Behavior](#13-responsive-behavior)
14. [Accessibility](#14-accessibility)

---

## 1. Overall Design Principles

### 1-1. SPA Architecture

- The entire application is a Single Page Application (SPA).
- Screen transitions change the URL using the History API (`pushState`) without a full page reload.
- All popups and dialogs are implemented as **Layer Popups**. Opening separate windows (`window.open`) is prohibited.
- The default display language is **English**.

### 1-2. Design Reference

- Inspired by the layout and card style of the Spring Cloud Eureka dashboard.
- Replaces Eureka's green/grey palette with a **Light Blue** theme throughout.
- Aims for a clean and technical interface aesthetic.

---

## 2. Design System

### 2-1. Color Palette

| Role | Variable | HEX | Usage |
|------|----------|-----|-------|
| Primary | `--color-primary` | `#2563EB` | Buttons, active links, active state |
| Primary Hover | `--color-primary-hover` | `#1D4ED8` | Button hover |
| Primary Light | `--color-primary-light` | `#DBEAFE` | Background highlight, selection |
| Primary Muted | `--color-primary-muted` | `#BFDBFE` | Borders, dividers |
| Header BG | `--color-header-bg` | `#1E40AF` | Header background |
| Surface | `--color-surface` | `#FFFFFF` | Cards, inputs, sidebar |
| Background | `--color-bg` | `#F0F4FF` | Page background |
| Text Primary | `--color-text-primary` | `#111827` | Body text |
| Text Secondary | `--color-text-secondary` | `#6B7280` | Secondary text, hints |
| Text Muted | `--color-text-muted` | `#9CA3AF` | Disabled, timestamps |
| Text On Primary | `--color-text-on-primary` | `#FFFFFF` | Text on header/buttons |
| Success | `--color-success` | `#16A34A` | Completion icons and messages |
| Error | `--color-error` | `#DC2626` | Error states |

### 2-2. Typography

| Role | Font Family | Size | Weight | Notes |
|------|-------------|------|--------|-------|
| Logo `hyperion` | `'Courier New', Courier, monospace` | 22px | 700 | All lowercase |
| Body | `'Inter', -apple-system, sans-serif` | 14px | 400 | |
| Code / SQL | `'Courier New', Courier, monospace` | 13px | 400 | |
| Progress messages | `'Inter', sans-serif` | 13px | 400 | |
| Buttons | `'Inter', sans-serif` | 14px | 500 | |
| Modal title | `'Inter', sans-serif` | 16px | 600 | |
| Sidebar items | `'Inter', sans-serif` | 13px | 400 | |

> The `hyperion` logo uses a fixed-width Monospace font in the style of the Windows Command Prompt or Linux Console, and must always be displayed in **lowercase**.

### 2-3. Spacing Scale

```
4px  — xs    (micro spacing)
8px  — sm    (element inner)
12px — md-   (component inner)
16px — md    (default padding)
24px — lg    (between sections)
32px — xl    (modal padding)
48px — 2xl   (large whitespace)
```

### 2-4. Common Component Styles

```css
:root {
  --color-primary:        #2563EB;
  --color-primary-hover:  #1D4ED8;
  --color-primary-light:  #DBEAFE;
  --color-primary-muted:  #BFDBFE;
  --color-header-bg:      #1E40AF;
  --color-surface:        #FFFFFF;
  --color-bg:             #F0F4FF;
  --color-text-primary:   #111827;
  --color-text-secondary: #6B7280;
  --color-text-muted:     #9CA3AF;
  --color-success:        #16A34A;
  --color-error:          #DC2626;
  --radius-sm:  6px;
  --radius-md:  10px;
  --radius-lg:  16px;
  --shadow-sm:  0 1px 4px rgba(0,0,0,0.08);
  --shadow-md:  0 4px 16px rgba(37,99,235,0.10);
  --shadow-lg:  0 20px 60px rgba(0,0,0,0.15);
}

/* Primary Button */
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

/* Outline Button */
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

/* Modal Overlay */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.45);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Modal Box */
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

## 3. Layout Structure

```
┌─────────────────────────────────────────────────────────────────┐
│  HEADER  (fixed, height: 56px)                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  MAIN FRAME  (height: calc(100vh - 56px), overflow: hidden)     │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  CHAT AREA  (flex-grow: 1, overflow-y: auto)              │  │
│  │  - User message bubbles                                   │  │
│  │  - Progress messages (when No is selected)                │  │
│  │  - Result rendering (Excel button / iframe / Markdown)    │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │  INPUT CONTROL  (fixed at bottom)                         │  │
│  │  [Select system ▼]  [□ Visualize] [□ Analyze Result]     │  │
│  │  ─────────────────────────────────────────────────────    │  │
│  │  textarea                                           [↑]   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                                           ┌──────────────────────┐
                                           │  SIDEBAR (right,     │
                                           │  sliding, 300px)     │
                                           └──────────────────────┘
```

- The header is always fixed at the top (`position: fixed`).
- The main frame fills the viewport below the header and uses a Flex Column layout internally.
- The sidebar slides in from the right and **overlays** the main frame without pushing it.

---

## 4. Header

### 4-1. Structure

```
┌────────────────────────────────────────────────────────────────────────┐
│  hyperion               Ask Data. Discover Insight.          [≡]       │
│  (logo / home link)     (slogan / GitHub link)           (sidebar)     │
└────────────────────────────────────────────────────────────────────────┘
```

### 4-2. Element Specification

| Element | Position | Style | Behavior |
|---------|----------|-------|----------|
| `hyperion` logo | Left | Monospace, white, 22px, bold | Click → `/` (reset home) |
| `Ask Data. Discover Insight.` | Right (left of icon) | White, 14px, italic, underline on hover | Click → open `https://github.com/lyvius2/hyperion` in new tab |
| Sidebar toggle button | Far right | White hamburger/list icon, 24px | Click → toggle right sidebar sliding |

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

## 5. Main Screen — Chat Interface

### 5-1. Initial State (No Chat History)

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│                                                              │
│              What data would you like to explore?            │
│                                                              │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  [Select a system ▼]   [□ Visualize] [□ Analyze Result]│  │
│  │  ─────────────────────────────────────────────────     │  │
│  │  Type your data request here...                        │  │
│  │  (Shift+Enter for new line)                       [↑]  │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

The centered placeholder `"What data would you like to explore?"` disappears once chat history exists.

### 5-2. Message Styles

```css
/* Chat area */
.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* User message (right-aligned) */
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

/* System response (left-aligned) */
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

## 6. Input Control Detail

### 6-1. Full Structure

```
┌──────────────────────────────────────────────────────────────────┐
│  [Select a system ▼]            [□ Visualize] [□ Analyze Result] │
├──────────────────────────────────────────────────────────────────┤
│  Type your data request here...                                  │
│  (multi-line input supported)                                    │
│                                                             [↑]  │
└──────────────────────────────────────────────────────────────────┘
```

### 6-2. System Selector Dropdown

| Item | Detail |
|------|--------|
| Position | Top-left of input control |
| Default | `Select a system...` (placeholder) |
| Data source | `GET /api/systems` — shows only systems with `ingestionStatus = COMPLETED` |
| Validation | If submitted without selection, show error toast: `"Please select a system first."` |

### 6-3. Checkbox Options

| Checkbox | Label | Behavior |
|---------|-------|----------|
| Visualize | `Visualize` | When checked, includes d3.js HTML visualization generation request in the LLM prompt |
| Analyze Result | `Analyze Result` | When checked, includes statistical and business meaning analysis request in the LLM prompt |

Both checkboxes are independently selectable and can both be checked simultaneously.

### 6-4. Text Input Area

| Item | Detail |
|------|--------|
| Tag | `<textarea>` |
| Min height | 80px |
| Max height | 240px (scrolls internally beyond this) |
| Auto-resize | Height grows automatically with content |
| Line break | `Shift + Enter` → new line |
| Submit | `Enter` alone → submit |
| Placeholder | `Type your data request here... (Shift+Enter for new line)` |
| Disabled when | Waiting for result (after selecting No), viewing history |

### 6-5. Send Button States

| State | Display | Clickable |
|-------|---------|:---------:|
| No input | Button hidden | — |
| Has input | Upward arrow ↑ | ✅ |
| Processing | Arrow gone, rotating spinner | ❌ |

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

/* Loading spinner */
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

## 7. Post-Submission Modal Flow

### 7-1. Full Flow Diagram

```
[Send button clicked]
        │
        ▼
┌──────────────────────────────────┐
│  [Modal 1] Wait confirmation     │
│  "Data analysis may take         │
│   a while.                       │
│   Would you like to grab         │
│   a coffee?"                     │
│                                  │
│         [ No ]       [ Yes ]     │
└──────┬───────────────────┬───────┘
       │                   │
       ▼                   ▼
 [Modal 2-No]        [Modal 2-Yes]
 "Please stay        "The result will
  on the screen       appear in chat.
  and wait."          Slack will notify
                      you when done!"
       │                   │
       ▼                   ▼
  [ Confirm ]          [ Confirm ]
       │                   │
       ▼                   ▼
  Wait mode           Return to home
  (show progress)     (background processing)
```

---

### 7-2. Modal 1 — Wait Confirmation

**Trigger**: Displayed immediately when the send button is clicked.

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

| Button | Next Action |
|--------|-------------|
| **Yes** | Close Modal 1 → Show Modal 2-Yes |
| **No** | Close Modal 1 → Show Modal 2-No |

---

### 7-3. Modal 2-Yes — Background Processing Notice

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

**After Confirm**:
- Modal closes
- Input text area is cleared
- URL returns to `/`
- Processing continues in the background

---

### 7-4. Modal 2-No — Wait on Screen Notice

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

**After Confirm**:
1. Modal closes
2. Text input area **disabled**
3. Send button arrow (↑) replaced with **loading spinner**
4. **Progress display area** appears below the input control
5. WebSocket subscription starts (`/topic/session/{sessionId}`)

---

### 7-5. Modal Common Rules

- Clicking the background does **not** close the modal.
- Pressing `ESC` does **not** close the modal.
- Always displayed at the center of the screen.
- No close button (✕) inside the modal.
- Overlay background: `rgba(0, 0, 0, 0.45)`

---

## 8. WebSocket Progress Display

### 8-1. Display Conditions

- Displayed **only** after selecting No → Confirm.
- Visible only when the currently logged-in user matches the requester.
- Positioned directly below the input control.

### 8-2. Progress Step Messages

Messages **accumulate downward** without replacing previous items.

| Step | Message | Icon |
|------|---------|------|
| 1 | `AI is analyzing your request...` | ⟳ in progress |
| 2 | `SQL query has been generated.` | ✓ done |
| 3 | `Executing query on the database...` | ⟳ |
| 4 | `Generating EXCEL file...` | ⟳ |
| 5 | `EXCEL file is ready.` | ✓ |
| 6-A *(if options selected)* | `Analyzing result data...` | ⟳ |
| 6-B *(if options selected)* | `Analysis complete. Displaying results.` | ✓ |

### 8-3. Branch Flow

**Branch 1 — Neither Visualize nor Analyze Result selected**

```
EXCEL file ready (Step 5)
  └─▶ [⬇ Download Excel] button appears
       └─▶ Input control re-enabled + spinner → arrow restored
```

**Branch 2 — Visualize or Analyze Result selected**

```
EXCEL file ready (Step 5)
  └─▶ [⬇ Download Excel] button appears
       └─▶ LLM visualization/analysis request (Step 6-A)
            └─▶ Result received (Step 6-B)
                 └─▶ Render results
                      └─▶ Input control re-enabled + spinner → arrow restored
```

### 8-4. Styles

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

## 9. Result Rendering Area

Located below the input control and the progress area.  
This area is **reset** when a new request is submitted.

### 9-1. Excel Download Button

```
[⬇ Download Excel]
```

- Appears when the EXCEL file is ready (Step 5)
- Click → `GET /board/{resultId}/download`
- When the file has expired, show the following text instead of a button:
  - `"The download period has expired and the EXCEL file has been deleted."`

### 9-2. Visualization Area (when Visualize is selected)

```
┌──────────────────────────────────────────────────────┐
│                       [⬇ Download Graph (PNG)]  ←    │  ← top-right overlay button
│                                                      │
│    (d3.js visualization — rendered in iframe)        │
│                                                      │
└──────────────────────────────────────────────────────┘
```

| Item | Detail |
|------|--------|
| Rendering | `<iframe src="/board/{resultId}/html" sandbox="allow-scripts">` |
| Default height | 500px |
| Download Graph button | Top-right overlay on the iframe; click → `html2canvas` → PNG download |
| PNG filename | `{datasetName}.png` |

### 9-3. Result Analysis Area (when Analyze Result is selected)

- LLM returns Markdown format
- Converted to HTML via `marked.js` and rendered
- Placed **below** the visualization area when both are present

### 9-4. Result Display Order

```
① [⬇ Download Excel] button  (or expiration message)
② Visualization iframe        (if Visualize selected)
③ Analysis Markdown           (if Analyze Result selected)
```

```css
.result-area {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Visualization container */
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

/* Result analysis */
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

## 10. Sidebar

### 10-1. Structure and Animation

```
┌─────────────────────────────┐
│  [+ New Chat]               │  ← pinned at top
├─────────────────────────────┤
│  Recent                     │  ← section label
│─────────────────────────────│
│  Top 10 Seoul Customers...  │  ← history item
│  hexa · Jun 12, 14:30  📊  │
│─────────────────────────────│
│  Monthly Battery Swaps...   │
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

### 10-2. New Chat Button

- Pinned at the top of the sidebar
- Click: close sidebar → reset main screen → navigate to `/`

### 10-3. History Items

| Display | Content |
|---------|---------|
| Title | `dataset_name` (1 line, truncated with ellipsis) |
| Subtitle | `{system name} · {date time}` |
| Icons | 📊 if Visualize was selected, 📋 if Analyze Result was selected |
| Sort order | `requested_at DESC` |

### 10-4. History Item Click Behavior

1. Sidebar closes
2. URL changes to `/history/{resultId}`
3. Input control **disabled** (read-only)
4. The original natural language input is displayed in the text area
5. Results from that request are displayed:
   - Excel download button or expiration message
   - Visualization iframe (if applicable)
   - Result analysis Markdown (if applicable)
6. A **"← New Chat"** button appears at the top → click to return to the initial screen

### 10-5. Expired File Handling

| Condition | Action |
|-----------|--------|
| `file_deleted = 'Y'` | Show expiration message instead of download button |
| Visualization HTML | **Always shown**, regardless of expiration |
| Result analysis Markdown | **Always shown**, regardless of expiration |

Expiration message:
> `"The download period has expired and the EXCEL file has been deleted."`

---

## 11. URL Routing

| URL | Screen State |
|-----|-------------|
| `/` | Main chat initial screen |
| `/chat/{resultId}` | Active result screen (processing or completed, after selecting No) |
| `/history/{resultId}` | Past result view from history |

---

## 12. State Transition Diagram

```
[Initial screen]
     │  Input text + submit
     ▼
[Modal 1: Wait confirmation]
     │ Yes                       │ No
     ▼                           ▼
[Modal 2-Yes]              [Modal 2-No]
     │ Confirm                   │ Confirm
     ▼                           ▼
[Return to home]           [Wait mode]
(background processing)    Input disabled
                           Spinner shown
                                │ WebSocket progress messages
                                ▼
                           [Display results]
                           Excel button
                           iframe (if selected)
                           Markdown (if selected)
                                │ Complete
                                ▼
                           [Input control re-enabled]
                           Spinner → arrow restored
                                │ New input + submit
                                ▼
                           [Result area cleared]
                           → [Modal 1] repeats
```

---

## 13. Responsive Behavior

| Viewport Width | Behavior |
|---------------|----------|
| ≥ 1024px | Full layout displayed |
| 768px – 1023px | Sidebar remains as overlay, input control 100% width |
| < 768px | Header slogan text hidden, modal width `90vw` |

---

## 14. Accessibility

| Item | Implementation |
|------|---------------|
| Button labels | Icon-only buttons must have `aria-label` (e.g. `aria-label="Send"`, `aria-label="Toggle sidebar"`) |
| Modal focus | Focus moves to first button when modal opens; Tab key is trapped within modal |
| Modal backdrop | Background content gets `aria-hidden="true"` when modal is open |
| Loading state | `aria-busy="true"`, progress area uses `aria-live="polite"` |
| Color contrast | Complies with WCAG AA standard (4.5:1 ratio) |
| Disabled input | Use both `disabled` attribute and `aria-disabled="true"` |

---

*This document is the UI/UX specification based on the hyperion design draft.*  
*Details may be adjusted during implementation.*
