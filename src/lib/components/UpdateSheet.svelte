<script>
  import Modal from './Modal.svelte';
  import Icon from './Icon.svelte';
  import { updates, installUpdate, openReleasePage, checkUpdates } from '../updates.svelte.js';
  import { ui } from '../store.svelte.js';

  const close = () => (ui.updateOpen = false);

  function dateText(iso) {
    if (!iso) return '';
    try {
      return new Date(iso).toLocaleDateString('es', { day: '2-digit', month: 'long', year: 'numeric' });
    } catch {
      return '';
    }
  }

  async function recheck() {
    await checkUpdates(true);
  }
</script>

<Modal
  open={ui.updateOpen}
  title={updates.available ? 'Actualización disponible' : 'Actualizaciones'}
  subtitle={updates.available
    ? 'Una versión nueva de Base Launcher Mobile está lista.'
    : 'Comprueba si hay una versión nueva del launcher.'}
  onclose={close}
>
  <div class="update-stats">
    <div class="v">
      <small>Instalada</small>
      <b>{updates.current}</b>
    </div>
    <div class="arrow"><Icon name="chevron" size={20} /></div>
    <div class="v">
      <small>Última</small>
      <b>{updates.checking ? '…' : updates.latest || updates.current}</b>
    </div>
  </div>

  {#if updates.error}
    <div class="notice">
      <div class="ni"><Icon name="alert" size={18} /></div>
      <div class="nc">
        <b>No se pudo comprobar</b>
        <p>{updates.error}</p>
        <div class="na">
          <button class="btn sm" onclick={recheck} disabled={updates.checking}>
            <Icon name="refresh" size={14} /> Reintentar
          </button>
        </div>
      </div>
    </div>
  {/if}

  {#if updates.available}
    {#if updates.notes}
      <div class="notes">{updates.notes}</div>
    {/if}

    {#if updates.downloading}
      <div class="progress" class:indeterminate={updates.percent < 0}>
        <i style="width:{updates.percent >= 0 ? updates.percent : 38}%"></i>
      </div>
      <p class="legal" style="text-align:center">
        {updates.percent >= 0 ? `Descargando el APK… ${updates.percent}%` : 'Descargando el APK…'}
      </p>
    {/if}

    {#if updates.needsPermission}
      <div class="notice">
        <div class="ni"><Icon name="shield" size={18} /></div>
        <div class="nc">
          <b>Falta permitir la instalación</b>
          <p>
            Android abrió los ajustes. Activa <b>Permitir desde esta fuente</b> para Base Launcher y
            vuelve a pulsar el botón.
          </p>
        </div>
      </div>
    {/if}
  {:else if !updates.checking && !updates.error}
    <div class="notice update">
      <div class="ni"><Icon name="check" size={18} /></div>
      <div class="nc">
        <b>Estás al día</b>
        <p>No hay versiones nuevas publicadas en GitHub.</p>
        <div class="na">
          <button class="btn sm" onclick={recheck} disabled={updates.checking}>
            <Icon name="refresh" size={14} /> Comprobar de nuevo
          </button>
        </div>
      </div>
    </div>
  {/if}

  {#if dateText(updates.publishedAt) && updates.available}
    <p class="legal">Publicada el {dateText(updates.publishedAt)}</p>
  {/if}

  {#snippet footer()}
    <button class="btn wide" onclick={close}>Cerrar</button>
    {#if updates.available}
      <button class="btn primary wide" onclick={installUpdate} disabled={updates.downloading}>
        <Icon name={updates.downloading ? 'clock' : 'download'} size={16} />
        {updates.downloading ? 'Descargando…' : 'Descargar e instalar'}
      </button>
    {:else}
      <button class="btn primary wide" onclick={openReleasePage}>
        <Icon name="external" size={16} /> Ver releases
      </button>
    {/if}
  {/snippet}
</Modal>
