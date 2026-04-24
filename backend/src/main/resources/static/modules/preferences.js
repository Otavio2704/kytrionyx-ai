/* ════════════════════════════════════════════════════════════════
   PREFERÊNCIAS - Tema, acentos, configurações
════════════════════════════════════════════════════════════════ */

function loadPreferences() {
  applyTheme(localStorage.getItem('oc-theme') || 'dark');
  applyAccent(localStorage.getItem('oc-accent') || '#00e5a0');
  const lang = localStorage.getItem('oc-language') || '';
  if (el.languageSelect) el.languageSelect.value = lang;
  state.language = lang;
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem('oc-theme', theme);
  const hljsLink = $('hljs-theme');
  if (hljsLink) {
    hljsLink.href = theme === 'dark'
      ? 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark-dimmed.min.css'
      : 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css';
  }
  syncBrandAssets(theme);
}

function syncBrandAssets(theme) {
  const isLight = theme === 'light';
  document.querySelectorAll('[data-theme-dark-src][data-theme-light-src]').forEach((image) => {
    image.src = isLight ? image.dataset.themeLightSrc : image.dataset.themeDarkSrc;
  });

  const favicon = $('app-favicon');
  if (favicon) {
    favicon.href = isLight ? './kytrionyx-logo-claro.png' : './kytrionyx-logo.png';
  }

  document.documentElement.style.setProperty(
    '--assistant-avatar-image',
    isLight ? "url('./kytrionyx-logo-claro.png')" : "url('./kytrionyx-logo.png')"
  );
}

function applyAccent(color) {
  document.documentElement.style.setProperty('--accent', color);
  document.documentElement.style.setProperty('--accent-dim', color + 'b3');
  document.documentElement.style.setProperty('--accent-glow', color + '1f');
  localStorage.setItem('oc-accent', color);
  document.querySelectorAll('.swatch').forEach(s => s.classList.toggle('active', s.dataset.color === color));
  if (el.customColorInput) el.customColorInput.value = color;
}
