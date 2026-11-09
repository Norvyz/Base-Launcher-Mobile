<script>
  import { onMount } from 'svelte';
  import Icon from '../components/Icon.svelte';
  import Modal from '../components/Modal.svelte';
  import { themeList } from '../themes.js';
  import { openExternal } from '../native.js';
  import {
    game,
    storage,
    demoMode,
    launchGame,
    requestStorage,
    refreshGame,
    refreshStorage
  } from '../launcher.svelte.js';
  import { updates, checkUpdates, RELEASES_URL } from '../updates.svelte.js';
  import {
    settings,
    setTheme,
    setMode,
    toggleMotion,
    toggleAutoUpdate,
    resetSettings,
    toast,
    ui
  } from '../store.svelte.js';

  let helpOpen = $state(false);
  let resetOpen = $state(false);

  onMount(() => {
    refreshGame();
    refreshStorage();
  });

  async function doCheck() {
    await checkUpdates(true);
    if (updates.error) toast(updates.error, 'bad');
    else if (updates.available) toast(`Nueva versión ${updates.latest} disponible`, 'ok');
    else toast('Estás al día', 'ok');
  }

  async function openStore() {
    try {
      await openExternal('https://play.google.com/store/apps/details?id=com.mojang.minecraftpe');
    } catch {
      toast('No se pudo abrir la tienda', 'bad');
    }
  }

  async function openLink(url, label) {
    try {
      await openExternal(url);
    } catch {
      toast(`No se pudo abrir ${label}`, 'bad');
    }
  }

  async function doReset() {
    resetOpen = false;
    await resetSettings();
  }
</script>

<div class="screen">
  <div class="screen-head">
    <div class="titles">
      <h1 class="screen-title">Ajustes</h1>
      <p class="screen-sub">Personaliza Base Launcher Mobile</p>
    </div>
  </div>

  <div class="section">
    <div class="section-title"><Icon name="palette" size={14} /> Apariencia</div>

    <div class="swatches">
      {#each themeList as t (t.id)}
        <button
          class="swatch"
          class:on={settings.theme === t.id}
          onclick={() => setTheme(t.id)}
          aria-label={`Tema ${t.name}`}
          aria-pressed={settings.theme === t.id}
        >
          <i
            style="background:linear-gradient(145deg,{settings.mode === 'light' ? t.la : t.a},{settings.mode === 'light' ? t.lc : t.c})"
          ></i>
          <span>{t.name}</span>
        </button>
      {/each}
    </div>

    <div class="segment">
      <button
        class:on={settings.mode === 'dark'}
        class:accent={settings.mode === 'dark'}
        onclick={() => setMode('dark')}
      >
        <Icon name="moon" size={13} /> Oscuro
      </button>
      <button
        class:on={settings.mode === 'light'}
        class:accent={settings.mode === 'light'}
        onclick={() => setMode('light')}
      >
        <Icon name="sun" size={13} /> Claro
      </button>
    </div>

    <div class="switch-row">
      <span class="sc">
        <b>Animaciones</b>
        <small>Desactívalas si el teléfono va lento</small>
      </span>
      <button
        class="switch"
        class:on={settings.motion}
        onclick={toggleMotion}
        role="switch"
        aria-checked={settings.motion}
        aria-label="Animaciones"
      ></button>
    </div>
  </div>

  <div class="section">
    <div class="section-title"><Icon name="gamepad" size={14} /> Minecraft</div>

    <div class="link-row" style="cursor:default">
      <span class="li"><Icon name="gamepad" size={17} /></span>
      <span class="lc">
        <b>Minecraft Bedrock</b>
        <small>{game.loading ? 'Comprobando…' : game.installed ? `Versión ${game.version}` : 'No instalado'}</small>
      </span>
      <span class="chip" class:ok={game.installed} class:bad={!game.installed}>
        {game.installed ? 'Detectado' : 'Falta'}
      </span>
    </div>

    <div style="display:flex;gap:8px">
      <button class="btn primary wide" onclick={launchGame} disabled={!game.installed || game.loading}>
        <Icon name="play" size={15} stroke={0} /> Abrir Minecraft
      </button>
      <button class="btn wide" onclick={openStore}><Icon name="external" size={15} /> Play Store</button>
    </div>

    <div class="link-row" style="cursor:default">
      <span class="li"><Icon name="shield" size={17} /></span>
      <span class="lc">
        <b>Acceso a archivos</b>
        <small>{storage.granted ? 'Permiso concedido' : 'Necesario para mundos y addons'}</small>
      </span>
      <span class="chip" class:ok={storage.granted} class:bad={!storage.granted}>
        {storage.granted ? 'OK' : 'Pendiente'}
      </span>
    </div>

    {#if !storage.granted}
      <button class="btn soft wide" onclick={requestStorage}>
        <Icon name="shield" size={15} /> Dar acceso a todos los archivos
      </button>
    {/if}

    <button class="btn wide" onclick={() => (helpOpen = true)}>
      <Icon name="folder" size={15} /> Activar almacenamiento externo
    </button>

    {#each storage.roots as r (r.path)}
      <div class="perm-row">
        <span class="pi" class:ok={r.readable && r.writable} class:bad={!r.readable}></span>
        <span class="pc">
          <b>{r.kind === 'external' ? 'Almacenamiento externo' : 'Almacenamiento de la app'}</b>
          <small>{r.path}</small>
        </span>
        <span class="chip" class:ok={r.readable && r.writable}>
          {r.readable ? (r.writable ? 'Listo' : 'Solo lectura') : 'Sin acceso'}
        </span>
      </div>
    {/each}
  </div>

  <div class="section">
    <div class="section-title"><Icon name="download" size={14} /> Actualizaciones</div>

    <div class="link-row" style="cursor:default">
      <span class="li"><Icon name="sparkles" size={17} /></span>
      <span class="lc">
        <b>Base Launcher {updates.current}</b>
        <small>
          {#if updates.checking}
            Comprobando en GitHub…
          {:else if updates.available}
            Nueva versión {updates.latest} disponible
          {:else if updates.error}
            {updates.error}
          {:else}
            Sin actualizaciones pendientes
          {/if}
        </small>
      </span>
      <button
        class="icon-btn sm"
        class:spin={updates.checking}
        onclick={doCheck}
        disabled={updates.checking}
        aria-label="Comprobar actualizaciones"
      >
        <Icon name="refresh" size={15} />
      </button>
    </div>

    {#if updates.available}
      <button class="btn primary wide" onclick={() => (ui.updateOpen = true)}>
        <Icon name="download" size={15} /> Ver actualización {updates.latest}
      </button>
    {/if}

    <div class="switch-row">
      <span class="sc">
        <b>Buscar al abrir</b>
        <small>Consulta las releases al iniciar la app</small>
      </span>
      <button
        class="switch"
        class:on={settings.autoUpdate}
        onclick={toggleAutoUpdate}
        role="switch"
        aria-checked={settings.autoUpdate}
        aria-label="Buscar actualizaciones al abrir"
      ></button>
    </div>

    <button class="btn wide" onclick={() => openLink(RELEASES_URL, 'las releases')}>
      <Icon name="book" size={15} /> Ver historial en GitHub
    </button>
  </div>

  <div class="section">
    <div class="section-title"><Icon name="link" size={14} /> Enlaces</div>

    <button class="link-row" onclick={() => openLink('https://baselauncher.site.je/', 'el sitio web')}>
      <span class="li"><Icon name="globe" size={17} /></span>
      <span class="lc"><b>Sitio web</b><small>baselauncher.site.je</small></span>
      <Icon name="external" size={16} />
    </button>

    <button
      class="link-row"
      onclick={() => openLink('https://github.com/Norvyz/Base-Launcher-Mobile', 'el repositorio móvil')}
    >
      <span class="li"><Icon name="package" size={17} /></span>
      <span class="lc"><b>Repositorio móvil</b><small>Norvyz/Base-Launcher-Mobile</small></span>
      <Icon name="external" size={16} />
    </button>

    <button
      class="link-row"
      onclick={() => openLink('https://github.com/Norvyz/Base-Launcher', 'el repositorio de escritorio')}
    >
      <span class="li"><Icon name="gamepad" size={17} /></span>
      <span class="lc"><b>Launcher de escritorio</b><small>Norvyz/Base-Launcher</small></span>
      <Icon name="external" size={16} />
    </button>
  </div>

  <div class="section">
    <div class="section-title"><Icon name="info" size={14} /> Acerca de</div>
    <p class="legal">
      Base Launcher Mobile {updates.current} es un proyecto independiente y no está afiliado a Mojang
      ni a Microsoft. No incluye, distribuye ni desbloquea Minecraft: necesitas una copia legítima de
      Minecraft Bedrock. Los mundos, addons y servidores son contenido propio del usuario. Las marcas
      pertenecen a sus respectivos dueños.
    </p>
    {#if demoMode}
      <p class="legal">
        Estás en la vista web de demostración. Las funciones de archivos, instalación de APK y
        apertura de Minecraft solo están disponibles en el APK de Android.
      </p>
    {/if}
    <button class="btn danger wide" onclick={() => (resetOpen = true)}>
      <Icon name="refresh" size={15} /> Restaurar ajustes
    </button>
  </div>
</div>

<Modal
  open={helpOpen}
  title="Almacenamiento externo"
  subtitle="Para que Base Launcher vea tus mundos"
  onclose={() => (helpOpen = false)}
>
  <p class="legal">
    Android 11 o superior no deja que otras apps lean la carpeta privada de Minecraft. La solución es
    mover el almacenamiento del juego a la carpeta compartida.
  </p>
  <ol class="steps">
    <li>Abre <b>Minecraft</b> y entra a <b>Ajustes</b>.</li>
    <li>Entra a <b>Almacenamiento</b> y elige <b>Externo</b> como ubicación.</li>
    <li>Cierra Minecraft por completo y vuelve a abrirlo.</li>
    <li>En Base Launcher, entra a <b>Mundos</b> y pulsa el botón de actualizar.</li>
    <li>
      Si el permiso está pendiente, pulsa <b>Dar acceso a todos los archivos</b> en Ajustes.
    </li>
  </ol>
  <p class="legal">
    La ruta que usa Base Launcher es
    <b>/storage/emulated/0/games/com.mojang</b>. Si nunca cambiaste el almacenamiento a Externo, los
    mundos están en la carpeta privada de Minecraft y no se pueden leer desde fuera.
  </p>
  {#snippet footer()}
    <button class="btn primary wide" onclick={() => (helpOpen = false)}>Entendido</button>
  {/snippet}
</Modal>

<Modal open={resetOpen} title="Restaurar ajustes" subtitle="Tema, modo y servidores guardados" onclose={() => (resetOpen = false)}>
  <p class="legal">
    Se restaurará el tema Base, el modo oscuro, las animaciones y se borrarán los servidores que
    añadiste. Los mundos y addons no se tocan.
  </p>
  {#snippet footer()}
    <button class="btn wide" onclick={() => (resetOpen = false)}>Cancelar</button>
    <button class="btn danger wide" onclick={doReset}><Icon name="refresh" size={15} /> Restaurar</button>
  {/snippet}
</Modal>
