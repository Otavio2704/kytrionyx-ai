/* ════════════════════════════════════════════════════════════════
   ANEXOS - Gerenciamento de arquivos e imagens
════════════════════════════════════════════════════════════════ */

async function handleFileSelect(e) {
  const files = Array.from(e.target.files);
  if (!files.length) return;
  const sv = state.capabilities?.supportsVision ?? false;
  for (const file of files) {
    const ext = file.name.split('.').pop().toLowerCase();
    const isImage = ['jpg', 'jpeg', 'png', 'webp', 'gif'].includes(ext);
    if (isImage && sv) {
      const b64full = await fileToBase64(file);
      const b64pure = b64full.includes(',') ? b64full.split(',')[1] : b64full;
      state.pendingFiles.push({ name: file.name, type: 'image', data: b64pure });
      addAttachPreview(file.name, 'image', b64full);
    } else if (['pdf', 'docx', 'txt', 'md'].includes(ext)) {
      addAttachPreview(file.name, 'doc', null);
      const text = await extractFileText(file, ext);
      if (text) state.pendingFiles.push({ name: file.name, type: 'doc', data: text });
    } else {
      alert(`Tipo de arquivo não suportado: .${ext}`);
    }
  }
  e.target.value = '';
}

async function extractFileText(file, ext) {
  if (ext === 'txt' || ext === 'md') return await file.text();
  const formData = new FormData();
  formData.append('file', file);
  try {
    const res = await fetch(`${API.BASE}/api/files/extract`, { method: 'POST', body: formData });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    return data.text || '';
  } catch (err) {
    console.error('Erro ao extrair texto:', err);
    alert('Não foi possível extrair o texto do arquivo.');
    return null;
  }
}

function addAttachPreview(name, type, src) {
  if (!el.attachPreview) return;
  el.attachPreview.hidden = false;
  const item = document.createElement('div');
  item.className = 'attach-item';
  item.dataset.name = name;
  item.innerHTML = `
    ${type === 'image' && src ? `<img src="${src}" alt="${escapeHtml(name)}" />` : '<span class="attach-icon">📄</span>'}
    <span class="attach-name">${escapeHtml(name)}</span>
    <button class="attach-remove" title="Remover">✕</button>
  `;
  item.querySelector('.attach-remove').addEventListener('click', () => {
    state.pendingFiles = state.pendingFiles.filter(f => f.name !== name);
    item.remove();
    if (!el.attachPreview.querySelector('.attach-item')) el.attachPreview.hidden = true;
  });
  el.attachPreview.appendChild(item);
}

function clearAttachPreview() {
  if (el.attachPreview) {
    el.attachPreview.innerHTML = '';
    el.attachPreview.hidden = true;
  }
  state.pendingFiles = [];
}
