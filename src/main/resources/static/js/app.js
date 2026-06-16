// ── Sign In modal ─────────────────────────────
const signinModal = (() => {
  const modalEl   = document.getElementById('signin-modal');
  const closeBtn  = document.querySelector('[data-role="signin-close"]');
  const form      = document.querySelector('[data-role="signin-form"]');
  const usernameInput = document.querySelector('[data-role="signin-username"]');
  const passwordInput = document.querySelector('[data-role="signin-password"]');
  const errorEl   = document.querySelector('[data-role="signin-error"]');
  const submitBtn = document.querySelector('[data-role="signin-submit"]');

  if (!modalEl) return {};

  /** Display an error message inside the modal. */
  function showError(message) {
    if (!errorEl) return;
    errorEl.textContent = message;
    errorEl.classList.remove('is-hidden');
    [usernameInput, passwordInput].forEach(el => el?.classList.add('is-error'));
  }

  /** Clear any visible error state. */
  function clearError() {
    if (!errorEl) return;
    errorEl.classList.add('is-hidden');
    errorEl.textContent = '';
    [usernameInput, passwordInput].forEach(el => el?.classList.remove('is-error'));
  }

  /** Close (hide) the modal with a fade-out transition. */
  function close() {
    modalEl.classList.add('is-hidden');
    modalEl.setAttribute('aria-hidden', 'true');
    clearError();
  }

  /** Open (show) the modal and focus the username field. */
  function open() {
    modalEl.classList.remove('is-hidden');
    modalEl.setAttribute('aria-hidden', 'false');
    usernameInput?.focus();
  }

  /**
   * Submit sign-in credentials to POST /auth/login.
   * On success, the server sets a JSESSIONID cookie and we reload.
   * On failure, display a user-friendly error message.
   */
  async function submit() {
    const username = usernameInput?.value.trim() ?? '';
    const password = passwordInput?.value ?? '';

    if (!username || !password) {
      showError('Please enter both your ID and password.');
      return;
    }

    clearError();
    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = 'Signing in…';
    }

    try {
      const res = await fetch('/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ username, password }),
      });

      if (res.ok) {
        // Session cookie (JSESSIONID) is set by the server automatically.
        close();
        // TODO: update header with member info (displayName, role) once
        //       the member API is wired up, instead of a full reload.
        window.location.reload();
        return;
      }

      // Map known HTTP status codes → user-friendly messages
      const ERROR_MESSAGES = {
        401: 'Incorrect ID or password. Please try again.',
        403: 'Your account is inactive. Contact your administrator.',
        423: 'Your account is locked due to too many failed attempts.',
      };
      showError(ERROR_MESSAGES[res.status] ?? 'Sign in failed. Please try again.');
    } catch (_) {
      showError('Network error. Please check your connection and try again.');
    } finally {
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Sign In';
      }
    }
  }

  // ── Wire up events ────────────────────────────────────────────────────

  // X button closes the modal
  closeBtn?.addEventListener('click', close);

  // Form submit (Enter key on any field, or button click)
  form?.addEventListener('submit', (event) => {
    event.preventDefault();
    submit();
  });

  // Clear error when the user starts typing again
  [usernameInput, passwordInput].forEach(el => {
    el?.addEventListener('input', clearError);
  });

  // Trap Tab key inside the modal for accessibility
  modalEl.addEventListener('keydown', (event) => {
    if (event.key !== 'Tab') return;
    const focusable = [...modalEl.querySelectorAll(
      'input, button:not(:disabled), [tabindex]:not([tabindex="-1"])'
    )];
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last  = focusable[focusable.length - 1];
    if (event.shiftKey) {
      if (document.activeElement === first) { event.preventDefault(); last.focus(); }
    } else {
      if (document.activeElement === last)  { event.preventDefault(); first.focus(); }
    }
  });

  // Auto-focus username when modal is visible on page load
  if (!modalEl.classList.contains('is-hidden')) {
    usernameInput?.focus();
  }

  return { open, close };
})();

// ── Input control ────────────────────────────────
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

// ── Sidebar ──────────────────────────────────────────
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
