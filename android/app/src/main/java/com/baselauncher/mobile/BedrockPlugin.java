package com.baselauncher.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Puente nativo entre la interfaz Svelte y Android.
 *
 * Se encarga de abrir Minecraft Bedrock, leer/escribir la carpeta com.mojang,
 * importar y exportar mundos y packs, consultar servidores por RakNet y
 * descargar/instalar los APK publicados en GitHub.
 */
@CapacitorPlugin(
    name = "Bedrock",
    permissions = {
        @Permission(
            alias = "storage",
            strings = { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE }
        )
    }
)
public class BedrockPlugin extends Plugin {

    private static final String BEDROCK_PACKAGE = "com.mojang.minecraftpe";
    private static final String WORLD_FOLDER = "minecraftWorlds";
    private static final int MAX_ICON_BYTES = 180 * 1024;

    private static final byte[] RAKNET_MAGIC = new byte[] {
        (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00,
        (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe,
        (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
        (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78
    };

    private File pendingExportFile;
    private String pendingExportName;

    // ------------------------------------------------------------------
    // Minecraft instalado
    // ------------------------------------------------------------------

    @PluginMethod
    public void getGameInfo(PluginCall call) {
        try {
            PackageManager pm = getContext().getPackageManager();
            PackageInfo info = pm.getPackageInfo(BEDROCK_PACKAGE, 0);
            long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            JSObject ret = new JSObject();
            ret.put("installed", true);
            ret.put("versionName", info.versionName == null ? "" : info.versionName);
            ret.put("versionCode", versionCode);
            call.resolve(ret);
        } catch (PackageManager.NameNotFoundException e) {
            JSObject ret = new JSObject();
            ret.put("installed", false);
            ret.put("versionName", "");
            ret.put("versionCode", 0);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("No se pudo consultar Minecraft: " + e.getMessage());
        }
    }

    @PluginMethod
    public void launchGame(PluginCall call) {
        try {
            Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(BEDROCK_PACKAGE);
            if (intent == null) {
                JSObject ret = new JSObject();
                ret.put("launched", false);
                call.resolve(ret);
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            JSObject ret = new JSObject();
            ret.put("launched", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("No se pudo abrir Minecraft: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Almacenamiento
    // ------------------------------------------------------------------

    @PluginMethod
    public void getStorageStatus(PluginCall call) {
        try {
            File ext = externalRoot();
            File app = appRoot();
            File readable = readRoot();

            JSObject ret = new JSObject();
            ret.put("granted", hasAllFiles());
            ret.put("manager", Build.VERSION.SDK_INT >= 30);
            ret.put("external", readable != null && readable.equals(ext));
            ret.put("readable", readable != null);
            ret.put("root", readable != null ? readable.getAbsolutePath() : preferredRoot().getAbsolutePath());

            JSONArray roots = new JSONArray();
            roots.put(rootInfo(ext, "external"));
            roots.put(rootInfo(app, "app"));
            ret.put("roots", roots);

            call.resolve(ret);
        } catch (Exception e) {
            call.reject("No se pudo comprobar el almacenamiento: " + e.getMessage());
        }
    }

    @PluginMethod
    public void requestStorage(PluginCall call) {
        if (hasAllFiles()) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            ret.put("opened", false);
            call.resolve(ret);
            return;
        }

        if (Build.VERSION.SDK_INT >= 30) {
            Activity activity = getActivity();
            if (activity == null) {
                call.reject("No hay actividad disponible");
                return;
            }
            try {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName())
                );
                activity.runOnUiThread(() -> startActivityForResult(call, intent, "storageSettingsResult"));
            } catch (Exception first) {
                try {
                    Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.runOnUiThread(() -> startActivityForResult(call, fallback, "storageSettingsResult"));
                } catch (Exception second) {
                    call.reject("No se pudieron abrir los ajustes de almacenamiento");
                }
            }
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            call.reject("No hay actividad disponible");
            return;
        }
        activity.runOnUiThread(() -> requestPermissionForAlias("storage", call, "storagePermsCallback"));
    }

    @ActivityCallback
    private void storageSettingsResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject ret = new JSObject();
        ret.put("granted", Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager());
        ret.put("opened", true);
        call.resolve(ret);
    }

    @PermissionCallback
    private void storagePermsCallback(PluginCall call) {
        if (call == null) return;
        JSObject ret = new JSObject();
        ret.put("granted", hasLegacyStorage());
        ret.put("opened", true);
        call.resolve(ret);
    }

    // ------------------------------------------------------------------
    // Mundos
    // ------------------------------------------------------------------

    @PluginMethod
    public void listWorlds(PluginCall call) {
        try {
            File root = readRoot();
            JSObject ret = new JSObject();
            if (root == null) {
                ret.put("readable", false);
                ret.put("root", preferredRoot().getAbsolutePath());
                ret.put("worlds", new JSONArray());
                call.resolve(ret);
                return;
            }

            File worldsDir = new File(root, WORLD_FOLDER);
            JSONArray worlds = new JSONArray();
            if (worldsDir.isDirectory()) {
                File[] dirs = worldsDir.listFiles();
                if (dirs != null) {
                    Arrays.sort(dirs, Comparator.comparingLong(File::lastModified).reversed());
                    for (File dir : dirs) {
                        if (!dir.isDirectory()) continue;
                        if (!new File(dir, "level.dat").isFile()) continue;

                        String title = readText(new File(dir, "levelname.txt"));
                        if (title.isEmpty()) title = dir.getName();

                        File icon = new File(dir, "world_icon.jpeg");
                        String mime = "image/jpeg";
                        if (!icon.isFile()) {
                            icon = new File(dir, "world_icon.jpg");
                        }
                        if (!icon.isFile()) {
                            icon = new File(dir, "world_icon.png");
                            mime = "image/png";
                        }

                        JSONObject world = new JSONObject();
                        world.put("name", dir.getName());
                        world.put("title", title);
                        world.put("size", dirSize(dir));
                        world.put("modified", dir.lastModified());
                        world.put("icon", iconDataUri(icon, mime));
                        worlds.put(world);
                    }
                }
            }

            ret.put("readable", worldsDir.isDirectory());
            ret.put("root", root.getAbsolutePath());
            ret.put("worlds", worlds);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("No se pudieron leer los mundos: " + e.getMessage());
        }
    }

    @PluginMethod
    public void deleteWorld(PluginCall call) {
        String name = call.getString("name");
        if (isBadName(name)) {
            call.reject("Nombre de mundo no válido");
            return;
        }
        File root = readRoot();
        if (root == null) {
            call.reject("No hay acceso a la carpeta de Minecraft");
            return;
        }
        File worldsDir = new File(root, WORLD_FOLDER);
        File world = new File(worldsDir, name);
        if (!isChildOf(world, worldsDir) || !world.exists()) {
            call.reject("El mundo no existe");
            return;
        }
        if (!deleteRecursive(world)) {
            call.reject("No se pudo eliminar el mundo");
            return;
        }
        JSObject ret = new JSObject();
        ret.put("deleted", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void exportWorld(PluginCall call) {
        String name = call.getString("name");
        if (isBadName(name)) {
            call.reject("Nombre de mundo no válido");
            return;
        }
        File root = readRoot();
        if (root == null) {
            call.reject("No hay acceso a la carpeta de Minecraft");
            return;
        }
        File world = new File(new File(root, WORLD_FOLDER), name);
        if (!world.isDirectory()) {
            call.reject("El mundo no existe");
            return;
        }
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("No hay actividad disponible");
            return;
        }

        try {
            File zip = buildWorldZip(world);
            pendingExportFile = zip;
            pendingExportName = name;

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, sanitizeName(name, "Mundo") + ".mcworld");
            activity.runOnUiThread(() -> startActivityForResult(call, intent, "exportWorldResult"));
        } catch (Exception e) {
            if (pendingExportFile != null) {
                pendingExportFile.delete();
                pendingExportFile = null;
            }
            call.reject("No se pudo preparar la copia: " + e.getMessage());
        }
    }

    @ActivityCallback
    private void exportWorldResult(PluginCall call, ActivityResult result) {
        File zip = pendingExportFile;
        String name = pendingExportName;
        pendingExportFile = null;
        pendingExportName = null;

        if (call == null) {
            if (zip != null) zip.delete();
            return;
        }

        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            if (zip != null) zip.delete();
            JSObject ret = new JSObject();
            ret.put("saved", false);
            ret.put("canceled", true);
            call.resolve(ret);
            return;
        }

        final Uri uri = result.getData().getData();
        final File source = zip;
        new Thread(() -> {
            try {
                if (source == null) throw new IOException("No se encontró la copia temporal");
                try (
                    InputStream in = new FileInputStream(source);
                    OutputStream out = getContext().getContentResolver().openOutputStream(uri)
                ) {
                    if (out == null) throw new IOException("No se pudo escribir el archivo");
                    copyStream(in, out);
                }
                source.delete();
                JSObject ret = new JSObject();
                ret.put("saved", true);
                ret.put("canceled", false);
                ret.put("name", name == null ? "" : name);
                call.resolve(ret);
            } catch (Exception e) {
                if (source != null) source.delete();
                call.reject("No se pudo guardar la copia: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void importFiles(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("No hay actividad disponible");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            activity.runOnUiThread(() -> startActivityForResult(call, intent, "importFilesResult"));
        } catch (Exception e) {
            call.reject("No se pudo abrir el selector de archivos");
        }
    }

    @ActivityCallback
    private void importFilesResult(PluginCall call, ActivityResult result) {
        if (call == null) return;

        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            JSObject ret = new JSObject();
            ret.put("canceled", true);
            ret.put("imported", new JSONArray());
            call.resolve(ret);
            return;
        }

        List<Uri> uris = new ArrayList<>();
        Intent data = result.getData();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        if (uris.isEmpty()) {
            JSObject ret = new JSObject();
            ret.put("canceled", true);
            ret.put("imported", new JSONArray());
            call.resolve(ret);
            return;
        }

        new Thread(() -> {
            File tempRoot = new File(getContext().getCacheDir(), "bl-import-" + UUID.randomUUID());
            tempRoot.mkdirs();
            try {
                JSONArray imported = new JSONArray();
                for (Uri uri : uris) {
                    String display = displayName(uri);
                    JSONObject item = new JSONObject();
                    item.put("file", display);
                    try {
                        File temp = copyUriToFile(uri, tempRoot, display);
                        ImportResult importedResult = importFile(temp, display, tempRoot);
                        item.put("ok", true);
                        item.put("kind", importedResult.kind);
                        item.put("message", importedResult.message);
                    } catch (Exception e) {
                        item.put("ok", false);
                        item.put("kind", "unknown");
                        item.put("message", e.getMessage() == null ? "No se pudo importar" : e.getMessage());
                    }
                    imported.put(item);
                }
                deleteRecursive(tempRoot);

                JSObject ret = new JSObject();
                ret.put("canceled", false);
                ret.put("imported", imported);
                call.resolve(ret);
            } catch (Exception e) {
                deleteRecursive(tempRoot);
                call.reject("No se pudo importar: " + e.getMessage());
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Addons
    // ------------------------------------------------------------------

    @PluginMethod
    public void listAddons(PluginCall call) {
        try {
            File root = readRoot();
            JSONArray addons = new JSONArray();
            if (root != null) {
                appendPacks(root, "resource_packs", "resources", addons);
                appendPacks(root, "behavior_packs", "behavior", addons);
                appendPacks(root, "skin_packs", "skin", addons);
            }
            JSObject ret = new JSObject();
            ret.put("readable", root != null);
            ret.put("root", root != null ? root.getAbsolutePath() : preferredRoot().getAbsolutePath());
            ret.put("addons", addons);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("No se pudieron leer los addons: " + e.getMessage());
        }
    }

    @PluginMethod
    public void deleteAddon(PluginCall call) {
        String name = call.getString("name");
        String type = call.getString("type", "resources");
        if (isBadName(name)) {
            call.reject("Nombre de addon no válido");
            return;
        }
        File root = readRoot();
        if (root == null) {
            call.reject("No hay acceso a la carpeta de Minecraft");
            return;
        }
        File parent = new File(root, packFolder(type));
        File pack = new File(parent, name);
        if (!isChildOf(pack, parent) || !pack.exists()) {
            call.reject("El addon no existe");
            return;
        }
        if (!deleteRecursive(pack)) {
            call.reject("No se pudo eliminar el addon");
            return;
        }
        JSObject ret = new JSObject();
        ret.put("deleted", true);
        call.resolve(ret);
    }

    // ------------------------------------------------------------------
    // Servidores RakNet
    // ------------------------------------------------------------------

    @PluginMethod
    public void pingServer(PluginCall call) {
        String host = call.getString("host");
        Integer port = call.getInt("port", 19132);
        if (host == null || host.trim().isEmpty()) {
            call.reject("Falta la dirección del servidor");
            return;
        }
        if (port == null || port < 1 || port > 65535) port = 19132;
        final int targetPort = port;

        new Thread(() -> {
            DatagramSocket socket = null;
            try {
                InetAddress address = InetAddress.getByName(host.trim());
                socket = new DatagramSocket();
                socket.setSoTimeout(3200);

                byte[] payload = new byte[33];
                payload[0] = 0x01;
                long stamp = System.currentTimeMillis();
                for (int i = 0; i < 8; i++) {
                    payload[1 + i] = (byte) ((stamp >> (8 * (7 - i))) & 0xFF);
                }
                System.arraycopy(RAKNET_MAGIC, 0, payload, 9, RAKNET_MAGIC.length);

                DatagramPacket request = new DatagramPacket(payload, payload.length, address, targetPort);
                byte[] buffer = new byte[4096];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);

                long start = System.nanoTime();
                socket.send(request);
                socket.receive(response);
                long latency = Math.max(0, (System.nanoTime() - start) / 1_000_000L);

                if ((buffer[0] & 0xFF) != 0x1C) {
                    throw new IOException("Respuesta inesperada del servidor");
                }
                int length = ((buffer[33] & 0xFF) << 8) | (buffer[34] & 0xFF);
                int available = Math.min(length, buffer.length - 35);
                String text = new String(buffer, 35, Math.max(0, available), StandardCharsets.UTF_8);
                String[] fields = text.split(";", -1);

                JSObject ret = new JSObject();
                ret.put("online", true);
                ret.put("motd", fields.length > 1 ? cleanMotd(fields[1]) : "");
                ret.put("protocol", fields.length > 2 ? parseInt(fields[2]) : 0);
                ret.put("version", fields.length > 3 ? fields[3] : "");
                ret.put("players", fields.length > 4 ? parseInt(fields[4]) : 0);
                ret.put("maxPlayers", fields.length > 5 ? parseInt(fields[5]) : 0);
                ret.put("latency", latency);
                call.resolve(ret);
            } catch (Exception e) {
                JSObject ret = new JSObject();
                ret.put("online", false);
                ret.put("error", e.getMessage() == null ? "Sin respuesta" : e.getMessage());
                call.resolve(ret);
            } finally {
                if (socket != null) socket.close();
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Enlaces externos
    // ------------------------------------------------------------------

    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url");
        String targetPackage = call.getString("package");
        if (url == null || url.trim().isEmpty()) {
            call.reject("Falta el enlace");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
        if (targetPackage != null && !targetPackage.trim().isEmpty()) {
            intent.setPackage(targetPackage.trim());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
            JSObject ret = new JSObject();
            ret.put("opened", true);
            call.resolve(ret);
        } catch (ActivityNotFoundException e) {
            if (intent.getPackage() != null) {
                intent.setPackage(null);
                try {
                    getContext().startActivity(intent);
                    JSObject ret = new JSObject();
                    ret.put("opened", true);
                    call.resolve(ret);
                    return;
                } catch (Exception ignored) {
                    // cae al rechazo de abajo
                }
            }
            call.reject("No hay ninguna app para abrir ese enlace");
        } catch (Exception e) {
            call.reject("No se pudo abrir el enlace: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Actualización del APK
    // ------------------------------------------------------------------

    @PluginMethod
    public void downloadAndInstall(PluginCall call) {
        final String url = call.getString("url");
        final String rawName = call.getString("name", "BaseLauncher-Mobile.apk");
        if (url == null || url.trim().isEmpty()) {
            call.reject("Falta la URL del APK");
            return;
        }

        new Thread(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), "updates");
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("No se pudo crear la carpeta de descargas");
                }
                String fileName = sanitizeName(rawName == null ? "BaseLauncher" : rawName.replaceAll("(?i)\\.apk$", ""), "BaseLauncher") + ".apk";
                File apk = new File(dir, fileName);
                if (apk.exists() && !apk.delete()) {
                    throw new IOException("No se pudo reemplazar la descarga anterior");
                }

                downloadFile(url, apk);

                if (Build.VERSION.SDK_INT >= 26 && !getContext().getPackageManager().canRequestPackageInstalls()) {
                    Intent settings = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getContext().getPackageName())
                    );
                    settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(settings);
                    JSObject ret = new JSObject();
                    ret.put("launched", false);
                    ret.put("needsPermission", true);
                    call.resolve(ret);
                    return;
                }

                Uri apkUri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getPackageName() + ".fileprovider",
                    apk
                );
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);

                JSObject ret = new JSObject();
                ret.put("launched", true);
                ret.put("needsPermission", false);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject(e.getMessage() == null ? "No se pudo descargar el APK" : e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String url, File destination) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(25_000);
            connection.setReadTimeout(35_000);
            connection.setRequestProperty("User-Agent", "BaseLauncher-Mobile");
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*");

            int code = connection.getResponseCode();
            if (code >= 400) throw new IOException("El servidor respondió " + code);

            long total = connection.getContentLengthLong();
            long done = 0;
            long lastEmit = 0;

            try (
                InputStream in = connection.getInputStream();
                FileOutputStream out = new FileOutputStream(destination)
            ) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    long now = System.currentTimeMillis();
                    if (now - lastEmit >= 180) {
                        lastEmit = now;
                        emitProgress(done, total);
                    }
                }
            }

            if (!destination.isFile() || destination.length() == 0) {
                throw new IOException("La descarga quedó vacía");
            }
            emitProgress(destination.length(), destination.length());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void emitProgress(long done, long total) {
        JSObject data = new JSObject();
        data.put("bytes", done);
        data.put("total", total > 0 ? total : -1);
        long percent = total > 0 ? Math.min(100L, Math.round(done * 100.0 / total)) : -1L;
        data.put("percent", percent);
        notifyListeners("downloadProgress", data);
    }

    // ------------------------------------------------------------------
    // Importación de archivos
    // ------------------------------------------------------------------

    private static class ImportResult {
        final String kind;
        final String message;

        ImportResult(String kind, String message) {
            this.kind = kind;
            this.message = message;
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
            // usa el último segmento de la URI
        }
        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment();
        }
        if (name == null || name.trim().isEmpty()) name = "archivo";
        return name;
    }

    private File copyUriToFile(Uri uri, File dir, String display) throws IOException {
        String cleaned = display.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (cleaned.isEmpty()) cleaned = "archivo";
        File out = new File(dir, UUID.randomUUID().toString().substring(0, 8) + "-" + cleaned);
        try (
            InputStream in = getContext().getContentResolver().openInputStream(uri);
            OutputStream os = new FileOutputStream(out)
        ) {
            if (in == null) throw new IOException("No se pudo leer el archivo");
            copyStream(in, os);
        }
        return out;
    }

    private ImportResult importFile(File file, String display, File tempRoot) throws Exception {
        if (!isZip(file)) {
            throw new Exception("Formato no compatible. Usa .mcworld, .mcpack o .mcaddon");
        }

        String lower = display.toLowerCase(Locale.ROOT);
        File extractDir = new File(tempRoot, "x-" + UUID.randomUUID());
        unzip(file, extractDir);

        if (lower.endsWith(".mcworld") || lower.endsWith(".mctemplate")) {
            return importWorldFromDir(extractDir, display);
        }

        if (lower.endsWith(".mcpack") || lower.endsWith(".mcaddon")) {
            int count = importPacksFromDir(extractDir);
            if (count == 0) throw new Exception("El pack no tiene un manifest.json válido");
            return new ImportResult("pack", count == 1 ? "Pack instalado" : count + " packs instalados");
        }

        if (findLevelDir(extractDir, 0) != null) {
            return importWorldFromDir(extractDir, display);
        }

        int count = importPacksFromDir(extractDir);
        if (count > 0) {
            return new ImportResult("pack", count == 1 ? "Pack instalado" : count + " packs instalados");
        }

        throw new Exception("No se reconoce el contenido del archivo");
    }

    private ImportResult importWorldFromDir(File extractDir, String display) throws Exception {
        File levelDir = findLevelDir(extractDir, 0);
        if (levelDir == null) throw new Exception("El archivo no parece un mundo de Minecraft");

        File worldsDir = new File(writeRoot(), WORLD_FOLDER);
        ensureDir(worldsDir);

        String title = readText(new File(levelDir, "levelname.txt"));
        if (title.isEmpty()) title = stripExtension(display);
        File destination = uniqueDir(worldsDir, sanitizeName(title, "Mundo"));
        copyDir(levelDir, destination);

        return new ImportResult("world", "Mundo \"" + destination.getName() + "\" importado");
    }

    private int importPacksFromDir(File extractDir) throws Exception {
        List<File> packs = new ArrayList<>();
        collectPackDirs(extractDir, packs);
        int count = 0;

        for (File packDir : packs) {
            String type = packType(packDir);
            File destinationDir = new File(writeRoot(), packFolder(type));
            ensureDir(destinationDir);

            String name = packTitle(packDir);
            if (name.isEmpty()) name = packDir.getName();
            File destination = uniqueDir(destinationDir, sanitizeName(name, packDir.getName()));
            copyDir(packDir, destination);
            count++;
        }

        return count;
    }

    // ------------------------------------------------------------------
    // Lectura de packs
    // ------------------------------------------------------------------

    private void appendPacks(File root, String folder, String type, JSONArray out) throws Exception {
        File dir = new File(root, folder);
        if (!dir.isDirectory()) return;
        File[] packs = dir.listFiles();
        if (packs == null) return;

        Arrays.sort(packs, Comparator.comparingLong(File::lastModified).reversed());
        for (File pack : packs) {
            if (!pack.isDirectory()) continue;
            File manifest = new File(pack, "manifest.json");
            if (!manifest.isFile()) continue;

            String title = pack.getName();
            String version = "";
            String uuid = "";
            try {
                JSONObject json = new JSONObject(readText(manifest));
                JSONObject header = json.optJSONObject("header");
                if (header != null) {
                    title = header.optString("name", title);
                    uuid = header.optString("uuid", "");
                    Object rawVersion = header.opt("version");
                    if (rawVersion instanceof JSONArray) {
                        JSONArray list = (JSONArray) rawVersion;
                        StringBuilder builder = new StringBuilder();
                        for (int i = 0; i < list.length(); i++) {
                            if (i > 0) builder.append('.');
                            builder.append(list.optInt(i));
                        }
                        version = builder.toString();
                    } else if (rawVersion != null) {
                        version = String.valueOf(rawVersion);
                    }
                }
            } catch (Exception ignored) {
                // manifest inválido: se muestra igual con los datos de la carpeta
            }

            JSONObject addon = new JSONObject();
            addon.put("type", type);
            addon.put("name", pack.getName());
            addon.put("title", title);
            addon.put("version", version);
            addon.put("uuid", uuid);
            addon.put("size", dirSize(pack));
            addon.put("modified", pack.lastModified());
            addon.put("icon", iconDataUri(new File(pack, "pack_icon.png"), "image/png"));
            out.put(addon);
        }
    }

    private String packTitle(File packDir) {
        try {
            JSONObject json = new JSONObject(readText(new File(packDir, "manifest.json")));
            JSONObject header = json.optJSONObject("header");
            if (header != null) return header.optString("name", "");
        } catch (Exception ignored) {
            // sin manifest legible
        }
        return "";
    }

    private String packType(File packDir) {
        try {
            JSONObject json = new JSONObject(readText(new File(packDir, "manifest.json")));
            JSONArray modules = json.optJSONArray("modules");
            if (modules != null) {
                for (int i = 0; i < modules.length(); i++) {
                    JSONObject module = modules.optJSONObject(i);
                    if (module == null) continue;
                    String type = module.optString("type", "");
                    if ("resources".equals(type)) return "resources";
                    if ("data".equals(type) || "script".equals(type) || "javascript".equals(type)) return "behavior";
                    if ("skin".equals(type)) return "skin";
                }
            }
        } catch (Exception ignored) {
            // se deduce por el nombre
        }

        String name = packDir.getName().toLowerCase(Locale.ROOT);
        if (name.contains("behavior")) return "behavior";
        if (name.contains("skin")) return "skin";
        return "resources";
    }

    private String packFolder(String type) {
        if ("behavior".equals(type)) return "behavior_packs";
        if ("skin".equals(type)) return "skin_packs";
        return "resource_packs";
    }

    private void collectPackDirs(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        if (new File(dir, "manifest.json").isFile()) {
            out.add(dir);
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectPackDirs(child, out);
        }
    }

    private File findLevelDir(File dir, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 4) return null;
        if (new File(dir, "level.dat").isFile()) return dir;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            File found = findLevelDir(child, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Utilidades de archivos
    // ------------------------------------------------------------------

    private File externalRoot() {
        return new File(Environment.getExternalStorageDirectory(), "games/com.mojang");
    }

    private File appRoot() {
        return new File(
            Environment.getExternalStorageDirectory(),
            "Android/data/" + BEDROCK_PACKAGE + "/files/games/com.mojang"
        );
    }

    private File preferredRoot() {
        File external = externalRoot();
        if (external.isDirectory()) return external;
        File app = appRoot();
        if (app.isDirectory()) return app;
        return external;
    }

    private File readRoot() {
        File external = externalRoot();
        if (external.isDirectory() && external.canRead()) return external;
        File app = appRoot();
        if (app.isDirectory() && app.canRead()) return app;
        return null;
    }

    private File writeRoot() throws IOException {
        boolean canManage = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
        File external = externalRoot();
        if (canManage) {
            ensureDir(external);
            if (external.canWrite()) return external;
        }
        File app = appRoot();
        if (app.isDirectory() && app.canWrite()) return app;
        ensureDir(external);
        return external;
    }

    private JSONObject rootInfo(File dir, String kind) throws Exception {
        JSONObject info = new JSONObject();
        info.put("path", dir.getAbsolutePath());
        info.put("kind", kind);
        info.put("exists", dir.isDirectory());
        info.put("readable", dir.isDirectory() && dir.canRead());
        info.put("writable", dir.isDirectory() && dir.canWrite());
        return info;
    }

    private boolean hasLegacyStorage() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAllFiles() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return hasLegacyStorage();
    }

    private boolean isBadName(String name) {
        if (name == null) return true;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..")) return true;
        return trimmed.contains("/") || trimmed.contains("\\");
    }

    private boolean isChildOf(File child, File parent) {
        try {
            String parentPath = parent.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            return childPath.startsWith(parentPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private void ensureDir(File dir) throws IOException {
        if (dir.isDirectory()) return;
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("No se pudo crear la carpeta " + dir.getName());
        }
    }

    private File uniqueDir(File parent, String name) {
        File destination = new File(parent, name);
        int index = 2;
        while (destination.exists()) {
            destination = new File(parent, name + " (" + index + ")");
            index++;
        }
        return destination;
    }

    private void copyDir(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            ensureDir(destination);
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) {
                copyDir(child, new File(destination, child.getName()));
            }
        } else {
            File parent = destination.getParentFile();
            if (parent != null) ensureDir(parent);
            try (
                InputStream in = new FileInputStream(source);
                OutputStream out = new FileOutputStream(destination)
            ) {
                copyStream(in, out);
            }
        }
    }

    private boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        return file.delete();
    }

    private long dirSize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += dirSize(child);
        }
        return total;
    }

    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }

    private String sanitizeName(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        value = value.replaceAll("^\\.+", "").trim();
        if (value.isEmpty()) value = fallback == null ? "Archivo" : fallback;
        if (value.length() > 64) value = value.substring(0, 64).trim();
        return value.isEmpty() ? "Archivo" : value;
    }

    private String stripExtension(String name) {
        if (name == null) return "Mundo";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String readText(File file) {
        if (file == null || !file.isFile()) return "";
        try (
            FileInputStream in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return text.replace("\u0000", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String iconDataUri(File file, String mime) {
        if (file == null || !file.isFile()) return "";
        long length = file.length();
        if (length <= 0 || length > MAX_ICON_BYTES) return "";
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) length];
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return "data:" + mime + ";base64," + Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isZip(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] head = new byte[4];
            if (in.read(head) != 4) return false;
            return head[0] == 'P' && head[1] == 'K';
        } catch (Exception e) {
            return false;
        }
    }

    private void unzip(File zipFile, File destinationDir) throws IOException {
        ensureDir(destinationDir);
        String basePath = destinationDir.getCanonicalPath();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File target = new File(destinationDir, entry.getName());
                String targetPath = target.getCanonicalPath();
                if (!targetPath.equals(basePath) && !targetPath.startsWith(basePath + File.separator)) {
                    zip.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    ensureDir(target);
                } else {
                    File parent = target.getParentFile();
                    if (parent != null) ensureDir(parent);
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        copyStream(zip, out);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private File buildWorldZip(File worldDir) throws IOException {
        File zipFile = new File(getContext().getCacheDir(), "bl-export-" + UUID.randomUUID() + ".mcworld");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipDirectory(worldDir, "", zip);
        } catch (IOException e) {
            zipFile.delete();
            throw e;
        }
        return zipFile;
    }

    private void zipDirectory(File dir, String prefix, ZipOutputStream zip) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String entryName = prefix + child.getName() + (child.isDirectory() ? "/" : "");
            zip.putNextEntry(new ZipEntry(entryName));
            if (child.isDirectory()) {
                zip.closeEntry();
                zipDirectory(child, entryName, zip);
            } else {
                try (FileInputStream in = new FileInputStream(child)) {
                    copyStream(in, zip);
                }
                zip.closeEntry();
            }
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String cleanMotd(String motd) {
        return motd.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }
}
