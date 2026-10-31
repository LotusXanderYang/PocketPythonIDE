package com.example.pocketpythonide;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/** Lightweight editor host for explicitly enabled and verified API v1 packs. */
public final class IdeActivity extends WorkbenchActivity {
    private PackRegistry packs;
    private DebugPanel debugPanel;
    private final DebugLaunchTarget debugLaunchTarget = new DebugLaunchTarget();
    private DebugPanel debugger() {
        if (debugPanel == null) debugPanel = new DebugPanel(this);
        return debugPanel;
    }
    @Override protected View createDebugSidebar() { return debugger().sidebarView(); }
    @Override protected View createDebugControls() { return debugger().controlsView(); }
    @Override protected View createDebugPanel() {
        return debugger().view();
    }
    @Override protected boolean hasBreakpoint(String path, int line, String key) {
        return debugPanel != null && debugPanel.hasBreakpoint(path, line, key);
    }
    @Override protected int debugActiveLine(String path) {
        return debugPanel == null ? 0 : debugPanel.activeLine(path);
    }
    @Override protected void toggleBreakpoint(String path, int line, String source, String key) {
        if (debugPanel != null) debugPanel.toggle(path, line, source, key);
    }
    @Override protected boolean debugSessionActive() {
        return debugPanel != null && debugPanel.active();
    }
    @Override protected void clearDebugOutput() {
        if (debugPanel != null) debugPanel.clearOutput();
    }
    @Override protected void onEditorFileChanged(String path, String workspaceKey) {
        if (debugPanel != null) debugPanel.showWorkspaceBreakpoints(workspaceKey);
        debugLaunchTarget.fileShown(path, workspaceKey, isDebugSourceNavigation());
    }
    @Override protected String debugEntryForCurrent(String path, String workspaceKey) {
        return debugLaunchTarget.entryFor(path, workspaceKey);
    }
    private PackRegistry registry() {
        if (packs == null) packs = new PackRegistry(this);
        return packs;
    }
    private PackRegistry.Descriptor runnable() {
        PackRegistry.Descriptor pack = registry().enabled();
        return pack != null && registry().verified("run") ? pack : null;
    }

    @Override protected boolean hasRuntime() { return runnable() != null; }
    @Override protected boolean modernUi() { return true; }
    @Override protected boolean packWorkspace() { return true; }
    @Override protected boolean packWorkspaceWriteback() {
        PackRegistry.Descriptor pack = registry().enabled();
        return pack != null && "com.example.pocketpythonstd".equals(pack.packId())
                && "changes-v1".equals(pack.manifest.optString("workspaceOutput"))
                && registry().verified("run");
    }
    @Override protected String capabilitySummary() {
        PackRegistry.Descriptor pack = registry().enabled();
        if (pack == null || !pack.accepts(currentFilename())) return "仅编辑";
        if (registry().verified("run") && registry().verified("debug"))
            return "可运行·可调试";
        if (registry().verified("run")) return "可运行";
        if (registry().verified("analyze")) return "可检查";
        return "仅编辑";
    }
    @Override protected boolean canBuildFile(String name) {
        PackRegistry.Descriptor pack = registry().enabled();
        return pack != null && pack.accepts(name) && registry().verified("build");
    }
    @Override protected boolean canAnalyzeFile(String name) {
        PackRegistry.Descriptor pack = registry().enabled();
        return pack != null && pack.accepts(name) && registry().verified("analyze");
    }
    @Override protected boolean canCompleteFile(String name) {
        PackRegistry.Descriptor pack = registry().enabled();
        return pack != null && pack.accepts(name) && registry().verified("complete");
    }
    @Override protected boolean canDebugFile(String name) {
        PackRegistry.Descriptor pack = registry().enabled();
        return pack != null && pack.accepts(name) && registry().verified("debug")
                && "dap-1".equals(pack.manifest.optJSONObject("debugAdapter") == null
                        ? "" : pack.manifest.optJSONObject("debugAdapter")
                                .optString("protocol"));
    }
    @Override protected void launchDebugger(File root, String entry, String workspaceKey,
            boolean temporaryMirror) {
        if (!canDebugFile(entry)) {
            Toast.makeText(this, "此语言包不支持调试", Toast.LENGTH_LONG).show();
            if (temporaryMirror) try { WorkspaceFiles.removeMirror(root); }
                catch (Exception ignored) { }
            return;
        }
        debugLaunchTarget.launched(entry, workspaceKey);
        debugPanel.start(root, entry, workspaceKey, temporaryMirror);
    }
    @Override protected void onDestroy() {
        if (debugPanel != null) debugPanel.close();
        super.onDestroy();
    }
    @Override protected String completeBackend(String filename, String workdir, int cursor)
            throws Exception {
        PackRegistry.Descriptor pack = registry().enabled();
        if (pack == null || !registry().verified("complete") || !pack.accepts(filename))
            throw new IllegalStateException("当前文件没有通过验证的补全语言包");
        return PackClient.complete(this, pack, new File(workdir),
                new File(filename), cursor).toString();
    }
    @Override protected String analyzeBackend(String filename, String workdir)
            throws Exception {
        PackRegistry.Descriptor pack = registry().enabled();
        if (pack == null || !registry().verified("analyze") || !pack.accepts(filename))
            throw new IllegalStateException("当前文件没有通过验证的检查语言包");
        return PackClient.invoke(this, pack, "analyze", new File(workdir),
                new File(filename), 30).toString();
    }
    @Override protected String buildBackend(String source, String filename, String workdir)
            throws Exception {
        PackRegistry.Descriptor pack = registry().enabled();
        if (pack == null || !registry().verified("build") || !pack.accepts(filename))
            throw new IllegalStateException("当前文件没有通过验证的构建语言包");
        return PackClient.invoke(this, pack, "build", new File(workdir),
                new File(filename), 60).toString();
    }
    @Override protected boolean canRunFile(String name) {
        PackRegistry.Descriptor pack = runnable();
        return pack != null && pack.accepts(name);
    }
    @Override protected boolean localFile(String name) {
        return name.endsWith(".py") || registry().knownExtension(name);
    }
    @Override protected boolean extraEditableFile(String name) {
        return registry().knownExtension(name);
    }
    @Override protected String starterScript() { return "print('Hello from Pocket Android IDE')\n"; }
    @Override protected String environmentSummary() {
        PackRegistry.Descriptor pack = registry().enabled();
        return pack == null ? "仅编辑模式。请在左侧扩展图标中发现并启用语言包。"
                : "已启用 " + pack.packId() + " " + pack.version()
                    + "\n仅通过验证的能力可使用。";
    }
    @Override protected String librariesSummary() {
        PackRegistry.Descriptor pack = registry().enabled();
        return pack == null ? "尚未启用语言包" : pack.packId() + " " + pack.version();
    }
    @Override protected String statusSummary() { return capabilitySummary(); }
    @Override protected CompletionCatalog completionCatalog() { return CompletionCatalog.EMPTY; }
    @Override protected String probeProgressText() { return "正在连接已启用语言包…"; }
    @Override protected String formatProbe(JSONObject result) {
        return result.optString("packId") + "：" + result.optString("error", "初始化完成");
    }
    @Override protected String probeBackend() throws Exception {
        PackRegistry.Descriptor pack = runnable();
        if (pack == null) throw new IllegalStateException("没有通过运行验证的语言包");
        return PackClient.invoke(this, pack, "initialize", null, null, 15).toString();
    }
    @Override protected String runBackend(String source, String filename, String workdir)
            throws Exception {
        PackRegistry.Descriptor pack = runnable();
        if (pack == null || !pack.accepts(filename))
            throw new IllegalStateException("当前文件没有通过验证的语言包");
        return PackClient.invoke(this, pack, "run", new File(workdir),
                new File(filename), 60).toString();
    }

    @Override protected void showLanguagePacks() {
        List<PackRegistry.Descriptor> found = registry().discover();
        PackRegistry.Descriptor active = registry().enabled();
        String[] names = new String[found.size() + 1];
        names[0] = active == null ? "当前：未启用" : "当前：" + active.packId() + "（点击禁用）";
        for (int i = 0; i < found.size(); i++)
            names[i + 1] = found.get(i).packId() + "  " + found.get(i).version();
        new AlertDialog.Builder(this).setTitle("可执行语言包")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        if (active != null) new AlertDialog.Builder(this).setTitle("禁用语言包")
                                .setMessage(active.packId() + " 将不再接收任务；已保存的文件保留。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("禁用", (d, w) -> {
                                    registry().disable();
                                    refreshCapabilityStatus();
                                    Toast.makeText(this, "语言包已禁用", Toast.LENGTH_SHORT).show();
                                }).show();
                        return;
                    }
                    inspect(found.get(which - 1));
                })
                .setNegativeButton("关闭", null).show();
    }

    private void inspect(PackRegistry.Descriptor pack) {
        JSONArray claimed = pack.manifest.optJSONArray("capabilities");
        String detail = "来源：" + pack.manifest.optString("source")
                + "\n版本：" + pack.version()
                + "\n许可证：" + pack.manifest.optString("license")
                + "\n证书 SHA-256：" + pack.certificate
                + "\n已安装 APK SHA-256：" + pack.apkSha256
                + "\nAndroid 权限：" + pack.permissions
                + "\n声明能力：" + claimed
                + "\n\n该 APK 在独立进程运行代码，并拥有自身 Android 权限。启用前将逐项执行包内自测；未通过的能力不会开放。";
        new AlertDialog.Builder(this).setTitle(pack.packId()).setMessage(detail)
                .setNegativeButton("取消", null)
                .setPositiveButton("验证并启用", (dialog, which) -> {
                    Toast.makeText(this, "语言包验证中…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        PackValidator.Report report = PackValidator.validate(this, pack);
                        runOnUiThread(() -> new AlertDialog.Builder(this)
                                .setTitle(report.initialized ? "验证完成" : "无法启用")
                                .setMessage(report.text)
                                .setPositiveButton(report.initialized ? "启用通过的能力" : "关闭",
                                        (d, w) -> {
                                            if (report.initialized)
                                                registry().enable(pack, report.capabilities);
                                            refreshCapabilityStatus();
                                        }).show());
                    }, "pack-validation").start();
                }).show();
    }
}
