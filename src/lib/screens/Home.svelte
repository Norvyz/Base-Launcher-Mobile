<script>
  import { onMount } from 'svelte';
  import Icon from '../components/Icon.svelte';
  import { openExternal } from '../native.js';
  import {
    game,
    storage,
    worlds,
    addons,
    demoMode,
    refreshGame,
    refreshWorlds,
    refreshAddons,
    launchGame,
    requestStorage
  } from '../launcher.svelte.js';
  import { updates } from '../updates.svelte.js';
  import { navigate, ui } from '../store.svelte.js';

  onMount(() => {
    refreshGame();
    refreshWorlds();
    refreshAddons();
  });

  const storageLabel = $derived(
    storage.loading ? '…' : storage.external ? 'Externo' : storage.readable ? 'App' : '—'
  );

  const needsHelp = $derived(!storage.loading && (!storage.granted || !storage.readable));

  async function installGame() {
    try {
      await openExternal('market://details?id=com.mojang.minecraftpe');
    } catch {
      try {
        await openExternal('https://play.google.com/store/apps/details?id=com.mojang.minecraftpe');
      } catch {
        /* sin tienda */
      }
    }
  }
</script>

<div class="screen">
  <section class="hero glass">
    <span class="eyebrow"><span class="dot on"></span> Minecraft Bedrock</span>

    <div class="play-wrap">
      <i class="play-ring"></i>
      <i class="play-ring"></i>
      <button
        class="play"
        onclick={launchGame}
        disabled={!game.installed || game.loading}
        aria-label="Abrir Minecraft"
      >
        <Icon name="play" size={44} stroke={0} />
      </button>
    </div>

    <h1>
      {#if game.loading}
        Buscando Minecraft…
      {:else if game.installed}
        Todo listo para jugar
      {:else}
        Minecraft no está instalado
      {/if}
    </h1>
    <p>
      {#if game.loading}
        Comprobando la app instalada en el teléfono.
      {:else if game.installed}
        Minecraft Bedrock {game.version} detectado en este dispositivo.
      {:else}
        Instala Minecraft Bedrock desde Play Store para empezar.
      {/if}
    </p>

    <div class="hero-actions">
      {#if game.installed}
        <button class="btn primary" onclick={launchGame}>
          <Icon name="play" size={15} stroke={0} /> Jugar ahora
        </button>
      {:else}
        <button class="btn primary" onclick={installGame}>
          <Icon name="download" size={15} /> Instalar Minecraft
        </button>
      {/if}
      <button class="btn" onclick={() => navigate('worlds')}>
        <Icon name="folder" size={15} /> Mundos
      </button>
    </div>
  </section>

  <div class="stat-grid">
    <div class="stat">
      <b>{worlds.loading ? '—' : worlds.items.length}</b>
      <span>Mundos</span>
    </div>
    <div class="stat">
      <b>{addons.loading ? '—' : addons.items.length}</b>
      <span>Addons</span>
    </div>
    <div class="stat">
      <b style="font-size:16px;padding-top:3px">{storageLabel}</b>
      <span>Almacenamiento</span>
    </div>
  </div>

  {#if updates.available}
    <div class="notice update">
      <div class="ni"><Icon name="download" size={18} /></div>
      <div class="nc">
        <b>Nueva versión {updates.latest}</b>
        <p>Hay una actualización de Base Launcher Mobile lista para instalar.</p>
        <div class="na">
          <button class="btn sm primary" onclick={() => (ui.updateOpen = true)}>
            Ver actualización
          </button>
        </div>
      </div>
    </div>
  {/if}

  {#if needsHelp}
    <div class="notice">
      <div class="ni"><Icon name="alert" size={18} /></div>
      <div class="nc">
        <b>{storage.granted ? 'No encontramos la carpeta de Minecraft' : 'Falta el acceso a archivos'}</b>
        <p>
          {#if storage.granted}
            Pon el almacenamiento de Minecraft en <b>Externo</b> para que Base Launcher pueda leer
            los mundos y los addons.
          {:else}
            Para gestionar mundos y addons, el launcher necesita acceso a todos los archivos del
            teléfono.
          {/if}
        </p>
        <div class="na">
          {#if !storage.granted}
            <button class="btn sm soft" onclick={requestStorage}>Dar acceso</button>
          {/if}
          <button class="btn sm" onclick={() => navigate('settings')}>
            {storage.granted ? 'Cómo activarlo' : 'Más información'}
          </button>
        </div>
      </div>
    </div>
  {/if}

  <div class="section">
    <div class="section-title"><Icon name="zap" size={14} /> Accesos rápidos</div>
    <div class="quick-grid">
      <button class="quick" onclick={() => navigate('worlds')}>
        <span class="qi"><Icon name="globe" size={18} /></span>
        <span><b>Mundos</b><small>Importar y exportar</small></span>
      </button>
      <button class="quick" onclick={() => navigate('addons')}>
        <span class="qi"><Icon name="package" size={18} /></span>
        <span><b>Addons</b><small>Texturas y packs</small></span>
      </button>
      <button class="quick" onclick={() => navigate('servers')}>
        <span class="qi"><Icon name="server" size={18} /></span>
        <span><b>Servidores</b><small>Entrar con un toque</small></span>
      </button>
      <button class="quick" onclick={() => navigate('settings')}>
        <span class="qi"><Icon name="palette" size={18} /></span>
        <span><b>Apariencia</b><small>Colores y tema</small></span>
      </button>
    </div>
  </div>

  {#if storage.root && !storage.loading}
    <div class="link-row" style="cursor:default">
      <span class="li"><Icon name="folder" size={17} /></span>
      <span class="lc">
        <b>Ubicación de datos</b>
        <small>{storage.root}</small>
      </span>
      <span class="chip" class:ok={storage.external} class:warn={!storage.external}>
        {storage.external ? 'Externo' : 'App'}
      </span>
    </div>
  {/if}

  {#if demoMode}
    <div class="notice">
      <div class="ni"><Icon name="info" size={18} /></div>
      <div class="nc">
        <b>Vista previa de demostración</b>
        <p>
          Estás viendo la interfaz en el navegador. Los mundos, addons y servidores reales solo
          funcionan dentro del APK en Android.
        </p>
      </div>
    </div>
  {/if}
</div>
