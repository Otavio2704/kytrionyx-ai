/* ════════════════════════════════════════════════════════════════
   PROJETOS - Gerenciamento de projetos (TRUNCADO - ver frontend)
════════════════════════════════════════════════════════════════ */

async function loadProjects() {
  try {
    const res = await fetch(`${API.BASE}/api/projects`);
    if (!res.ok) return;
    renderProjects(await res.json());
  } catch (err) {
    console.error('Erro ao carregar projetos:', err);
  }
}

function renderProjects(projects) {
  el.projectsList.innerHTML = '';
  if (!projects.length) {
    el.projectsList.appendChild(el.projectsEmpty);
    return;
  }
  projects.forEach(proj => {
    const item = document.createElement('div');
    item.className = 'project-item' + (proj.id === state.activeProjectId ? ' active' : '');
    item.dataset.id = proj.id;
    item.innerHTML = `
      <span class="project-item-icon">📁</span>
      <div class="project-item-content">
        <div class="project-item-name" title="${escapeHtml(proj.name)}">${escapeHtml(proj.name)}</div>
        <div class="project-item-count">${proj.files ? proj.files.length + ' arquivo(s)' : 'Sem arquivos'}</div>
      </div>
    `;
    item.addEventListener('click', () => openProjectModal('detail', proj.id));
    el.projectsList.appendChild(item);
  });
}

async function deleteProject(id, itemEl) {
  openConfirm(async () => {
    try {
      await fetch(`${API.BASE}/api/projects/${id}`, { method: 'DELETE' });
      itemEl.remove();
      if (state.activeProjectId === id) clearProjectContext();
      await loadProjects();
    } catch (err) {
      console.error('Erro ao deletar projeto:', err);
    }
  });
}

async function openProjectModal(mode, projectId) {
  currentProjectId = projectId || null;
  el.projectFormSection.hidden = true;
  el.projectDetailSection.hidden = true;
  el.projectTextAdd.hidden = true;
  el.projectBackdrop.hidden = false;

  if (mode === 'new') {
    el.projectModalTitle.textContent = 'Novo Projeto';
    el.projectNameInput.value = '';
    el.projectDescInput.value = '';
    el.projectFormSection.hidden = false;
  } else if (mode === 'detail') {
    try {
      const res = await fetch(`${API.BASE}/api/projects/${projectId}`);
      const proj = await res.json();
      el.projectModalTitle.textContent = proj.name;
      el.projectDetailDesc.textContent = proj.description || 'Sem descrição.';
      el.projectDetailSection.hidden = false;
      renderProjectFiles(proj.files || []);
      await loadProjectChats(projectId);
    } catch { }
  }
}

function closeProjectModal() {
  el.projectBackdrop.hidden = true;
  currentProjectId = null;
  el.projectTextAdd.hidden = true;
}

async function saveProject() {
  const name = el.projectNameInput.value.trim();
  if (!name) {
    el.projectNameInput.focus();
    return;
  }
  const desc = el.projectDescInput.value.trim();
  try {
    if (currentProjectId) {
      await fetch(`${API.BASE}/api/projects/${currentProjectId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description: desc }),
      });
    } else {
      const res = await fetch(`${API.BASE}/api/projects`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description: desc }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    }
    closeProjectModal();
    await loadProjects();
  } catch (err) {
    console.error('Erro ao salvar projeto:', err);
  }
}

async function loadProjectChats(projectId) {
  if (!el.projectChatsList) return;
  el.projectChatsList.innerHTML = '<div class="spinner"></div>';
  try {
    const res = await fetch(`${API.BASE}/api/history/project/${projectId}`);
    if (!res.ok) throw new Error();
    renderProjectChats(await res.json());
  } catch {
    el.projectChatsList.innerHTML = '<p class="project-chats-empty">Erro ao carregar chats.</p>';
  }
}

function renderProjectChats(chats) {
  el.projectChatsList.innerHTML = '';
  if (!chats.length) {
    el.projectChatsList.innerHTML = '<p class="project-chats-empty">Nenhum chat com este projeto.</p>';
    return;
  }
  chats.forEach(conv => {
    const item = document.createElement('div');
    item.className = 'project-chat-item';
    const dateStr = formatDate(new Date(conv.updatedAt || conv.createdAt));
    item.innerHTML = `
      <div class="project-chat-item-info">
        <div class="project-chat-item-title" title="${escapeHtml(conv.title)}">${escapeHtml(conv.title)}</div>
        <div class="project-chat-item-meta">
          <span>${dateStr}</span>
          <span class="project-chat-item-model">${escapeHtml(conv.modelName || '')}</span>
        </div>
      </div>
    `;
    item.addEventListener('click', () => {
      closeProjectModal();
      loadConversation(conv.id);
    });
    el.projectChatsList.appendChild(item);
  });
}

function renderProjectFiles(files) {
  el.projectFilesList.innerHTML = '';
  if (!files.length) {
    el.projectFilesList.innerHTML = '<p style="font-size:12px;color:var(--text-2);text-align:center;padding:12px 0">Nenhum arquivo ainda.</p>';
    return;
  }
  files.forEach(f => {
    const item = document.createElement('div');
    item.className = 'project-file-item';
    const icon = f.fileType === 'pdf' ? '📄' : '📝';
    item.innerHTML = `
      <span class="project-file-icon">${icon}</span>
      <span class="project-file-name" title="${escapeHtml(f.filename)}">${escapeHtml(f.filename)}</span>
      <button class="btn-project-file-delete" onclick="deleteProjectFile('${f.id}', this)" title="Remover">✕</button>
    `;
    el.projectFilesList.appendChild(item);
  });
}

async function handleProjectFileUpload(e) {
  const files = Array.from(e.target.files);
  if (!files.length || !currentProjectId) return;
  for (const file of files) {
    const formData = new FormData();
    formData.append('file', file);
    try {
      await fetch(`${API.BASE}/api/projects/${currentProjectId}/files`, { method: 'POST', body: formData });
    } catch (err) {
      console.error('Erro ao enviar arquivo:', err);
    }
  }
  e.target.value = '';
  const res = await fetch(`${API.BASE}/api/projects/${currentProjectId}`);
  const proj = await res.json();
  renderProjectFiles(proj.files || []);
  await loadProjects();
}

globalThis.deleteProjectFile = async function (fileId, btn) {
  try {
    await fetch(`${API.BASE}/api/projects/files/${fileId}`, { method: 'DELETE' });
    btn.closest('.project-file-item').remove();
    await loadProjects();
  } catch (err) {
    console.error('Erro ao deletar arquivo:', err);
  }
};

function startProjectChat() {
  if (!currentProjectId) return;
  const name = el.projectModalTitle.textContent;
  setProjectContext(currentProjectId, name);
  closeProjectModal();
  newConversation();
  setTimeout(() => el.messageInput.focus(), 100);
}

function setProjectContext(id, name) {
  state.activeProjectId = id;
  state.activeProjectName = name;
  el.projectContextBadge.hidden = false;
  el.projectContextName.textContent = `Projeto: ${name}`;
  document.querySelectorAll('.project-item').forEach(i => i.classList.toggle('active', i.dataset.id === id));
}

function clearProjectContext() {
  state.activeProjectId = null;
  state.activeProjectName = '';
  el.projectContextBadge.hidden = true;
  document.querySelectorAll('.project-item').forEach(i => i.classList.remove('active'));
}
