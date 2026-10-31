package com.example.pocketpythonide;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Project, editor, live CPU task and results in one small Lab screen. */
public final class LabHomeActivity extends Activity {
    private static final int IMPORT_DATA = 40;
    private static final int EXPORT_RESULTS = 41;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean taskActive = new AtomicBoolean();
    private LabProjectStore projects;
    private File project;
    private String editing = "main.py";
    private String runId = "";
    private Future<?> task;
    private TextView projectLabel;
    private TextView status;
    private TextView logs;
    private EditText editor;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { projects = new LabProjectStore(this); }
        catch (Exception problem) { new AlertDialog.Builder(this).setMessage(problem.toString())
                .setPositiveButton("关闭", (dialog, which) -> finish()).show(); return; }
        buildScreen();
        String saved = getPreferences(MODE_PRIVATE).getString("project", "");
        if (!saved.isEmpty()) {
            try { openProject(saved); }
            catch (Exception problem) { showError("恢复项目失败：" + problem.getMessage()); }
        }
    }

    private void buildScreen() {
        int background = Color.rgb(23, 32, 38);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(12), dp(16), dp(16));
        page.setBackgroundColor(background);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setContentView(scroll);

        TextView title = label("Python · PyTorch Lab", 23, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        page.addView(title);
        page.addView(label("离线 CPU 训练 · 一次一个任务 · 无 GPU / 任意 wheel 安装能力",
                13, Color.rgb(176, 195, 196)));
        projectLabel = label("尚未打开项目", 17, Color.WHITE);
        projectLabel.setPadding(0, dp(16), 0, dp(6));
        page.addView(projectLabel);
        row(page, button("新建项目", v -> promptNew()), button("打开项目", v -> chooseProject()));
        row(page, button("矩阵/求导样例", v -> createSample("matrix")),
                button("极小训练样例", v -> createSample("tiny")));
        row(page, button("导入数据", v -> importData()),
                button("导出模型与日志", v -> exportResults()));
        row(page, button("代码 main.py", v -> switchFile("main.py")),
                button("参数 params.json", v -> switchFile("params.json")));
        editor = new EditText(this);
        editor.setTextColor(Color.WHITE);
        editor.setHintTextColor(Color.rgb(145, 160, 160));
        editor.setBackgroundColor(Color.rgb(31, 43, 49));
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(14);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        editor.setMinLines(11);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHint("新建项目或选择离线样例后可编辑");
        page.addView(editor, new LinearLayout.LayoutParams(-1, dp(250)));
        row(page, button("保存", v -> saveEditor(true)),
                button("启动 CPU 任务", v -> startTraining()),
                button("停止", v -> stopTraining()));
        status = label("待运行", 15, Color.rgb(133, 211, 175));
        status.setPadding(0, dp(12), 0, dp(8));
        page.addView(status);
        logs = label("任务日志会显示在这里。", 13, Color.rgb(220, 224, 220));
        logs.setTypeface(Typeface.MONOSPACE);
        logs.setTextIsSelectable(true);
        logs.setPadding(dp(12), dp(12), dp(12), dp(12));
        logs.setBackgroundColor(Color.rgb(15, 24, 29));
        page.addView(logs, new LinearLayout.LayoutParams(-1, dp(210)));
        TextView limits = label("前台服务可在切后台、锁屏时继续小任务；系统仍可能回收进程。"
                + "日志和已完成检查点保留，未完成任务需重新启动。"
                + "训练消耗 CPU、电量和存储，建议接电并预留约 1 GB 空间；脚本改动在下次运行生效。",
                12, Color.rgb(176, 195, 196));
        limits.setPadding(0, dp(12), 0, dp(12));
        page.addView(limits);
        page.addView(button("旧版脚本编辑器（保留原有文件）",
                v -> startActivity(new Intent(this, LabActivity.class))));
    }

    private void promptNew() {
        if (taskActive.get()) { showError("先停止当前训练"); return; }
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("如：course_lab");
        new AlertDialog.Builder(this).setTitle("新建私有训练项目")
                .setView(name).setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> {
                    try { saveEditor(false); setProject(projects.create(
                            name.getText().toString().trim(), "blank")); }
                    catch (Exception error) { showError(error.getMessage()); }
                }).show();
    }

    private void chooseProject() {
        if (taskActive.get()) { showError("先停止当前训练"); return; }
        String[] names = projects.names();
        if (names.length == 0) { showError("还没有项目，可先新建或选择样例"); return; }
        new AlertDialog.Builder(this).setTitle("打开私有训练项目")
                .setItems(names, (dialog, which) -> {
                    try { saveEditor(false); openProject(names[which]); }
                    catch (Exception error) { showError(error.getMessage()); }
                }).show();
    }

    private void createSample(String kind) {
        if (taskActive.get()) { showError("先停止当前训练"); return; }
        try {
            saveEditor(false);
            String name = projects.uniqueName("matrix".equals(kind) ? "matrix_demo" : "tiny_train");
            setProject(projects.create(name, kind));
            status.setText("已创建离线样例。查看代码和参数后点击“启动 CPU 任务”。");
        } catch (Exception error) { showError("创建样例失败：" + error.getMessage()); }
    }

    private void openProject(String name) throws Exception { setProject(projects.open(name)); }

    private void setProject(File next) throws Exception {
        project = next;
        editing = "main.py";
        editor.setText(projects.read(project, editing));
        projectLabel.setText("项目：" + project.getName() + " · " + editing);
        getPreferences(MODE_PRIVATE).edit().putString("project", project.getName()).apply();
        restoreRunStatus();
    }

    private void switchFile(String name) {
        if (project == null) { showError("请先打开项目"); return; }
        if (name.equals(editing)) return;
        try {
            if (!saveEditor(false)) return;
            editing = name;
            editor.setText(projects.read(project, name));
            projectLabel.setText("项目：" + project.getName() + " · " + name);
        } catch (Exception error) { showError("打开失败：" + error.getMessage()); }
    }

    private boolean saveEditor(boolean notify) {
        if (project == null) { if (notify) showError("请先打开项目"); return false; }
        try {
            projects.save(project, editing, editor.getText().toString());
            if (notify) status.setText(editing + " 已保存");
            return true;
        } catch (Exception error) { showError("保存失败：" + error.getMessage()); return false; }
    }

    private void startTraining() {
        if (project == null) { showError("请先打开项目"); return; }
        if (!taskActive.compareAndSet(false, true)) { showError("当前任务仍在运行"); return; }
        if (!saveEditor(false)) { taskActive.set(false); return; }
        try {
            new JSONObject(projects.read(project, "params.json"));
        } catch (Exception error) {
            taskActive.set(false); showError("params.json 不是有效 JSON：" + error.getMessage()); return;
        }
        runId = UUID.randomUUID().toString();
        File runningProject = project;
        String runningId = runId;
        getPreferences(MODE_PRIVATE).edit().putString("runId", runId).apply();
        status.setText("运行中 · CPU · 可切后台或锁屏；从通知返回此页停止");
        logs.setText("正在启动独立 Python 进程…");
        task = io.submit(() -> {
            try {
                JSONObject report = LabTrainingClient.execute(getApplicationContext(),
                        runningProject, runningId, text -> runOnUiThread(() -> {
                            if (runningId.equals(runId)) logs.setText(text);
                        }));
                runOnUiThread(() -> showReport(runningId, report));
            } catch (Exception error) {
                String detail = error.getMessage() == null ? error.toString() : error.getMessage();
                // Future.cancel(true) interrupts this worker. Clear the flag before
                // persisting the cancellation record: Files.write may otherwise
                // create an empty file and fail with ClosedByInterruptException.
                Thread.interrupted();
                try {
                    JSONObject report = new JSONObject().put("ok", false)
                            .put("error", detail).put("runId", runningId);
                    projects.saveRun(runningProject, runningId, report);
                } catch (Exception ignored) { /* Service may already have saved the result. */ }
                runOnUiThread(() -> {
                    if (runningId.equals(runId)) status.setText("任务中断：" + detail);
                });
            } finally {
                taskActive.set(false);
                getPreferences(MODE_PRIVATE).edit().remove("runId").apply();
            }
        });
    }

    private void showReport(String id, JSONObject report) {
        if (!id.equals(runId)) return;
        boolean okay = report.optBoolean("ok");
        String summary = okay ? "已完成" : "训练失败";
        if (report.has("wallMs"))
            summary += " · " + String.format(java.util.Locale.ROOT, "%.1f 秒",
                    report.optLong("wallMs") / 1000.0);
        if (report.has("peakPssKb"))
            summary += " · 训练进程峰值 PSS " + report.optLong("peakPssKb") / 1024 + " MiB";
        status.setText(summary);
        if (!okay) logs.append("\n" + report.optString("error") + "\n"
                + report.optString("file") + ":" + report.optInt("line"));
        if (okay && report.optJSONArray("checkpoints") != null
                && report.optJSONArray("checkpoints").length() > 0)
            logs.append("\n检查点：" + report.optJSONArray("checkpoints").toString());
    }

    private void stopTraining() {
        if (taskActive.get() && task != null) {
            status.setText("停止中…日志和已完成检查点会保留");
            task.cancel(true);
            return;
        }
        String previous = getPreferences(MODE_PRIVATE).getString("runId", "");
        if (previous.isEmpty()) { showError("当前没有运行任务"); return; }
        status.setText("请求停止上次任务…");
        io.submit(() -> {
            try { LabTrainingClient.stopDetached(getApplicationContext());
                runOnUiThread(() -> status.setText("已请求停止独立 Python 进程")); }
            catch (Exception error) { runOnUiThread(() -> showError(error.getMessage())); }
        });
    }

    private void restoreRunStatus() {
        String previous = getPreferences(MODE_PRIVATE).getString("runId", "");
        if (!previous.isEmpty()) {
            File record = new File(new File(project, "runs"), previous + ".json");
            if (record.isFile()) {
                try { showReportAfterRestart(new JSONObject(new String(
                        Files.readAllBytes(record.toPath()), StandardCharsets.UTF_8))); }
                catch (Exception error) { status.setText("上次结果无法读取：" + error.getMessage()); }
            } else status.setText("上次任务状态未确认；可看日志，必要时点“停止”后重启任务");
            File log = new File(new File(project, "logs"), previous + ".log");
            if (log.isFile()) try {
                logs.setText(new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
            return;
        }
        File[] history = new File(project, "logs").listFiles((dir, name) -> name.endsWith(".log"));
        if (history != null && history.length > 0) {
            Arrays.sort(history, (left, right) -> Long.compare(
                    right.lastModified(), left.lastModified()));
            try { logs.setText(new String(Files.readAllBytes(history[0].toPath()),
                    StandardCharsets.UTF_8)); }
            catch (Exception ignored) { }
            status.setText("最近日志：" + history[0].getName());
        } else { status.setText("待运行"); logs.setText("任务日志会显示在这里。"); }
    }

    private void showReportAfterRestart(JSONObject report) {
        status.setText(report.optBoolean("ok") ? "上次任务已完成（记录已保存）"
                : "上次任务失败／中断：" + report.optString("error"));
        getPreferences(MODE_PRIVATE).edit().remove("runId").apply();
    }

    private void importData() {
        if (project == null) { showError("请先打开项目"); return; }
        if (taskActive.get()) { showError("训练时不能导入数据"); return; }
        if (!saveEditor(false)) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, IMPORT_DATA);
    }

    private void exportResults() {
        if (project == null) { showError("请先打开项目"); return; }
        if (taskActive.get()) { showError("请等任务结束后再导出"); return; }
        if (!saveEditor(false)) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, project.getName() + "-results.zip");
        startActivityForResult(intent, EXPORT_RESULTS);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null
                || project == null) return;
        Uri uri = data.getData();
        File selected = project;
        if (request == IMPORT_DATA) {
            status.setText("正在导入数据…");
            io.submit(() -> {
                try {
                    String path = projects.importData(selected, uri);
                    projects.setDataFileIfEmpty(selected, path);
                    runOnUiThread(() -> {
                        status.setText("已导入 " + path + "；空白 data_file 已自动设置");
                        if (selected.equals(project) && "params.json".equals(editing))
                            try { editor.setText(projects.read(project, editing)); }
                            catch (Exception ignored) { }
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> showError("导入失败：" + error.getMessage()));
                }
            });
        } else if (request == EXPORT_RESULTS) {
            status.setText("正在导出模型与日志…");
            io.submit(() -> {
                try { projects.exportResults(selected, uri);
                    runOnUiThread(() -> status.setText("已导出结果归档（不含原始数据）")); }
                catch (Exception error) { runOnUiThread(() -> showError(
                        "导出失败，目标文件可能残留部分内容：" + error.getMessage())); }
            });
        }
    }

    @Override protected void onPause() {
        if (project != null) saveEditor(false);
        super.onPause();
    }

    private void showError(String text) {
        if (text == null) text = "未知错误";
        status.setText(text);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private void row(LinearLayout parent, Button... buttons) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        for (Button button : buttons) line.addView(button,
                new LinearLayout.LayoutParams(0, dp(48), 1));
        parent.addView(line);
    }

    private Button button(String text, View.OnClickListener click) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(12);
        button.setOnClickListener(click);
        return button;
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
