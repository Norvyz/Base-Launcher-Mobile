# Publicar una actualización

Los APK se compilan y firman en GitHub Actions. El repositorio solo publica
APKs; no hay Play Store.

## Secretos del repositorio

En `Settings → Secrets and variables → Actions` hay que crear cuatro secretos:

| Secreto | Contenido |
| --- | --- |
| `KEYSTORE_B64` | El archivo `.jks` convertido a Base64 en una sola línea |
| `KEYSTORE_PASSWORD` | Contraseña del keystore |
| `KEY_ALIAS` | Alias de la clave dentro del keystore |
| `KEY_PASSWORD` | Contraseña de la clave |

Sin `KEYSTORE_B64` el workflow compila igual, pero firma con la clave de
depuración. Las etiquetas `v*` se niegan a publicar si falta la firma, para no
sacar un APK que después no se pueda actualizar.

## Generar el keystore (una sola vez)

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias baselauncher \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "TU_CONTRASEÑA" -keypass "TU_CONTRASEÑA" \
  -dname "CN=Base Launcher, OU=Mobile, O=Base Launcher, C=PE"
```

Convertirlo a Base64:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```

```bash
base64 -w 0 release.jks
```

Guarda el `.jks` y las contraseñas fuera del repositorio. Si se pierde el
keystore, Android no aceptará actualizaciones sobre la app ya instalada: habría
que desinstalar y volver a instalar.

## Subir una versión

1. Cambia `version` en `package.json` (por ejemplo `0.1.1`). Ese valor es el
   `versionName` del APK y el que compara el botón de actualización.
2. Haz commit y push a `main`.
3. Crea y sube la etiqueta:

```bash
git tag v0.1.1
git push origin v0.1.1
```

El workflow:

- compila la interfaz con Vite;
- sincroniza Capacitor;
- compila `assembleRelease` con el keystore de los secretos;
- usa `github.run_number` como `versionCode` (siempre crece);
- publica el APK como asset de la release `v0.1.1`.

El launcher consulta `https://api.github.com/repos/Norvyz/Base-Launcher-Mobile/releases`
y solo muestra el botón de actualización cuando encuentra una release con un
`.apk` y una versión mayor que la instalada.
