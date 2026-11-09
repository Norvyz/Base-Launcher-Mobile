// Paletas compartidas con el launcher de escritorio.
// a/b/c = acento + degradado en modo oscuro; la/lb/lc = modo claro.
export const themes = {
  base: { name: 'Base', a: '#44e860', b: '#1b1138', c: '#0a0716', la: '#1c9a3a', lb: '#e0f8e6', lc: '#cdeed6' },
  atardecer: { name: 'Atardecer', a: '#ffb454', b: '#2b1d3a', c: '#0f1224', la: '#d9781a', lb: '#ffe6cf', lc: '#f6d3e0' },
  cueva: { name: 'Cueva', a: '#5fd0c0', b: '#10303a', c: '#0a1118', la: '#0f8f80', lb: '#d8f3ef', lc: '#bfe3ee' },
  nether: { name: 'Nether', a: '#ff6a4d', b: '#3a1216', c: '#14090b', la: '#d63a1e', lb: '#ffe0d8', lc: '#f3c3b8' },
  end: { name: 'End', a: '#c9a7ff', b: '#241638', c: '#0c0816', la: '#7b4fd1', lb: '#eadfff', lc: '#d5c7f2' },
  bosque: { name: 'Bosque', a: '#7ed957', b: '#12331c', c: '#08130b', la: '#2f8f1f', lb: '#e0f5d4', lc: '#c6e8b8' },
  oceano: { name: 'Océano', a: '#4db8ff', b: '#0f2c4d', c: '#070f1e', la: '#1577c4', lb: '#d6ecff', lc: '#b9d8f5' },
  cereza: { name: 'Cerezo', a: '#ff7aa8', b: '#3a1230', c: '#130812', la: '#d03a72', lb: '#ffe0ec', lc: '#f4c2d6' },
  desierto: { name: 'Desierto', a: '#f2c35b', b: '#3d2c14', c: '#14100a', la: '#b07a12', lb: '#fff0cc', lc: '#f1dba6' },
  hielo: { name: 'Hielo', a: '#9fe3ff', b: '#12303d', c: '#0a131a', la: '#2a8db5', lb: '#e3f6ff', lc: '#c8e6f3' },
  esmeralda: { name: 'Esmeralda', a: '#3ee0a0', b: '#0e3a2e', c: '#07130f', la: '#12906a', lb: '#d6f5ea', lc: '#b8e5d3' },
  lava: { name: 'Lava', a: '#ffa31a', b: '#3a1d08', c: '#120a05', la: '#cc6a00', lb: '#ffe8cc', lc: '#f5cfa6' },
  medianoche: { name: 'Medianoche', a: '#8aa4ff', b: '#1a2140', c: '#090b16', la: '#3f5bd1', lb: '#e3e8ff', lc: '#c9d3f5' },
  grafito: { name: 'Grafito', a: '#dcdce2', b: '#25272e', c: '#0d0e12', la: '#3a3f4d', lb: '#eceef2', lc: '#d3d7df' }
};

export const themeList = Object.entries(themes).map(([id, t]) => ({ id, ...t }));
