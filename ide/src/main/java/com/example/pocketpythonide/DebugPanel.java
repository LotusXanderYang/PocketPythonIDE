package com.example.pocketpythonide;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** DAP session and terminal panel hosted by the ordinary editable workbench. */
final class DebugPanel {
    private static final int SURFACE = 0xff24322e;
    private static final int TEXT = 0xffd9e9e3;
    private static final int MUTED = 0xffa9c3b8;
    private final IdeActivity host;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private final LinearLayout view;
    private final LinearLayout sidebar;
    private final View controls;
    private final LinearLayout breakpointRows, stackRows, variableRows;
    private final TextView status, output;
    private final TextView resumeButton, pauseButton, nextButton, stepInButton, stepOutButton;
    private final Map<String, BreakpointStore> stores = new HashMap<>();
    private File root;
    private String entry, workspaceKey;
    private boolean temporaryMirror;
    private BreakpointStore breakpoints;
    private volatile DebugPackClient session;
    private volatile DapTransport dap;
    private volatile boolean closing;
    private volatile boolean connecting;
    private volatile boolean stopping;
    private volatile boolean stopped;
    private volatile int activeLine;
    private volatile int activeThreadId = 1;
    private volatile boolean nativeDebug;
    private volatile PackRegistry.Descriptor activePack;
    private volatile String activePath = "";
    private int lastExitCode = -1;

    DebugPanel(IdeActivity host) {
        this.host = host;
        view = column();
        view.setBackgroundColor(SURFACE);
        sidebar = column();
        sidebar.setBackgroundColor(SURFACE);
        TextView title = text("运行和调试", 13, TEXT);
        title.setPadding(dp(12), dp(12), 0, dp(10));
        sidebar.addView(title);
        variableRows = section(sidebar, "变量", 4);
        stackRows = section(sidebar, "调用堆栈", 2);
        breakpointRows = section(sidebar, "断点", 2);

        HorizontalScrollView controlScroll = new HorizontalScrollView(host);
        controlScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout buttons = new LinearLayout(host);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        resumeButton = action(buttons, "▶ 继续", () -> control("continue"));
        pauseButton = action(buttons, "Ⅱ 暂停", () -> control("pause"));
        nextButton = action(buttons, "↷ 单步", () -> control("next"));
        stepInButton = action(buttons, "↓ 步入", () -> control("stepIn"));
        stepOutButton = action(buttons, "↑ 步出", () -> control("stepOut"));
        action(buttons, "■ 停止", () -> commands.execute(() -> stop(false)));
        controlScroll.addView(buttons);
        controls = controlScroll;

        status = text("就绪 · 在编辑器行号处点按可设置断点", 12, MUTED);
        status.setPadding(dp(12), dp(7), dp(8), dp(7));
        view.addView(status);
        if (host.compactLayout())
            view.addView(sidebar, new LinearLayout.LayoutParams(-1, 0, 2));
        ScrollView outputScroll = new ScrollView(host);
        output = text("", 12, TEXT);
        output.setTextIsSelectable(true);
        output.setPadding(dp(12), dp(4), dp(12), dp(8));
        outputScroll.addView(output);
        view.addView(outputScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        showBreakpoints();
        updateControls();
    }

    View view() { return view; }
    View sidebarView() { return host.compactLayout() ? null : sidebar; }
    View controlsView() { return controls; }
    boolean active() { return connecting || stopping || (session != null && !closing); }
    int activeLine(String path) { return path.equals(activePath) ? activeLine : 0; }
    void clearOutput() { output.setText(""); }
    void showWorkspaceBreakpoints(String key) {
        if (!active()) {
            breakpoints = store(key);
            showBreakpoints();
        }
    }

    boolean hasBreakpoint(String path, int line, String key) {
        return store(key).has(path, line);
    }

    void toggle(String path, int line, String source, String key) {
        if (path.isEmpty()) return;
        store(key).toggle(path, line, source);
        showBreakpoints();
        if (dap != null && key.equals(workspaceKey))
            commands.execute(() -> applyBreakpoints(path));
    }

    void start(File newRoot, String newEntry, String key, boolean mirror) {
        if (active()) {
            if (mirror) removeMirror(newRoot);
            status("调试已经运行；请先停止当前会话");
            return;
        }
        root = newRoot;
        entry = newEntry;
        workspaceKey = key;
        temporaryMirror = mirror;
        breakpoints = store(key);
        connecting = true;
        closing = false;
        stopped = false;
        lastExitCode = -1;
        activePath = "";
        activeLine = 0;
        activeThreadId = 1;
        nativeDebug = false;
        activePack = null;
        output.setText("");
        updateControls();
        host.refreshDebugGutter();
        host.showDebugPanel();
        showBreakpoints();
        worker.execute(this::connect);
    }

    private BreakpointStore store(String key) {
        BreakpointStore found = stores.get(key);
        if (found == null) {
            found = new BreakpointStore(host, key);
            stores.put(key, found);
        }
        if (!active()) breakpoints = found;
        return found;
    }

    private void connect() {
        try {
            int unresolved = breakpoints.remap(root);
            host.runOnUiThread(() -> {
                showBreakpoints(); host.refreshDebugGutter();
            });
            if (unresolved > 0) status(unresolved + " 个断点无法映射，请检查源文件");
            PackRegistry registry = new PackRegistry(host);
            PackRegistry.Descriptor pack = registry.enabled();
            if (pack == null || !registry.verified("debug") || !pack.accepts(entry))
                throw new IllegalStateException("此语言包不支持调试");
            activePack = pack;
            nativeDebug = "org.idecpp".equals(pack.packId());
            status("正在连接调试适配器…");
            session = new DebugPackClient(host, pack, root, new File(root, entry));
            if (closing) { session.close(); session = null; return; }
            dap = session.dap();
            dap.request("initialize", new JSONObject().put("clientID", "pocketide")
                    .put("adapterID", pack.packId().equals("org.idecpp") ? "lldb" : "pocket-python")
                    .put("pathFormat", "path").put("linesStartAt1", true)
                    .put("columnsStartAt1", true), 10);
            dap.request("launch", new JSONObject().put("program", entry), 10);
            Set<String> paths = new HashSet<>();
            int requestedBreakpoints = 0;
            for (BreakpointStore.Mark mark : breakpoints.all())
                if (mark.line > 0 && pack.accepts(mark.path)) {
                    paths.add(mark.path); requestedBreakpoints++;
                }
            for (String path : paths) applyBreakpoints(path);
            int submitted = requestedBreakpoints;
            host.runOnUiThread(() -> output.append("[调试] 入口：" + entry
                    + "；已提交 " + submitted + " 个断点。\n"));
            if (paths.isEmpty() && !breakpoints.all().isEmpty())
                host.runOnUiThread(() -> output.append("[调试] 当前语言没有可提交的断点；请检查左侧断点列表。\n"));
            dap.request("setExceptionBreakpoints", new JSONObject()
                    .put("filters", nativeDebug ? new JSONArray().put("cpp_throw")
                            : new JSONArray().put("all")), 10);
            dap.request("configurationDone", new JSONObject(), 10);
            connecting = false;
            host.runOnUiThread(this::updateControls);
            status("运行中");
            DapTransport current = dap;
            new Thread(() -> readEvents(current), "debug-panel-events").start();
        } catch (Exception failed) {
            error(failed);
            stop(false);
        }
    }

    private void applyBreakpoints(String path) {
        try {
            if (dap == null || activePack == null || !activePack.accepts(path)) return;
            JSONArray lines = new JSONArray();
            for (BreakpointStore.Mark mark : breakpoints.all())
                if (mark.line > 0 && mark.path.equals(path))
                    lines.put(new JSONObject().put("line", mark.line));
            JSONObject reply = dap.request("setBreakpoints", new JSONObject()
                    .put("source", new JSONObject().put("path", path))
                    .put("breakpoints", lines), 10);
            JSONArray confirmed = reply.optJSONArray("breakpoints");
            if (confirmed != null) for (int i = 0; i < confirmed.length(); i++) {
                JSONObject item = confirmed.optJSONObject(i);
                if (item != null && !item.optBoolean("verified")
                        && !item.optString("message").isEmpty()) {
                    String warning = "[断点未生效] " + path + ":" + item.optInt("line")
                            + " " + item.optString("message") + "\n";
                    host.runOnUiThread(() -> output.append(warning));
                }
            }
        } catch (Exception failed) { error(failed); }
    }

    private void readEvents(DapTransport transport) {
        try {
            while (!closing && dap == transport) {
                JSONObject event = transport.nextEvent(2);
                if (event == null) continue;
                if (closing || dap != transport) return;
                JSONObject body = event.optJSONObject("body");
                switch (event.optString("event")) {
                    case "stopped":
                        if (body != null) activeThreadId = body.optInt("threadId", activeThreadId);
                        stopped = true;
                        host.runOnUiThread(this::updateControls);
                        status("已暂停：" + (body == null ? "" : body.optString("reason")));
                        worker.execute(this::showStop);
                        break;
                    case "continued":
                        stopped = false;
                        activePath = ""; activeLine = 0;
                        host.runOnUiThread(() -> {
                            host.refreshDebugGutter(); updateControls();
                        });
                        status("运行中");
                        break;
                    case "output":
                        if (body != null) host.runOnUiThread(() ->
                                output.append(body.optString("output")));
                        break;
                    case "exited":
                        lastExitCode = body == null ? -1 : body.optInt("exitCode", -1);
                        host.runOnUiThread(() -> output.append("\n[退出码 " + lastExitCode + "]"));
                        break;
                    case "terminated":
                        commands.execute(() -> stop(true));
                        return;
                    default: break;
                }
            }
        } catch (Exception failed) {
            if (!closing && dap == transport) error(failed);
        }
    }

    private void showStop() {
        try {
            if (dap == null || !stopped) return;
            JSONArray frames = dap.request("stackTrace", new JSONObject()
                    .put("threadId", activeThreadId), 10).getJSONArray("stackFrames");
            host.runOnUiThread(() -> {
                stackRows.removeAllViews();
                for (int i = 0; i < frames.length(); i++) {
                    JSONObject frame = frames.optJSONObject(i);
                    if (frame == null) continue;
                    JSONObject source = frame.optJSONObject("source");
                    String path = source == null ? "" : source.optString("path");
                    int line = frame.optInt("line");
                    TextView row = text(frame.optString("name") + "  " + path + ":" + line,
                            12, TEXT);
                    row.setPadding(dp(8), dp(4), dp(8), dp(4));
                    row.setOnClickListener(v -> {
                        host.navigateDebugSource(path, line);
                        activePath = path; activeLine = line;
                        host.refreshDebugGutter();
                        worker.execute(() -> showVariables(frame.optInt("id")));
                    });
                    stackRows.addView(row);
                }
                if (frames.length() > 0) {
                    JSONObject first = frames.optJSONObject(0);
                    if (first != null && first.optJSONObject("source") != null) {
                        activePath = first.optJSONObject("source").optString("path");
                        activeLine = first.optInt("line");
                        host.navigateDebugSource(activePath, activeLine);
                    }
                }
            });
            if (frames.length() > 0) showVariables(frames.getJSONObject(0).getInt("id"));
        } catch (Exception failed) { if (!closing && stopped) error(failed); }
    }

    private void showVariables(int frameId) {
        try {
            if (dap == null || !stopped) return;
            JSONArray scopes = dap.request("scopes", new JSONObject()
                    .put("frameId", frameId), 10).getJSONArray("scopes");
            if (scopes.length() == 0) return;
            JSONArray[] values = new JSONArray[scopes.length()];
            for (int index = 0; index < scopes.length(); index++)
                values[index] = dap.request("variables", new JSONObject()
                        .put("variablesReference", scopes.getJSONObject(index)
                                .getInt("variablesReference")), 10).getJSONArray("variables");
            host.runOnUiThread(() -> {
                variableRows.removeAllViews();
                for (int group = 0; group < values.length; group++) {
                    JSONObject scope = scopes.optJSONObject(group);
                    TextView heading = text("▾ " + (scope == null ? "变量" :
                            scope.optString("name")), 12, MUTED);
                    heading.setPadding(dp(9), dp(4), 0, dp(3));
                    variableRows.addView(heading);
                    JSONArray entries = values[group];
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject value = entries.optJSONObject(i);
                        if (value != null) variableRows.addView(variableRow(value, 0));
                    }
                }
            });
        } catch (Exception failed) { if (!closing && stopped) error(failed); }
    }

    private LinearLayout variableRow(JSONObject value, int depth) {
        LinearLayout block = column();
        int reference = value.optInt("variablesReference");
        TextView label = text((reference > 0 ? "▸ " : "  ")
                + value.optString("name") + " = " + value.optString("value"), 12, TEXT);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(dp(10 + depth * 12), dp(3), dp(8), dp(3));
        block.addView(label);
        if (reference > 0 && depth < 4) {
            LinearLayout children = column();
            children.setVisibility(View.GONE);
            block.addView(children);
            label.setOnClickListener(view -> {
                if (children.getVisibility() == View.VISIBLE) {
                    children.setVisibility(View.GONE);
                    label.setText(label.getText().toString().replaceFirst("^▾", "▸"));
                    return;
                }
                if (children.getChildCount() > 0) {
                    children.setVisibility(View.VISIBLE);
                    label.setText(label.getText().toString().replaceFirst("^▸", "▾"));
                    return;
                }
                worker.execute(() -> {
                    try {
                        if (dap == null || !stopped) return;
                        JSONArray entries = dap.request("variables", new JSONObject()
                                .put("variablesReference", reference), 10)
                                .getJSONArray("variables");
                        host.runOnUiThread(() -> {
                            for (int i = 0; i < entries.length(); i++) {
                                JSONObject child = entries.optJSONObject(i);
                                if (child != null) children.addView(variableRow(child, depth + 1));
                            }
                            children.setVisibility(View.VISIBLE);
                            label.setText(label.getText().toString().replaceFirst("^▸", "▾"));
                        });
                    } catch (Exception failed) { if (!closing && stopped) error(failed); }
                });
            });
        }
        return block;
    }

    private void showBreakpoints() {
        breakpointRows.removeAllViews();
        if (breakpoints == null || breakpoints.all().isEmpty()) {
            breakpointRows.addView(text("点按编辑器行号添加", 12, MUTED));
            return;
        }
        for (BreakpointStore.Mark mark : breakpoints.all()) {
            TextView row = text("●  " + mark.path + ":" +
                    (mark.line > 0 ? mark.line : "未定位"), 12, TEXT);
            row.setPadding(dp(8), dp(4), dp(8), dp(4));
            row.setOnClickListener(v -> {
                if (mark.line > 0) host.navigateDebugSource(mark.path, mark.line);
            });
            breakpointRows.addView(row);
        }
    }

    private void control(String command) {
        commands.execute(() -> {
            boolean pause = "pause".equals(command);
            DapTransport transport = dap;
            if (transport == null || connecting || closing || stopping) {
                status("调试会话尚未就绪");
                return;
            }
            if (pause && stopped) {
                status("当前已暂停；请使用继续或单步");
                return;
            }
            boolean wasStopped = stopped;
            try {
                if (!pause) {
                    stopped = false;
                    host.runOnUiThread(this::updateControls);
                }
                status(pause ? "正在请求暂停…" : "正在执行：" + command);
                if (pause && nativeDebug && activeThreadId == 1) {
                    JSONArray threads = transport.request("threads", new JSONObject(), 10)
                            .optJSONArray("threads");
                    if (threads == null || threads.length() == 0)
                        throw new IllegalStateException("尚未取得 C/C++ 调试线程");
                    activeThreadId = threads.getJSONObject(0).getInt("id");
                }
                transport.request(command, new JSONObject().put("threadId", activeThreadId), 10);
            } catch (Exception failed) {
                if (!pause && wasStopped && dap == transport && !closing) stopped = true;
                host.runOnUiThread(this::updateControls);
                if (!closing) {
                    String message = String.valueOf(failed.getMessage());
                    if (message.contains("Debuggee is running")) status("程序正在运行");
                    else if (pause && message.contains("Unsupported while stopped"))
                        status("当前已暂停；请使用继续或单步");
                    else error(failed);
                }
            }
        });
    }

    private void stop(boolean naturallyTerminated) {
        stopping = true;
        closing = true;
        connecting = false;
        DebugPackClient current = session;
        session = null; dap = null;
        stopped = false;
        activePath = ""; activeLine = 0;
        activePack = null;
        if (current != null) try { current.close(naturallyTerminated); }
            catch (Exception failed) { error(failed); }
        if (temporaryMirror && root != null) removeMirror(root);
        temporaryMirror = false;
        host.runOnUiThread(() -> {
            host.refreshDebugGutter();
            host.leaveDebugLayout();
            updateControls();
            if (!status.getText().toString().startsWith("调试错误："))
                status.setText(lastExitCode < 0 ? "调试已结束" :
                        "调试结束 · 退出码 " + lastExitCode);
            stopping = false;
        });
    }

    void close() {
        closing = true;
        commands.execute(() -> stop(false));
        commands.shutdown();
        worker.shutdown();
    }

    private void updateControls() {
        boolean ready = dap != null && !connecting && !closing && !stopping;
        boolean paused = ready && stopped;
        setEnabled(resumeButton, ready, paused);
        setEnabled(nextButton, ready, paused);
        setEnabled(stepInButton, ready, paused);
        setEnabled(stepOutButton, ready, paused);
        setEnabled(pauseButton, ready, !paused);
    }

    private void setEnabled(TextView button, boolean enabled, boolean prominent) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? (prominent ? 1f : 0.55f) : 0.4f);
    }

    private void removeMirror(File folder) {
        try { WorkspaceFiles.removeMirror(folder); }
        catch (Exception failed) { error(failed); }
    }

    private void status(String value) { host.runOnUiThread(() -> status.setText(value)); }
    private void error(Exception failed) {
        status("调试错误：" + failed.getClass().getSimpleName() + ": " + failed.getMessage());
    }

    private LinearLayout section(LinearLayout parent, String title, int weight) {
        LinearLayout box = column();
        TextView heading = text(title, 12, MUTED);
        heading.setPadding(dp(10), dp(7), 0, dp(7));
        box.addView(heading);
        ScrollView scroll = new ScrollView(host);
        LinearLayout content = column();
        scroll.addView(content);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        parent.addView(box, new LinearLayout.LayoutParams(-1, 0, weight));
        return content;
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(host);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    private TextView text(String value, int size, int color) {
        TextView result = new TextView(host);
        result.setText(value); result.setTextSize(size); result.setTextColor(color);
        result.setTypeface(Typeface.MONOSPACE);
        return result;
    }

    private TextView action(LinearLayout bar, String name, Runnable click) {
        TextView button = text(name, 13, TEXT);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setOnClickListener(v -> click.run());
        bar.addView(button, new LinearLayout.LayoutParams(-2, -1));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * host.getResources().getDisplayMetrics().density);
    }
}
