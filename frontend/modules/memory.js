/* ════════════════════════════════════════════════════════════════
   MEMÓRIA - Sistema de memória
════════════════════════════════════════════════════════════════ */

async function openMemoryModal() {
  el.memoryBackdrop.hidden = false;
  await renderMemoryList();
}

async function renderMemoryList() {
  el.memoryList.innerHTML = '<div class="spinner"></div>';
  try {
    const res = await fetch(`${API.BASE}/api/memories`);
    if (!res.ok) throw new Error();
    const memories = await res.json();
    if (!memories.length) {
      el.memoryList.innerHTML = '<p class="memory-empty">Nenhuma memória ainda.</p>';
      return;
    }
    el.memoryList.innerHTML = memories.map(m => `
      <div class="memory-item ${m.active ? '' : 'inactive'}" data-id="${m.id}">
        <div class="memory-item-content">
          <span class="memory-category-tag">${escapeHtml(m.category || 'geral')}</span>
          <span class="memory-text">${escapeHtml(m.content)}</span>
        </div>
        <div class="memory-item-actions">
          <button class="btn-memory-toggle" onclick="toggleMemory('${m.id}')" title="${m.active ? 'Desativar' : 'Ativar'}">${m.active ? '●' : '○'}</button>
          <button class="btn-memory-delete" onclick="deleteMemory('${m.id}')" title="Deletar">✕</button>
        </div>
      </div>
    `).join('');
  } catch {
    el.memoryList.innerHTML = '<span style="color:var(--danger)">Erro ao carregar memórias.</span>';
  }
}

async function addMemory() {
  const content = el.memoryInput.value.trim();
  if (!content) return;
  try {
    await fetch(`${API.BASE}/api/memories`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content, category: el.memoryCategory.value }),
    });
    el.memoryInput.value = '';
    await renderMemoryList();
  } catch {
    console.error('Erro ao adicionar memória');
  }
}

window.toggleMemory = async (id) => {
  try {
    await fetch(`${API.BASE}/api/memories/${id}/toggle`, { method: 'PATCH' });
    await renderMemoryList();
  } catch { }
};

window.deleteMemory = async (id) => {
  try {
    await fetch(`${API.BASE}/api/memories/${id}`, { method: 'DELETE' });
    await renderMemoryList();
  } catch { }
};
