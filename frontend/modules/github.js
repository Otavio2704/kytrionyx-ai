/* ════════════════════════════════════════════════════════════════
   GITHUB CONNECTOR - Gerenciamento de repositórios
════════════════════════════════════════════════════════════════ */

async function openGithubModal() {
  el.githubBackdrop.hidden = false;
  await loadGithubRepos();
}

function closeGithubModal() {
  el.githubBackdrop.hidden = true;
}

async function loadGithubRepos() {
  el.githubRepoList.innerHTML = '<div class="spinner"></div>';
  try {
    const res = await fetch(`${API.BASE}/api/github/repositories`);
    if (!res.ok) throw new Error();
    githubRepos = await res.json();
    renderGithubRepos();
  } catch {
    el.githubRepoList.innerHTML = '<p style="color:var(--text-2);font-size:12px;text-align:center;padding:12px">Erro ao carregar repositórios.</p>';
  }
}

function renderGithubRepos() {
  if (!githubRepos.length) {
    el.githubRepoList.innerHTML = '<p style="color:var(--text-2);font-size:12px;text-align:center;padding:12px">Nenhum repositório conectado.</p>';
    return;
  }

  el.githubRepoList.innerHTML = githubRepos
    .map(repo => {
      const statusBadge = {
        READY: '<span class="repo-status ready">● Pronto</span>',
        INDEXING: '<span class="repo-status indexing">◌ Indexando...</span>',
        PENDING: '<span class="repo-status pending">◌ Pendente</span>',
        ERROR: '<span class="repo-status error">✕ Erro</span>',
      }[repo.indexStatus] || '';

      const isActive = state.activeGithubRepo?.id === repo.id;

      return `
        <div class="github-repo-item ${isActive ? 'active' : ''}" data-repo-id="${repo.id}">
          <div class="github-repo-info">
            <div class="github-repo-name">${escapeHtml(repo.fullName)}</div>
            <div class="github-repo-meta">
              <span>🌿 ${escapeHtml(repo.branch)}</span>
              <span>${repo.indexedFilesCount} arquivos</span>
              ${statusBadge}
            </div>
          </div>
          <div class="github-repo-actions">
            ${repo.indexStatus === 'READY' ? `
              <button class="btn-repo-use ${isActive ? 'active' : ''}"
                      onclick="toggleGithubRepo('${repo.id}', '${escapeHtml(repo.fullName)}')">
                ${isActive ? 'Desativar' : 'Usar'}
              </button>
            ` : ''}
            <button class="btn-repo-reindex" onclick="reindexRepo('${repo.id}')" title="Re-indexar">↺</button>
            <button class="btn-repo-delete" onclick="deleteRepo('${repo.id}')" title="Remover">✕</button>
          </div>
        </div>
      `;
    })
    .join('');
}

async function connectGithubRepo() {
  const repoInput = el.githubRepoInput.value.trim();
  const fullName = parseGithubRepoInput(repoInput);
  const branch = el.githubBranchInput.value.trim() || 'main';
  const accessToken = el.githubTokenInput.value.trim();
  const isPrivate = el.githubPrivateChk.checked;

  if (!fullName) {
    el.githubRepoInput.focus();
    return;
  }

  el.btnGithubConnect.disabled = true;
  el.btnGithubConnect.textContent = 'Conectando...';

  try {
    const res = await fetch(`${API.BASE}/api/github/repositories`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fullName, repoUrl: repoInput, branch, accessToken, isPrivate }),
    });
    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.error || `HTTP ${res.status}`);
    }
    el.githubRepoInput.value = '';
    el.githubBranchInput.value = '';
    el.githubTokenInput.value = '';
    el.githubPrivateChk.checked = false;
    await loadGithubRepos();
  } catch (err) {
    alert(`Erro ao conectar repositório: ${err.message}`);
  } finally {
    el.btnGithubConnect.disabled = false;
    el.btnGithubConnect.textContent = 'Conectar';
  }
}

window.toggleGithubRepo = function (repoId, fullName) {
  if (state.activeGithubRepo?.id === repoId) {
    clearGithubContext();
  } else {
    setGithubContext(repoId, fullName);
    closeGithubModal();
  }
  renderGithubRepos();
};

function setGithubContext(id, fullName) {
  state.activeGithubRepo = { id, fullName };
  el.githubContextBadge.hidden = false;
  el.githubContextName.textContent = `GitHub: ${fullName}`;
  el.btnGithub.classList.add('active');
}

function clearGithubContext() {
  state.activeGithubRepo = null;
  el.githubContextBadge.hidden = true;
  el.btnGithub.classList.remove('active');
}

window.reindexRepo = async function (repoId) {
  try {
    await fetch(`${API.BASE}/api/github/repositories/${repoId}/reindex`, { method: 'POST' });
    await loadGithubRepos();
  } catch (err) {
    console.error('Erro ao re-indexar:', err);
  }
};

window.deleteRepo = async function (repoId) {
  openConfirm(async () => {
    try {
      await fetch(`${API.BASE}/api/github/repositories/${repoId}`, { method: 'DELETE' });
      if (state.activeGithubRepo?.id === repoId) clearGithubContext();
      await loadGithubRepos();
    } catch (err) {
      console.error('Erro ao deletar repo:', err);
    }
  });
};
