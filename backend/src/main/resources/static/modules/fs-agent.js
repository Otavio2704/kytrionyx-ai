/* ════════════════════════════════════════════════════════════════
   FILE SYSTEM AGENT - Integração com File System Access API
════════════════════════════════════════════════════════════════ */

const FsAgent = (() => {
  let rootHandle = null;
  let rootPath = null;
  const listeners = new Set();

  const notify = () => {
    listeners.forEach(cb => {
      try {
        cb(rootPath);
      } catch (_) { }
    });
  };

  const sanitizeRelativePath = (relativePath) => {
    const clean = String(relativePath || '')
      .replace(/\\\\/g, '/')
      .replace(/^\/+/, '')
      .trim();
    if (!clean || clean.includes('..')) throw new Error('caminho inválido');
    return clean;
  };

  const resolveParentDirectory = async (relativePath, create) => {
    if (!rootHandle) throw new Error('pasta não selecionada');
    const clean = sanitizeRelativePath(relativePath);
    const parts = clean.split('/').filter(Boolean);
    if (!parts.length) throw new Error('caminho inválido');

    let dir = rootHandle;
    for (const part of parts.slice(0, -1)) {
      dir = await dir.getDirectoryHandle(part, { create });
    }

    return { dir, fileName: parts[parts.length - 1], clean };
  };

  return {
    isSupported() {
      return typeof globalThis !== 'undefined' && typeof globalThis.showDirectoryPicker === 'function';
    },

    onRootChange(cb) {
      if (typeof cb === 'function') listeners.add(cb);
      return () => listeners.delete(cb);
    },

    hasRoot() {
      return Boolean(rootHandle);
    },

    getRootPath() {
      return rootPath;
    },

    async selectRoot() {
      if (!this.isSupported()) return { ok: false, reason: 'unsupported' };
      try {
        const handle = await globalThis.showDirectoryPicker();
        rootHandle = handle;
        rootPath = handle?.name || 'pasta-selecionada';
        notify();
        return { ok: true, path: rootPath };
      } catch (err) {
        if (err?.name === 'AbortError') return { ok: false, reason: 'cancelled' };
        return { ok: false, reason: err?.message || 'erro ao selecionar pasta' };
      }
    },

    clearRoot() {
      rootHandle = null;
      rootPath = null;
      notify();
    },

    async verifyPermission() {
      if (!rootHandle) return false;
      try {
        const options = { mode: 'readwrite' };
        if ((await rootHandle.queryPermission(options)) === 'granted') return true;
        return (await rootHandle.requestPermission(options)) === 'granted';
      } catch (_) {
        return false;
      }
    },

    async writeFile(relativePath, content) {
      try {
        const hasPermission = await this.verifyPermission();
        if (!hasPermission) return { ok: false, reason: 'permissão negada' };

        const { dir, fileName, clean } = await resolveParentDirectory(relativePath, true);
        const fileHandle = await dir.getFileHandle(fileName, { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(String(content ?? ''));
        await writable.close();
        return { ok: true, path: clean };
      } catch (err) {
        return { ok: false, reason: err?.message || 'erro ao salvar arquivo' };
      }
    },

    async deleteFile(relativePath) {
      try {
        const hasPermission = await this.verifyPermission();
        if (!hasPermission) return { ok: false, reason: 'permissão negada' };

        const { dir, fileName, clean } = await resolveParentDirectory(relativePath, false);
        await dir.removeEntry(fileName, { recursive: false });
        return { ok: true, path: clean };
      } catch (err) {
        return { ok: false, reason: err?.message || 'erro ao remover arquivo' };
      }
    },
  };
})();

function setupFsAgent() {
  if (!FsAgent.isSupported()) {
    if (el.agentFsUnsupported) el.agentFsUnsupported.hidden = false;
    if (el.btnAgentFsSelect) el.btnAgentFsSelect.disabled = true;
    return;
  }

  FsAgent.onRootChange(path => {
    state.fsRootPath = path;
    renderFsBar();
  });

  el.btnAgentFsSelect?.addEventListener('click', async () => {
    const result = await FsAgent.selectRoot();
    if (result.ok) {
      showFsToast(`Pasta conectada: ${result.path}`, 'success');
    } else if (result.reason !== 'cancelled') {
      showFsToast(`Erro ao acessar pasta: ${result.reason}`, 'error');
    }
  });

  el.btnAgentFsClear?.addEventListener('click', () => {
    FsAgent.clearRoot();
    showFsToast('Pasta desconectada.', 'info');
  });

  renderFsBar();
}

function updateFsBarVisibility() {
  if (!el.agentFsBar) return;
  el.agentFsBar.hidden = !state.agentModeEnabled;
}

function renderFsBar() {
  if (!el.agentFsBar) return;
  const hasRoot = FsAgent.hasRoot();
  const path = FsAgent.getRootPath();

  if (el.agentFsPath) {
    el.agentFsPath.textContent = hasRoot ? path : 'Nenhuma pasta selecionada';
    el.agentFsPath.classList.toggle('connected', hasRoot);
  }

  if (el.btnAgentFsSelect) {
    el.btnAgentFsSelect.textContent = hasRoot ? '↺ Trocar pasta' : 'Selecionar pasta';
  }

  if (el.btnAgentFsClear) {
    el.btnAgentFsClear.hidden = !hasRoot;
  }

  el.btnAgentMode?.classList.toggle('fs-connected', hasRoot);
}

function showFsToast(message, type = 'info') {
  const existing = document.querySelector('.fs-toast');
  if (existing) existing.remove();

  const toast = document.createElement('div');
  toast.className = `fs-toast fs-toast--${type}`;
  toast.innerHTML = `
    <span class="fs-toast-icon">${type === 'success' ? '✓' : type === 'error' ? '✕' : 'ℹ'}</span>
    <span>${escapeHtml(message)}</span>
  `;
  document.body.appendChild(toast);

  requestAnimationFrame(() => toast.classList.add('visible'));
  setTimeout(() => {
    toast.classList.remove('visible');
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}
