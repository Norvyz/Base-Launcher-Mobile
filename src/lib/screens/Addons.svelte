<script>
  import { onMount } from 'svelte';
  import Icon from '../components/Icon.svelte';
  import Modal from '../components/Modal.svelte';
  import { formatBytes, formatDate } from '../native.js';
  import {
    addons,
    storage,
    refreshAddons,
    refreshStorage,
    importFiles,
    deleteAddon,
    requestStorage
  } from '../launcher.svelte.js';
  import { navigate } from '../store.svelte.js';

  const typeMeta = {
    resources: { label: 'Recursos', icon: 'palette' },
    behavior: { label: 'Comportamiento', icon: 'gamepad' },
    skin: { label: 'Skins', icon: 'users' }
  };

  let tab = $state('all');
  let search = $state('');
  let confirmPack = $state(null);
  let busy = $state('');

  onMount(() => {
    refreshAddons();
    refreshStorage();
  });

  const counts = $derived({
    all: addons.items.length,
    resources: addons.items.filter((a) => a.type === 'resources').length,
    behavior: addons.items.filter((a) => a.type === 'behavior').length,
    skin: addons.items.filter((a) => a.type === 'skin').length
  });

  const filtered = $derived(
    addons.items.filter((a) => {
      const okTab = tab === 'all' || a.type === tab;
      const q = search.trim().toLowerCase();
      const okSearch = !q || String(a.title || '').toLowerCase().includes(q) || String(a.name || '').toLowerCase().includes(q);
      return okTab && okSearch;
    })
  );

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
    await refreshAddons(true);
    await refreshStorage(true);
  }

  async function doImport() {
    await importFiles();
  }

  async function doDelete() {
    const p = confirmPack;
    confirmPack = null;
    if (!p) return;
    busy = p.name;
    await deleteAddon(p.type, p.name);
    busy = '';
  }
</script>

<div class="screen">
  <div class="screen-head">
    <div class="titles">
      <h1 class="screen-title">Addons</h1>
      <p class="screen-sub">
        {addons.loading ? 'Leyendo packs…' : `${addons.items.length} packs instalados`}
      </p>
    </div>
    <div class="head-actions">
      <button
        class="icon-btn"
        class:spin={addons.loading}
        onclick={reload}
        disabled={addons.loading}
        aria-label="Actualizar"
      >
        <Icon name="refresh" size={18} />
      </button>
      <button class="btn primary sm" onclick={doImport} style="height:40px">
        <Icon name="plus" size={15} /> Instalar
      </button>
    </div>
  </div>

  {#if !storage.loading && !storage.granted}
    <div class="empty">
      <span class="ei"><Icon name="shield" size={24} /></span>
      <b>Acceso a archivos pendiente</b>
      <p>
        Para instalar texturas, comportamientos y skins, Base Launcher necesita el permiso de
        <b>Acceso a todos los archivos</b>.
      </p>
      <button class="btn primary" onclick={requestStorage}><Icon name="shield" size={15} /> Dar acceso</button>
    </div>
  {:else}
    <div class="segment">
      <button class:on={tab === 'all'} class:accent={tab === 'all'} onclick={() => (tab = 'all')}>
        Todos <span class="badge-count">{counts.all}</span>
      </button>
      <button class:on={tab === 'resources'} class:accent={tab === 'resources'} onclick={() => (tab = 'resources')}>
        Recursos <span class="badge-count">{counts.resources}</span>
      </button>
      <button class:on={tab === 'behavior'} class:accent={tab === 'behavior'} onclick={() => (tab = 'behavior')}>
        Mobs <span class="badge-count">{counts.behavior}</span>
      </button>
      <button class:on={tab === 'skin'} class:accent={tab === 'skin'} onclick={() => (tab = 'skin')}>
        Skins <span class="badge-count">{counts.skin}</span>
      </button>
    </div>

    {#if addons.items.length > 4}
      <div class="search">
        <Icon name="search" size={17} />
        <input bind:value={search} placeholder="Buscar pack…" aria-label="Buscar pack" />
        {#if search}
          <button onclick={() => (search = '')} aria-label="Limpiar búsqueda">
            <Icon name="close" size={13} />
          </button>
        {/if}
      </div>
    {/if}

    {#if addons.loading}
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
    {:else if addons.error}
      <div class="empty">
        <span class="ei"><Icon name="alert" size={24} /></span>
        <b>No se pudieron leer los addons</b>
        <p>{addons.error}</p>
        <button class="btn" onclick={reload}><Icon name="refresh" size={15} /> Reintentar</button>
      </div>
    {:else if !filtered.length}
      <div class="empty">
        <span class="ei"><Icon name="package" size={24} /></span>
        <b>{search || tab !== 'all' ? 'Sin resultados' : 'Todavía no hay addons'}</b>
        <p>
          {search || tab !== 'all'
            ? 'Prueba con otro filtro o nombre.'
            : 'Instala archivos .mcpack o .mcaddon. Base Launcher detecta si son texturas, comportamientos o skins y los coloca en la carpeta correcta.'}
        </p>
        {#if !search && tab === 'all'}
          <button class="btn primary" onclick={doImport}><Icon name="upload" size={15} /> Instalar addon</button>
        {/if}
      </div>
    {:else}
      <div class="list">
        {#each filtered as a (a.type + ':' + a.name)}
          {@const meta = typeMeta[a.type] || typeMeta.resources}
          <article class="row">
            <div class="thumb" style="background:{a.icon ? 'none' : gradient(a.name)}">
              {#if a.icon}
                <img src={a.icon} alt="" />
              {:else}
                <Icon name={meta.icon} size={22} />
              {/if}
            </div>
            <div class="info">
              <h3>{a.title || a.name}</h3>
              <p>{formatBytes(a.size)} · {formatDate(a.modified)}</p>
              <div class="chips">
                <span class="chip accent"><Icon name={meta.icon} size={10} /> {meta.label}</span>
                {#if a.version}<span class="chip">v{a.version}</span>{/if}
              </div>
            </div>
            <div class="actions">
              <button
                class="icon-btn sm danger"
                onclick={() => (confirmPack = a)}
                disabled={busy === a.name}
                aria-label="Eliminar {a.title}"
                title="Eliminar"
              >
                <Icon name="trash" size={15} />
              </button>
            </div>
          </article>
        {/each}
      </div>
    {/if}

    <p class="legal">
      Formatos compatibles: .mcpack, .mcaddon y .mcworld. Los packs se copian a resource_packs,
      behavior_packs o skin_packs dentro de la carpeta de Minecraft.
    </p>
  {/if}
</div>

<Modal
  open={!!confirmPack}
  title="Eliminar addon"
  subtitle={confirmPack?.title || ''}
  onclose={() => (confirmPack = null)}
>
  <p class="legal">
    Se borrará el pack de la carpeta de Minecraft. No se puede deshacer.
  </p>
  {#snippet footer()}
    <button class="btn wide" onclick={() => (confirmPack = null)}>Cancelar</button>
    <button class="btn danger wide" onclick={doDelete}><Icon name="trash" size={15} /> Eliminar</button>
  {/snippet}
</Modal>
