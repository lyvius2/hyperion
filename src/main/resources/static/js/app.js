// Basic interactions for the input control.
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

    const provider = document.querySelector('[data-role="llm-select"]').value;
    const system   = document.querySelector('[data-role="system-select"]').value;
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