/* ════════════════════════════════════════════════════════════════
   STREAMING - Controle de streaming e overlay de codificação
════════════════════════════════════════════════════════════════ */

function stopStreaming() {
  if (state.abortController) state.abortController.abort();
  setStreamingUI(false);
}

function setStreamingUI(streaming) {
  state.isStreaming = streaming;
  el.iconSend.style.display = streaming ? 'none' : '';
  el.iconStop.style.display = streaming ? '' : 'none';
  el.btnSend.classList.toggle('stopping', streaming);
}

/* Coding Overlay */
let _codingOverlayEl = null;
let _codingFileListEl = null;
let _codingDetectedFiles = [];
let _codingBuffer = '';
let _codingPhraseInterval = null;

const CODING_PHRASES = [
  'Codando...',
  'Analisando estrutura...',
  'Escrevendo lógica...',
  'Organizando arquivos...',
  'Aplicando boas práticas...',
  'Finalizando implementação...',
];

function showCodingOverlay(assistantMsgEl) {
  hideCodingOverlay();

  _codingDetectedFiles = [];
  _codingBuffer = '';

  const overlay = document.createElement('div');
  overlay.className = 'coding-overlay';
  overlay.innerHTML = `
    <div class="coding-overlay-inner">
      <div class="coding-spinner-wrap">
        <div class="coding-spinner">
          <div class="coding-spinner-ring"></div>
          <div class="coding-spinner-ring coding-spinner-ring--2"></div>
          <div class="coding-spinner-ring coding-spinner-ring--3"></div>
          <svg class="coding-spinner-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
          </svg>
        </div>
      </div>
      <div class="coding-status-wrap">
        <p class="coding-status-text" id="coding-status-text">Codando...</p>
        <div class="coding-files-list" id="coding-files-list"></div>
      </div>
    </div>
  `;

  const textEl = assistantMsgEl.querySelector('.message-text');
  if (textEl) textEl.appendChild(overlay);

  _codingOverlayEl = overlay;
  _codingFileListEl = overlay.querySelector('#coding-files-list');

  let phraseIdx = 0;
  const statusEl = overlay.querySelector('#coding-status-text');
  _codingPhraseInterval = setInterval(() => {
    phraseIdx = (phraseIdx + 1) % CODING_PHRASES.length;
    if (statusEl) {
      statusEl.style.opacity = '0';
      setTimeout(() => {
        statusEl.textContent = CODING_PHRASES[phraseIdx];
        statusEl.style.opacity = '1';
      }, 200);
    }
  }, 2200);
}

function updateCodingOverlayFile(filePath) {
  if (!_codingFileListEl) return;
  if (_codingDetectedFiles.includes(filePath)) return;
  _codingDetectedFiles.push(filePath);

  const item = document.createElement('div');
  item.className = 'coding-file-item coding-file-item--new';
  const ext = filePath.split('.').pop().toLowerCase();
  item.innerHTML = `
    <span class="coding-file-icon">${getFileIcon(ext)}</span>
    <span class="coding-file-name">${escapeHtml(filePath)}</span>
    <span class="coding-file-status">Criando...</span>
  `;
  _codingFileListEl.appendChild(item);

  requestAnimationFrame(() => {
    requestAnimationFrame(() => item.classList.remove('coding-file-item--new'));
  });
}

function markCodingFilesDone() {
  if (!_codingFileListEl) return;
  _codingFileListEl.querySelectorAll('.coding-file-status').forEach(s => {
    s.textContent = 'Concluído';
    s.classList.add('done');
  });
}

function hideCodingOverlay() {
  if (_codingPhraseInterval) {
    clearInterval(_codingPhraseInterval);
    _codingPhraseInterval = null;
  }
  if (_codingOverlayEl) {
    _codingOverlayEl.remove();
    _codingOverlayEl = null;
  }
  _codingFileListEl = null;
  _codingDetectedFiles = [];
  _codingBuffer = '';
}

function detectFilesInBuffer(chunk) {
  _codingBuffer += chunk;
  const filePattern = /```[a-zA-Z0-9+#_-]*:([^\n`]+)/g;
  let match;
  while ((match = filePattern.exec(_codingBuffer)) !== null) {
    const filePath = match[1].trim();
    if (filePath) updateCodingOverlayFile(filePath);
  }
}

function notifyCodeGenerated() {
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission();
  }

  if ('Notification' in window && Notification.permission === 'granted' && document.hidden) {
    const notification = new Notification('Kyron AI - Código Pronto! 🎉', {
      body: 'Seu código foi gerado com sucesso. Clique para voltar ao chat.',
      icon: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"%3E%3Cpolygon points="50,5 93,27.5 93,72.5 50,95 7,72.5 7,27.5" fill="%2310a37f"/%3E%3Ctext x="50" y="67" font-size="48" text-anchor="middle" fill="%23FFFFFF" font-family="system-ui" font-weight="bold"%3E💬%3C/text%3E%3C/svg%3E',
      tag: 'kyron-code-ready',
      requireInteraction: false,
    });

    notification.addEventListener('click', () => {
      window.focus();
      notification.close();
    });

    setTimeout(() => notification.close(), 5000);
  }
}
