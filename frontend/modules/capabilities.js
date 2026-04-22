/* ════════════════════════════════════════════════════════════════
   CAPABILITIES - Carregamento de capacidades do modelo
════════════════════════════════════════════════════════════════ */

async function loadCapabilities(modelName) {
  if (!modelName) return;
  updateModelTags(null, modelName);
  try {
    const res = await fetch(`${API.BASE}/api/models/${encodeURIComponent(modelName)}/capabilities`);
    if (!res.ok) throw new Error();
    const caps = await res.json();
    state.capabilities = caps;
    applyCapabilitiesToUI(caps);
    updateModelTags(caps, modelName);
  } catch {
    state.capabilities = null;
    applyCapabilitiesToUI(null);
    updateModelTags(null, modelName);
  }
}

function updateModelTags(caps, modelName) {
  if (!el.modelTags) return;
  const tags = [];
  const name = (modelName || '').toLowerCase();
  if (name.includes(':cloud') || name.includes('cloud'))
    tags.push('<span class="model-tag cloud">☁ Cloud</span>');
  if (caps?.supportsVision || name.includes('vision') || name.includes('llava'))
    tags.push('<span class="model-tag vision">👁 Vision</span>');
  if (caps?.supportsThinking || name.includes('qwen3') || name.includes('r1') || name.includes('qwq') || name.includes('deepseek'))
    tags.push('<span class="model-tag think">💭 Think</span>');
  if (name.includes('embed') || name.includes('embedding'))
    tags.push('<span class="model-tag embed">⊕ Embed</span>');
  if (name.includes('tools') || name.includes('tool'))
    tags.push('<span class="model-tag tools">🔧 Tools</span>');
  el.modelTags.innerHTML = tags.join('');
}

function applyCapabilitiesToUI(caps) {
  if (el.thinkingField) {
    const supported = caps?.supportsThinking ?? false;
    el.thinkingField.classList.toggle('disabled', !supported);
    el.thinkingChk.disabled = !supported;
    if (!supported) {
      el.thinkingChk.checked = false;
      state.thinkingMode = false;
      delete state.options.think;
    }
    el.thinkingField.title = supported ? '' : 'Este modelo não suporta Thinking Mode';
  }
  if (el.btnAttach) {
    el.btnAttach.disabled = false;
    const sv = caps?.supportsVision ?? false;
    el.fileInput.accept = sv ? '.pdf,.docx,.txt,.md,image/jpeg,image/png,image/webp,image/gif' : '.pdf,.docx,.txt,.md';
    el.btnAttach.title = sv ? 'Anexar arquivo ou imagem' : 'Anexar documento (PDF, DOCX, TXT, MD)';
  }
  if (caps?.contextLength > 0 && el.ctxSlider) {
    el.ctxSlider.max = caps.contextLength;
    const cur = Math.min(parseInt(el.ctxSlider.value), caps.contextLength);
    el.ctxSlider.value = cur;
    el.ctxValue.textContent = cur;
    state.options.num_ctx = cur;
  }
}
