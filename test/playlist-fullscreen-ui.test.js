import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('Playlist detail view has play button, compact sort subbar, and icon-only download button', () => {
  const html = fs.readFileSync(path.join(process.cwd(), 'src/index.html'), 'utf-8');
  const css = fs.readFileSync(path.join(process.cwd(), 'src/styles.css'), 'utf-8');

  // 1. Play button in playlist header
  assert.ok(html.includes('id="btn-play-playlist"'), 'btn-play-playlist must exist in index.html');
  assert.ok(css.includes('.playlist-header-play-btn'), 'playlist-header-play-btn styles must exist');
  assert.ok(css.includes('margin-left: auto'), 'playlist-header-play-btn must be right-aligned');

  // 2. Icon-only download button (no label span)
  assert.ok(html.includes('id="btn-download-playlist"'), 'btn-download-playlist must exist');
  assert.ok(!html.includes('id="btn-download-playlist-label"'), 'btn-download-playlist-label text span must be removed');

  // 3. Subbar with search and compact sort
  assert.ok(html.includes('class="playlist-subbar"'), 'playlist-subbar must exist');
  assert.ok(html.includes('class="playlist-sort-wrap"'), 'playlist-sort-wrap must exist');
  assert.ok(html.includes('id="playlist-sort"'), 'playlist-sort must exist in playlist-subbar');
});

test('Fullscreen player has timeline and volume below controls, with clean settings button hitbox', () => {
  const html = fs.readFileSync(path.join(process.cwd(), 'src/index.html'), 'utf-8');
  const css = fs.readFileSync(path.join(process.cwd(), 'src/styles.css'), 'utf-8');

  // 1. Fullscreen timeline and volume elements exist
  assert.ok(html.includes('id="tvk-timeline-bar"'), 'tvk-timeline-bar must exist');
  assert.ok(html.includes('id="tvk-scrubber-track"'), 'tvk-scrubber-track must exist');
  assert.ok(html.includes('id="tvk-volume-bar"'), 'tvk-volume-bar must exist');
  assert.ok(html.includes('id="tvk-volume-slider"'), 'tvk-volume-slider must exist');
  assert.ok(html.includes('id="btn-tvk-volume-mute"'), 'btn-tvk-volume-mute must exist');

  // Timeline and volume are positioned after tvk-controls in DOM
  const controlsIdx = html.indexOf('id="tvk-controls"');
  const timelineIdx = html.indexOf('id="tvk-timeline-bar"');
  const volumeIdx = html.indexOf('id="tvk-volume-bar"');

  assert.ok(controlsIdx !== -1 && timelineIdx > controlsIdx, 'tvk-timeline-bar must be below tvk-controls');
  assert.ok(volumeIdx > timelineIdx, 'tvk-volume-bar must be below tvk-timeline-bar');

  // 2. Hitbox fixes for fullscreen buttons
  assert.ok(css.includes('.tv-karaoke-close::after'), 'tv-karaoke-close::after rule must exist');
  assert.ok(css.includes('#btn-tvk-settings::after'), 'btn-tvk-settings::after rule must exist');
  assert.ok(css.includes('-webkit-app-region: no-drag'), 'fullscreen buttons must have no-drag');
});
