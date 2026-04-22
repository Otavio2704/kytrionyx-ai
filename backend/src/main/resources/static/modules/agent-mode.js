/* ════════════════════════════════════════════════════════════════
   AGENT MODE - Ações de agente com escrita local
════════════════════════════════════════════════════════════════ */

function renderAgentActions(actions) {
  state.pendingActions = actions.filter(a => a.status === 'PENDING');
  if (state.pendingActions.length === 0) {
    el.agentActionsPanel.hidden = true;
    return;
  }

  const fsAvailable = FsAgent.isSupported() && FsAgent.hasRoot();

  el.agentActionsPanel.hidden = false;
  el.agentActionsList.innerHTML = state.pendingActions
    .map(action => {
      const needsFs = ['CREATE_FILE', 'EDIT_FILE', 'DELETE_FILE'].includes(action.actionType);
      const fsWarn = needsFs && !fsAvailable
        ? `<div class="agent-action-fs-warn">
             <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
               <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
               <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
             </svg>
             Selecione uma pasta para salvar localmente
           </div>`
        : '';

      return `
        <div class="agent-action-card" data-action-id="${action.id}">
          <div class="agent-action-header">
            <span class="agent-action-type agent-action-type-${action.actionType.toLowerCase()}">
              ${getActionTypeIcon(action.actionType)} ${action.actionType.replace('_', ' ')}
            </span>
            ${action.filePath ? `<span class="agent-action-path">${escapeHtml(action.filePath)}</span>` : ''}
            ${fsAvailable && needsFs
        ? `<span class="agent-action-fs-badge">
                 <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                   <path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/>
                 </svg>
                 ${escapeHtml(FsAgent.getRootPath())}
               </span>`
        : ''}
          </div>
          <div class="agent-action-desc">${escapeHtml(action.description || '')}</div>
          ${fsWarn}
          ${action.proposedContent ? `
            <details class="agent-action-preview">
              <summary>Ver conteúdo proposto</summary>
              <pre class="agent-action-code"><code>${escapeHtml(action.proposedContent.slice(0, 500))}${action.proposedContent.length > 500 ? '\n...' : ''}</code></pre>
            </details>
          ` : ''}
          <div class="agent-action-btns">
            <button class="btn-agent-reject" onclick="rejectAgentAction('${action.id}')">
              ✕ Rejeitar
            </button>
            <button class="btn-agent-approve" onclick="approveAgentAction('${action.id}')">
              ✓ Aprovar${fsAvailable && needsFs ? ' e salvar' : ''}
            </button>
          </div>
        </div>
      `;
    })
    .join('');
}

function getActionTypeIcon(type) {
  const icons = {
    CREATE_FILE: '📄',
    EDIT_FILE: '✏️',
    DELETE_FILE: '🗑️',
    RUN_COMMAND: '⚡',
    EXPLAIN: '💡',
  };
  return icons[type] || '⚙️';
}

globalThis.approveAgentAction = async function (actionId) {
  const card = document.querySelector(`.agent-action-card[data-action-id="${actionId}"]`);
  if (card) card.classList.add('processing');

  try {
    const fileActionTypes = ['CREATE_FILE', 'EDIT_FILE', 'DELETE_FILE'];
    const pendingAction = state.pendingActions.find(action => action.id === actionId);
    const needsLocalWrite = Boolean(pendingAction && fileActionTypes.includes(pendingAction.actionType));

    if (needsLocalWrite && !FsAgent.isSupported()) {
      if (card) card.classList.remove('processing');
      showFsToast('Seu navegador não suporta acesso local de arquivos. Use um navegador compatível com File System Access API.', 'error');
      return;
    }

    if (needsLocalWrite && !FsAgent.hasRoot()) {
      const selected = await FsAgent.selectRoot();
      if (!selected?.ok) {
        if (card) card.classList.remove('processing');
        showFsToast('Selecione uma pasta para permitir que o agente crie arquivos localmente.', 'error');
        return;
      }
    }

    if (needsLocalWrite) {
      const targetPath = pendingAction?.filePath;
      if (!targetPath) {
        if (card) card.classList.remove('processing');
        showFsToast('Ação sem caminho de arquivo. Não foi possível executar localmente.', 'error');
        return;
      }

      const permitted = await FsAgent.verifyPermission();
      if (!permitted) {
        if (card) card.classList.remove('processing');
        setCardFsStatus(card, 'error', 'permissão negada');
        showFsToast('Permissão de escrita negada. Selecione a pasta novamente.', 'error');
        return;
      }

      const fsResult = pendingAction.actionType === 'DELETE_FILE'
        ? await FsAgent.deleteFile(targetPath)
        : await FsAgent.writeFile(targetPath, pendingAction.proposedContent || '');

      if (!fsResult?.ok) {
        if (card) card.classList.remove('processing');
        setCardFsStatus(card, 'error', fsResult?.reason || 'erro desconhecido');
        showFsToast(`Não foi possível salvar localmente: ${fsResult?.reason || 'erro desconhecido'}`, 'error');
        return;
      }

      setCardFsStatus(card, 'saved', targetPath);
      showFsToast(`${targetPath} salvo em ${FsAgent.getRootPath()}`, 'success');
    }

    const res = await fetch(`${API.BASE}/api/agent/actions/${actionId}/approve`, { method: 'POST' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    await res.json();

    if (card) {
      card.classList.remove('processing');
      card.classList.add('approved');
      const btnsEl = card.querySelector('.agent-action-btns');
      if (btnsEl) {
        btnsEl.innerHTML = needsLocalWrite
          ? `<span class="agent-status-badge approved">✓ Executado e salvo localmente</span>`
          : `<span class="agent-status-badge approved">✓ Executado</span>`;
      }
      setTimeout(() => card.remove(), 2000);
    }

    state.pendingActions = state.pendingActions.filter(a => a.id !== actionId);
    if (state.pendingActions.length === 0) {
      setTimeout(() => {
        el.agentActionsPanel.hidden = true;
      }, 2100);
    }
  } catch (err) {
    console.error('Erro ao aprovar ação:', err);
    if (card) card.classList.remove('processing');
    showFsToast(`Erro: ${err.message}`, 'error');
  }
};

globalThis.rejectAgentAction = async function (actionId) {
  const card = document.querySelector(`.agent-action-card[data-action-id="${actionId}"]`);
  try {
    await fetch(`${API.BASE}/api/agent/actions/${actionId}/reject`, { method: 'POST' });
    if (card) {
      card.classList.add('rejected');
      card.querySelector('.agent-action-btns').innerHTML =
        '<span class="agent-status-badge rejected">✕ Rejeitado</span>';
      setTimeout(() => card.remove(), 1200);
    }
    state.pendingActions = state.pendingActions.filter(a => a.id !== actionId);
    if (state.pendingActions.length === 0) {
      setTimeout(() => {
        el.agentActionsPanel.hidden = true;
      }, 1300);
    }
  } catch (err) {
    console.error('Erro ao rejeitar ação:', err);
  }
};

function setCardFsStatus(card, status, detail) {
  if (!card) return;
  let el = card.querySelector('.agent-action-fs-result');
  if (!el) {
    el = document.createElement('div');
    el.className = 'agent-action-fs-result';
    card.querySelector('.agent-action-btns')?.before(el);
  }
  el.className = `agent-action-fs-result fs-result--${status}`;
  el.innerHTML = status === 'saved'
    ? `<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg> Salvo em <strong>${escapeHtml(FsAgent.getRootPath())}/${escapeHtml(detail)}</strong>`
    : `<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg> Falha ao salvar localmente: ${escapeHtml(detail)}`;
}
