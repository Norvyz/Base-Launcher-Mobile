# Base Launcher Mobile

Launcher para **Minecraft Bedrock en Android** con la misma identidad visual de
[Base Launcher](https://github.com/Norvyz/Base-Launcher): paneles de vidrio,
acento configurable, tipografía pixel y actualizaciones desde GitHub.

## Qué hace

- Abre Minecraft Bedrock y detecta la versión instalada.
- Lista, importa, exporta y elimina mundos (`.mcworld`, `.mctemplate`).
- Instala y elimina addons (`.mcpack`, `.mcaddon`): texturas, comportamientos y skins.
- Consulta servidores Bedrock con un ping RakNet y entra con un toque.
- Añade servidores a la lista del juego con enlaces `minecraft://`.
- 14 temas, modo claro/oscuro y animaciones opcionales.
- Botón de actualización que descarga e instala el APK publicado en GitHub.

## Desarrollo

```bash
npm install
npm run dev        # vista web de demostración
npm run build      # interfaz de producción
npm run sync       # build + cap sync android
```

Para probar en un teléfono:

```bash
npm run sync
cd android
gradlew.bat assembleDebug
```

El APK de depuración queda en `android/app/build/outputs/apk/debug/`.

## Base de datos de Minecraft

Base Launcher busca la carpeta `com.mojang` en dos ubicaciones:

- `/storage/emulated/0/games/com.mojang` (almacenamiento **Externo** de Minecraft)
- `/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang`
  (ruta privada de la app)

Android 11 o superior no permite leer la ruta privada de otra app, así que el
launcher guía al usuario a abrir Minecraft y poner el almacenamiento en
**Externo**. Después pide el permiso especial **Acceso a todos los archivos**.

## Publicar un APK

Las releases se compilan y firman en GitHub Actions. Consulta
[`docs/RELEASING.md`](docs/RELEASING.md) para los secretos y el proceso de
etiquetas.

## Estructura

```
src/                 interfaz Svelte 5
  lib/screens/       Inicio, Mundos, Addons, Servidores, Ajustes
  lib/native.js      puente con el plugin nativo y datos de demostración
android/             proyecto Capacitor
  app/src/main/java/com/baselauncher/mobile/BedrockPlugin.java
.github/workflows/   compilación y firma del APK
```

## Aviso legal

Base Launcher Mobile es un proyecto independiente: no está afiliado a Mojang ni
a Microsoft. No incluye, distribuye ni desbloquea Minecraft. Necesitas una copia
legítima de Minecraft Bedrock. Los mundos, addons y servidores son contenido
del usuario y de sus respectivos autores.
