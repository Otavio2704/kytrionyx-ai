/* ════════════════════════════════════════════════════════════════
   CHAT - Envio de mensagens e streaming
════════════════════════════════════════════════════════════════ */

async function sendMessage() {
  const text = el.messageInput.value.trim();
  if (!text && !state.pendingFiles.length) return;
  if (!state.model) {
    alert('Selecione um modelo antes de enviar.');
    return;
  }

  const filesToSend = [...state.pendingFiles];
  el.messageInput.value = '';
  el.messageInput.style.height = 'auto';
  updateCharCount();
  clearAttachPreview();
  showChat();

  const thinkingActiveNow = state.thinkingMode;
  const codeModeActiveNow = state.codeModeEnabled;

  appendMessage('user', text, false, filesToSend);

  const assistantEl = appendMessage('assistant', '', true, [], thinkingActiveNow);
  const textEl = assistantEl.querySelector('.message-text');
  const cursorEl = assistantEl.querySelector('.streaming-cursor');

  if (codeModeActiveNow) {
    if (cursorEl) cursorEl.remove();
    showCodingOverlay(assistantEl);
  }

  setStreamingUI(true);
  state.abortController = new AbortController();

  const isNew = !state.conversationId;
  const endpoint = isNew ? `${API.BASE}/api/chat/new` : `${API.BASE}/api/chat`;

  const images = filesToSend.filter(f => f.type === 'image').map(f => f.data);
  const docTexts = filesToSend.filter(f => f.type === 'doc').map(f => `[Conteúdo de "${f.name}"]:\n${f.data}`).join('\n\n');
  const fullText = docTexts ? (text ? `${text}\n\n${docTexts}` : docTexts) : text;

  let finalSystemPrompt = state.systemPrompt || null;
  if (state.language) {
    const li = `Responda SEMPRE em ${state.language}.`;
    finalSystemPrompt = finalSystemPrompt ? `${li}\n\n${finalSystemPrompt}` : li;
  }

  const body = {
    message: fullText,
    model: state.model,
    options: state.options,
    systemPrompt: finalSystemPrompt,
    images,
    webSearch: state.webSearchEnabled,
    codeMode: state.codeModeEnabled,
    agentMode: state.agentModeEnabled,
    githubRepoId: state.activeGithubRepo?.id ?? null,
    ...(state.activeProjectId && { projectId: state.activeProjectId }),
    ...(!isNew && state.conversationId && { conversationId: state.conversationId }),
  };

  let fullResponse = '';
  let thinkingText = '';
  let searchBanner = null;

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: state.abortController.signal,
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let currentEvent = '';
    let pendingCodeFilesPromise = null;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();

      const flushEvent = async (evName, data) => {
        if (evName === 'conversation-id') {
          state.conversationId = data.trim();
          return;
        }

        if (evName === 'search-start') {
          searchBanner = document.createElement('div');
          searchBanner.className = 'search-status-banner';
          searchBanner.innerHTML = `<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> Buscando na web...`;
          el.messagesArea.insertBefore(searchBanner, assistantEl);
          el.btnWebSearch.classList.add('searching');
          return;
        }
        if (evName === 'search-done') {
          if (searchBanner) {
            searchBanner.classList.add('done');
            setTimeout(() => searchBanner?.remove(), 600);
            searchBanner = null;
          }
          el.btnWebSearch.classList.remove('searching');
          return;
        }

        if (evName === 'code-files' && data.trim()) {
          pendingCodeFilesPromise = handleCodeFilesEvent(data.trim())
            .catch(e => console.error('Erro ao carregar sessão de código:', e))
            .finally(() => {
              pendingCodeFilesPromise = null;
            });
          await pendingCodeFilesPromise;
          return;
        }

        if (evName === 'agent-actions' && data.trim()) {
          try {
            const actions = JSON.parse(data.trim());
            renderAgentActions(actions);
          } catch (e) {
            console.error('Erro ao parsear ações do agente:', e);
          }
          return;
        }

        if (evName === 'github-context') {
          return;
        }

        if (evName === 'done' || data.trim() === '[DONE]') {
          await Promise.resolve(pendingCodeFilesPromise);
          if (codeModeActiveNow) {
            markCodingFilesDone();
            setTimeout(() => {
              hideCodingOverlay();
              renderCodeCompletionMessage(textEl, fullResponse);
              notifyCodeGenerated();
            }, 800);
          } else {
            cursorEl?.remove();
            renderFinal(textEl, thinkingActiveNow ? thinkingText : '', fullResponse);
          }
          scrollToBottom();
          return;
        }
        if (evName === 'error') {
          hideCodingOverlay();
          textEl.innerHTML = `<span style="color:var(--danger)">Erro: ${escapeHtml(data.trim())}</span>`;
          cursorEl?.remove();
          return;
        }
        if (evName === 'thinking') {
          if (thinkingActiveNow && !codeModeActiveNow) {
            thinkingText += data;
            renderStreaming(textEl, thinkingText, fullResponse);
            scrollToBottom();
          }
          return;
        }
        if (evName === 'token' || evName === '') {
          fullResponse += data;
          if (codeModeActiveNow) {
            detectFilesInBuffer(data);
          } else {
            renderStreaming(textEl, thinkingActiveNow ? thinkingText : '', fullResponse);
            scrollToBottom();
          }
        }
      };

      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('event:')) {
          if (currentEvent !== '') {
            // flush previous
          }
          currentEvent = trimmed.slice(6).trim();
          continue;
        }
        if (line.startsWith('data:')) {
          // accumulate data
          continue;
        }
        if (trimmed === '') {
          // event end
        }
      }
    }

    await Promise.resolve(pendingCodeFilesPromise);

    if (codeModeActiveNow && _codingOverlayEl) {
      markCodingFilesDone();
      setTimeout(() => {
        hideCodingOverlay();
        renderCodeCompletionMessage(textEl, fullResponse);
      }, 800);
    } else if (!codeModeActiveNow && textEl.querySelector('.streaming-cursor')) {
      cursorEl?.remove();
      renderFinal(textEl, thinkingActiveNow ? thinkingText : '', fullResponse);
    }
  } catch (err) {
    searchBanner?.remove();
    el.btnWebSearch.classList.remove('searching');
    hideCodingOverlay();
    if (err.name === 'AbortError') {
      cursorEl?.remove();
      if (fullResponse && !codeModeActiveNow)
        renderFinal(textEl, thinkingActiveNow ? thinkingText : '', fullResponse);
      else if (codeModeActiveNow) renderCodeCompletionMessage(textEl, fullResponse);
      else textEl.innerHTML = '<em style="color:var(--text-2)">Geração interrompida.</em>';
    } else {
      console.error('Erro SSE:', err);
      textEl.innerHTML = `<span style="color:var(--danger)">Erro: ${escapeHtml(err.message)}</span>`;
      cursorEl?.remove();
    }
  } finally {
    setStreamingUI(false);
    state.abortController = null;
    el.messageInput.focus();
    await loadHistory();
  }
}
