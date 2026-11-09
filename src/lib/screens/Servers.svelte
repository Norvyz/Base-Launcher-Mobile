<script>
  import { onMount } from 'svelte';
  import Icon from '../components/Icon.svelte';
  import Modal from '../components/Modal.svelte';
  import { featuredServers } from '../servers.js';
  import { initials } from '../native.js';
  import { pings, pingServer, joinServer, addToGame } from '../launcher.svelte.js';
  import { settings, addCustomServer, removeCustomServer, toast, tap } from '../store.svelte.js';

  let tab = $state('featured');
  let search = $state('');
  let formOpen = $state(false);
  let form = $state({ name: '', host: '', port: '19132' });

  onMount(() => {
    featuredServers.forEach((s) => pingServer(s.host, s.port));
  });

  const list = $derived(
    (tab === 'featured' ? featuredServers : settings.customServers).filter((s) => {
      const q = search.trim().toLowerCase();
      if (!q) return true;
      return (
        String(s.name || '').toLowerCase().includes(q) ||
        String(s.host || '').toLowerCase().includes(q) ||
        String(s.tag || '').toLowerCase().includes(q)
      );
    })
  );

  function pingAll() {
    tap();
    const base = tab === 'featured' ? featuredServers : settings.customServers;
    base.forEach((s) => pingServer(s.host, s.port));
  }

  function colorOf(s) {
    if (s.color) return s.color;
    let h = 0;
    for (const c of String(s.name || s.host || 'x')) h = (h * 31 + c.charCodeAt(0)) % 360;
    return `hsl(${h} 62% 46%)`;
  }

  function openForm() {
    form = { name: '', host: '', port: '19132' };
    formOpen = true;
    tap();
  }

  function saveServer() {
    const host = form.host
      .trim()
      .replace(/^https?:\/\//i, '')
      .replace(/\/.*$/, '')
      .replace(/\s+/g, '');
    const port = Number(form.port) || 19132;
    if (!host) {
      toast('Escribe la IP o el dominio del servidor', 'bad');
      return;
    }
    if (port < 1 || port > 65535) {
      toast('El puerto debe estar entre 1 y 65535', 'bad');
      return;
    }
    const name = form.name.trim() || host;
    if (!addCustomServer({ name, host, port })) {
      toast('Ese servidor ya está en la lista');
      return;
    }
    formOpen = false;
    tab = 'custom';
    toast('Servidor añadido', 'ok');
  }

  function removeServer(server) {
    removeCustomServer(server.id);
    toast('Servidor quitado');
  }

  async function copyIp(s) {
    const text = `${s.host}:${s.port}`;
    try {
      await navigator.clipboard.writeText(text);
      toast('IP copiada', 'ok');
    } catch {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      const ok = document.execCommand('copy');
      ta.remove();
      toast(ok ? 'IP copiada' : 'No se pudo copiar', ok ? 'ok' : 'bad');
    }
  }
</script>

<div class="screen">
  <div class="screen-head">
    <div class="titles">
      <h1 class="screen-title">Servidores</h1>
      <p class="screen-sub">Entra a servidores Bedrock con un toque</p>
    </div>
    <div class="head-actions">
      <button class="icon-btn" onclick={pingAll} aria-label="Comprobar estado"><Icon name="refresh" size={18} /></button>
      <button class="icon-btn accent" onclick={openForm} aria-label="Añadir servidor"><Icon name="plus" size={18} /></button>
    </div>
  </div>

  <div class="segment">
    <button class:on={tab === 'featured'} class:accent={tab === 'featured'} onclick={() => (tab = 'featured')}>
      Destacados <span class="badge-count">{featuredServers.length}</span>
    </button>
    <button class:on={tab === 'custom'} class:accent={tab === 'custom'} onclick={() => (tab = 'custom')}>
      Tus servidores <span class="badge-count">{settings.customServers.length}</span>
    </button>
  </div>

  <div class="search">
    <Icon name="search" size={17} />
    <input bind:value={search} placeholder="Buscar servidor…" aria-label="Buscar servidor" />
    {#if search}
      <button onclick={() => (search = '')} aria-label="Limpiar búsqueda"><Icon name="close" size={13} /></button>
    {/if}
  </div>

  {#if !list.length}
    <div class="empty">
      <span class="ei"><Icon name="server" size={24} /></span>
      <b>{search ? 'Sin resultados' : 'Aún no tienes servidores'}</b>
      <p>
        {search
          ? 'Prueba con otro nombre o IP.'
          : 'Añade la IP de un servidor Bedrock o de tu mundo en LAN. También puedes entrar directo desde la lista de destacados.'}
      </p>
      {#if !search}
        <button class="btn primary" onclick={openForm}><Icon name="plus" size={15} /> Añadir servidor</button>
      {/if}
    </div>
  {:else}
    <div class="list">
      {#each list as s (s.id || `${s.host}:${s.port}`)}
        {@const ping = pings[`${s.host}:${s.port}`]}
        <article class="server-card glass">
          <div class="server-top">
            <div class="thumb sm" style="background:linear-gradient(150deg,{colorOf(s)},#101322)">
              {initials(s.name)}
            </div>
            <div class="info">
              <h3>{s.name}</h3>
              <p>{s.host}:{s.port}</p>
              <div class="chips">
                {#if s.tag}<span class="chip">{s.tag}</span>{/if}
                {#if s.custom}<span class="chip accent">Tu servidor</span>{/if}
                {#if ping?.loading}
                  <span class="chip"><Icon name="clock" size={10} /> Comprobando…</span>
                {:else if ping?.online}
                  <span class="chip ok"><Icon name="users" size={10} /> {ping.players}/{ping.maxPlayers}</span>
                  <span class="chip ok">{ping.latency} ms</span>
                {:else if ping}
                  <span class="chip bad">Sin conexión</span>
                {/if}
              </div>
            </div>
          </div>

          <div class="server-actions">
            <button class="btn sm primary" onclick={() => joinServer(s.host, s.port, s.name)}>
              <Icon name="play" size={13} stroke={0} /> Entrar
            </button>
            <button class="btn sm" onclick={() => copyIp(s)}><Icon name="copy" size={13} /> Copiar</button>
            <button class="btn sm" onclick={() => addToGame(s.name, s.host, s.port)}>
              <Icon name="plus" size={13} /> Añadir
            </button>
            {#if s.custom}
              <button class="icon-btn sm danger" onclick={() => removeServer(s)} aria-label="Quitar servidor">
                <Icon name="trash" size={14} />
              </button>
            {/if}
          </div>
        </article>
      {/each}
    </div>
  {/if}

  <p class="legal">
    Los servidores destacados son comunidades independientes, no están afiliados a Mojang ni a Base
    Launcher. El estado se consulta directo al servidor desde tu teléfono; puede aparecer sin
    conexión si el servidor está caído o bloquea la consulta.
  </p>
</div>

<Modal
  open={formOpen}
  title="Añadir servidor"
  subtitle="Servidor Bedrock o mundo en LAN"
  onclose={() => (formOpen = false)}
>
  <div class="field">
    <label for="srv-name">Nombre</label>
    <input id="srv-name" bind:value={form.name} placeholder="Mi servidor" maxlength="40" />
  </div>
  <div class="field">
    <label for="srv-host">IP o dominio</label>
    <input id="srv-host" bind:value={form.host} placeholder="play.ejemplo.net" autocomplete="off" autocapitalize="off" spellcheck="false" />
  </div>
  <div class="field">
    <label for="srv-port">Puerto</label>
    <input id="srv-port" bind:value={form.port} placeholder="19132" inputmode="numeric" type="number" min="1" max="65535" />
  </div>
  {#snippet footer()}
    <button class="btn wide" onclick={() => (formOpen = false)}>Cancelar</button>
    <button class="btn primary wide" onclick={saveServer}><Icon name="plus" size={15} /> Guardar</button>
  {/snippet}
</Modal>
