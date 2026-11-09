<script>
  import Icon from './Icon.svelte';
  import { updates } from '../updates.svelte.js';
  import { game, demoMode } from '../launcher.svelte.js';
  import { navigate, ui } from '../store.svelte.js';
</script>

<header class="topbar">
  <button class="brand" onclick={() => navigate('home')} aria-label="Ir al inicio">
    <span class="brand-mark"><img src="./icon.png" alt="" /></span>
    <span class="brand-text">
      <span class="brand-title">BASE LAUNCHER</span>
      <span class="brand-sub">mobile</span>
    </span>
  </button>

  <div class="top-actions">
    <button class="status-chip" onclick={() => navigate('settings')} aria-label="Estado de Minecraft">
      <span class="dot" class:on={game.installed} class:off={!game.installed && !game.loading}></span>
      {#if game.loading}
        …
      {:else if demoMode}
        Demo
      {:else if game.installed}
        <b>{game.version}</b>
      {:else}
        Sin juego
      {/if}
    </button>

    {#if updates.available}
      <button class="update-pill" onclick={() => (ui.updateOpen = true)} aria-label="Actualización disponible">
        <Icon name="download" size={13} />
        {updates.latest}
      </button>
    {/if}
  </div>
</header>
