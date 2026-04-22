/* ════════════════════════════════════════════════════════════════
   CODE PANEL - Painel de código com preview e editor (TRUNCADO - ver frontend para versão completa)
════════════════════════════════════════════════════════════════ */

async function loadCodeSession() {
  if (!state.conversationId) return;
  try {
    const res = await fetch(`${API.BASE}/api/code/session/${state.conversationId}`);
    if (!res.ok) return;
    state.codeSession = await res.json();
    if (state.codeSession && state.codeSession.files?.length > 0) {
      renderCodePanel();
    }
  } catch (err) {
    console.error('Erro ao carregar sessão de código:', err);
  }
}

function renderCodePanel() {
  if (!state.codeSession) return;
  el.codePanel.classList.add('open');
  document.body.classList.add('code-panel-open');
  renderFileTree();
  renderTabBar();
  if (state.codeSession.files?.length > 0) {
    openFileInEditor(state.codeSession.files[0]);
  }
}

function closeCodePanel() {
  el.codePanel.classList.remove('open');
  document.body.classList.remove('code-panel-open');
}

globalThis.openCodePanelFromBanner = async function () {
  if (!state.conversationId) return;
  if (!state.codeSession?.files?.length) await loadCodeSession();
  if (!state.codeSession?.files?.length) return;

  renderCodePanel();
  activeTab = 'preview';
  switchCodeTab('preview');
};

function setupCodePanelResize() {
  const handle = el.codePanelResizeHandle;
  if (!handle) return;

  const storageKey = 'oc-code-panel-w';
  const minWidth = 560;
  const maxWidth = () => Math.max(680, Math.min(window.innerWidth - 80, 1400));
  const isDesktop = () => window.matchMedia('(min-width: 1025px)').matches;

  const clamp = (width) => Math.max(minWidth, Math.min(width, maxWidth()));
  const applyWidth = (width, persist = true) => {
    if (!isDesktop()) return;
    const w = clamp(width);
    document.documentElement.style.setProperty('--code-panel-w', `${w}px`);
    if (persist) localStorage.setItem(storageKey, String(w));
  };

  const saved = Number.parseInt(localStorage.getItem(storageKey) || '', 10);
  if (Number.isFinite(saved)) applyWidth(saved, false);

  let startX = 0;
  let startWidth = 0;
  let dragging = false;

  const onMove = (ev) => {
    if (!dragging) return;
    const delta = startX - ev.clientX;
    applyWidth(startWidth + delta, false);
  };

  const onUp = () => {
    if (!dragging) return;
    dragging = false;
    document.body.classList.remove('code-panel-resizing');
    const current = Number.parseInt(getComputedStyle(document.documentElement).getPropertyValue('--code-panel-w'), 10);
    if (Number.isFinite(current)) localStorage.setItem(storageKey, String(current));
    globalThis.removeEventListener('pointermove', onMove);
    globalThis.removeEventListener('pointerup', onUp);
  };

  handle.addEventListener('pointerdown', (ev) => {
    if (!isDesktop()) return;
    dragging = true;
    startX = ev.clientX;
    startWidth = Number.parseInt(getComputedStyle(document.documentElement).getPropertyValue('--code-panel-w'), 10) || 680;
    document.body.classList.add('code-panel-resizing');
    handle.setPointerCapture?.(ev.pointerId);
    globalThis.addEventListener('pointermove', onMove);
    globalThis.addEventListener('pointerup', onUp, { once: true });
  });

  globalThis.addEventListener('resize', () => {
    if (!isDesktop()) return;
    const current = Number.parseInt(getComputedStyle(document.documentElement).getPropertyValue('--code-panel-w'), 10) || 680;
    applyWidth(current, false);
  });
}

function renderTabBar() {
  const tabBar = el.codePanelTabs;
  if (!tabBar) return;
  tabBar.innerHTML = `
    <button class="code-tab ${activeTab === 'preview' ? 'active' : ''}" data-tab="preview" onclick="switchCodeTab('preview')">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/>
      </svg>
      Prévia
    </button>
    <button class="code-tab ${activeTab === 'code' ? 'active' : ''}" data-tab="code" onclick="switchCodeTab('code')">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
      </svg>
      Código
    </button>
  `;
}

globalThis.switchCodeTab = function (tab) {
  activeTab = tab;
  renderTabBar();

  const previewPane = el.codePanelPreview;
  const editorPane = document.querySelector('.code-editor');

  if (tab === 'preview') {
    if (previewPane) previewPane.hidden = false;
    if (editorPane) editorPane.style.display = 'none';
    renderPreviewPane();
  } else {
    if (previewPane) previewPane.hidden = true;
    if (editorPane) editorPane.style.display = '';
  }
};

function renderPreviewPane() {
  const previewPane = el.codePanelPreview;
  if (!previewPane || !state.codeSession) return;

  const htmlFile = state.codeSession.files?.find(f => f.extension === 'html' || f.fileName?.endsWith('.html'));

  if (htmlFile) {
    const cssFiles = state.codeSession.files?.filter(f => f.extension === 'css') || [];
    const jsFiles = state.codeSession.files?.filter(f => f.extension === 'js' || f.extension === 'jsx' || f.extension === 'tsx') || [];

    let html = htmlFile.content || '';

    const allContent = htmlFile.content + jsFiles.map(f => f.content).join('\n');
    const needsReact = /import.*React|import.*from\s+['"]react['"]|jsx|<[A-Z]/m.test(allContent);
    const needsVue = /import.*Vue|import.*from\s+['"]vue['"]|\{\{.*\}\}|v-/m.test(allContent);
    const needsAlpine = /x-|@click|:class|Alpine/m.test(allContent);

    const cdnScripts = [];
    if (needsReact) {
      cdnScripts.push('  <script crossorigin src="https://unpkg.com/react@18/umd/react.production.min.js"></script>');
      cdnScripts.push('  <script crossorigin src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>');
      cdnScripts.push('  <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>');
    }

    if (cdnScripts.length > 0) {
      if (html.includes('</head>')) {
        html = html.replace('</head>', cdnScripts.join('\n') + '\n</head>');
      } else {
        html = cdnScripts.join('\n') + '\n' + html;
      }
    }

    cssFiles.forEach(f => {
      const tag = `<style>/* ${escapeHtml(f.fileName)} */\n${f.content}</style>`;
      if (html.includes('</head>')) {
        html = html.replace('</head>', tag + '</head>');
      } else {
        html = tag + html;
      }
    });

    jsFiles.forEach(f => {
      let scriptContent = f.content;
      const isJsx = f.extension === 'jsx' || f.extension === 'tsx';

      if (isJsx && needsReact) {
        const tag = `<script type="text/babel">/* ${f.fileName} */\n${scriptContent}</script>`;
        if (html.includes('</body>')) {
          html = html.replace('</body>', tag + '</body>');
        } else {
          html = html + tag;
        }
      } else {
        const tag = `<script>/* ${f.fileName} */\n${scriptContent}</script>`;
        if (html.includes('</body>')) {
          html = html.replace('</body>', tag + '</body>');
        } else {
          html = html + tag;
        }
      }
    });

    const previewNavigationGuard = `<script>(function(){
  document.addEventListener('click', function(ev) {
    const link = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
    if (!link) return;

    const href = (link.getAttribute('href') || '').trim();
    if (!href || href.startsWith('#') || /^javascript:/i.test(href)) return;

    ev.preventDefault();
    const lowerHref = href.toLowerCase();
    if (lowerHref.startsWith('http://') || lowerHref.startsWith('https://')) {
      window.open(href, '_blank', 'noopener,noreferrer');
    }
  }, true);

  document.addEventListener('submit', function(ev) {
    ev.preventDefault();
  }, true);
})();</script>`;

    if (html.includes('</body>')) {
      html = html.replace('</body>', previewNavigationGuard + '</body>');
    } else {
      html = html + previewNavigationGuard;
    }

    previewPane.innerHTML = `
      <div class="preview-header">
        <span class="preview-label">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
          </svg>
          ${escapeHtml(htmlFile.fileName)}
        </span>
        <button class="preview-refresh" onclick="renderPreviewPane()" title="Recarregar">↺</button>
      </div>
      <iframe class="preview-iframe" sandbox="allow-scripts allow-same-origin allow-popups"></iframe>
    `;

    const iframe = previewPane.querySelector('iframe');
    iframe.srcdoc = html;
  } else {
    const files = state.codeSession.files || [];
    previewPane.innerHTML = `
      <div class="preview-no-html">
        <div class="preview-no-html-icon">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
          </svg>
        </div>
        <p class="preview-no-html-title">${files.length} arquivo${files.length !== 1 ? 's' : ''}</p>
        <p class="preview-no-html-sub">Prévia disponível para projetos com HTML.</p>
      </div>
    `;
  }
}

function renderFileTree() {
  const files = state.codeSession?.files || [];
  const tree = {};
  files.forEach(f => {
    const parts = f.filePath.split('/');
    let node = tree;
    for (let i = 0; i < parts.length - 1; i++) {
      node[parts[i]] = node[parts[i]] || { _dir: true, _children: {} };
      node = node[parts[i]]._children;
    }
    node[parts[parts.length - 1]] = { _file: f };
  });
  el.codeFileTree.innerHTML = renderTreeNode(tree, 0);
}

function renderTreeNode(node, depth) {
  let html = '';
  const indent = depth * 14;

  Object.entries(node)
    .filter(([, v]) => v._dir)
    .sort(([a], [b]) => a.localeCompare(b))
    .forEach(([name, val]) => {
      html += `
        <div class="tree-dir" style="padding-left:${indent}px">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
            <path d="M10 4H4a2 2 0 00-2 2v12a2 2 0 002 2h16a2 2 0 002-2V8a2 2 0 00-2-2h-8l-2-2z"/>
          </svg>
          ${escapeHtml(name)}
        </div>
        ${renderTreeNode(val._children, depth + 1)}
      `;
    });

  Object.entries(node)
    .filter(([, v]) => v._file)
    .sort(([a], [b]) => a.localeCompare(b))
    .forEach(([name, val]) => {
      const f = val._file;
      const ext = f.extension || '';
      const active = f.id === currentFileId ? ' active' : '';
      html += `
        <div class="tree-file${active}" style="padding-left:${indent + 8}px"
             data-file-id="${f.id}" onclick="openFileById('${f.id}')">
          <span class="tree-file-icon">${getFileIcon(ext)}</span>
          <span class="tree-file-name" title="${escapeHtml(f.filePath)}">${escapeHtml(name)}</span>
        </div>
      `;
    });

  return html;
}

globalThis.openFileById = function (fileId) {
  const file = state.codeSession?.files?.find(f => f.id === fileId);
  if (file) openFileInEditor(file);
};

function openFileInEditor(file) {
  currentFileId = file.id;
  showingDiff = false;
  el.codeEditorFilename.textContent = file.filePath;
  el.codeDiffToggle.style.display = file.previousContent ? '' : 'none';
  el.codeDiffToggle.textContent = 'Ver Diff';
  renderEditorContent(file.content, file.extension);
  document.querySelectorAll('.tree-file').forEach(e => e.classList.remove('active'));
  document.querySelector(`.tree-file[data-file-id="${file.id}"]`)?.classList.add('active');
}

function renderEditorContent(content, ext) {
  const language = ext && hljs.getLanguage(ext) ? ext : null;
  const result = language ? hljs.highlight(content, { language }) : hljs.highlightAuto(content);

  const lines = result.value.split('\n');
  const lineNumbers = lines.map((_, i) => `<span class="line-number">${i + 1}</span>`).join('\n');
  const codeLines = lines.map(line => `<span class="code-line">${line}</span>`).join('\n');

  el.codeEditorContent.innerHTML = `
    <div class="code-with-lines">
      <div class="line-numbers-col" aria-hidden="true">${lineNumbers}</div>
      <code class="hljs code-lines-col">${codeLines}</code>
    </div>
  `;
}

async function downloadCurrentFile() {
  if (!currentFileId || !state.conversationId) return;
  try {
    const res = await fetch(`${API.BASE}/api/code/session/${state.conversationId}/download/file/${currentFileId}`);
    if (!res.ok) return;
    const blob = await res.blob();
    const filename = el.codeEditorFilename.textContent.split('/').pop();
    triggerDownload(blob, filename);
  } catch (err) {
    console.error('Erro ao baixar arquivo:', err);
  }
}

async function downloadProjectZip() {
  if (!state.conversationId) return;
  try {
    const res = await fetch(`${API.BASE}/api/code/session/${state.conversationId}/download/zip`);
    if (!res.ok) return;
    const blob = await res.blob();
    triggerDownload(blob, `kyronai-${state.conversationId.slice(0, 8)}.zip`);
  } catch (err) {
    console.error('Erro ao baixar ZIP:', err);
  }
}
