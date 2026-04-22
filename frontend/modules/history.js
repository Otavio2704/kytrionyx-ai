/* ════════════════════════════════════════════════════════════════
   HISTÓRICO - Gerenciamento de conversas
════════════════════════════════════════════════════════════════ */

async function loadHistory() {
  try {
    const res = await fetch(`${API.BASE}/api/history`);
    if (!res.ok) return;
    allHistory = await res.json();
    renderHistory(allHistory);
  } catch (err) {
    console.error('Erro histórico:', err);
  }
}

function buildHistoryItem(conv) {
  const item = document.createElement('div');
  item.className = 'history-item' + (conv.id === state.conversationId ? ' active' : '') + (conv.pinned ? ' pinned' : '');
  item.dataset.id = conv.id;
  const dateStr = formatDate(new Date(conv.updatedAt || conv.createdAt));
  const pinIcon = conv.pinned
    ? `<svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><path d="M16 2H8a1 1 0 0 0-.707 1.707L9 5.414V10l-2 2v2h6v5l1 1 1-1v-5h6v-2l-2-2V5.414l1.707-1.707A1 1 0 0 0 16 2z"/></svg>`
    : `<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 2H8a1 1 0 0 0-.707 1.707L9 5.414V10l-2 2v2h6v5l1 1 1-1v-5h6v-2l-2-2V5.414l1.707-1.707A1 1 0 0 0 16 2z"/></svg>`;

  item.innerHTML = `
    <div class="history-item-content">
      <div class="history-item-title" title="${escapeHtml(conv.title)}">
        ${conv.pinned ? '<span class="pin-indicator">📌</span>' : ''}${escapeHtml(conv.title)}
      </div>
      <div class="history-item-meta">
        <span>${dateStr}</span>
        <span class="history-item-model">${escapeHtml(conv.modelName || '')}</span>
      </div>
    </div>
    <div class="history-item-actions">
      <button class="btn-history-action pin ${conv.pinned ? 'pinned' : ''}" title="${conv.pinned ? 'Desafixar' : 'Fixar'}">${pinIcon}</button>
      <button class="btn-history-action rename" title="Renomear">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
          <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
        </svg>
      </button>
      <button class="btn-history-action delete" title="Deletar">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/>
          <path d="M10 11v6M14 11v6"/>
        </svg>
      </button>
    </div>
  `;
  item.querySelector('.pin').addEventListener('click', e => {
    e.stopPropagation();
    togglePin(conv.id);
  });
  item.querySelector('.rename').addEventListener('click', e => {
    e.stopPropagation();
    openRename(conv.id, conv.title, item);
  });
  item.querySelector('.delete').addEventListener('click', e => {
    e.stopPropagation();
    deleteConversation(conv.id, item);
  });
  item.addEventListener('click', () => loadConversation(conv.id));
  return item;
}

function renderHistory(items) {
  el.historyList.innerHTML = '';
  if (!items.length) {
    el.historyList.appendChild(el.historyEmpty);
    return;
  }
  const pinned = items.filter(c => c.pinned);
  const recents = items.filter(c => !c.pinned);
  if (pinned.length) {
    const sep = document.createElement('div');
    sep.className = 'history-section-sep';
    sep.textContent = 'Fixados';
    el.historyList.appendChild(sep);
    pinned.forEach(c => el.historyList.appendChild(buildHistoryItem(c)));
    if (recents.length) {
      const sep2 = document.createElement('div');
      sep2.className = 'history-section-sep';
      sep2.textContent = 'Recentes';
      el.historyList.appendChild(sep2);
    }
  }
  recents.forEach(c => el.historyList.appendChild(buildHistoryItem(c)));
}

function filterHistory() {
  const term = el.searchInput.value.toLowerCase().trim();
  renderHistory(term ? allHistory.filter(c => c.title.toLowerCase().includes(term)) : allHistory);
}

async function loadConversation(id) {
  try {
    const res = await fetch(`${API.BASE}/api/history/${id}`);
    if (!res.ok) throw new Error();
    const conv = await res.json();
    state.conversationId = id;
    if (conv.modelName) {
      el.modelSelect.value = conv.modelName;
      state.model = conv.modelName;
      await loadCapabilities(state.model);
    }
    state.inlinePreviews = {};
    state.nextInlinePreviewId = 0;
    el.messagesArea.innerHTML = '';
    showChat();

    const webSearchCount = (conv.messages || [])
      .filter(m => m.role === 'tool' && m.content?.startsWith('[WEB_SEARCH_CONTEXT]'))
      .length;

    if (webSearchCount > 0) {
      const badge = document.createElement('div');
      badge.className = 'web-search-history-badge';
      badge.innerHTML = `
        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        ${webSearchCount} busca${webSearchCount > 1 ? 's' : ''} na web realizada${webSearchCount > 1 ? 's' : ''} nesta conversa
      `;
      el.messagesArea.appendChild(badge);
    }

    (conv.messages || [])
      .filter(m => m.role === 'user' || m.role === 'assistant')
      .forEach(msg => {
        const shouldShowThinking = msg.role === 'assistant' && msg.thinkingEnabled === true;
        const msgEl = appendMessage(msg.role, msg.content, false, [], shouldShowThinking);
        if (msg.role === 'assistant') {
          const parsed = parseStoredMessage(msg.content || '');
          if (isCodeGenerationResponse(parsed.content || '')) {
            const textEl = msgEl.querySelector('.message-text');
            if (textEl) renderCodeCompletionMessage(textEl, parsed.content || '');
          }
        }
      });

    scrollToBottom();
    document.querySelectorAll('.history-item').forEach(i => i.classList.remove('active'));
    document.querySelector(`.history-item[data-id="${id}"]`)?.classList.add('active');

    if (state.codeModeEnabled) {
      await loadCodeSession();
      if (state.codeSession?.files?.length > 0 && el.codePanel.classList.contains('open')) {
        renderPreviewPane();
      }
    }

    if (state.agentModeEnabled) {
      const actRes = await fetch(`${API.BASE}/api/agent/actions/${id}`);
      if (actRes.ok) {
        const actions = await actRes.json();
        renderAgentActions(actions);
      }
    }
  } catch (err) {
    console.error('Erro ao carregar conversa:', err);
  }
}

let _renameTarget = null;

function openRename(id, currentTitle, itemEl) {
  _renameTarget = { id, itemEl };
  el.renameInput.value = currentTitle;
  el.renameBackdrop.hidden = false;
  setTimeout(() => {
    el.renameInput.focus();
    el.renameInput.select();
  }, 50);
  el.renameConfirm.onclick = async () => {
    const newTitle = el.renameInput.value.trim();
    if (!newTitle) return;
    try {
      const res = await fetch(`${API.BASE}/api/history/${id}/rename`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: newTitle }),
      });
      if (!res.ok) throw new Error();
      const titleEl = itemEl.querySelector('.history-item-title');
      if (titleEl) {
        titleEl.textContent = newTitle;
        titleEl.title = newTitle;
      }
      const hist = allHistory.find(c => c.id === id);
      if (hist) hist.title = newTitle;
      closeRename();
    } catch {
      alert('Erro ao renomear conversa.');
    }
  };
}

function closeRename() {
  el.renameBackdrop.hidden = true;
  _renameTarget = null;
}

function deleteConversation(id, itemEl) {
  openConfirm(() => performDelete(id, itemEl));
}

async function performDelete(id, itemEl) {
  try {
    const res = await fetch(`${API.BASE}/api/history/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`Erro ${res.status}`);
    allHistory = allHistory.filter(c => c.id !== id);
    itemEl.remove();
    document.querySelectorAll('.history-section-sep').forEach(sep => {
      const next = sep.nextElementSibling;
      if (!next || next.classList.contains('history-section-sep')) sep.remove();
    });
    if (!allHistory.length) el.historyList.appendChild(el.historyEmpty);
    if (state.conversationId === id) newConversation();
  } catch (err) {
    console.error('Erro ao deletar conversa:', err);
    alert(`Falha ao deletar conversa: ${err.message}`);
  }
}

let _confirmCallback = null;

function openConfirm(cb) {
  _confirmCallback = cb;
  el.confirmBackdrop.hidden = false;
  el.confirmDelete.onclick = () => {
    const cb = _confirmCallback;
    closeConfirm();
    if (cb) cb();
  };
}

function closeConfirm() {
  el.confirmBackdrop.hidden = true;
  _confirmCallback = null;
}

async function togglePin(id) {
  try {
    const res = await fetch(`${API.BASE}/api/history/${id}/pin`, { method: 'PATCH' });
    const data = await res.json();
    if (!data.success && data.reason === 'limit_reached') {
      alert('Máximo de 3 chats fixados atingido.');
      return;
    }
    await loadHistory();
  } catch (err) {
    console.error('Erro ao fixar conversa:', err);
  }
}

function newConversation() {
  state.conversationId = null;
  state.codeSession = null;
  state.pendingActions = [];
  state.inlinePreviews = {};
  state.nextInlinePreviewId = 0;
  el.messagesArea.innerHTML = '';
  el.messageInput.value = '';
  updateCharCount();
  clearAttachPreview();
  showWelcome();
  closeCodePanel();
  el.agentActionsPanel.hidden = true;
  document.querySelectorAll('.history-item').forEach(i => i.classList.remove('active'));
  if (window.innerWidth <= 768) el.sidebar.classList.remove('open');
}

function showWelcome() {
  el.welcomeScreen.classList.remove('hidden');
  el.messagesArea.classList.add('hidden');
}

function showChat() {
  el.welcomeScreen.classList.add('hidden');
  el.messagesArea.classList.remove('hidden');
}
