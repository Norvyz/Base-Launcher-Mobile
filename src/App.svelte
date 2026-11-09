<script>
  import { onMount, onDestroy } from 'svelte';
  import { Capacitor } from '@capacitor/core';
  import { App as CapApp } from '@capacitor/app';
  import { StatusBar, Style } from '@capacitor/status-bar';

  import TopBar from './lib/components/TopBar.svelte';
  import Dock from './lib/components/Dock.svelte';
  import UpdateSheet from './lib/components/UpdateSheet.svelte';
  import Toast from './lib/components/Toast.svelte';

  import Home from './lib/screens/Home.svelte';
  import Worlds from './lib/screens/Worlds.svelte';
  import Addons from './lib/screens/Addons.svelte';
  import Servers from './lib/screens/Servers.svelte';
  import Settings from './lib/screens/Settings.svelte';

  import { ui, settings, loadSettings, navigate } from './lib/store.svelte.js';
  import { refreshAll } from './lib/launcher.svelte.js';
  import { checkUpdates } from './lib/updates.svelte.js';

  const screens = { home: Home, worlds: Worlds, addons: Addons, servers: Servers, settings: Settings };
  const Screen = $derived(screens[ui.screen] || Home);
  const isNative = Capacitor.isNativePlatform();

  let backHandle = null;

  async function syncStatusBar() {
    if (!isNative) return;
    try {
      await StatusBar.setStyle({ style: settings.mode === 'light' ? Style.Light : Style.Dark });
      await StatusBar.setBackgroundColor({ color: settings.mode === 'light' ? '#f3f1fb' : '#0a0716' });
    } catch {
      /* la barra de estado es opcional */
    }
  }

  onMount(async () => {
    await loadSettings();
    syncStatusBar();
    refreshAll();
    if (settings.autoUpdate) checkUpdates();

    if (isNative) {
      try {
        backHandle = await CapApp.addListener('backButton', () => {
          if (ui.updateOpen) {
            ui.updateOpen = false;
            return;
          }
          if (ui.screen !== 'home') {
            navigate('home');
            return;
          }
          CapApp.exitApp();
        });
      } catch {
        /* botón atrás del sistema */
      }
    }
  });

  onDestroy(() => {
    backHandle?.remove();
  });

  $effect(() => {
    // Reacciona a los cambios de modo y tema para la barra de estado.
    settings.mode;
    settings.theme;
    syncStatusBar();
  });
</script>

<div class="app">
  <div class="bg" aria-hidden="true">
    <i class="orb o1"></i>
    <i class="orb o2"></i>
  </div>

  <TopBar />

  <main class="scroll">
    {#key ui.screen}
      <Screen />
    {/key}
  </main>

  <Dock />
  <UpdateSheet />
  <Toast />
</div>
