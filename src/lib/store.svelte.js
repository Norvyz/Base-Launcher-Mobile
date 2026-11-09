import { Preferences } from '@capacitor/preferences';
import { Haptics, ImpactStyle } from '@capacitor/haptics';
import { isNative } from './native.js';
import { themes } from './themes.js';

const KEY = 'baselauncher-mobile-settings';

const defaults = {
  theme: 'base',
  mode: 'dark',
  autoUpdate: true,
  motion: true,
  customServers: []
};

export const settings = $state({ ...defaults });
export const ui = $state({
  screen: 'home',
  updateOpen: false,
  toast: null
});

let toastTimer = null;

export function toast(message, kind = 'info') {
  ui.toast = { message, kind, id: Date.now() };
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (ui.toast = null), 3400);
}

export function navigate(screen) {
  if (['home', 'worlds', 'addons', 'servers', 'settings'].includes(screen)) ui.screen = screen;
}

export async function tap() {
  if (!isNative) return;
  try {
    await Haptics.impact({ style: ImpactStyle.Light });
  } catch {
    /* sin vibración */
  }
}

export function applyTheme() {
  const t = themes[settings.theme] || themes.base;
  const light = settings.mode === 'light';
  const root = document.documentElement;
  root.style.setProperty('--a', light ? t.la : t.a);
  root.style.setProperty('--b', light ? t.lb : t.b);
  root.style.setProperty('--c', light ? t.lc : t.c);
  root.dataset.mode = light ? 'light' : 'dark';
  root.dataset.theme = settings.theme;
  root.dataset.motion = settings.motion ? 'on' : 'off';
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', light ? t.lc : t.c);
}

export async function loadSettings() {
  try {
    const { value } = await Preferences.get({ key: KEY });
    if (value) {
      const saved = JSON.parse(value);
      Object.assign(settings, defaults, saved);
      if (!Array.isArray(settings.customServers)) settings.customServers = [];
    }
  } catch {
    /* primera vez */
  }
  applyTheme();
}

export async function saveSettings() {
  applyTheme();
  try {
    await Preferences.set({
      key: KEY,
      value: JSON.stringify({
        theme: settings.theme,
        mode: settings.mode,
        autoUpdate: settings.autoUpdate,
        motion: settings.motion,
        customServers: settings.customServers
      })
    });
  } catch {
    /* sin almacenamiento */
  }
}

export async function setTheme(id) {
  settings.theme = id;
  await saveSettings();
  tap();
}

export async function setMode(mode) {
  settings.mode = mode;
  await saveSettings();
  tap();
}

export async function toggleMotion() {
  settings.motion = !settings.motion;
  await saveSettings();
  tap();
}

export async function toggleAutoUpdate() {
  settings.autoUpdate = !settings.autoUpdate;
  await saveSettings();
  tap();
}

export function addCustomServer(server) {
  const exists = settings.customServers.some(
    (s) => s.host.toLowerCase() === server.host.toLowerCase() && Number(s.port) === Number(server.port)
  );
  if (exists) return false;
  settings.customServers.push({ ...server, id: `${Date.now()}` });
  saveSettings();
  return true;
}

export function removeCustomServer(id) {
  settings.customServers = settings.customServers.filter((s) => s.id !== id);
  saveSettings();
}

export async function resetSettings() {
  Object.assign(settings, defaults, { customServers: [] });
  await saveSettings();
  toast('Ajustes restaurados', 'ok');
}
