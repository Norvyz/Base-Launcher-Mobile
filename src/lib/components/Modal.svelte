<script>
  import Icon from './Icon.svelte';

  let { open = false, title = '', subtitle = '', onclose = () => {}, children, footer } = $props();
</script>

{#if open}
  <div
    class="overlay"
    role="presentation"
    onclick={(e) => {
      if (e.target === e.currentTarget) onclose();
    }}
  >
    <div class="sheet" role="dialog" aria-modal="true" aria-label={title}>
      <div class="sheet-grab"></div>
      <header class="sheet-head">
        <div>
          <h2>{title}</h2>
          {#if subtitle}<p>{subtitle}</p>{/if}
        </div>
        <button class="icon-btn sm" onclick={onclose} aria-label="Cerrar">
          <Icon name="close" size={15} />
        </button>
      </header>
      <div class="sheet-body">
        {@render children?.()}
      </div>
      {#if footer}
        <div class="sheet-foot">{@render footer()}</div>
      {/if}
    </div>
  </div>
{/if}
