package com.example.pocketpythonide;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import org.pocketide.pack.v1.PackContract;

import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Discovery is metadata-only; enabling still requires explicit user approval and a live handshake. */
final class PackRegistry {
    static final String HOST_VERSION = "0.2.0-alpha.1";
    private final Context context;
    private Descriptor cachedEnabled;

    PackRegistry(Context context) { this.context = context.getApplicationContext(); }

    static final class Descriptor {
        final ComponentName component;
        final JSONObject manifest;
        final String certificate;
        final String apkSha256;
        final String permissions;
        final long firstInstallTime;
        final long lastUpdateTime;
        Descriptor(ComponentName component, JSONObject manifest, String certificate,
                String apkSha256, String permissions, long firstInstallTime,
                long lastUpdateTime) {
            this.component = component;
            this.manifest = manifest;
            this.certificate = certificate;
            this.apkSha256 = apkSha256;
            this.permissions = permissions;
            this.firstInstallTime = firstInstallTime;
            this.lastUpdateTime = lastUpdateTime;
        }
        String packId() { return manifest.optString("packId"); }
        String version() { return manifest.optString("version"); }
        boolean supports(String capability) {
            JSONArray values = manifest.optJSONArray("capabilities");
            if (values == null) return false;
            for (int i = 0; i < values.length(); i++)
                if (capability.equals(values.optString(i))) return true;
            return false;
        }
        boolean accepts(String filename) {
            JSONArray values = manifest.optJSONArray("extensions");
            if (values == null) return false;
            for (int i = 0; i < values.length(); i++)
                if (filename.toLowerCase(Locale.ROOT).endsWith(values.optString(i))) return true;
            return false;
        }
    }

    List<Descriptor> discover() {
        PackageManager pm = context.getPackageManager();
        List<Descriptor> found = new ArrayList<>();
        Intent intent = new Intent(PackContract.ACTION);
        for (ResolveInfo resolved : pm.queryIntentServices(intent, PackageManager.GET_META_DATA)) {
            try {
                ServiceInfo service = resolved.serviceInfo;
                if (service == null || !service.exported || service.metaData == null) continue;
                int raw = service.metaData.getInt(PackContract.MANIFEST_META_DATA);
                if (raw == 0) continue;
                Resources resources = pm.getResourcesForApplication(service.packageName);
                byte[] bytes;
                try (InputStream input = resources.openRawResource(raw)) {
                    bytes = readLimited(input, 65536);
                }
                JSONObject manifest = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                if (!valid(manifest, service)) continue;
                String warmup = manifest.optString("warmupActivity");
                if (!warmup.isEmpty()) {
                    // ComponentName pins the activity to this installed APK; Java package
                    // names need not share the APK's applicationId prefix.
                    if (!pm.getActivityInfo(new ComponentName(service.packageName, warmup),
                            0).exported) continue;
                }
                String certificate = certificate(pm, service.packageName);
                PackageInfo info = pm.getPackageInfo(service.packageName,
                        PackageManager.GET_PERMISSIONS);
                String permissions = info.requestedPermissions == null ? "无"
                        : String.join(", ", info.requestedPermissions);
                String apkSha256;
                try (InputStream installedApk = new FileInputStream(service.applicationInfo.sourceDir)) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] block = new byte[65536];
                    int read;
                    while ((read = installedApk.read(block)) != -1)
                        digest.update(block, 0, read);
                    apkSha256 = hex(digest.digest());
                }
                found.add(new Descriptor(new ComponentName(service.packageName, service.name),
                        manifest, certificate, apkSha256, permissions,
                        info.firstInstallTime, info.lastUpdateTime));
            } catch (Exception ignored) { /* Invalid or incomplete pack stays unavailable. */ }
        }
        return Collections.unmodifiableList(found);
    }

    Descriptor enabled() {
        String id = context.getSharedPreferences("language_packs", 0).getString("packId", "");
        if (id.isEmpty()) { cachedEnabled = null; return null; }
        if (cachedEnabled != null && id.equals(cachedEnabled.packId())) {
            try {
                PackageInfo installed = context.getPackageManager().getPackageInfo(id, 0);
                if (installed.firstInstallTime == cachedEnabled.firstInstallTime
                        && installed.lastUpdateTime == cachedEnabled.lastUpdateTime)
                    return cachedEnabled;
            } catch (PackageManager.NameNotFoundException ignored) { }
            cachedEnabled = null;
        }
        String fingerprint = context.getSharedPreferences("language_packs", 0)
                .getString("certificate", "");
        String apk = context.getSharedPreferences("language_packs", 0)
                .getString("apkSha256", "");
        long installed = context.getSharedPreferences("language_packs", 0)
                .getLong("firstInstallTime", -1);
        long updated = context.getSharedPreferences("language_packs", 0)
                .getLong("lastUpdateTime", -1);
        for (Descriptor candidate : discover()) {
            if (id.equals(candidate.packId()) && fingerprint.equals(candidate.certificate)
                    && apk.equals(candidate.apkSha256)
                    && installed == candidate.firstInstallTime
                    && updated == candidate.lastUpdateTime) {
                cachedEnabled = candidate;
                return candidate;
            }
        }
        if (!id.isEmpty()) disable();
        return null;
    }

    boolean verified(String capability) {
        Descriptor descriptor = enabled();
        if (descriptor == null) return false;
        String version = context.getSharedPreferences("language_packs", 0)
                .getString("validatedVersion", "");
        String abilities = context.getSharedPreferences("language_packs", 0)
                .getString("validatedCapabilities", "");
        return descriptor.version().equals(version)
                && abilities.contains("|" + capability + "|");
    }

    void enable(Descriptor descriptor, String validatedCapabilities) {
        JSONArray extensions = descriptor.manifest.optJSONArray("extensions");
        android.content.SharedPreferences known = context.getSharedPreferences("known_pack_files", 0);
        android.content.SharedPreferences.Editor files = known.edit();
        if (extensions != null) for (int i = 0; i < extensions.length(); i++)
            files.putBoolean(extensions.optString(i), true);
        files.apply();
        context.getSharedPreferences("language_packs", 0).edit()
                .putString("packId", descriptor.packId())
                .putString("certificate", descriptor.certificate)
                .putString("apkSha256", descriptor.apkSha256)
                .putLong("firstInstallTime", descriptor.firstInstallTime)
                .putLong("lastUpdateTime", descriptor.lastUpdateTime)
                .putString("validatedVersion", descriptor.version())
                .putString("validatedCapabilities", validatedCapabilities).apply();
        cachedEnabled = descriptor;
    }

    void disable() {
        cachedEnabled = null;
        context.getSharedPreferences("language_packs", 0).edit().clear().apply();
    }

    boolean knownExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && context.getSharedPreferences("known_pack_files", 0)
                .getBoolean(filename.substring(dot).toLowerCase(Locale.ROOT), false);
    }

    private static boolean valid(JSONObject item, ServiceInfo service) {
        if (item.optInt("schemaVersion") != 1
                || !service.packageName.equals(item.optString("packId"))
                || !service.name.equals(item.optString("entryService"))
                || item.optInt("minSdk", 999) > Build.VERSION.SDK_INT
                || !HOST_VERSION.equals(item.optString("minHostVersion"))) return false;
        if (item.optString("version").isEmpty()
                || !item.optString("source").startsWith("https://")
                || item.optString("license").isEmpty() || item.optJSONArray("dependencies") == null
                || item.optJSONArray("languageIds") == null
                || item.optJSONArray("languageIds").length() == 0
                || item.optJSONArray("extensions") == null
                || item.optJSONArray("extensions").length() == 0
                || item.optJSONObject("selfTests") == null) return false;
        String outputFormat = item.optString("workspaceOutput");
        if (!outputFormat.isEmpty() && !"changes-v1".equals(outputFormat)) return false;
        JSONArray extensions = item.optJSONArray("extensions");
        for (int i = 0; i < extensions.length(); i++)
            if (!extensions.optString(i).matches("\\.[a-z0-9_]+")) return false;
        JSONArray abis = item.optJSONArray("supportedAbis");
        JSONArray capabilities = item.optJSONArray("capabilities");
        if (abis == null || capabilities == null || capabilities.length() == 0) return false;
        boolean abiMatch = false;
        for (int i = 0; i < abis.length(); i++)
            for (String deviceAbi : Build.SUPPORTED_ABIS)
                if (deviceAbi.equals(abis.optString(i))) abiMatch = true;
        if (!abiMatch) return false;
        boolean initialize = false;
        boolean cancel = false;
        for (int i = 0; i < capabilities.length(); i++) {
            String ability = capabilities.optString(i);
            if (!("initialize".equals(ability) || "analyze".equals(ability)
                    || "complete".equals(ability) || "build".equals(ability)
                    || "run".equals(ability) || "cancel".equals(ability)
                    || "debug".equals(ability)
                    || "diagnostics".equals(ability))) return false;
            if (item.optJSONObject("selfTests").optJSONObject(ability) == null) return false;
            if ("debug".equals(ability)) {
                JSONObject adapter = item.optJSONObject("debugAdapter");
                if (adapter == null || !"dap-1".equals(adapter.optString("protocol"))
                        || !"binder-socketpair".equals(adapter.optString("transport"))
                        || !service.name.equals(adapter.optString("service"))) return false;
            }
            if ("initialize".equals(ability)) initialize = true;
            if ("cancel".equals(ability)) cancel = true;
        }
        return initialize && cancel;
    }

    private static byte[] readLimited(InputStream input, int limit) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] block = new byte[4096];
        int read;
        while ((read = input.read(block)) != -1) {
            if (output.size() + read > limit) throw new IllegalArgumentException("Manifest too large");
            output.write(block, 0, read);
        }
        return output.toByteArray();
    }

    private static String certificate(PackageManager pm, String packageName) throws Exception {
        android.content.pm.Signature signature;
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            signature = info.signingInfo.getApkContentsSigners()[0];
        } else {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            signature = info.signatures[0];
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
        return hex(digest);
    }

    private static String hex(byte[] digest) {
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
}
