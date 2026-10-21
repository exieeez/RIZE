// ==========================================
// Votify Advanced Import Modal Logic
// ==========================================
function safeClick(id, fn) {
  const elem = document.getElementById(id);
  if (elem) elem.addEventListener('click', fn);
}
function showToast(msg, type = 'info') {
  if (typeof window.showToast === 'function') {
    window.showToast(msg, type);
  } else {
    console.log('[Toast]', msg);
  }
}
function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
window.openImportModal = openImportModal;
window.closeImportModal = closeImportModal;
// ==========================================
let currentImportEventSource = null;
let importedTracksState = [];
let selectedImportFileItems = [];

function openImportModal() {
  const overlay = document.getElementById('import-modal-overlay');
  if (!overlay) return;

  switchImportView('form');
  switchImportTab('link');

  const linkInput = document.getElementById('import-link-input');
  if (linkInput) {
    linkInput.value = '';
    setTimeout(() => linkInput.focus(), 120);
  }
  const textInput = document.getElementById('import-text-input');
  if (textInput) textInput.value = '';
  const fileSelected = document.getElementById('import-file-selected');
  if (fileSelected) fileSelected.style.display = 'none';
  const dropzone = document.getElementById('import-dropzone');
  if (dropzone) dropzone.style.display = 'block';
  const fileInput = document.getElementById('import-file-input');
  if (fileInput) fileInput.value = '';
  selectedImportFileItems = [];

  overlay.classList.remove('hidden');
}

function closeImportModal() {
  const overlay = document.getElementById('import-modal-overlay');
  if (overlay) overlay.classList.add('hidden');
  if (currentImportEventSource) {
    try { currentImportEventSource.close(); } catch (e) {}
    currentImportEventSource = null;
  }
}

function switchImportView(viewName) {
  const formView = document.getElementById('import-form-view');
  const progressView = document.getElementById('import-progress-view');
  const resultsView = document.getElementById('import-results-view');

  if (formView) formView.style.display = viewName === 'form' ? 'flex' : 'none';
  if (progressView) progressView.style.display = viewName === 'progress' ? 'flex' : 'none';
  if (resultsView) resultsView.style.display = viewName === 'results' ? 'flex' : 'none';
}

function switchImportTab(tabName) {
  document.querySelectorAll('.import-tab-btn').forEach(btn => {
    btn.classList.toggle('active', btn.getAttribute('data-import-tab') === tabName);
  });
  document.querySelectorAll('.import-tab-pane').forEach(pane => {
    pane.style.display = pane.id === `import-pane-${tabName}` ? 'block' : 'none';
  });
}

function updateImportProgress({ title, subtitle, current, total, percent }) {
  if (title) {
    const tEl = document.getElementById('import-progress-title');
    if (tEl) tEl.textContent = title;
  }
  if (subtitle) {
    const sEl = document.getElementById('import-progress-subtitle');
    if (sEl) sEl.textContent = subtitle;
  }
  if (current != null && total != null) {
    const cEl = document.getElementById('import-progress-counter');
    if (cEl) cEl.textContent = `${current} / ${total} треков`;
  }
  const fillEl = document.getElementById('import-progress-bar-fill');
  const pctEl = document.getElementById('import-progress-percent');
  const p = percent != null ? percent : (total > 0 && current != null ? Math.round((current / total) * 100) : 0);
  if (fillEl) fillEl.style.width = `${Math.min(100, Math.max(0, p))}%`;
  if (pctEl) pctEl.textContent = `${Math.min(100, Math.max(0, p))}%`;
}

function showCurrentImportTrack(track) {
  const container = document.getElementById('import-current-track');
  const nameEl = document.getElementById('import-current-track-name');
  if (!container || !nameEl || !track) return;
  const label = track.artist ? `${track.artist} — ${track.title}` : (track.title || 'Трек');
  nameEl.textContent = label;
  container.style.display = 'inline-flex';
}

function showImportResults(playlistName, tracks) {
  importedTracksState = (tracks || []).filter(t => t && (t.id || t.title));
  if (importedTracksState.length === 0) {
    showToast('Не удалось найти треки для импорта');
    switchImportView('form');
    return;
  }

  switchImportView('results');

  const nameInput = document.getElementById('import-playlist-name-input');
  if (nameInput) nameInput.value = playlistName || 'Импортированный плейлист';

  const countEl = document.getElementById('import-results-count');
  if (countEl) countEl.textContent = `Найдено ${importedTracksState.length} треков`;

  const listContainer = document.getElementById('import-track-list-scroll');
  if (!listContainer) return;

  listContainer.innerHTML = importedTracksState.map((track, idx) => {
    const title = track.title || 'Трек';
    const artist = track.artist || 'Неизвестный исполнитель';
    const cover = track.cover || 'icon.png';
    const durationStr = typeof formatTime === 'function' ? formatTime(track.duration || 0) : '0:00';
    return `
      <div class="import-track-item" data-index="${idx}">
        <input type="checkbox" class="import-track-checkbox" checked data-index="${idx}" />
        <img src="${escapeHtml(cover)}" alt="" class="import-track-cover" onerror="this.src='icon.png'" />
        <div class="import-track-info">
          <div class="import-track-title">${escapeHtml(title)}</div>
          <div class="import-track-artist">${escapeHtml(artist)}</div>
        </div>
        <div class="import-track-duration">${escapeHtml(durationStr)}</div>
      </div>
    `;
  }).join('');

  // Item click to toggle checkbox
  listContainer.querySelectorAll('.import-track-item').forEach(row => {
    row.addEventListener('click', e => {
      if (e.target.tagName.toLowerCase() === 'input') return;
      const cb = row.querySelector('.import-track-checkbox');
      if (cb) cb.checked = !cb.checked;
    });
  });
}

function startStreamingUrlImport(url) {
  switchImportView('progress');
  updateImportProgress({
    title: 'Импорт плейлиста...',
    subtitle: 'Анализ ссылки и получение треков...',
    current: 0,
    total: 0,
    percent: 0,
  });

  const currTrackEl = document.getElementById('import-current-track');
  if (currTrackEl) currTrackEl.style.display = 'none';

  const encoded = encodeURIComponent(url);
  const es = new EventSource(`/api/playlist/stream?url=${encoded}`);
  currentImportEventSource = es;

  let playlistTitle = 'Импортированный плейлист';
  const collectedTracks = [];

  es.addEventListener('status', e => {
    try {
      const data = JSON.parse(e.data);
      if (data.message) {
        const sub = document.getElementById('import-progress-subtitle');
        if (sub) sub.textContent = data.message;
      }
    } catch (err) {}
  });

  es.addEventListener('metadata', e => {
    try {
      const data = JSON.parse(e.data);
      if (data.name) playlistTitle = data.name;
      const sub = document.getElementById('import-progress-subtitle');
      if (sub) sub.textContent = `Найдено ${data.total} треков, сопоставление аудио...`;
      updateImportProgress({ total: data.total });
    } catch (err) {}
  });

  es.addEventListener('progress', e => {
    try {
      const data = JSON.parse(e.data);
      if (data.track) {
        collectedTracks.push(data.track);
        showCurrentImportTrack(data.track);
      }
      updateImportProgress({
        current: data.current || collectedTracks.length,
        total: data.total || collectedTracks.length,
        percent: data.percent,
      });
    } catch (err) {}
  });

  es.addEventListener('complete', e => {
    try {
      const data = JSON.parse(e.data);
      es.close();
      currentImportEventSource = null;
      const finalTracks = Array.isArray(data.tracks) && data.tracks.length > 0 ? data.tracks : collectedTracks;
      if (data.name) playlistTitle = data.name;
      showImportResults(playlistTitle, finalTracks);
    } catch (err) {
      showImportResults(playlistTitle, collectedTracks);
    }
  });

  es.addEventListener('error', e => {
    es.close();
    currentImportEventSource = null;
    let errMsg = 'Ошибка при импорте плейлиста. Проверьте ссылку и доступность плейлиста.';
    try {
      if (e.data) {
        const d = JSON.parse(e.data);
        if (d.error) errMsg = d.error;
      }
    } catch (err) {}
    showToast(errMsg);
    switchImportView('form');
  });
}

async function startLocalAudioImport(playlistName, items) {
  switchImportView('progress');
  updateImportProgress({
    title: 'Импорт локальных треков...',
    subtitle: 'Поиск обложек и данных из сети...',
    current: 0,
    total: items.length,
    percent: 0
  });

  const currTrackEl = document.getElementById('import-current-track');
  if (currTrackEl) currTrackEl.style.display = 'block';

  importedTracksState = [];
  const results = [];

  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    showCurrentImportTrack({ title: item.title, artist: item.artist });
    updateImportProgress({
      title: 'Импорт локальных треков...',
      subtitle: `Поиск обложки: ${item.artist ? item.artist + ' - ' : ''}${item.title}`,
      current: i + 1,
      total: items.length,
      percent: Math.round(((i + 1) / items.length) * 100)
    });

    let extCover = 'icon.png';
    let finalTitle = item.title;
    let finalArtist = item.artist || '';
    let finalDuration = 0;
    let finalAlbum = '';

    try {
      const query = (finalArtist ? `${finalArtist} ${finalTitle}` : finalTitle).trim();
      const itunesResp = await fetch(`https://itunes.apple.com/search?term=${encodeURIComponent(query)}&entity=song&limit=1`);
      if (itunesResp.ok) {
        const itunesData = await itunesResp.json();
        if (itunesData.results && itunesData.results[0]) {
          const hit = itunesData.results[0];
          finalTitle = hit.trackName || finalTitle;
          finalArtist = hit.artistName || finalArtist;
          finalAlbum = hit.collectionName || '';
          extCover = (hit.artworkUrl100 || '').replace('100x100bb', '600x600bb') || extCover;
          finalDuration = Math.round((hit.trackTimeMillis || 0) / 1000);
        }
      }
    } catch (e) {
      try {
        const query = (finalArtist ? `${finalArtist} ${finalTitle}` : finalTitle).trim();
        const dzResp = await fetch(`https://api.deezer.com/search?q=${encodeURIComponent(query)}&limit=1`);
        if (dzResp.ok) {
          const dzData = await dzResp.json();
          if (dzData.data && dzData.data[0]) {
            const hit = dzData.data[0];
            finalTitle = hit.title || finalTitle;
            finalArtist = hit.artist?.name || finalArtist;
            finalAlbum = hit.album?.title || '';
            extCover = hit.album?.cover_xl || hit.album?.cover_medium || extCover;
            finalDuration = hit.duration || 0;
          }
        }
      } catch (_dz) {}
    }

    const localUrl = item.localUrl || (item.file ? URL.createObjectURL(item.file) : null);
    const trackObj = {
      id: 'local_' + Date.now() + '_' + Math.random().toString(36).slice(2, 9),
      title: finalTitle,
      artist: finalArtist,
      album: finalAlbum,
      cover: extCover,
      duration: finalDuration,
      isLocal: true,
      localUrl: localUrl,
      url: localUrl,
      source: 'local'
    };

    results.push(trackObj);
  }

  importedTracksState = results;
  showImportResults(playlistName || 'Мои треки', results);
}

async function startStreamingBatchImport(name, items) {
  if (!items || items.length === 0) {
    showToast('Список треков пуст');
    return;
  }

  switchImportView('progress');
  updateImportProgress({
    title: `Импорт: ${name}`,
    subtitle: `Поиск ${items.length} треков...`,
    current: 0,
    total: items.length,
    percent: 0,
  });

  const currTrackEl = document.getElementById('import-current-track');
  if (currTrackEl) currTrackEl.style.display = 'none';

  try {
    const res = await fetch('/api/playlist/resolve-stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, items }),
    });

    if (!res.ok) throw new Error('Ошибка запуска импорта');

    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    const collectedTracks = [];

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      const events = buffer.split('\n\n');
      buffer = events.pop();

      for (const ev of events) {
        if (!ev.trim()) continue;
        const lines = ev.split('\n');
        let eventType = 'message';
        let eventData = '';
        for (const line of lines) {
          if (line.startsWith('event: ')) eventType = line.slice(7).trim();
          else if (line.startsWith('data: ')) eventData = line.slice(6).trim();
        }
        if (!eventData) continue;
        try {
          const parsed = JSON.parse(eventData);
          if (eventType === 'progress') {
            if (parsed.track) {
              collectedTracks.push(parsed.track);
              showCurrentImportTrack(parsed.track);
            }
            updateImportProgress({
              current: parsed.current,
              total: parsed.total,
              percent: parsed.percent,
            });
          } else if (eventType === 'complete') {
            const finalTracks = Array.isArray(parsed.tracks) && parsed.tracks.length > 0 ? parsed.tracks : collectedTracks;
            showImportResults(name, finalTracks);
            return;
          }
        } catch (err) {}
      }
    }
    showImportResults(name, collectedTracks);
  } catch (err) {
    showToast(err.message || 'Ошибка импорта');
    switchImportView('form');
  }
}

function cleanTrackNameAndArtist(rawLine) {
  let str = String(rawLine || '').trim();
  if (!str) return null;
  // Strip Windows drive and path prefixes (e.g. C:\Users\Name\Music\ or D:\Music\)
  str = str.replace(/^[a-zA-Z]:\\[^\n\r]*\\/g, '');
  // Strip general directory path components (forward or backward slashes)
  str = str.replace(/^.*[\\\/]/, '');
  // Strip common audio and file extensions
  str = str.replace(/\.(mp3|wav|flac|m4a|ogg|aac|wma|opus|aiff|alac|m3u|m3u8|txt|csv)$/i, '');
  // Strip leading track numbers like "01. ", "01 - ", "01 ", "1. "
  str = str.replace(/^\d+[\.\s\-_]+/, '');
  str = str.trim();
  if (!str) return null;

  const dashIdx = str.indexOf(' - ');
  if (dashIdx !== -1) {
    const artist = str.slice(0, dashIdx).trim();
    const title = str.slice(dashIdx + 3).trim();
    return { artist, title: title || artist };
  }
  const emDashIdx = str.indexOf(' — ');
  if (emDashIdx !== -1) {
    const artist = str.slice(0, emDashIdx).trim();
    const title = str.slice(emDashIdx + 3).trim();
    return { artist, title: title || artist };
  }
  return { artist: '', title: str };
}

function parsePlaylistFileContent(filename, text) {
  const ext = (filename.split('.').pop() || '').toLowerCase();
  const items = [];

  if (['mp3', 'wav', 'flac', 'm4a', 'ogg', 'aac', 'wma', 'opus'].includes(ext)) {
    const item = cleanTrackNameAndArtist(filename);
    if (item) items.push(item);
    return items;
  }

  if (ext === 'json') {
    try {
      const data = JSON.parse(text);
      const list = Array.isArray(data) ? data : (data.tracks || data.items || []);
      for (const item of list) {
        if (typeof item === 'string') {
          const parsed = cleanTrackNameAndArtist(item);
          if (parsed) items.push(parsed);
        } else if (item && typeof item === 'object') {
          const title = item.title || item.name || '';
          const artist = item.artist || (Array.isArray(item.artists) ? item.artists.map(a => a.name || a).join(', ') : item.artists || '');
          if (title) items.push({ title: String(title).trim(), artist: String(artist).trim() });
        }
      }
    } catch (e) {
      throw new Error('Некорректный JSON файл');
    }
  } else if (ext === 'csv') {
    const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (i === 0 && (line.toLowerCase().includes('title') || line.toLowerCase().includes('artist') || line.toLowerCase().includes('track') || line.toLowerCase().includes('название') || line.toLowerCase().includes('исполнитель'))) {
        continue; // skip header
      }
      const parts = line.split(/[,;\t]/).map(p => p.replace(/^["']|["']$/g, '').trim());
      if (parts.length >= 2) {
        const item1 = cleanTrackNameAndArtist(parts[0]);
        const item2 = cleanTrackNameAndArtist(parts[1]);
        if (item1 && item2) {
          items.push({ title: item1.title || parts[0], artist: item2.title || parts[1] });
        } else {
          items.push({ title: parts[0], artist: parts[1] });
        }
      } else if (parts[0]) {
        const parsed = cleanTrackNameAndArtist(parts[0]);
        if (parsed) items.push(parsed);
      }
    }
  } else {
    // M3U / M3U8 / TXT
    const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
    for (const line of lines) {
      if (line.startsWith('#EXTM3U')) continue;
      if (line.startsWith('#EXTINF:')) {
        const commaIdx = line.indexOf(',');
        if (commaIdx !== -1) {
          const info = line.slice(commaIdx + 1).trim();
          const parsed = cleanTrackNameAndArtist(info);
          if (parsed) items.push(parsed);
        }
        continue;
      }
      if (line.startsWith('#')) continue;
      const parsed = cleanTrackNameAndArtist(line);
      if (parsed) items.push(parsed);
    }
  }

  return items;
}

// Wire Import Modal Events
document.addEventListener('DOMContentLoaded', () => {
  // Open / Close triggers
  safeClick('import-playlist-btn', openImportModal);
  safeClick('import-modal-close-btn', closeImportModal);
  safeClick('import-btn-cancel', closeImportModal);
  safeClick('import-results-cancel', closeImportModal);
  safeClick('import-btn-abort', () => {
    if (currentImportEventSource) {
      currentImportEventSource.close();
      currentImportEventSource = null;
    }
    showToast('Импорт прерван');
    switchImportView('form');
  });

  // Modal Backdrop click
  const modalOverlayEl = document.getElementById('import-modal-overlay');
  if (modalOverlayEl) {
    modalOverlayEl.addEventListener('click', e => {
      if (e.target === modalOverlayEl) closeImportModal();
    });
  }

  // Tab buttons
  document.querySelectorAll('.import-tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const tab = btn.getAttribute('data-import-tab');
      if (tab) switchImportTab(tab);
    });
  });

  // Paste button
  safeClick('import-paste-btn', async () => {
    try {
      const text = await navigator.clipboard.readText();
      const inp = document.getElementById('import-link-input');
      if (inp && text) {
        inp.value = text.trim();
        inp.focus();
      }
    } catch (e) {
      showToast('Не удалось прочитать буфер обмена');
    }
  });

  // File Dropzone & Picker
  const dropzone = document.getElementById('import-dropzone');
  const fileInput = document.getElementById('import-file-input');
  if (dropzone && fileInput) {
    dropzone.addEventListener('click', () => fileInput.click());
    dropzone.addEventListener('dragover', e => {
      e.preventDefault();
      dropzone.classList.add('dragover');
    });
    dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
    dropzone.addEventListener('drop', e => {
      e.preventDefault();
      dropzone.classList.remove('dragover');
      if (e.dataTransfer?.files?.length) handleImportFiles(e.dataTransfer.files);
    });
    fileInput.addEventListener('change', () => {
      if (fileInput.files?.length) handleImportFiles(fileInput.files);
    });
  }

  function handleImportFiles(fileList) {
    const files = Array.from(fileList || []);
    if (!files.length) return;
    
    selectedImportFileItems = [];
    let processedCount = 0;

    function finalizeUI() {
      const fileSelected = document.getElementById('import-file-selected');
      const fileNameEl = document.getElementById('import-file-name');
      const fileMetaEl = document.getElementById('import-file-meta');
      const fileIconEl = document.getElementById('import-file-type-icon');
      const dropzoneEl = document.getElementById('import-dropzone');

      const isAudio = selectedImportFileItems.some(i => i.isAudioFile);
      const displayName = files.length === 1 ? files[0].name : `${files.length} файлов`;

      if (fileNameEl) fileNameEl.textContent = displayName;
      if (fileMetaEl) {
        if (isAudio) {
          fileMetaEl.textContent = `Аудиофайлы готовы к импорту (${selectedImportFileItems.length} треков)`;
        } else {
          fileMetaEl.textContent = `Список распознан (${selectedImportFileItems.length} треков)`;
        }
      }
      if (fileIconEl) {
        if (files.length === 1 && isAudio) fileIconEl.textContent = 'audiotrack';
        else if (isAudio) fileIconEl.textContent = 'library_music';
        else fileIconEl.textContent = 'queue_music';
      }
      if (fileSelected) fileSelected.style.display = 'block';
      if (dropzoneEl) dropzoneEl.style.display = 'none';
    }

    files.forEach(file => {
      const ext = (file.name.split('.').pop() || '').toLowerCase();
      if (['mp3', 'wav', 'flac', 'm4a', 'ogg', 'aac', 'wma', 'opus'].includes(ext)) {
        const parsed = cleanTrackNameAndArtist(file.name);
        if (parsed) {
          selectedImportFileItems.push({
            file,
            localUrl: URL.createObjectURL(file),
            title: parsed.title,
            artist: parsed.artist,
            isAudioFile: true
          });
        }
        processedCount++;
        if (processedCount === files.length) finalizeUI();
      } else {
        const reader = new FileReader();
        reader.onload = e => {
          try {
            const text = e.target.result;
            const items = parsePlaylistFileContent(file.name, text);
            selectedImportFileItems.push(...items);
          } catch (err) {
            showToast(err.message || 'Ошибка чтения файла');
          } finally {
            processedCount++;
            if (processedCount === files.length) finalizeUI();
          }
        };
        reader.readAsText(file);
      }
    });
  }

  safeClick('import-file-remove', () => {
    selectedImportFileItems = [];
    const fileSelected = document.getElementById('import-file-selected');
    const dropzoneEl = document.getElementById('import-dropzone');
    const fileInputEl = document.getElementById('import-file-input');
    if (fileSelected) fileSelected.style.display = 'none';
    if (dropzoneEl) dropzoneEl.style.display = 'block';
    if (fileInputEl) fileInputEl.value = '';
  });

  // Start Import Button
  safeClick('import-btn-start', () => {
    const activeTab = document.querySelector('.import-tab-btn.active')?.getAttribute('data-import-tab') || 'link';

    if (activeTab === 'link') {
      const url = document.getElementById('import-link-input')?.value?.trim();
      if (!url) {
        showToast('Введите ссылку на плейлист или трек');
        return;
      }
      if (!/^https?:\/\//i.test(url) && !url.startsWith('spotify:')) {
        showToast('Введите корректный URL адрес');
        return;
      }
      startStreamingUrlImport(url);
    } else if (activeTab === 'file') {
      if (!selectedImportFileItems || selectedImportFileItems.length === 0) {
        showToast('Сначала выберите файл плейлиста или треки');
        return;
      }
      const fileName = document.getElementById('import-file-name')?.textContent?.split('(')[0]?.trim() || 'Мои треки';
      if (selectedImportFileItems.some(i => i.isAudioFile)) {
        startLocalAudioImport(fileName.replace(/\.[a-zA-Z0-9]+$/, ''), selectedImportFileItems);
      } else {
        startStreamingBatchImport(fileName.replace(/\.[a-zA-Z0-9]+$/, ''), selectedImportFileItems);
      }
    } else if (activeTab === 'text') {
      const text = document.getElementById('import-text-input')?.value?.trim();
      if (!text) {
        showToast('Вставьте список треков');
        return;
      }
      const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
      const items = lines.map(line => {
        const dashIdx = line.indexOf(' - ');
        if (dashIdx !== -1) {
          return { artist: line.slice(0, dashIdx).trim(), title: line.slice(dashIdx + 3).trim() };
        }
        return { title: line, artist: '' };
      });
      startStreamingBatchImport('Текстовый плейлист', items);
    }
  });

  // Toggle All Checkboxes
  safeClick('import-toggle-all-btn', () => {
    const checkboxes = document.querySelectorAll('.import-track-checkbox');
    const toggleBtn = document.getElementById('import-toggle-all-btn');
    const anyUnchecked = Array.from(checkboxes).some(cb => !cb.checked);
    checkboxes.forEach(cb => { cb.checked = anyUnchecked; });
    if (toggleBtn) toggleBtn.textContent = anyUnchecked ? 'Снять все' : 'Выбрать все';
  });

  // Save Playlist
  function getSelectedImportedTracks() {
    const checkboxes = document.querySelectorAll('.import-track-checkbox');
    const selected = [];
    checkboxes.forEach(cb => {
      if (cb.checked) {
        const idx = parseInt(cb.getAttribute('data-index'));
        if (importedTracksState[idx]) selected.push(importedTracksState[idx]);
      }
    });
    return selected;
  }

  safeClick('import-btn-save', () => {
    const selected = getSelectedImportedTracks();
    if (selected.length === 0) {
      showToast('Выберите хотя бы один трек');
      return;
    }
    const name = document.getElementById('import-playlist-name-input')?.value?.trim() || 'Импортированный плейлист';
    playlists[name] = selected;
    savePlaylists();
    renderSidebarPlaylists();
    renderPlaylists();
    closeImportModal();
    showToast(`Плейлист «${name}» сохранен (${selected.length} треков)`);
  });

  // Play Now
  safeClick('import-btn-play-now', () => {
    const selected = getSelectedImportedTracks();
    if (selected.length === 0) {
      showToast('Выберите хотя бы один трек');
      return;
    }
    const name = document.getElementById('import-playlist-name-input')?.value?.trim() || 'Импортированный плейлист';
    playlists[name] = selected;
    savePlaylists();
    renderSidebarPlaylists();
    renderPlaylists();
    closeImportModal();
    showToast(`Запуск плейлиста «${name}»`);
    if (typeof playPlaylist === 'function') {
      playPlaylist(name);
    } else if (selected[0]) {
      playTrack(selected[0]);
    }
  });
});

// ==========================================
