/* ════════════════════════════════════════════════════════════════
   CODE COMPLETION - Renderização de código gerado no chat
════════════════════════════════════════════════════════════════ */

function renderCodeCompletionMessage(textEl, rawResponse) {
  const withoutCode = rawResponse.replaceAll(/```[\s\S]*?```/g, '').trim();

  let files = buildFilesFromResponse(rawResponse);
  if (!files.length && state.codeSession?.files?.length) {
    files.push(...state.codeSession.files.map(f => ({ ...f })));
  }

  const previewId = `preview-${Date.now()}-${state.nextInlinePreviewId++}`;
  state.inlinePreviews[previewId] = files;

  const previewHtml = buildInlineCodePreview(rawResponse, withoutCode, files, previewId);
  textEl.innerHTML = previewHtml;

  textEl.querySelectorAll('pre code').forEach(b => hljs.highlightElement(b));
  applyKaTeX(textEl);
}

function buildInlineCodePreview(rawResponse, explanationText, files, previewId) {
  const htmlFile = files.find(f => f.extension === 'html' || f.fileName?.endsWith('.html'));
  const cssFiles = files.filter(f => f.extension === 'css');
  const jsFiles = files.filter(f => f.extension === 'js');

  let previewContent = '';

  if (htmlFile) {
    let html = htmlFile.content || '';
    cssFiles.forEach(f => {
      const tag = `<style>/* ${f.fileName} */\n${f.content}</style>`;
      html = html.includes('</head>') ? html.replace('</head>', tag + '</head>') : tag + html;
    });
    jsFiles.forEach(f => {
      const tag = `<script>/* ${f.fileName} */\n${f.content}</script>`;
      html = html.includes('</body>') ? html.replace('</body>', tag + '</body>') : html + tag;
    });

    const encoded = html
      .replaceAll(/&/g, '&amp;')
      .replaceAll(/"/g, '&quot;');

    previewContent = `
      <div class="inline-preview-pane inline-preview-pane--preview">
        <iframe
          class="inline-preview-iframe"
          sandbox="allow-scripts allow-same-origin"
          srcdoc="${encoded}"
        ></iframe>
      </div>
      <div class="inline-code-pane inline-code-pane--code" style="display:none">
        <div class="inline-code-files">
          ${files.map(f => `
            <div class="inline-code-file-tab ${f.id === files[0]?.id ? 'active' : ''}"
                 onclick="switchInlineTab(this, '${f.id}', '${previewId}')">
              ${getFileIcon(f.extension || '')} ${escapeHtml(f.fileName)}
            </div>
          `).join('')}
        </div>
        <div class="inline-code-editor inline-code-editor-body">
          ${buildInlineEditor(files[0])}
        </div>
      </div>
    `;
  } else if (files.length > 0) {
    previewContent = `
      <div class="inline-preview-pane inline-preview-pane--nohtml inline-preview-pane--preview">
        <div class="inline-no-preview-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
          </svg>
        </div>
        <p>${files.length} arquivo${files.length !== 1 ? 's' : ''}</p>
      </div>
      <div class="inline-code-pane inline-code-pane--code" style="display:none">
        <div class="inline-code-files">
          ${files.map(f => `
            <div class="inline-code-file-tab ${f.id === files[0]?.id ? 'active' : ''}"
                 onclick="switchInlineTab(this, '${f.id}', '${previewId}')">
              ${getFileIcon(f.extension || '')} ${escapeHtml(f.fileName)}
            </div>
          `).join('')}
        </div>
        <div class="inline-code-editor inline-code-editor-body">
          ${buildInlineEditor(files[0])}
        </div>
      </div>
    `;
  }

  const explanationHtml = explanationText
    ? `<div class="inline-explanation">${renderMarkdown(explanationText)}</div>`
    : '';

  const tabBar = files.length > 0 ? `
    <div class="inline-code-tabs">
      <button class="inline-tab active" onclick="toggleInlineView('preview', this, '${previewId}')">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/>
        </svg>
        Prévia
      </button>
      <button class="inline-tab" onclick="toggleInlineView('code', this, '${previewId}')">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
        </svg>
        Código
      </button>
      <button class="inline-tab inline-tab--open" onclick="openCodePanelFromBanner()" title="Abrir painel completo">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
        </svg>
        Expandir
      </button>
    </div>
    <div class="inline-code-shell" data-preview-id="${previewId}">
      ${previewContent}
    </div>
  ` : '';

  return explanationHtml + tabBar;
}

function buildInlineEditor(file) {
  if (!file) return '';
  const language = file.extension && hljs.getLanguage(file.extension) ? file.extension : null;
  const result = language
    ? hljs.highlight(file.content || '', { language })
    : hljs.highlightAuto(file.content || '');

  const lines = result.value.split('\n');
  const lineNums = lines.map((_, i) => `<span class="line-number">${i + 1}</span>`).join('\n');
  const codeLines = lines.map(l => `<span class="code-line">${l}</span>`).join('\n');

  return `
    <div class="code-with-lines">
      <div class="line-numbers-col" aria-hidden="true">${lineNums}</div>
      <code class="hljs code-lines-col">${codeLines}</code>
    </div>
  `;
}

globalThis.toggleInlineView = function (view, btnEl, previewId) {
  const shell = btnEl.closest(`.inline-code-shell[data-preview-id="${previewId}"]`);
  if (!shell) return;

  const previewPane = shell.querySelector('.inline-preview-pane--preview');
  const codePane = shell.querySelector('.inline-code-pane--code');
  const tabs = btnEl.closest('.inline-code-tabs')?.querySelectorAll('.inline-tab:not(.inline-tab--open)') || [];
  tabs.forEach(t => t.classList.remove('active'));
  btnEl.classList.add('active');

  if (view === 'preview') {
    if (previewPane) previewPane.style.display = '';
    if (codePane) codePane.style.display = 'none';
  } else {
    if (previewPane) previewPane.style.display = 'none';
    if (codePane) codePane.style.display = '';
  }
};

globalThis.switchInlineTab = function (tabEl, fileId, previewId) {
  const container = tabEl.closest('.inline-code-pane');
  if (!container) return;
  container.querySelectorAll('.inline-code-file-tab').forEach(t => t.classList.remove('active'));
  tabEl.classList.add('active');

  const files = state.inlinePreviews[previewId] || [];
  const file = files.find(f => f.id === fileId);
  const editor = container.querySelector('.inline-code-editor-body');
  if (file && editor) editor.innerHTML = buildInlineEditor(file);
};

function isCodeGenerationResponse(content) {
  return /```[a-zA-Z0-9+#_-]+:[^\n`]+\n[\s\S]*?```/m.test(content || '');
}

function buildFilesFromResponse(rawResponse) {
  const files = [];
  const regex = /```([a-zA-Z0-9+#_-]+):([^\n`]+)\n([\s\S]*?)```/g;
  let match;

  while ((match = regex.exec(rawResponse || '')) !== null) {
    const language = (match[1] || '').trim().toLowerCase();
    const filePath = (match[2] || '').trim();
    const content = (match[3] || '').replace(/\n$/, '');
    if (!filePath) continue;

    const parts = filePath.split('/');
    const fileName = parts.at(-1) || filePath;
    const dot = fileName.lastIndexOf('.');
    const extension = dot > -1 ? fileName.slice(dot + 1).toLowerCase() : language;

    files.push({
      id: `inline-${files.length + 1}-${Date.now()}`,
      filePath,
      fileName,
      extension,
      content,
    });
  }

  return files;
}

async function handleCodeFilesEvent(data) {
  const files = data.split(';').map(s => {
    const [path, id, status, version] = s.split('|');
    return { path, id, status, version: Number.parseInt(version) };
  });

  files.forEach(f => {
    if (f.path) updateCodingOverlayFile(f.path);
  });

  await loadCodeSession();
  if (state.codeSession?.files?.length > 0) {
    renderCodePanel();
    activeTab = 'preview';
    switchCodeTab('preview');
  }
}

function renderStreaming(textEl, thinking, content) {
  let html = '';
  if (thinking) html += `<details class="thinking-block"><summary>Pensamento</summary><p>${escapeHtml(thinking)}</p></details>`;
  const count = (content.match(/```/g) || []).length;
  const safe = count % 2 === 0 ? content : content + '\n```';
  html += renderMarkdown(safe) + '<span class="streaming-cursor"></span>';
  textEl.innerHTML = html;
  applyKaTeX(textEl);
}

function renderFinal(textEl, thinking, content) {
  let html = '';
  if (thinking) html += `<details class="thinking-block"><summary>Pensamento</summary><p>${escapeHtml(thinking)}</p></details>`;
  html += renderMarkdown(content);
  textEl.innerHTML = html;
  textEl.querySelectorAll('pre code').forEach(b => hljs.highlightElement(b));
  applyKaTeX(textEl);
}

function appendMessage(role, rawContent, streaming, files = [], thinkingWasActive = null) {
  const msg = document.createElement('div');
  msg.className = `message ${role}`;
  const avatarLabel = role === 'user' ? 'Eu' : '⬡';
  const roleLabel = role === 'user' ? 'Você' : 'Assistente';

  const { thinking, content: parsedContent } = parseStoredMessage(rawContent || '');

  let displayContent = parsedContent;
  let restoredCards = [];
  if (role === 'user' && !streaming && files.length === 0) {
    const { cards, cleanText } = parseDocAttachments(parsedContent);
    restoredCards = cards;
    displayContent = cleanText;
  }

  const shouldShowThinking = thinking && thinkingWasActive === true;
  const thinkHtml = shouldShowThinking
    ? `<details class="thinking-block"><summary>Pensamento</summary><p>${escapeHtml(thinking)}</p></details>`
    : '';

  const rendered = displayContent ? renderMarkdown(displayContent) : '';

  const docFiles = files.filter(f => f.type === 'doc');
  const imgFiles = files.filter(f => f.type === 'image');

  const restoredHtml = restoredCards.map(f =>
    `<div class="message-attach-card">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
        <polyline points="14 2 14 8 20 8"/>
      </svg>
      <span class="message-attach-name">${escapeHtml(f.name)}</span>
    </div>`
  ).join('');

  const attachHtml = [
    restoredHtml,
    ...docFiles.map(f =>
      `<div class="message-attach-card">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
        <span class="message-attach-name">${escapeHtml(f.name)}</span>
      </div>`),
    ...imgFiles.map(f =>
      `<img src="data:image/jpeg;base64,${f.data}" class="message-img-thumb" alt="${escapeHtml(f.name)}" />`),
  ].join('');

  const attachSection = attachHtml ? `<div class="message-attachments">${attachHtml}</div>` : '';

  msg.innerHTML = `
    <div class="message-inner">
      <div class="message-avatar">${avatarLabel}</div>
      <div class="message-content">
        <div class="message-role">${roleLabel}</div>
        ${attachSection}
        <div class="message-text">${thinkHtml}${rendered}${streaming ? '<span class="streaming-cursor"></span>' : ''}</div>
      </div>
    </div>
  `;
  if (!streaming && displayContent) msg.querySelectorAll('pre code').forEach(b => hljs.highlightElement(b));
  if (!streaming) applyKaTeX(msg.querySelector('.message-text'));
  el.messagesArea.appendChild(msg);
  scrollToBottom();
  return msg;
}
