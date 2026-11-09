import { native, onDownloadProgress, openExternal } from './native.js';
import { toast } from './store.svelte.js';

export const REPO = 'Norvyz/Base-Launcher-Mobile';
export const RELEASES_URL = `https://github.com/${REPO}/releases`;
const API_URL = `https://api.github.com/repos/${REPO}/releases?per_page=20`;
const CACHE_MS = 5 * 60 * 1000;

export const updates = $state({
  current: typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.1.0',
  latest: '',
  available: false,
  checking: false,
  error: '',
  notes: '',
  publishedAt: '',
  apkUrl: '',
  apkName: '',
  releaseUrl: '',
  downloading: false,
  percent: 0,
  needsPermission: false
});

let checkedAt = 0;
let progressOff = null;

function parts(v) {
  return String(v || '')
    .replace(/^v/i, '')
    .split(/[.+-]/)
    .slice(0, 3)
    .map((n) => parseInt(n, 10) || 0);
}

export function compareVersions(a, b) {
  const pa = parts(a);
  const pb = parts(b);
  for (let i = 0; i < 3; i++) {
    if ((pa[i] || 0) > (pb[i] || 0)) return 1;
    if ((pa[i] || 0) < (pb[i] || 0)) return -1;
  }
  return 0;
}

export async function checkUpdates(force = false) {
  if (updates.checking) return;
  if (!force && checkedAt && Date.now() - checkedAt < CACHE_MS) return;
  updates.checking = true;
  updates.error = '';
  try {
    const res = await fetch(API_URL, {
      headers: { Accept: 'application/vnd.github+json' }
    });
    if (!res.ok) throw new Error(res.status === 403 ? 'GitHub limitó las consultas, intenta más tarde' : `GitHub respondió ${res.status}`);
    const releases = await res.json();
    const rel = (Array.isArray(releases) ? releases : []).find(
      (r) => !r.draft && Array.isArray(r.assets) && r.assets.some((a) => /\.apk$/i.test(a.name || ''))
    );
    if (!rel) {
      updates.latest = '';
      updates.available = false;
      updates.notes = '';
      checkedAt = Date.now();
      return;
    }
    const asset = rel.assets.find((a) => /\.apk$/i.test(a.name || ''));
    updates.latest = String(rel.tag_name || '').replace(/^v/i, '');
    updates.notes = rel.body || 'Sin notas de la versión.';
    updates.publishedAt = rel.published_at || '';
    updates.releaseUrl = rel.html_url || RELEASES_URL;
    updates.apkUrl = asset?.browser_download_url || '';
    updates.apkName = asset?.name || `BaseLauncher-Mobile-${updates.latest}.apk`;
    updates.available = compareVersions(updates.latest, updates.current) > 0;
    checkedAt = Date.now();
  } catch (e) {
    updates.error = e?.message || 'No se pudo comprobar la actualización';
  } finally {
    updates.checking = false;
  }
}

function listenProgress() {
  if (progressOff) return;
  progressOff = onDownloadProgress((d) => {
    const pct = Number(d?.percent);
    updates.percent = Number.isFinite(pct) && pct >= 0 ? Math.min(100, pct) : -1;
  });
}

export async function installUpdate() {
  if (updates.downloading) return;
  if (!updates.apkUrl) {
    toast('No hay APK disponible en la última versión', 'bad');
    return;
  }
  listenProgress();
  updates.downloading = true;
  updates.percent = 0;
  updates.needsPermission = false;
  try {
    const r = await native.downloadAndInstall(updates.apkUrl, updates.apkName);
    if (r?.needsPermission) {
      updates.needsPermission = true;
      toast('Activa "Instalar apps desconocidas" y vuelve a intentar');
    } else {
      toast('Instalador abierto, sigue los pasos de Android', 'ok');
    }
  } catch (e) {
    toast(e?.message || 'No se pudo descargar la actualización', 'bad');
  } finally {
    updates.downloading = false;
  }
}

export async function openReleasePage() {
  try {
    await openExternal(updates.releaseUrl || RELEASES_URL);
  } catch {
    toast('No se pudo abrir el navegador', 'bad');
  }
}
