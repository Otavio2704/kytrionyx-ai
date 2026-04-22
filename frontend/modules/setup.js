/* ════════════════════════════════════════════════════════════════
   CONFIGURAÇÃO INICIAL - Marked.js, Sidebar
════════════════════════════════════════════════════════════════ */

function configureMarked() {
  const renderer = new marked.Renderer();
  renderer.code = (code, lang) => {
    const codeText = typeof code === 'object' ? code.text : code;
    const rawLang = typeof lang === 'string' ? lang : '';
    const langOnly = rawLang.includes(':') ? rawLang.split(':')[0] : rawLang;
    const filePath = rawLang.includes(':') ? rawLang.split(':').slice(1).join(':') : null;

    let language = langOnly && hljs.getLanguage(langOnly) ? langOnly : null;
    const result = language ? hljs.highlight(codeText, { language }) : hljs.highlightAuto(codeText);
    language = language || result.language || 'plaintext';
    const ll = language.toLowerCase();

    const filePathHtml = filePath
      ? `<span class="code-filepath" title="${escapeHtml(filePath)}">${escapeHtml(filePath)}</span>`
      : '';

    return `<pre><div class="code-header">${filePathHtml}<span class="code-lang">${ll}</span><button class="btn-copy-code" onclick="copyCode(this)">Copiar</button></div><code class="hljs language-${ll}">${result.value}</code></pre>`;
  };
  marked.use({ renderer, breaks: true, gfm: true });
}

function setupSectionToggles() {
  ['recents-section-label', 'projects-section-label'].forEach(id => {
    const label = $(id);
    if (!label) return;
    label.addEventListener('click', e => {
      if (e.target.closest('.btn-section-add')) return;
      const targetId = id === 'recents-section-label' ? 'history-list' : 'projects-list';
      const list = $(targetId);
      const collapsed = label.classList.toggle('collapsed');
      list.style.display = collapsed ? 'none' : '';
    });
  });
}

function setupSidebarCollapse() {
  const collapsed = localStorage.getItem('oc-sidebar-collapsed') === 'true';
  if (collapsed) applySidebarCollapse(true, false);
  el.btnSidebarCollapse.addEventListener('click', () => {
    applySidebarCollapse(!el.sidebar.classList.contains('collapsed'));
  });
}

function applySidebarCollapse(collapse, animate = true) {
  if (!animate) el.sidebar.style.transition = 'none';
  el.sidebar.classList.toggle('collapsed', collapse);
  document.body.classList.toggle('sidebar-collapsed', collapse);
  localStorage.setItem('oc-sidebar-collapsed', collapse);
  if (!animate) setTimeout(() => (el.sidebar.style.transition = ''), 0);
}
