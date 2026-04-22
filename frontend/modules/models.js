/* ════════════════════════════════════════════════════════════════
   MODELOS - Carregamento e gerenciamento
════════════════════════════════════════════════════════════════ */

async function loadModels() {
  try {
    const res = await fetch(`${API.BASE}/api/models`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    const models = data.models || [];
    el.modelSelect.innerHTML = '';
    if (!models.length) {
      el.modelSelect.innerHTML = '<option value="">Nenhum modelo encontrado</option>';
      setStatus(false, 'Nenhum modelo instalado');
      return;
    }
    models.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.name;
      opt.textContent = m.name;
      el.modelSelect.appendChild(opt);
    });
    state.model = models[0].name;
    setStatus(true, `Ollama online · ${models.length} modelo${models.length > 1 ? 's' : ''}`);
    await loadCapabilities(state.model);
  } catch (err) {
    console.error('Erro ao carregar modelos:', err);
    el.modelSelect.innerHTML = '<option value="">Ollama indisponível</option>';
    setStatus(false, 'Ollama offline');
  }
}

async function showModelInfo() {
  if (!state.model) return;
  el.modalTitle.textContent = state.model;
  el.modalBody.innerHTML = '<div class="spinner"></div>';
  el.modalBackdrop.hidden = false;
  try {
    const res = await fetch(`${API.BASE}/api/models/${encodeURIComponent(state.model)}/info`);
    if (!res.ok) throw new Error();
    const data = await res.json();
    const caps = state.capabilities;
    const rows = [
      ['Modelo', data.modelfile?.split('\n')[0] || state.model],
      ['Parâmetros', data.details?.parameter_size || '—'],
      ['Quantização', data.details?.quantization_level || '—'],
      ['Família', data.details?.family || '—'],
      ['Contexto', caps?.contextLength > 0 ? caps.contextLength.toLocaleString() + ' tokens' : '—'],
      ['Thinking', caps?.supportsThinking ? '✅ Sim' : '❌ Não'],
      ['Vision', caps?.supportsVision ? '✅ Sim' : '❌ Não'],
      ['Tamanho', data.size ? formatBytes(data.size) : '—'],
    ];
    el.modalBody.innerHTML = `<table class="model-info-table">${rows.map(([k, v]) => `<tr><td>${escapeHtml(k)}</td><td>${escapeHtml(String(v))}</td></tr>`).join('')}</table>`;
  } catch {
    el.modalBody.innerHTML = '<span style="color:var(--text-2)">Informações não disponíveis.</span>';
  }
}

function closeModal() {
  el.modalBackdrop.hidden = true;
}
