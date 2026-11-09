<script>
  import { onMount } from 'svelte';
  import Icon from '../components/Icon.svelte';
  import Modal from '../components/Modal.svelte';
  import { formatBytes, formatDate } from '../native.js';
  import {
    worlds,
    storage,
    refreshWorlds,
    refreshStorage,
    importFiles,
    deleteWorld,
    exportWorld,
    requestStorage
  } from '../launcher.svelte.js';
  import { navigate } from '../store.svelte.js';

  let search = $state('');
  let confirmWorld = $state(null);
  let busy = $state('');

  onMount(() => {
    refreshWorlds();
    refreshStorage();
  });

  const filtered = $derived(
    worlds.items.filter(
      (w) => !search.trim() || String(w.title || '').toLowerCase().includes(search.trim().toLowerCase())
    )
  );

  const hasSearch = $derived(worlds.items.length > 4);

  function hue(name) {
    let h = 0;
    for (const c of String(name || 'x')) h = (h * 31 + c.charCodeAt(0)) % 360;
    return h;
  }

  function gradient(name) {
    const h = hue(name);
    return `linear-gradient(150deg, hsl(${h} 62% 46%), hsl(${h} 55% 15%))`;
  }

  async function reload() {
    await refreshWorlds(true);
    await refreshStorage(true);
  }

  async function doImport() {
    await importFiles();
  }

  async function doExport(world) {
    busy = world.name;
    await exportWorld(world.name);
    busy = '';
  }

  async function doDelete() {
    const w = confirmWorld;
    confirmWorld = null;
    if (!w) return;
    busy = w.name;
    await deleteWorld(w.name);
    busy = '';
  }
</script>

<div class="screen">
  <div class="screen-head">
    <div class="titles">
      <h1 class="screen-title">Mundos</h1>
      <p class="screen-sub">
        {worlds.loading ? 'Leyendo mundos…' : `${worlds.items.length} mundos${storage.external ? ' · almacenamiento externo' : ''}`}
      </p>
    </div>
    <div class="head-actions">
      <button
        class="icon-btn"
        class:spin={worlds.loading}
        onclick={reload}
        disabled={worlds.loading}
        aria-label="Actualizar"
      >
        <Icon name="refresh" size={18} />
      </button>
      <button class="btn primary sm" onclick={doImport} style="height:40px">
        <Icon name="plus" size={15} /> Importar
      </button>
    </div>
  </div>

  {#if !storage.loading && !storage.granted}
    <div class="empty">
      <span class="ei"><Icon name="shield" size={24} /></span>
      <b>Acceso a archivos pendiente</b>
      <p>
        Android 11 o superior exige el permiso de <b>Acceso a todos los archivos</b> para leer los
        mundos de Minecraft. Es un permiso especial porque el APK se instala por fuera de Play Store.
      </p>
      <button class="btn primary" onclick={requestStorage}><Icon name="shield" size={15} /> Dar acceso</button>
    </div>
  {:else if !worlds.loading && !worlds.readable}
    <div class="empty">
      <span class="ei"><Icon name="folder" size={24} /></span>
      <b>No encontramos los mundos</b>
      <p>
        Abre Minecraft, entra a <b>Ajustes → Almacenamiento</b> y elige <b>Externo</b>. Después vuelve
        aquí y pulsa actualizar.
      </p>
      <div style="display:flex;gap:8px;flex-wrap:wrap;justify-content:center">
        <button class="btn primary" onclick={reload}><Icon name="refresh" size={15} /> Reintentar</button>
        <button class="btn" onclick={() => navigate('settings')}>Cómo activarlo</button>
      </div>
    </div>
  {:else}
    {#if hasSearch}
      <div class="search">
        <Icon name="search" size={17} />
        <input bind:value={search} placeholder="Buscar mundo…" aria-label="Buscar mundo" />
        {#if search}
          <button onclick={() => (search = '')} aria-label="Limpiar búsqueda">
            <Icon name="close" size={13} />
          </button>
        {/if}
      </div>
    {/if}

    {#if worlds.loading}
      <div class="list">
        {#each [1, 2, 3] as n (n)}
          <div class="sk-row">
            <div class="sk-thumb skeleton"></div>
            <div class="sk-lines">
              <div class="sk-line w60 skeleton"></div>
              <div class="sk-line w35 skeleton"></div>
            </div>
          </div>
        {/each}
      </div>
    {:else if worlds.error}
      <div class="empty">
        <span class="ei"><Icon name="alert" size={24} /></span>
        <b>No se pudieron leer los mundos</b>
        <p>{worlds.error}</p>
        <button class="btn" onclick={reload}><Icon name="refresh" size={15} /> Reintentar</button>
      </div>
    {:else if !filtered.length}
      <div class="empty">
        <span class="ei"><Icon name="globe" size={24} /></span>
        <b>{search ? 'Sin resultados' : 'Todavía no hay mundos'}</b>
        <p>
          {search
            ? 'Prueba con otro nombre.'
            : 'Importa un archivo .mcworld y aparecerá aquí. También puedes exportar tus mundos como copia de seguridad.'}
        </p>
        {#if !search}
          <button class="btn primary" onclick={doImport}><Icon name="upload" size={15} /> Importar mundo</button>
        {/if}
      </div>
    {:else}
      <div class="list">
        {#each filtered as w (w.name)}
          <article class="row">
            <div class="thumb" style="background:{w.icon ? 'none' : gradient(w.name)}">
              {#if w.icon}
                <img src={w.icon} alt="" />
              {:else}
                {String(w.title || '?').slice(0, 2).toUpperCase()}
              {/if}
            </div>
            <div class="info">
              <h3>{w.title || w.name}</h3>
              <p>{formatBytes(w.size)} · {formatDate(w.modified)}</p>
              <div class="chips">
                <span class="chip accent"><Icon name="globe" size={10} /> Mundo</span>
              </div>
            </div>
            <div class="actions">
              <button
                class="icon-btn sm accent"
                onclick={() => doExport(w)}
                disabled={busy === w.name}
                aria-label="Exportar {w.title}"
                title="Exportar copia"
              >
                <Icon name="download" size={15} />
              </button>
              <button
                class="icon-btn sm danger"
                onclick={() => (confirmWorld = w)}
                disabled={busy === w.name}
                aria-label="Eliminar {w.title}"
                title="Eliminar"
              >
                <Icon name="trash" size={15} />
              </button>
            </div>
          </article>
        {/each}
      </div>
    {/if}

    {#if worlds.readable}
      <p class="legal">
        Ubicación: {worlds.root}/minecraftWorlds · Exportar crea un archivo .mcworld que puedes
        compartir o guardar.
      </p>
    {/if}
  {/if}
</div>

<Modal
  open={!!confirmWorld}
  title="Eliminar mundo"
  subtitle={confirmWorld?.title || ''}
  onclose={() => (confirmWorld = null)}
>
  <p class="legal">
    Se borrará la carpeta del mundo y no se puede deshacer. Si quieres conservarlo, expórtalo antes
    como copia de seguridad.
  </p>
  {#snippet footer()}
    <button class="btn wide" onclick={() => (confirmWorld = null)}>Cancelar</button>
    <button class="btn danger wide" onclick={doDelete}><Icon name="trash" size={15} /> Eliminar</button>
  {/snippet}
</Modal>
