/* ════════════════════════════════════════════════════════════════
   UTILIDADES - Funções auxiliares globais
════════════════════════════════════════════════════════════════ */

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatDate(d) {
  const diff = Date.now() - d;
  const days = Math.floor(diff / 86400000);
  if (days === 0) return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  if (days === 1) return 'Ontem';
  if (days < 7) return d.toLocaleDateString('pt-BR', { weekday: 'short' });
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
}

function formatBytes(b) {
  if (b < 1024) return b + ' B';
  if (b < 1024 ** 2) return (b / 1024).toFixed(1) + ' KB';
  if (b < 1024 ** 3) return (b / 1024 ** 2).toFixed(1) + ' MB';
  return (b / 1024 ** 3).toFixed(2) + ' GB';
}

function getFileIcon(ext) {
  const icons = {
    java: '☕', py: '🐍', js: '🟨', ts: '🔷', tsx: '⚛', jsx: '⚛',
    html: '🌐', css: '🎨', scss: '🎨', json: '📋', xml: '📋',
    yml: '⚙', yaml: '⚙', md: '📝', sql: '🗃', sh: '⬛',
    go: '🐹', rs: '🦀', kt: '🎯', swift: '🧡', cs: '💜',
    php: '🐘', rb: '💎', dart: '🎯', cpp: '⚙', c: '⚙',
  };
  return icons[ext] || '📄';
}

window.copyCode = btn => {
  navigator.clipboard.writeText(btn.closest('pre').querySelector('code').textContent).then(() => {
    btn.textContent = '✓ Copiado';
    btn.classList.add('copied');
    setTimeout(() => {
      btn.textContent = 'Copiar';
      btn.classList.remove('copied');
    }, 2000);
  });
};

function renderMarkdown(text) {
  try {
    return marked.parse(text || '');
  } catch {
    return escapeHtml(text || '');
  }
}

function applyKaTeX(el) {
  if (typeof renderMathInElement === 'undefined') return;
  try {
    renderMathInElement(el, {
      delimiters: [
        { left: '$$', right: '$$', display: true },
        { left: '$', right: '$', display: false },
        { left: '\\[', right: '\\]', display: true },
        { left: '\\(', right: '\\)', display: false },
      ],
      throwOnError: false,
    });
  } catch (e) { /* silencioso */ }
}

function parseGithubRepoInput(value) {
  const raw = (value || '').trim();
  if (!raw) return '';

  const normalized = raw
    .replace(/^git@github\.com:/i, '')
    .replace(/^https?:\/\/(www\.)?github\.com\//i, '')
    .replace(/^github\.com\//i, '')
    .replace(/\.git$/i, '')
    .replace(/^\/+|\/+$/g, '');

  const match = normalized.match(/^([^/\s]+)\/([^/\s]+)$/);
  if (match) return `${match[1]}/${match[2]}`;

  return raw;
}

function scrollToBottom() {
  el.messagesArea.scrollTop = el.messagesArea.scrollHeight;
}

function updateCharCount() {
  const l = el.messageInput.value.length;
  el.charCount.textContent = `${l} / 32000`;
  el.charCount.style.color = l > 30000 ? 'var(--danger)' : '';
}

function setStatus(online, text) {
  el.statusDot.className = 'status-dot ' + (online ? 'online' : 'offline');
  el.statusText.textContent = text;
}

function fileToBase64(file) {
  return new Promise((res, rej) => {
    const r = new FileReader();
    r.onload = () => res(r.result);
    r.onerror = rej;
    r.readAsDataURL(file);
  });
}

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function parseStoredMessage(raw) {
  if (!raw) return { thinking: '', content: '' };
  const openTag = '<thinking>';
  const closeTag = '</thinking>\n\n';
  const start = raw.indexOf(openTag);
  const end = raw.indexOf(closeTag);
  if (start === 0 && end !== -1) {
    return {
      thinking: raw.slice(openTag.length, end),
      content: raw.slice(end + closeTag.length),
    };
  }
  return { thinking: '', content: raw };
}

function parseDocAttachments(text) {
  if (!text) return { cards: [], cleanText: text || '' };
  const cards = [];
  const pattern = /\[Conteúdo de "([^"]+)"\]:\n[\s\S]*?(?=\[Conteúdo de "|$)/g;
  const cleanText = text
    .replace(pattern, (_, name) => {
      cards.push({ name });
      return '';
    })
    .trim();
  return { cards, cleanText };
}
