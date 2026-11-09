<script>
  import Icon from './Icon.svelte';
  import { ui } from '../store.svelte.js';
</script>

{#if ui.toast}
  {#key ui.toast.id}
    <div class="toast" class:ok={ui.toast.kind === 'ok'} class:bad={ui.toast.kind === 'bad'} role="status">
      <Icon name={ui.toast.kind === 'ok' ? 'check' : ui.toast.kind === 'bad' ? 'alert' : 'info'} size={17} />
      <span>{ui.toast.message}</span>
    </div>
  {/key}
{/if}
