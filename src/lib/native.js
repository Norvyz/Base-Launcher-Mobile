import { registerPlugin, Capacitor } from '@capacitor/core';

// Plugin propio escrito en android/app/src/main/java/com/baselauncher/mobile/BedrockPlugin.java
const Bedrock = registerPlugin('Bedrock');

export const isNative = Capacitor.isNativePlatform();
export const isAndroid = isNative && Capacitor.getPlatform() === 'android';

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const hash = (s) => {
  let h = 0;
  for (const ch of String(s || '')) h = (h * 31 + ch.charCodeAt(0)) % 997;
  return h;
};

function demoWorlds() {
  return [
    { name: 'Mundo_Supervivencia', title: 'Supervivencia', size: 38420480, modified: Date.now() - 3600e3, icon: '' },
    { name: 'Mundo_Creativo', title: 'Ciudad creativa', size: 15872000, modified: Date.now() - 86400e3, icon: '' },
    { name: 'Mundo_Skyblock', title: 'Skyblock', size: 6742016, modified: Date.now() - 3 * 86400e3, icon: '' }
  ];
}

function demoAddons() {
  return [
    { type: 'resources', name: 'Texturas_Base', title: 'Texturas Base 32x', version: '1.2.0', uuid: 'demo-1', icon: '', size: 8388608, modified: Date.now() - 7200e3 },
    { type: 'behavior', name: 'Mobs_Extra', title: 'Mobs Extra', version: '2.0.1', uuid: 'demo-2', icon: '', size: 3145728, modified: Date.now() - 2 * 86400e3 },
    { type: 'skin', name: 'Skins_Pack', title: 'Pack de skins', version: '1.0.0', uuid: 'demo-3', icon: '', size: 524288, modified: Date.now() - 4 * 86400e3 }
  ];
}

const demoStorage = () => ({
  granted: true,
  manager: true,
  root: '/storage/emulated/0/games/com.mojang',
  external: true,
  readable: true,
  roots: [
    { path: '/storage/emulated/0/games/com.mojang', kind: 'external', exists: true, readable: true, writable: true },
    { path: '/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang', kind: 'app', exists: false, readable: false, writable: false }
  ]
});

const demo = {
  getGameInfo: async () => ({ installed: true, versionName: '1.21.80', versionCode: 121080 }),
  launchGame: async () => ({ launched: true }),
  getStorageStatus: async () => demoStorage(),
  requestStorage: async () => ({ granted: true, opened: false }),
  listWorlds: async () => ({ readable: true, root: demoStorage().root, worlds: demoWorlds() }),
  deleteWorld: async () => ({ deleted: true }),
  importFiles: async () => ({
    canceled: false,
    imported: [{ file: 'demo.mcworld', ok: true, kind: 'world', message: 'Mundo importado (demo)' }]
  }),
  exportWorld: async () => ({ saved: true, canceled: false }),
  listAddons: async () => ({ addons: demoAddons() }),
  deleteAddon: async () => ({ deleted: true }),
  pingServer: async ({ host, port }) => {
    const h = hash(host + port);
    return {
      online: true,
      motd: 'Base Launcher Demo',
      version: '1.21.80',
      players: 120 + (h % 900),
      maxPlayers: 2000,
      latency: 28 + (h % 120)
    };
  },
  openUrl: async () => ({ opened: true }),
  downloadAndInstall: async () => ({ launched: true, needsPermission: false })
};

async function invoke(method, args) {
  if (!isNative) {
    await wait(280);
    return typeof demo[method] === 'function' ? demo[method](args || {}) : {};
  }
  return Bedrock[method](args || {});
}

export const native = {
  getGameInfo: () => invoke('getGameInfo'),
  launchGame: () => invoke('launchGame'),
  getStorageStatus: () => invoke('getStorageStatus'),
  requestStorage: () => invoke('requestStorage'),
  listWorlds: () => invoke('listWorlds'),
  deleteWorld: (name) => invoke('deleteWorld', { name }),
  importFiles: () => invoke('importFiles'),
  exportWorld: (name) => invoke('exportWorld', { name }),
  listAddons: () => invoke('listAddons'),
  deleteAddon: (type, name) => invoke('deleteAddon', { type, name }),
  pingServer: (host, port) => invoke('pingServer', { host, port }),
  openUrl: (url, pkg) => invoke('openUrl', { url, package: pkg }),
  downloadAndInstall: (url, name) => invoke('downloadAndInstall', { url, name })
};

// Progreso de descarga del APK (solo Android real).
export function onDownloadProgress(cb) {
  if (!isNative) return () => {};
  let handle = null;
  let removed = false;
  Bedrock.addListener('downloadProgress', (data) => cb(data)).then((h) => {
    handle = h;
    if (removed) h.remove();
  });
  return () => {
    removed = true;
    if (handle) handle.remove();
  };
}

// Enlaces internos: en Android se abren con un Intent; en web con window.open.
export async function openExternal(url, pkg) {
  if (!isNative) {
    window.open(url, '_blank', 'noopener');
    return { opened: true };
  }
  return Bedrock.openUrl({ url, package: pkg });
}

export function formatBytes(bytes) {
  const n = Number(bytes) || 0;
  if (n <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const i = Math.min(units.length - 1, Math.floor(Math.log(n) / Math.log(1024)));
  const v = n / Math.pow(1024, i);
  return `${v >= 100 || i === 0 ? Math.round(v) : v.toFixed(1)} ${units[i]}`;
}

export function formatDate(ts) {
  const d = new Date(Number(ts) || Date.now());
  const diff = Date.now() - d.getTime();
  if (diff < 60e3) return 'ahora';
  if (diff < 3600e3) return `hace ${Math.round(diff / 60e3)} min`;
  if (diff < 86400e3) return `hace ${Math.round(diff / 3600e3)} h`;
  if (diff < 7 * 86400e3) return `hace ${Math.round(diff / 86400e3)} d`;
  return d.toLocaleDateString('es', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function initials(name) {
  const clean = String(name || '?').replace(/[^\p{L}\p{N} ]/gu, ' ').trim();
  const parts = clean.split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}
