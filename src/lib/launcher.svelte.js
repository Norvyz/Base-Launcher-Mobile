import { native, isAndroid } from './native.js';
import { toast, tap } from './store.svelte.js';

export const demoMode = !isAndroid;

export const game = $state({
  loading: true,
  installed: false,
  version: '',
  error: ''
});

export const storage = $state({
  loading: true,
  granted: false,
  manager: false,
  root: '',
  external: false,
  readable: false,
  roots: [],
  error: ''
});

export const worlds = $state({
  loading: true,
  loaded: false,
  readable: false,
  root: '',
  items: [],
  error: ''
});

export const addons = $state({
  loading: true,
  loaded: false,
  items: [],
  error: ''
});

export const pings = $state({});

let gameLoaded = false;

export async function refreshGame(force = false) {
  if (gameLoaded && !force) return;
  game.loading = true;
  try {
    const info = await native.getGameInfo();
    game.installed = !!info.installed;
    game.version = info.versionName || '';
    game.error = '';
    gameLoaded = true;
  } catch (e) {
    game.error = e?.message || 'No se pudo consultar Minecraft';
  } finally {
    game.loading = false;
  }
  await refreshStorage(force);
}

export async function refreshStorage(force = false) {
  if (storage.loading === false && !force && storage.roots.length) return;
  storage.loading = true;
  try {
    const s = await native.getStorageStatus();
    storage.granted = !!s.granted;
    storage.manager = !!s.manager;
    storage.root = s.root || '';
    storage.external = !!s.external;
    storage.readable = !!s.readable;
    storage.roots = s.roots || [];
    storage.error = '';
  } catch (e) {
    storage.error = e?.message || 'No se pudo comprobar el almacenamiento';
  } finally {
    storage.loading = false;
  }
}

export async function requestStorage() {
  try {
    const r = await native.requestStorage();
    await refreshStorage(true);
    if (r?.granted || storage.granted) toast('Acceso a archivos concedido', 'ok');
    else toast('Activa "Acceso a todos los archivos" para continuar');
  } catch (e) {
    toast(e?.message || 'No se pudo abrir el permiso', 'bad');
  }
}

export async function refreshWorlds(force = false) {
  if (worlds.loaded && !force) return;
  worlds.loading = true;
  try {
    const r = await native.listWorlds();
    worlds.readable = !!r.readable;
    worlds.root = r.root || '';
    worlds.items = r.worlds || [];
    worlds.error = '';
    worlds.loaded = true;
  } catch (e) {
    worlds.error = e?.message || 'No se pudieron leer los mundos';
  } finally {
    worlds.loading = false;
  }
}

export async function refreshAddons(force = false) {
  if (addons.loaded && !force) return;
  addons.loading = true;
  try {
    const r = await native.listAddons();
    addons.items = r.addons || [];
    addons.error = '';
    addons.loaded = true;
  } catch (e) {
    addons.error = e?.message || 'No se pudieron leer los addons';
  } finally {
    addons.loading = false;
  }
}

export async function refreshAll(force = false) {
  await Promise.all([refreshGame(force), refreshWorlds(force), refreshAddons(force)]);
}

export async function launchGame() {
  tap();
  try {
    const r = await native.launchGame();
    if (r && r.launched === false) {
      toast('Minecraft no está instalado', 'bad');
      await refreshGame(true);
      return;
    }
    toast('Abriendo Minecraft…', 'ok');
  } catch (e) {
    toast(e?.message || 'No se pudo abrir Minecraft', 'bad');
  }
}

export async function importFiles() {
  tap();
  try {
    const r = await native.importFiles();
    if (!r || r.canceled) return;
    const list = r.imported || [];
    const ok = list.filter((x) => x.ok).length;
    const bad = list.length - ok;
    if (ok) {
      await Promise.all([refreshWorlds(true), refreshAddons(true), refreshStorage(true)]);
      toast(ok === 1 ? 'Archivo importado' : `${ok} archivos importados`, 'ok');
    }
    if (bad) {
      const first = list.find((x) => !x.ok);
      toast(first?.message || `${bad} archivo(s) no se pudieron importar`, 'bad');
    }
  } catch (e) {
    toast(e?.message || 'No se pudo importar', 'bad');
  }
}

export async function deleteWorld(name) {
  try {
    await native.deleteWorld(name);
    worlds.items = worlds.items.filter((w) => w.name !== name);
    toast('Mundo eliminado', 'ok');
  } catch (e) {
    toast(e?.message || 'No se pudo eliminar el mundo', 'bad');
  }
}

export async function exportWorld(name) {
  tap();
  try {
    const r = await native.exportWorld(name);
    if (r?.canceled) return;
    toast('Copia guardada', 'ok');
  } catch (e) {
    toast(e?.message || 'No se pudo exportar el mundo', 'bad');
  }
}

export async function deleteAddon(type, name) {
  try {
    await native.deleteAddon(type, name);
    addons.items = addons.items.filter((a) => !(a.type === type && a.name === name));
    toast('Addon eliminado', 'ok');
  } catch (e) {
    toast(e?.message || 'No se pudo eliminar el addon', 'bad');
  }
}

export async function pingServer(host, port) {
  const key = `${host}:${port}`;
  if (pings[key]?.loading) return;
  pings[key] = { ...(pings[key] || {}), loading: true };
  try {
    const r = await native.pingServer(host, port);
    pings[key] = { ...r, loading: false };
  } catch {
    pings[key] = { loading: false, online: false };
  }
}

export async function joinServer(host, port, name) {
  tap();
  const url = `minecraft://connect/?serverUrl=${encodeURIComponent(host)}&serverPort=${encodeURIComponent(port)}`;
  try {
    await native.openUrl(url, 'com.mojang.minecraftpe');
    toast(`Entrando a ${name || host}…`, 'ok');
  } catch {
    toast('No se pudo abrir Minecraft con ese servidor', 'bad');
  }
}

export async function addToGame(name, host, port) {
  tap();
  const url = `minecraft://?addExternalServer=${encodeURIComponent(name)}|${encodeURIComponent(host)}:${encodeURIComponent(port)}`;
  try {
    await native.openUrl(url, 'com.mojang.minecraftpe');
    toast('Servidor añadido a Minecraft', 'ok');
  } catch {
    toast('No se pudo añadir el servidor', 'bad');
  }
}
