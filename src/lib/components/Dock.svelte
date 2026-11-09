<script>
  import Icon from './Icon.svelte';
  import { ui, navigate, tap } from '../store.svelte.js';

  const items = [
    { id: 'home', label: 'Inicio', icon: 'home' },
    { id: 'worlds', label: 'Mundos', icon: 'globe' },
    { id: 'addons', label: 'Addons', icon: 'package' },
    { id: 'servers', label: 'Servidores', icon: 'server' },
    { id: 'settings', label: 'Ajustes', icon: 'settings' }
  ];

  function go(id) {
    if (ui.screen !== id) {
      navigate(id);
      tap();
    }
  }
</script>

<nav class="dock" aria-label="Navegación principal">
  {#each items as it (it.id)}
    <button
      class="dock-btn"
      class:on={ui.screen === it.id}
      onclick={() => go(it.id)}
      aria-current={ui.screen === it.id ? 'page' : undefined}
    >
      <Icon name={it.icon} size={20} />
      <span class="dock-label">{it.label}</span>
    </button>
  {/each}
</nav>
