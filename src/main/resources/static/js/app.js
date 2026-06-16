// ── Input control — UI_UX_DESIGN §5, §6 ────────────────────────────────
// TODO: wire up to /api/llm/providers, /api/systems, and send pipeline.
(() => {
  const textarea = document.querySelector('[data-role="input-textarea"]');
  const sendBtn  = document.querySelector('[data-role="send-btn"]');
  const form     = document.querySelector('[data-role="input-form"]');

  if (!textarea || !sendBtn || !form) return;

  const MAX_HEIGHT = 240;

  const sync = () => {
    const hasContent = textarea.value.trim().length > 0;
    sendBtn.classList.toggle('is-hidden', !hasContent);

    textarea.style.height = 'auto';
    const next = Math.min(textarea.scrollHeight, MAX_HEIGHT);
    textarea.style.height = next + 'px';
  };

  textarea.addEventListener('input', sync);

  textarea.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      form.requestSubmit();
    }
  });

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const text = textarea.value.trim();
    if (!text) return;

    const provider  = document.querySelector('[data-role="llm-select"]').value;
    const system    = document.querySelector('[data-role="system-select"]').value;
    const visualize = document.querySelector('[data-role="opt-visualize"]').checked;
    const analyze   = document.querySelector('[data-role="opt-analyze"]').checked;

    if (!system) {
      // TODO: replace with toast component per UI_UX_DESIGN §11
      console.warn('Please select a system first.');
      return;
    }

    // TODO: POST to backend endpoint once it exists.
    console.log('submit', { provider, system, visualize, analyze, text });
  });

  sync();
})();

// ── Sidebar — UI_UX_DESIGN §10 ──────────────────────────────────────────
const sidebar = (() => {
  const sidebarEl  = document.getElementById('sidebar');
  const overlayEl  = document.querySelector('[data-role="sidebar-overlay"]');
  const toggleBtn  = document.querySelector('[data-role="sidebar-toggle"]');
  const newChatBtn = document.querySelector('[data-role="new-chat-btn"]');
  const historyList = document.querySelector('[data-role="sidebar-history-list"]');
  const emptyEl    = document.querySelector('[data-role="sidebar-empty"]');

  if (!sidebarEl || !overlayEl || !toggleBtn) return {};

  /** Open the sidebar with sliding animation and show the backdrop. */
  function open() {
    sidebarEl.classList.add('open');
    sidebarEl.setAttribute('aria-hidden', 'false');
    overlayEl.classList.add('visible');
    toggleBtn.setAttribute('aria-label', 'Close sidebar');
    toggleBtn.setAttribute('aria-expanded', 'true');
    loadHistory();
  }

  /** Close the sidebar and hide the backdrop. */
  function close() {
    sidebarEl.classList.remove('open');
    sidebarEl.setAttribute('aria-hidden', 'true');
    overlayEl.classList.remove('visible');
    toggleBtn.setAttribute('aria-label', 'Open sidebar');
    toggleBtn.setAttribute('aria-expanded', 'false');
  }

  /** Toggle open / closed state. */
  function toggle() {
    sidebarEl.classList.contains('open') ? close() : open();
  }

  /**
   * Render a history item <li> element.
   * @param {{ id: number, datasetName: string, systemName: string,
   *           requestedAt: string, hasVisualize: boolean, hasAnalyze: boolean }} item
   */
  function renderHistoryItem(item) {
    const li = document.createElement('li');
    li.className = 'sidebar-history-item';
    li.setAttribute('role', 'listitem');
    li.dataset.resultId = item.id;

    // Format date: "Jun 12, 14:30"
    const date = new Date(item.requestedAt);
    const dateStr = date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
      + ', ' + date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });

    const icons = [
      item.hasVisualize ? '📊' : '',
      item.hasAnalyze   ? '📋' : '',
    ].filter(Boolean).join(' ');

    li.innerHTML = `
      <span class="sidebar-history-item__title">${escapeHtml(item.datasetName)}</span>
      <div class="sidebar-history-item__meta">
        <span>${escapeHtml(item.systemName)} · ${dateStr}</span>
        ${icons ? `<span class="sidebar-history-item__icons">${icons}</span>` : ''}
      </div>
    `;

    li.addEventListener('click', () => {
      close();
      // Highlight active item
      document.querySelectorAll('.sidebar-history-item').forEach(el => el.classList.remove('is-active'));
      li.classList.add('is-active');
      // TODO: navigate to /history/{id} via History API once routing is implemented
      console.log('history item clicked', item.id);
    });

    return li;
  }

  /**
   * Load and render the query result history.
   * TODO: replace stub data with real GET /api/board call once backend is ready.
   */
  async function loadHistory() {
    if (!historyList) return;

    // Clear existing dynamic items (keep the empty state element)
    historyList.querySelectorAll('.sidebar-history-item').forEach(el => el.remove());

    // --- Stub: replace with real fetch once API exists ---
    // const res  = await fetch('/api/board?size=30&sort=requestedAt,desc');
    // const body = await res.json();
    // const items = body.data ?? [];
    const items = []; // TODO: real API call
    // ----------------------------------------------------

    if (items.length === 0) {
      if (emptyEl) emptyEl.style.display = '';
      return;
    }

    if (emptyEl) emptyEl.style.display = 'none';
    items.forEach(item => historyList.appendChild(renderHistoryItem(item)));
  }

  /** Simple HTML escape to prevent XSS in dataset names / system names. */
  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  // Wire up events
  toggleBtn.addEventListener('click', toggle);

  // Clicking the overlay closes the sidebar
  overlayEl.addEventListener('click', close);

  // "New Chat" button: close sidebar and reset to home
  if (newChatBtn) {
    newChatBtn.addEventListener('click', () => {
      close();
      // TODO: full reset logic (clear chat area, navigate to '/') when routing is in place
      window.history.pushState({}, '', '/');
      console.log('new chat');
    });
  }

  // Close on Escape key
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && sidebarEl.classList.contains('open')) {
      close();
    }
  });

  return { open, close, toggle, loadHistory };
})();
