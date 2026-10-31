package com.example.pocketpythonide;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class WorkbenchActivity extends Activity {
    private int CHROME = Color.rgb(45, 45, 45);
    private int RAIL = Color.rgb(51, 51, 51);
    private int SIDEBAR = Color.rgb(37, 37, 38);
    private int SURFACE = Color.rgb(37, 37, 38);
    private int EDITOR = Color.rgb(31, 31, 31);
    private int BORDER = Color.rgb(61, 61, 61);
    private int TEXT = Color.rgb(220, 220, 220);
    private int MUTED = Color.rgb(143, 143, 143);
    private int ACCENT = Color.rgb(0, 122, 204);
    private static final int OPEN_FOLDER_REQUEST = 501;
    private static final int CREATE_FILE_REQUEST = 502;
    private static final int IMPORT_SCRIPT_REQUEST = 503;
    private static final int WALLPAPER_REQUEST = 504;
    private static final int FONT_REQUEST = 505;
    private static final String WORKSPACE_PREF = "workspace_tree_uri";
    private static final String FONT_SIZE_PREF = "editor_font_size_sp";
    private static final String CRASH_REPORT = "last-java-crash.txt";
    private static boolean crashHandlerInstalled;
    protected abstract boolean hasRuntime();
    protected abstract String starterScript();
    protected abstract String environmentSummary();
    protected abstract String librariesSummary();
    protected abstract String statusSummary();
    protected abstract CompletionCatalog completionCatalog();
    protected abstract String probeProgressText();
    protected abstract String formatProbe(JSONObject result);
    protected abstract String probeBackend() throws Exception;
    protected abstract String runBackend(String source, String filename, String workdir)
            throws Exception;
    protected void showLanguagePacks() { showLibraries(); }
    protected boolean modernUi() { return false; }
    protected String capabilitySummary() { return statusSummary(); }
    protected final String currentFilename() { return filename; }
    protected boolean canBuildFile(String name) { return false; }
    protected boolean canAnalyzeFile(String name) { return false; }
    protected boolean canCompleteFile(String name) { return false; }
    protected boolean canDebugFile(String name) { return false; }
    protected View createDebugPanel() { return null; }
    protected View createDebugSidebar() { return null; }
    protected View createDebugControls() { return null; }
    protected final boolean compactLayout() { return compact; }
    protected boolean hasBreakpoint(String path, int line, String workspaceKey) { return false; }
    protected int debugActiveLine(String path) { return 0; }
    protected void toggleBreakpoint(String path, int line, String source,
            String workspaceKey) { }
    protected boolean debugSessionActive() { return false; }
    protected void clearDebugOutput() { }
    protected void onEditorFileChanged(String path, String workspaceKey) { }
    protected String debugEntryForCurrent(String path, String workspaceKey) { return path; }
    protected final boolean isDebugSourceNavigation() { return navigatingDebugSource; }
    protected final void refreshDebugGutter() {
        if (editor != null && lineNumbers != null) updateLineNumbers(editor.getText());
    }
    protected final void showDebugPanel() {
        enterDebugLayout();
        showConsoleTab("调试");
    }
    protected final void leaveDebugLayout() {
        if (!debugLayoutActive) return;
        debugLayoutActive = false;
        if (debugSidebar != null) debugSidebar.setVisibility(View.GONE);
        if (fileSidebar != null) fileSidebar.setVisibility(View.VISIBLE);
        if (debugControls != null) debugControls.setVisibility(View.GONE);
        if (editor instanceof EditorEditText) ((EditorEditText) editor)
                .setLineHighlightColor("light".equals(getPreferences(MODE_PRIVATE)
                        .getString("ide_theme", "dark"))
                        ? Color.argb(35, 31, 126, 101) : Color.argb(50, 107, 148, 131));
        sidebar.setVisibility(sidebarVisibleBeforeDebug ? View.VISIBLE : View.GONE);
        fileDivider.setVisibility(sidebarVisibleBeforeDebug ? View.VISIBLE : View.GONE);
        if (compact) workspace.setVisibility(sidebarVisibleBeforeDebug ? View.GONE : View.VISIBLE);
    }
    private void enterDebugLayout() {
        if (debugLayoutActive) return;
        debugLayoutActive = true;
        sidebarVisibleBeforeDebug = sidebar.getVisibility() == View.VISIBLE;
        if (fileSidebar != null && debugSidebar != null) {
            fileSidebar.setVisibility(View.GONE);
            debugSidebar.setVisibility(View.VISIBLE);
            if (!compact) {
                sidebar.setVisibility(View.VISIBLE);
                fileDivider.setVisibility(View.VISIBLE);
            }
        }
        if (debugControls != null) debugControls.setVisibility(View.VISIBLE);
        if (editor instanceof EditorEditText) ((EditorEditText) editor)
                .setLineHighlightColor("light".equals(getPreferences(MODE_PRIVATE)
                        .getString("ide_theme", "dark"))
                        ? Color.argb(48, 170, 130, 20) : Color.argb(55, 185, 153, 50));
    }
    protected final void navigateDebugSource(String path, int line) {
        if (path == null || path.isEmpty() || !editableFile(path)) return;
        if (!path.equals(filename)) {
            if (!saveScript(false)) return;
            navigatingDebugSource = true;
            try { loadScript(path); }
            finally { navigatingDebugSource = false; }
            if (!path.equals(filename)) return;
        }
        int offset = 0;
        CharSequence text = editor.getText();
        for (int number = 1; number < line && offset < text.length(); number++) {
            int end = text.toString().indexOf('\n', offset);
            if (end < 0) break;
            offset = end + 1;
        }
        editor.setSelection(Math.min(offset, text.length()));
        android.text.Layout layout = editor.getLayout();
        if (layout != null) editor.scrollTo(editor.getScrollX(),
                Math.max(0, layout.getLineTop(Math.min(layout.getLineCount() - 1,
                        Math.max(0, line - 1))) - dp(80)));
        refreshDebugGutter();
    }
    protected void launchDebugger(File root, String entry, String workspaceKey,
            boolean temporaryMirror) { showError("此语言包不支持调试"); }
    protected boolean packWorkspace() { return false; }
    protected boolean packWorkspaceWriteback() { return false; }
    protected String completeBackend(String filename, String workdir, int cursor) throws Exception {
        throw new UnsupportedOperationException("语言包未提供补全能力");
    }
    protected String analyzeBackend(String filename, String workdir) throws Exception {
        throw new UnsupportedOperationException("语言包未提供检查能力");
    }
    protected String buildBackend(String source, String filename, String workdir) throws Exception {
        throw new UnsupportedOperationException("语言包未提供构建能力");
    }
    protected boolean canRunFile(String name) { return name.endsWith(".py"); }
    protected boolean localFile(String name) { return name.endsWith(".py"); }
    protected boolean extraEditableFile(String name) { return false; }

    private final ExecutorService runner = Executors.newSingleThreadExecutor();
    private final ExecutorService searchWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService fileWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService wallpaperWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService completionWorker = Executors.newSingleThreadExecutor();
    private Future<?> activeRun;
    private AtomicBoolean activeCancellation;
    private AtomicBoolean activeSync;
    private long runGeneration;
    private EditText editor;
    private EditorCompletionPopup completions;
    private Future<?> pendingCompletion;
    private Future<?> pendingFileRefresh;
    private int fileRefreshGeneration;
    private int completionGeneration;
    private String filename = "main.py";
    private View sidebar, workspace, consolePane, fileDivider, consoleDivider;
    private View fileSidebar, debugSidebar, debugControls;
    private boolean debugLayoutActive, sidebarVisibleBeforeDebug, navigatingDebugSource;
    private FrameLayout editorBackdrop;
    private ImageView wallpaperView;
    private TextView emptyHint;
    private LinearLayout tabs;
    private TextView capabilityView;
    private int sidebarWidthDp = 242, consoleHeightDp = 230;
    private String consoleTab = "输出";
    private final ArrayList<String> buildRecords = new ArrayList<>();
    private String lastOutput = "", lastProblem = "尚无问题";
    private String activeBuildName;
    private int wallpaperGeneration;
    private LinearLayout editorArea;
    private final Runnable completionTask = () -> {
        if (completions == null) return;
        if (filename.endsWith(".py") && editorArea.getVisibility() == View.VISIBLE)
            completions.update();
        else if (editorArea.getVisibility() == View.VISIBLE && cppFilename(filename)
                && canCompleteFile(filename))
            completions.show(CppCompletions.suggest(editor.getText().toString(),
                    editor.getSelectionStart(), cppDialect(filename)));
        else completions.dismiss();
    };
    private TextView filenameView, breadcrumbView, outputView, consoleState, cursorView, runButton, lineNumbers;
    private SyntaxPalette syntaxPalette;
    private boolean customSyntaxPalette;
    private ScrollView consoleOutputScroll;
    private View debugPanel;
    private TextView cancelTaskButton;
    private TextView folderLabel, commandCenter;
    private LinearLayout fileList;
    private CodeMinimapView minimap;
    private View fileRailIcon, terminalRailIcon;
    private final ArrayList<String> history = new ArrayList<>();
    private final ArrayList<String> openTabs = new ArrayList<>();
    private int historyIndex = -1;
    private boolean compact;
    private File scriptsDir;
    private byte[] loadedHash;
    private String pendingRecoverySource;
    private String pendingRecoveryFilename;
    private String currentFolder = "";
    private WorkspaceFiles workspaceFiles;
    private String pendingCreateFolder;
    private Uri pendingCreateTree;
    private String pendingCreateName;
    private final Set<String> expandedFolders = new HashSet<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (modernUi()) configurePalette();
        installCrashHandler(getApplicationContext());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(CHROME);
        getWindow().setNavigationBarColor(ACCENT);
        scriptsDir = new File(getFilesDir(), "scripts");
        if (!scriptsDir.exists() && !scriptsDir.mkdirs())
            Toast.makeText(this, "无法创建脚本目录", Toast.LENGTH_LONG).show();
        String savedTree = getPreferences(MODE_PRIVATE).getString(WORKSPACE_PREF, null);
        if (savedTree != null) {
            try {
                workspaceFiles = new WorkspaceFiles(this, Uri.parse(savedTree));
                filename = "";
            } catch (Exception e) {
                getPreferences(MODE_PRIVATE).edit().remove(WORKSPACE_PREF).apply();
                Toast.makeText(this, "工作区授权已失效，请重新打开文件夹", Toast.LENGTH_LONG).show();
            }
        }
        buildUi();
        if (modernUi() && state != null) {
            ArrayList<String> restored = state.getStringArrayList("openTabs");
            if (restored != null) openTabs.addAll(restored);
        }
        File starter = scriptFile("main.py");
        boolean seedStarter = !modernUi() || !getPreferences(MODE_PRIVATE)
                .getBoolean("ide_starter_initialized", false);
        if (!starter.exists() && seedStarter) {
            try { Files.write(starter.toPath(), starterScript().getBytes(StandardCharsets.UTF_8)); }
            catch (IOException e) { showError("无法创建示例脚本：" + e.getMessage()); }
        }
        if (modernUi() && starter.exists()) getPreferences(MODE_PRIVATE).edit()
                .putBoolean("ide_starter_initialized", true).apply();
        String initial = state == null ? filename : state.getString("filename", filename);
        if (workspaceFiles != null) {
            try {
                WorkspaceFiles.Entry entry = initial.isEmpty() ? null : workspaceFiles.find(initial);
                if (entry == null || entry.directory || !editableFile(initial)) initial = "";
                if (!initial.isEmpty()) loadScript(initial);
                else { filename = ""; editorArea.setVisibility(View.INVISIBLE); updateFilename(); }
            } catch (IOException e) { showError("工作区加载失败：" + e.getMessage()); }
        } else {
            if (initial.isEmpty() || !scriptFile(initial).exists()) {
                String[] localNames = scriptsDir.list((dir, name) -> localFile(name));
                initial = localNames == null || localNames.length == 0 ? "" : localNames[0];
            }
            if (initial.isEmpty()) {
                filename = "";
                editorArea.setVisibility(View.INVISIBLE);
                updateFilename();
            } else loadScript(initial);
        }
        if (state != null) {
            pendingCreateFolder = state.getString("pendingCreateFolder");
            pendingCreateName = state.getString("pendingCreateName");
            String pendingTree = state.getString("pendingCreateTree");
            pendingCreateTree = pendingTree == null ? null : Uri.parse(pendingTree);
            String pendingDraft = state.getString("pendingRecoverySource");
            String savedSource = pendingDraft == null ? state.getString("source") : pendingDraft;
            String savedName = pendingDraft == null ? state.getString("filename", "")
                    : state.getString("pendingRecoveryFilename", "");
            if (savedSource != null && savedName.equals(filename)
                    && editorArea.getVisibility() == View.VISIBLE) {
                boolean sameFile = Arrays.equals(state.getByteArray("loadedHash"), loadedHash);
                boolean sameInstallation = state.getLong("packageUpdateTime", -1)
                        == packageUpdateTime();
                if (savedSource.equals(editor.getText().toString())) {
                    editor.setSelection(Math.min(state.getInt("selection", 0), editor.length()));
                } else if (sameFile && sameInstallation
                        && pendingDraft == null) {
                    editor.setText(savedSource);
                    editor.setSelection(Math.min(state.getInt("selection", 0), editor.length()));
                } else {
                    promptRestoreDraft(savedName, savedSource,
                            state.getInt("selection", 0));
                }
            }
            outputView.setText(state.getString("output", outputView.getText().toString()));
        }
        showPreviousCrash();
        if (modernUi()) loadWallpaper();
    }

    private void configurePalette() {
        String theme = getPreferences(MODE_PRIVATE).getString("ide_theme", "dark");
        boolean light = "light".equals(theme);
        customSyntaxPalette = getPreferences(MODE_PRIVATE).contains(light
                ? SyntaxPalette.PREF_LIGHT : SyntaxPalette.PREF_DARK);
        syntaxPalette = SyntaxPalette.load(getPreferences(MODE_PRIVATE), light);
        CHROME = Color.rgb(light ? 237 : 34, light ? 240 : 39, light ? 235 : 38);
        RAIL = CHROME;
        SIDEBAR = Color.rgb(light ? 246 : 39, light ? 247 : 44, light ? 243 : 42);
        SURFACE = SIDEBAR;
        EDITOR = Color.rgb(light ? 252 : 28, light ? 252 : 33, light ? 249 : 33);
        BORDER = Color.rgb(light ? 207 : 67, light ? 214 : 73, light ? 204 : 70);
        TEXT = Color.rgb(light ? 30 : 232, light ? 40 : 235, light ? 38 : 228);
        MUTED = Color.rgb(light ? 91 : 166, light ? 103 : 177, light ? 97 : 170);
        String accent = getPreferences(MODE_PRIVATE).getString("ide_accent", "forest");
        ACCENT = "clay".equals(accent) ? Color.rgb(170, 80, 49)
                : "indigo".equals(accent) ? Color.rgb(75, 91, 170)
                : Color.rgb(31, 126, 101);
    }

    private static synchronized void installCrashHandler(Context context) {
        if (crashHandlerInstalled) return;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        File report = new File(context.getFilesDir(), CRASH_REPORT);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter stack = new StringWriter();
                error.printStackTrace(new PrintWriter(stack));
                Files.write(report.toPath(), stack.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) { /* Preserve the platform's crash handler. */ }
            if (previous != null) previous.uncaughtException(thread, error);
        });
        crashHandlerInstalled = true;
    }

    private void showPreviousCrash() {
        File report = new File(getFilesDir(), CRASH_REPORT);
        if (!report.isFile()) return;
        try {
            String stack = new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8);
            if (!report.delete()) report.deleteOnExit();
            new AlertDialog.Builder(this)
                    .setTitle("上次运行发生异常")
                    .setMessage(stack.length() > 12000 ? stack.substring(0, 12000) : stack)
                    .setPositiveButton("复制诊断信息", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("IDE crash", stack));
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (IOException ignored) { /* A report is optional diagnostic data. */ }
    }

    private void buildUi() {
        int width = getResources().getConfiguration().screenWidthDp;
        compact = width < 760;
        if (modernUi()) { buildModernUi(); return; }
        LinearLayout page = column(CHROME);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), 0,
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        page.addView(buildTitleBar(), size(-1, dp(44)));
        page.addView(divider(false));

        LinearLayout body = row(EDITOR);
        LinearLayout rail = column(RAIL);
        fileRailIcon = railButton(R.drawable.ic_explorer, "资源管理器", true, v -> toggleSidebar());
        rail.addView(fileRailIcon, size(-1, dp(49)));
        rail.addView(railButton(R.drawable.ic_search, "搜索", false, v -> promptSearch()), size(-1, dp(49)));
        rail.addView(railButton(R.drawable.ic_branch, "文件历史", false, v -> showHistory()), size(-1, dp(49)));
        rail.addView(railButton(R.drawable.ic_run, "运行脚本", false, v -> runScript()), size(-1, dp(49)));
        terminalRailIcon = railButton(R.drawable.ic_terminal, "运行输出", false, v -> toggleConsole());
        rail.addView(terminalRailIcon, size(-1, dp(49)));
        rail.addView(railButton(R.drawable.ic_extensions, "语言能力", false, v -> showLanguagePacks()), size(-1, dp(49)));
        rail.addView(railButton(R.drawable.ic_python, "Python 环境", false, v -> showEnvironment()), size(-1, dp(49)));
        rail.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        rail.addView(railButton(R.drawable.ic_account, "环境信息", false, v -> showEnvironment()), size(-1, dp(49)));
        rail.addView(railButton(R.drawable.ic_settings, "设置", false, v -> showSettings()), size(-1, dp(49)));
        body.addView(rail, size(dp(49), -1));
        body.addView(divider(true));

        sidebar = buildSidebar();
        body.addView(sidebar, compact ? new LinearLayout.LayoutParams(0, -1, 1)
                : size(dp(Math.min(250, Math.max(215, width / 5))), -1));
        fileDivider = divider(true);
        body.addView(fileDivider);
        LinearLayout work = column(EDITOR);
        workspace = work;
        work.addView(buildEditorPane(), new LinearLayout.LayoutParams(-1, 0, 1));
        consolePane = buildConsolePane();
        work.addView(consolePane, size(-1, dp(230)));
        consolePane.setVisibility(View.GONE);
        body.addView(work, new LinearLayout.LayoutParams(0, -1, 1));
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(buildStatusBar(), size(-1, dp(27)));
        setContentView(page);
        if (compact) {
            sidebar.setVisibility(View.GONE);
            fileDivider.setVisibility(View.GONE);
        }
        updateFilename();
        refreshFiles();
    }

    private void buildModernUi() {
        sidebarWidthDp = getPreferences(MODE_PRIVATE).getInt("ide_sidebar_dp", 242);
        consoleHeightDp = getPreferences(MODE_PRIVATE).getInt("ide_console_dp", 230);
        LinearLayout page = column(CHROME);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), 0,
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        page.addView(buildModernToolbar(), size(-1, dp(52)));
        page.addView(divider(false));
        LinearLayout body = row(EDITOR);
        FrameLayout sidebarContainer = new FrameLayout(this);
        fileSidebar = buildSidebar();
        sidebarContainer.addView(fileSidebar, new FrameLayout.LayoutParams(-1, -1));
        debugSidebar = createDebugSidebar();
        if (debugSidebar != null) {
            debugSidebar.setVisibility(View.GONE);
            sidebarContainer.addView(debugSidebar, new FrameLayout.LayoutParams(-1, -1));
        }
        sidebar = sidebarContainer;
        body.addView(sidebar, size(dp(sidebarWidthDp), -1));
        fileDivider = resizeHandle(true);
        body.addView(fileDivider, size(dp(8), -1));
        LinearLayout work = column(EDITOR);
        workspace = work;
        work.addView(buildEditorPane(), new LinearLayout.LayoutParams(-1, 0, 1));
        consoleDivider = resizeHandle(false);
        work.addView(consoleDivider, size(-1, dp(8)));
        consolePane = buildConsolePane();
        work.addView(consolePane, size(-1, dp(consoleHeightDp)));
        body.addView(work, new LinearLayout.LayoutParams(0, -1, 1));
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(buildStatusBar(), size(-1, dp(31)));
        setContentView(page);
        if (compact) {
            sidebar.setVisibility(View.GONE);
            fileDivider.setVisibility(View.GONE);
        }
        if (!getPreferences(MODE_PRIVATE).getBoolean("ide_console_open", false)) {
            consolePane.setVisibility(View.GONE);
            consoleDivider.setVisibility(View.GONE);
        }
        updateFilename();
        refreshFiles();
    }

    private LinearLayout buildModernToolbar() {
        LinearLayout bar = row(CHROME);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), 0, dp(10), 0);
        ImageView brand = new ImageView(this);
        brand.setImageResource(R.drawable.ic_launcher);
        brand.setContentDescription("Pocket Android IDE");
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams brandSize = size(dp(34), dp(34));
        brandSize.rightMargin = dp(compact ? 8 : 14);
        bar.addView(brand, brandSize);
        TextView files = toolbarAction("文件 ▾", v -> {
            PopupMenu menu = new PopupMenu(this, v);
            for (String name : new String[]{"新建脚本", "新建文件夹", "打开脚本", "打开文件夹",
                    "关闭文件夹", "保存脚本", "删除当前文件", "导入旧脚本", "显示/隐藏文件面板",
                    "检查当前文件", "补全建议", "构建当前文件", "字体大小", "小地图"})
                menu.getMenu().add(name);
            menu.setOnMenuItemClickListener(item -> {
                handleMenuItem(item.getTitle().toString()); return true;
            });
            menu.show();
        });
        bar.addView(files, size(-2, -1));
        commandCenter = toolbarAction("工作区 / 文件", v -> promptOpen());
        commandCenter.setSingleLine(true);
        commandCenter.setEllipsize(android.text.TextUtils.TruncateAt.END);
        commandCenter.setBackground(rounded(SIDEBAR, 5));
        LinearLayout.LayoutParams commandSize = new LinearLayout.LayoutParams(0, dp(36), 1);
        commandSize.leftMargin = dp(6);
        commandSize.rightMargin = dp(6);
        bar.addView(commandCenter, commandSize);
        bar.addView(toolbarAction("搜索", v -> promptProjectSearch()), size(-2, -1));
        if (!compact) bar.addView(toolbarAction("构建", v -> buildCurrent()), size(-2, -1));
        runButton = toolbarAction("运行", v -> runScript());
        runButton.setBackground(rounded(ACCENT, 5));
        runButton.setTextColor(Color.WHITE);
        bar.addView(runButton, size(-2, -1));
        bar.addView(toolbarAction("调试", v -> debugCurrent()), size(-2, -1));
        capabilityView = toolbarAction("仅编辑", v -> showLanguagePacks());
        bar.addView(capabilityView, size(-2, -1));
        bar.addView(toolbarAction("设置", v -> showSettings()), size(-2, -1));
        return bar;
    }

    private TextView toolbarAction(String title, View.OnClickListener click) {
        TextView view = label(title, 12, TEXT, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setContentDescription(title);
        interactive(view, click);
        return view;
    }

    private View resizeHandle(boolean vertical) {
        View handle = new View(this);
        handle.setBackgroundColor(BORDER);
        handle.setContentDescription(vertical ? "拖动调整文件面板宽度" : "拖动调整输出面板高度");
        handle.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (vertical) {
                    int proposed = Math.round(event.getRawX() / getResources().getDisplayMetrics().density);
                    int max = Math.max(200, getResources().getConfiguration().screenWidthDp - 320);
                    sidebarWidthDp = Math.max(170, Math.min(max, proposed));
                    sidebar.getLayoutParams().width = dp(sidebarWidthDp);
                    sidebar.requestLayout();
                } else {
                    int[] position = new int[2];
                    workspace.getLocationOnScreen(position);
                    int proposed = Math.round((position[1] + workspace.getHeight() - event.getRawY())
                            / getResources().getDisplayMetrics().density);
                    consoleHeightDp = Math.max(110, Math.min(420, proposed));
                    consolePane.getLayoutParams().height = dp(consoleHeightDp);
                    consolePane.requestLayout();
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                getPreferences(MODE_PRIVATE).edit()
                        .putInt("ide_sidebar_dp", sidebarWidthDp)
                        .putInt("ide_console_dp", consoleHeightDp).apply();
                return true;
            }
            return true;
        });
        return handle;
    }

    private LinearLayout buildTitleBar() {
        LinearLayout bar = row(CHROME);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_workbench_logo);
        logo.setContentDescription("Pocket Python IDE");
        LinearLayout.LayoutParams logoSize = size(dp(24), dp(24));
        logoSize.leftMargin = dp(10);
        logoSize.rightMargin = dp(8);
        bar.addView(logo, logoSize);
        topMenu(bar, "文件(F)", "新建脚本", "新建文件夹", "打开脚本", "打开文件夹", "关闭文件夹", "保存脚本", "导入旧脚本");
        topMenu(bar, "编辑(E)", "全选", "剪切", "复制", "粘贴", "补全建议");
        if (!compact) {
            topMenu(bar, "选择(S)", "全选", "跳转到行");
            topMenu(bar, "查看(V)", "资源管理器", "小地图", "运行输出", "字体大小");
            topMenu(bar, "转到(G)", "上一文件", "下一文件", "跳转到行");
        }
        runButton = topMenu(bar, "运行(R)", "运行当前脚本", "取消运行", "查看运行输出");
        if (modernUi()) topMenu(bar, "调试(D)", "开始调试");
        topMenu(bar, "终端(T)", "显示/隐藏输出", "清空输出");
        if (!compact) topMenu(bar, "帮助(H)", "关于", "环境自检");
        if (!compact) {
            TextView back = titleIcon("‹", "上一文件", v -> navigateHistory(-1));
            TextView forward = titleIcon("›", "下一文件", v -> navigateHistory(1));
            bar.addView(back, size(dp(30), -1));
            bar.addView(forward, size(dp(30), -1));
        }
        commandCenter = label("⌕   scripts", 12, Color.rgb(182, 182, 182), false);
        commandCenter.setGravity(Gravity.CENTER_VERTICAL);
        commandCenter.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable commandBackground = rounded(Color.rgb(49, 49, 49), 5);
        commandBackground.setStroke(dp(1), Color.rgb(65, 65, 65));
        commandCenter.setBackground(commandBackground);
        interactive(commandCenter, v -> promptOpen());
        LinearLayout.LayoutParams commandSize = new LinearLayout.LayoutParams(0, dp(29), 1);
        commandSize.leftMargin = dp(7);
        commandSize.rightMargin = dp(12);
        bar.addView(commandCenter, commandSize);
        if (!compact) {
            TextView layout = titleIcon("◫", "布局", v -> toggleSidebar());
            bar.addView(layout, size(dp(35), -1));
            TextView panel = titleIcon("▣", "输出面板", v -> toggleConsole());
            bar.addView(panel, size(dp(35), -1));
        }
        return bar;
    }

    private TextView topMenu(LinearLayout bar, String title, String... items) {
        TextView button = label(title, 12, Color.rgb(174, 174, 174), false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(7), 0, dp(7), 0);
        interactive(button, v -> {
            PopupMenu menu = new PopupMenu(this, button);
            for (int i = 0; i < items.length; i++) menu.getMenu().add(0, i, i, items[i]);
            menu.setOnMenuItemClickListener(item -> { handleMenuItem(items[item.getItemId()]); return true; });
            menu.show();
        });
        bar.addView(button, size(-2, -1));
        return button;
    }

    private TextView titleIcon(String symbol, String description, View.OnClickListener listener) {
        TextView icon = label(symbol, 24, MUTED, false);
        icon.setGravity(Gravity.CENTER);
        icon.setContentDescription(description);
        interactive(icon, listener);
        return icon;
    }

    private void handleMenuItem(String item) {
        switch (item) {
            case "新建脚本": promptNew(); break;
            case "新建文件夹": promptNewFolder(); break;
            case "打开脚本": promptOpen(); break;
            case "打开文件夹": openFolder(); break;
            case "关闭文件夹": closeFolder(); break;
            case "保存脚本": saveScript(); break;
            case "删除当前文件": promptDeleteFile(filename); break;
            case "导入旧脚本": importScript(); break;
            case "全选": editor.onTextContextMenuItem(android.R.id.selectAll); break;
            case "剪切": editor.onTextContextMenuItem(android.R.id.cut); break;
            case "复制": editor.onTextContextMenuItem(android.R.id.copy); break;
            case "粘贴": editor.onTextContextMenuItem(android.R.id.paste); break;
            case "资源管理器": toggleSidebar(); break;
            case "显示/隐藏文件面板": toggleSidebar(); break;
            case "构建当前文件": buildCurrent(); break;
            case "检查当前文件": checkCurrent(); break;
            case "补全建议": completeCurrent(); break;
            case "小地图": minimap.setVisibility(minimap.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); break;
            case "字体大小": showFontSizeDialog(); break;
            case "运行输出":
            case "查看运行输出": if (consolePane.getVisibility() != View.VISIBLE) toggleConsole(); break;
            case "上一文件": navigateHistory(-1); break;
            case "下一文件": navigateHistory(1); break;
            case "跳转到行": promptGoToLine(); break;
            case "运行当前脚本": runScript(); break;
            case "开始调试": debugCurrent(); break;
            case "取消运行": cancelRun(); break;
            case "显示/隐藏输出": toggleConsole(); break;
            case "清空输出": outputView.setText(""); consoleState.setText("已清空"); break;
            case "关于": showEnvironment(); break;
            case "环境自检": probeRuntime(); break;
        }
    }

    private LinearLayout buildStatusBar() {
        if (modernUi()) {
            LinearLayout bar = row(CHROME);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(14), 0, dp(14), 0);
            TextView tasks = label("任务 / 问题", 11, MUTED, false);
            interactive(tasks, v -> showTasks());
            bar.addView(tasks);
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            cursorView = label("行 1，列 1", 11, MUTED, false);
            bar.addView(cursorView);
            TextView details = label("  ·  UTF-8  ·  4 空格", 11, MUTED, false);
            bar.addView(details);
            return bar;
        }
        LinearLayout bar = row(ACCENT);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), 0, dp(10), 0);
        TextView diagnostics = label("◁    ⊗ 0  △ 0", 11, Color.WHITE, false);
        interactive(diagnostics, v -> toggleConsole());
        bar.addView(diagnostics);
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        cursorView = label("行 1，列 1", 11, Color.WHITE, false);
        bar.addView(cursorView);
        if (!compact) {
            bar.addView(statusItem("空格: 4"));
            bar.addView(statusItem("UTF-8"));
            bar.addView(statusItem("LF"));
        }
        bar.addView(statusItem(statusSummary()));
        return bar;
    }

    private TextView statusItem(String text) {
        TextView item = label(text, 11, Color.WHITE, false);
        item.setPadding(dp(13), 0, 0, 0);
        return item;
    }

    private View railButton(int iconId, String description, boolean selected,
            View.OnClickListener listener) {
        LinearLayout item = row(RAIL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        View marker = new View(this);
        marker.setBackgroundColor(selected ? Color.WHITE : Color.TRANSPARENT);
        item.addView(marker, size(dp(2), -1));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconId);
        icon.setColorFilter(selected ? Color.WHITE : MUTED);
        LinearLayout.LayoutParams iconSize = size(dp(25), dp(25));
        iconSize.leftMargin = dp(10);
        item.addView(icon, iconSize);
        item.setContentDescription(description);
        interactive(item, listener);
        return item;
    }

    private void showEnvironment() {
        new AlertDialog.Builder(this).setTitle("运行环境")
                .setMessage(environmentSummary())
                .setNeutralButton(hasRuntime() ? "运行自检" : "了解", (dialog, which) -> {
                    if (hasRuntime()) probeRuntime();
                })
                .setPositiveButton("确定", null).show();
    }

    private void probeRuntime() {
        if (!hasRuntime()) { showError("未安装运行语言包；当前仅可编辑和保存文件"); return; }
        if (consolePane.getVisibility() != View.VISIBLE) toggleConsole();
        consoleState.setText("环境自检中…");
        outputView.setText(probeProgressText());
        if (modernUi()) { consoleTab = "输出"; lastOutput = probeProgressText(); }
        runner.execute(() -> {
            String report;
            boolean ok = false;
            try {
                report = probeBackend();
                JSONObject json = new JSONObject(report);
                ok = json.optBoolean("ok");
                if (ok) {
                    report = formatProbe(json);
                } else report = json.optString("error", "自检失败");
            } catch (Throwable e) { report = "解释器错误：" + e.getMessage(); }
            String finalReport = report;
            boolean finalOk = ok;
            runOnUiThread(() -> {
                outputView.setText(finalReport);
                if (modernUi()) {
                    lastOutput = finalReport;
                    if (!finalOk) lastProblem = finalReport;
                    if (!"输出".equals(consoleTab)) showConsoleTab(consoleTab);
                }
                consoleState.setText(finalOk ? "自检通过" : "自检失败");
            });
        });
    }

    private void showLibraries() {
        new AlertDialog.Builder(this).setTitle("可用语言能力")
                .setMessage(librariesSummary())
                .setPositiveButton("确定", null).show();
    }

    private void showOutline() {
        String[] lines = editor.getText().toString().split("\n", -1);
        ArrayList<String> symbols = new ArrayList<>();
        ArrayList<Integer> offsets = new ArrayList<>();
        int offset = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("def ") || trimmed.startsWith("class ")) {
                symbols.add(trimmed);
                offsets.add(offset);
            }
            offset += line.length() + 1;
        }
        if (symbols.isEmpty()) {
            Toast.makeText(this, "当前脚本没有函数或类", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("大纲")
                .setItems(symbols.toArray(new String[0]), (dialog, which) -> {
                    editor.requestFocus();
                    editor.setSelection(Math.min(offsets.get(which), editor.length()));
                }).show();
    }

    private void showSettings() {
        if (!modernUi()) { showFontSizeDialog(); return; }
        String[] choices = {"编辑器字体大小", "浅色主题", "深色主题", "主体色：松绿",
                "主体色：陶土", "主体色：靛蓝", "选择工作区背景", "背景效果", "重置背景",
                "文件面板宽度", "输出面板高度", "快捷键", "导入编辑器字体",
                "恢复默认编辑器字体", "代码高亮配色"};
        new AlertDialog.Builder(this).setTitle("外观与编辑")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) showFontSizeDialog();
                    else if (which == 1 || which == 2) {
                        if (!saveScript(false)) return;
                        wallpaperCache().delete();
                        getPreferences(MODE_PRIVATE).edit().putString("ide_theme",
                                which == 1 ? "light" : "dark").apply();
                        recreate();
                    } else if (which >= 3 && which <= 5) {
                        if (!saveScript(false)) return;
                        getPreferences(MODE_PRIVATE).edit().putString("ide_accent",
                                new String[]{"forest", "clay", "indigo"}[which - 3]).apply();
                        recreate();
                    } else if (which == 6) chooseWallpaper();
                    else if (which == 7) showWallpaperSettings();
                    else if (which == 8) resetWallpaper();
                    else if (which == 9 || which == 10) showPanelSizeDialog(which == 9);
                    else if (which == 11) showShortcuts();
                    else if (which == 12) chooseEditorFont();
                    else if (which == 13) resetEditorFont();
                    else showSyntaxPaletteDialog();
                }).show();
    }

    private void showPanelSizeDialog(boolean sidebarPanel) {
        String[] labels = sidebarPanel ? new String[]{"窄 · 190 dp", "标准 · 242 dp", "宽 · 310 dp"}
                : new String[]{"紧凑 · 150 dp", "标准 · 230 dp", "扩展 · 340 dp"};
        int[] values = sidebarPanel ? new int[]{190, 242, 310} : new int[]{150, 230, 340};
        new AlertDialog.Builder(this).setTitle(sidebarPanel ? "文件面板宽度" : "输出面板高度")
                .setItems(labels, (dialog, which) -> {
                    if (sidebarPanel) {
                        sidebarWidthDp = values[which];
                        sidebar.getLayoutParams().width = dp(sidebarWidthDp);
                        sidebar.requestLayout();
                        getPreferences(MODE_PRIVATE).edit()
                                .putInt("ide_sidebar_dp", sidebarWidthDp).apply();
                    } else {
                        consoleHeightDp = values[which];
                        consolePane.getLayoutParams().height = dp(consoleHeightDp);
                        consolePane.requestLayout();
                        getPreferences(MODE_PRIVATE).edit()
                                .putInt("ide_console_dp", consoleHeightDp).apply();
                    }
                }).show();
    }

    private void showShortcuts() {
        new AlertDialog.Builder(this).setTitle("实体键盘快捷键")
                .setMessage("Ctrl+N 新建 · Ctrl+O 打开 · Ctrl+S 保存\n"
                        + "Ctrl+P 文件切换 · Ctrl+Tab / Ctrl+Shift+Tab 标签切换\n"
                        + "Ctrl+F 当前文件查找 · Ctrl+Shift+F 项目搜索\n"
                        + "Ctrl+B 文件面板 · Ctrl+J 输出面板\n"
                        + "Ctrl+Shift+B 构建 · Ctrl+Enter / F5 运行\n"
                        + "Alt+1 编辑器 · Alt+2 文件树 · Alt+3 输出\n"
                        + "补全：Ctrl+Space，↑↓ 选择，Enter 插入，Esc 关闭；Tab 缩进")
                .setPositiveButton("关闭", null).show();
    }

    private void chooseWallpaper() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, WALLPAPER_REQUEST); }
        catch (Exception problem) { showError("设备没有可用的图片选择器"); }
    }

    private void showWallpaperSettings() {
        String[] keys = {"ide_wallpaper_blur", "ide_wallpaper_fog",
                "ide_wallpaper_desaturate", "ide_wallpaper_opacity"};
        String[] names = {"模糊", "雾化", "降饱和度", "不透明度"};
        int[] defaults = {20, 45, 30, 35};
        LinearLayout controls = column(SURFACE);
        controls.setPadding(dp(20), dp(12), dp(20), dp(8));
        SeekBar[] sliders = new SeekBar[4];
        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView heading = label(names[i] + "：" + getPreferences(MODE_PRIVATE)
                    .getInt(keys[i], defaults[i]) + "%", 13, TEXT, false);
            controls.addView(heading);
            SeekBar slider = new SeekBar(this);
            slider.setMax(100);
            slider.setProgress(getPreferences(MODE_PRIVATE).getInt(keys[i], defaults[i]));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                    heading.setText(names[index] + "：" + value + "%");
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
            controls.addView(slider, size(-1, dp(40)));
            sliders[i] = slider;
        }
        android.widget.CheckBox lowPower = new android.widget.CheckBox(this);
        lowPower.setText("低性能模式：关闭模糊与调色效果");
        lowPower.setTextColor(TEXT);
        lowPower.setChecked(getPreferences(MODE_PRIVATE).getBoolean("ide_wallpaper_low_power", false));
        controls.addView(lowPower);
        new AlertDialog.Builder(this).setTitle("工作区背景")
                .setView(controls).setNegativeButton("取消", null)
                .setPositiveButton("应用", (dialog, which) -> {
                    android.content.SharedPreferences.Editor edit = getPreferences(MODE_PRIVATE).edit();
                    for (int i = 0; i < 4; i++) edit.putInt(keys[i], sliders[i].getProgress());
                    edit.putBoolean("ide_wallpaper_low_power", lowPower.isChecked()).apply();
                    wallpaperCache().delete();
                    loadWallpaper();
                }).show();
    }

    private File wallpaperCache() { return new File(getCacheDir(), "ide-wallpaper.png"); }

    private void resetWallpaper() {
        wallpaperGeneration++;
        getPreferences(MODE_PRIVATE).edit().remove("ide_wallpaper_uri").apply();
        if (wallpaperView != null) wallpaperView.setImageDrawable(null);
        wallpaperWorker.submit(() -> { if (wallpaperCache().exists()) wallpaperCache().delete(); });
    }

    private void loadWallpaper() {
        if (!modernUi() || wallpaperView == null) return;
        String stored = getPreferences(MODE_PRIVATE).getString("ide_wallpaper_uri", null);
        if (stored == null) { wallpaperView.setImageDrawable(null); return; }
        int generation = ++wallpaperGeneration;
        wallpaperWorker.submit(() -> {
            try {
                Uri uri = Uri.parse(stored);
                if (wallpaperCache().isFile()) {
                    try (InputStream check = getContentResolver().openInputStream(uri)) {
                        if (check == null) throw new IOException("图片授权失效");
                    }
                    Bitmap cached = BitmapFactory.decodeFile(wallpaperCache().getAbsolutePath());
                    if (cached != null) {
                        runOnUiThread(() -> {
                            if (generation == wallpaperGeneration && wallpaperView != null)
                                wallpaperView.setImageDrawable(new BitmapDrawable(getResources(), cached));
                            else cached.recycle();
                        });
                        return;
                    }
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IOException("图片授权失效");
                    BitmapFactory.decodeStream(stream, null, bounds);
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("图片格式不可读取");
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (bounds.outWidth / options.inSampleSize > 1280
                        || bounds.outHeight / options.inSampleSize > 1280)
                    options.inSampleSize *= 2;
                Bitmap decoded;
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IOException("图片授权失效");
                    decoded = BitmapFactory.decodeStream(stream, null, options);
                }
                if (decoded == null) throw new IOException("图片解码失败");
                int width = Math.min(960, decoded.getWidth());
                int height = Math.max(1, Math.round(decoded.getHeight() * (width / (float) decoded.getWidth())));
                Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
                if (scaled != decoded) decoded.recycle();
                boolean lowPower = getPreferences(MODE_PRIVATE).getBoolean("ide_wallpaper_low_power", false);
                int blur = getPreferences(MODE_PRIVATE).getInt("ide_wallpaper_blur", 20);
                Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(result);
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                int desaturate = lowPower ? 0 : getPreferences(MODE_PRIVATE)
                        .getInt("ide_wallpaper_desaturate", 30);
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(1f - desaturate / 100f);
                paint.setColorFilter(new ColorMatrixColorFilter(matrix));
                paint.setAlpha(Math.round(255 * getPreferences(MODE_PRIVATE)
                        .getInt("ide_wallpaper_opacity", 35) / 100f));
                canvas.drawBitmap(scaled, 0, 0, paint);
                scaled.recycle();
                if (!lowPower && blur > 0) blurBitmap(result, 1 + blur / 14);
                int fog = getPreferences(MODE_PRIVATE).getInt("ide_wallpaper_fog", 45);
                boolean light = "light".equals(getPreferences(MODE_PRIVATE).getString("ide_theme", "dark"));
                int minimum = light ? 215 : 200;
                int alpha = Math.max(minimum, Math.round(fog * 255 / 100f));
                canvas.drawColor(Color.argb(alpha, Color.red(EDITOR), Color.green(EDITOR), Color.blue(EDITOR)));
                if (generation != wallpaperGeneration) { result.recycle(); return; }
                try (FileOutputStream stream = new FileOutputStream(wallpaperCache())) {
                    result.compress(Bitmap.CompressFormat.PNG, 90, stream);
                }
                runOnUiThread(() -> {
                    if (generation == wallpaperGeneration && wallpaperView != null)
                        wallpaperView.setImageDrawable(new BitmapDrawable(getResources(), result));
                    else result.recycle();
                });
            } catch (Exception problem) {
                runOnUiThread(() -> {
                    if (generation != wallpaperGeneration) return;
                    wallpaperView.setImageDrawable(null);
                    getPreferences(MODE_PRIVATE).edit().remove("ide_wallpaper_uri").apply();
                    Toast.makeText(this, "背景图片不可读取，已恢复纯色", Toast.LENGTH_LONG).show();
                });
                wallpaperCache().delete();
            }
        });
    }

    private static void blurBitmap(Bitmap bitmap, int radius) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] source = new int[width * height], target = new int[source.length];
        bitmap.getPixels(source, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int a = 0, r = 0, g = 0, b = 0, count = 0;
            for (int k = Math.max(0, x - radius); k <= Math.min(width - 1, x + radius); k++) {
                int pixel = source[y * width + k];
                a += Color.alpha(pixel); r += Color.red(pixel);
                g += Color.green(pixel); b += Color.blue(pixel); count++;
            }
            target[y * width + x] = Color.argb(a / count, r / count, g / count, b / count);
        }
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int a = 0, r = 0, g = 0, b = 0, count = 0;
            for (int k = Math.max(0, y - radius); k <= Math.min(height - 1, y + radius); k++) {
                int pixel = target[k * width + x];
                a += Color.alpha(pixel); r += Color.red(pixel);
                g += Color.green(pixel); b += Color.blue(pixel); count++;
            }
            source[y * width + x] = Color.argb(a / count, r / count, g / count, b / count);
        }
        bitmap.setPixels(source, 0, width, 0, 0, width, height);
    }

    private void applyFontSize(int sizeSp) {
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        lineNumbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    private File editorFontFile() { return new File(getFilesDir(), "editor-font"); }

    private Typeface editorTypeface() {
        if (!modernUi() || !editorFontFile().isFile()) return Typeface.MONOSPACE;
        try { return Typeface.createFromFile(editorFontFile()); }
        catch (RuntimeException invalid) { return Typeface.MONOSPACE; }
    }

    private void applyEditorTypeface() {
        Typeface face = editorTypeface();
        editor.setTypeface(face);
        lineNumbers.setTypeface(face);
    }

    private void chooseEditorFont() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, FONT_REQUEST); }
        catch (Exception failed) { showError("设备没有可用的字体文件选择器"); }
    }

    private void importEditorFont(Uri uri) {
        fileWorker.execute(() -> {
            File temporary = new File(getFilesDir(), "editor-font.pending");
            try {
                String name = null;
                try (Cursor cursor = getContentResolver().query(uri,
                        new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                }
                if (name == null || !name.toLowerCase(java.util.Locale.ROOT)
                        .matches(".*\\.(ttf|otf)"))
                    throw new IOException("请选择 .ttf 或 .otf 字体文件");
                long length = 0;
                try (InputStream input = getContentResolver().openInputStream(uri);
                        FileOutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) throw new IOException("字体文件无法读取");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        length += read;
                        if (length > 10 * 1024 * 1024)
                            throw new IOException("字体文件超过 10 MiB");
                        output.write(buffer, 0, read);
                    }
                }
                if (length == 0) throw new IOException("字体文件为空");
                Typeface.createFromFile(temporary);
                Files.move(temporary.toPath(), editorFontFile().toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                runOnUiThread(() -> {
                    applyEditorTypeface();
                    refreshDebugGutter();
                    Toast.makeText(this, "已导入编辑器字体", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception failed) {
                runOnUiThread(() -> showError("导入字体失败：" + failed.getMessage()));
            } finally { temporary.delete(); }
        });
    }

    private void resetEditorFont() {
        if (editorFontFile().exists() && !editorFontFile().delete()) {
            showError("无法移除已导入的字体"); return;
        }
        applyEditorTypeface();
        refreshDebugGutter();
        Toast.makeText(this, "已恢复默认编辑器字体", Toast.LENGTH_SHORT).show();
    }

    private void applySyntaxPalette() {
        if (editor == null || syntaxPalette == null) return;
        editor.setTextColor(syntaxPalette.plain);
        String name = filename.toLowerCase(java.util.Locale.ROOT);
        boolean light = "light".equals(getPreferences(MODE_PRIVATE)
                .getString("ide_theme", "dark"));
        if (name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".cc")
                || name.endsWith(".cxx") || name.endsWith(".h") || name.endsWith(".hpp")) {
            if (customSyntaxPalette) CppHighlighter.apply(editor.getText(), syntaxPalette);
            else CppHighlighter.apply(editor.getText(), light);
        } else {
            if (customSyntaxPalette) PythonHighlighter.apply(editor.getText(), syntaxPalette);
            else PythonHighlighter.apply(editor.getText(), light);
        }
    }

    private void showSyntaxPaletteDialog() {
        boolean light = "light".equals(getPreferences(MODE_PRIVATE)
                .getString("ide_theme", "dark"));
        SyntaxPalette current = syntaxPalette == null ? SyntaxPalette.defaults(light) : syntaxPalette;
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(SURFACE);
        content.setPadding(dp(18), dp(10), dp(18), dp(10));
        TextView hint = label((light ? "浅色" : "深色")
                + "主题的代码颜色 · 输入 #RRGGBB", 12, MUTED, false);
        content.addView(hint);
        EditText[] inputs = new EditText[SyntaxPalette.KEYS.length];
        int[] colors = current.colors();
        for (int i = 0; i < inputs.length; i++) {
            LinearLayout row = row(SURFACE);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView swatch = label("● " + SyntaxPalette.NAMES[i], 13, colors[i], false);
            row.addView(swatch, new LinearLayout.LayoutParams(0, dp(48), 1));
            swatch.setGravity(Gravity.CENTER_VERTICAL);
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setText(SyntaxPalette.hex(colors[i]));
            input.setTextSize(13);
            input.setTextColor(TEXT);
            input.setTypeface(Typeface.MONOSPACE);
            input.setSelectAllOnFocus(true);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count,
                        int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before,
                        int count) {
                    try { swatch.setTextColor(SyntaxPalette.parse(s.toString())); }
                    catch (IllegalArgumentException ignored) { swatch.setTextColor(MUTED); }
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            inputs[i] = input;
            row.addView(input, size(dp(115), dp(48)));
            content.addView(row);
        }
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("代码高亮配色")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (ignored, which) -> {
                    getPreferences(MODE_PRIVATE).edit().remove(light
                            ? SyntaxPalette.PREF_LIGHT : SyntaxPalette.PREF_DARK).apply();
                    customSyntaxPalette = false;
                    syntaxPalette = SyntaxPalette.defaults(light);
                    applySyntaxPalette();
                })
                .setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    int[] selected = new int[inputs.length];
                    for (int i = 0; i < inputs.length; i++) {
                        try { selected[i] = SyntaxPalette.parse(
                                inputs[i].getText().toString().trim()); }
                        catch (IllegalArgumentException invalid) {
                            inputs[i].setError("请输入 #RRGGBB");
                            inputs[i].requestFocus();
                            return;
                        }
                    }
                    syntaxPalette = new SyntaxPalette(selected[0], selected[1], selected[2],
                            selected[3], selected[4], selected[5]);
                    customSyntaxPalette = true;
                    getPreferences(MODE_PRIVATE).edit().putString(light
                            ? SyntaxPalette.PREF_LIGHT : SyntaxPalette.PREF_DARK,
                            syntaxPalette.json()).apply();
                    applySyntaxPalette();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showFontSizeDialog() {
        int original = getPreferences(MODE_PRIVATE).getInt(FONT_SIZE_PREF, 15);
        LinearLayout content = column(SURFACE);
        content.setPadding(dp(24), dp(15), dp(24), dp(13));
        TextView sizeLabel = label(original + " sp", 15, TEXT, true);
        content.addView(sizeLabel);
        SeekBar slider = new SeekBar(this);
        slider.setMin(10);
        slider.setMax(32);
        slider.setProgress(original);
        content.addView(slider, size(-1, dp(51)));
        TextView preview = label("def hello():\n    print(\"Hello, Python\")", original, TEXT, false);
        preview.setTypeface(editorTypeface());
        preview.setPadding(dp(10), dp(9), dp(10), dp(9));
        preview.setBackground(rounded(EDITOR, 5));
        content.addView(preview, size(-1, -2));
        TextView reset = label("恢复默认 · 15 sp", 12, modernUi() ? ACCENT
                : Color.rgb(92, 180, 231), false);
        reset.setPadding(0, dp(14), 0, 0);
        interactive(reset, view -> slider.setProgress(15));
        content.addView(reset);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                sizeLabel.setText(value + " sp");
                preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, value);
                applyFontSize(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        new AlertDialog.Builder(this).setTitle("编辑器字体大小")
                .setView(content)
                .setNegativeButton("取消", (dialog, which) -> applyFontSize(original))
                .setPositiveButton("保存", (dialog, which) -> getPreferences(MODE_PRIVATE)
                        .edit().putInt(FONT_SIZE_PREF, slider.getProgress()).apply())
                .setOnCancelListener(dialog -> applyFontSize(original))
                .show();
    }

    private void promptSearch() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("在当前脚本中查找");
        new AlertDialog.Builder(this).setTitle("搜索")
                .setView(input).setNegativeButton("取消", null)
                .setPositiveButton("查找", (dialog, which) -> {
                    String needle = input.getText().toString();
                    if (needle.isEmpty()) return;
                    int start = Math.max(0, editor.getSelectionEnd());
                    String code = editor.getText().toString();
                    int found = code.indexOf(needle, start);
                    if (found < 0) found = code.indexOf(needle);
                    if (found >= 0) {
                        editor.requestFocus();
                        editor.setSelection(found, found + needle.length());
                    } else Toast.makeText(this, "未找到：" + needle, Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void promptProjectSearch() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("文件名或文本内容");
        new AlertDialog.Builder(this).setTitle("搜索项目")
                .setView(input).setNegativeButton("取消", null)
                .setPositiveButton("搜索", (dialog, which) -> searchProject(input.getText().toString().trim()))
                .show();
    }

    private void searchProject(String needle) {
        if (needle.isEmpty()) return;
        Toast.makeText(this, "正在搜索项目…", Toast.LENGTH_SHORT).show();
        WorkspaceFiles selected = workspaceFiles;
        File local = scriptsDir;
        searchWorker.submit(() -> {
            ArrayList<String> matches = new ArrayList<>();
            try {
                if (selected != null) {
                    for (WorkspaceFiles.Entry entry : selected.listTree()) {
                        if (matches.size() >= 80) break;
                        if (entry.directory || !editableFile(entry.path)) continue;
                        if (entry.path.toLowerCase(java.util.Locale.ROOT).contains(
                                needle.toLowerCase(java.util.Locale.ROOT))) {
                            matches.add(entry.path);
                            continue;
                        }
                        try {
                            if (new String(selected.read(entry, 2 * 1024 * 1024),
                                    StandardCharsets.UTF_8).contains(needle)) matches.add(entry.path);
                        } catch (IOException ignored) { /* Skip oversized or unreadable files. */ }
                    }
                } else {
                    String[] names = local.list((dir, name) -> localFile(name));
                    if (names != null) for (String name : names) {
                        if (matches.size() >= 80) break;
                        if (name.toLowerCase(java.util.Locale.ROOT).contains(
                                needle.toLowerCase(java.util.Locale.ROOT))) {
                            matches.add(name);
                            continue;
                        }
                        if (Files.size(scriptFile(name).toPath()) <= 2 * 1024 * 1024
                                && new String(Files.readAllBytes(scriptFile(name).toPath()),
                                StandardCharsets.UTF_8).contains(needle)) matches.add(name);
                    }
                }
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("搜索：" + needle)
                        .setItems(matches.isEmpty() ? new String[]{"没有匹配文件"}
                                        : matches.toArray(new String[0]), (dialog, index) -> {
                            if (!matches.isEmpty() && saveScript(false)) {
                                expandParents(matches.get(index));
                                loadScript(matches.get(index));
                                int offset = editor.getText().toString().indexOf(needle);
                                if (offset >= 0) editor.setSelection(offset, offset + needle.length());
                            }
                        }).setNegativeButton("关闭", null).show());
            } catch (Exception problem) {
                runOnUiThread(() -> showError("项目搜索失败：" + problem.getMessage()));
            }
        });
    }

    private void showTasks() {
        boolean active = activeRun != null && !activeRun.isDone();
        StringBuilder text = new StringBuilder(active ? "当前：任务运行中，可在运行菜单取消\n\n"
                : "当前：没有活动任务\n\n");
        if (buildRecords.isEmpty()) text.append("还没有构建记录");
        else for (String record : buildRecords) text.append(record).append("\n");
        new AlertDialog.Builder(this).setTitle("任务与构建记录")
                .setMessage(text.toString())
                .setNegativeButton("关闭", null)
                .setPositiveButton(active ? "取消当前任务" : "查看输出", (d, w) -> {
                    if (active) cancelRun();
                    else showConsoleTab("输出");
                }).show();
    }

    private void promptGoToLine() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("行号");
        new AlertDialog.Builder(this).setTitle("转到行")
                .setView(input).setNegativeButton("取消", null)
                .setPositiveButton("转到", (dialog, which) -> {
                    try {
                        int target = Integer.parseInt(input.getText().toString());
                        int position = 0, line = 1;
                        String code = editor.getText().toString();
                        while (line < target && position < code.length())
                            if (code.charAt(position++) == '\n') line++;
                        editor.requestFocus();
                        editor.setSelection(position);
                    } catch (NumberFormatException ignored) { }
                }).show();
    }

    private void showHistory() {
        if (history.isEmpty()) { promptOpen(); return; }
        new AlertDialog.Builder(this).setTitle("最近打开")
                .setItems(history.toArray(new String[0]), (dialog, which) -> {
                    if (saveScript(false)) { historyIndex = which; loadScript(history.get(which), false); }
                }).show();
    }

    private void navigateHistory(int direction) {
        int next = historyIndex + direction;
        if (next < 0 || next >= history.size()) return;
        if (saveScript(false)) { historyIndex = next; loadScript(history.get(next), false); }
    }

    private void toggleConsole() {
        boolean show = consolePane.getVisibility() != View.VISIBLE;
        consolePane.setVisibility(show ? View.VISIBLE : View.GONE);
        if (modernUi()) {
            consoleDivider.setVisibility(show ? View.VISIBLE : View.GONE);
            getPreferences(MODE_PRIVATE).edit().putBoolean("ide_console_open", show).apply();
        }
    }

    private LinearLayout buildSidebar() {
        LinearLayout panel = column(SIDEBAR);
        if (modernUi()) {
            LinearLayout sections = row(SIDEBAR);
            sections.setGravity(Gravity.CENTER_VERTICAL);
            for (String title : new String[]{"文件", "搜索", "语言包", "任务"}) {
                TextView section = toolbarAction(title, v -> {
                    switch (title) {
                        case "文件": fileList.requestFocus(); break;
                        case "搜索": promptProjectSearch(); break;
                        case "语言包": showLanguagePacks(); break;
                        case "任务": showTasks(); break;
                    }
                });
                if ("搜索".equals(title)) section.setContentDescription("项目搜索");
                sections.addView(section, new LinearLayout.LayoutParams(0, dp(40), 1));
            }
            panel.addView(sections);
            panel.addView(divider(false));
        }
        LinearLayout header = row(SIDEBAR);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(modernUi() ? "工作区文件" : "资源管理器", 12, TEXT, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, -1, 1));
        TextView more = label("⋯", 19, MUTED, false);
        more.setGravity(Gravity.CENTER);
        interactive(more, v -> {
            PopupMenu menu = new PopupMenu(this, more);
            menu.getMenu().add("新建脚本");
            menu.getMenu().add("新建文件夹");
            menu.getMenu().add("打开脚本");
            menu.getMenu().add("打开文件夹");
            menu.getMenu().add("关闭文件夹");
            menu.setOnMenuItemClickListener(item -> {
                handleMenuItem(item.getTitle().toString());
                return true;
            });
            menu.show();
        });
        header.addView(more, size(dp(40), -1));
        panel.addView(header, size(-1, dp(42)));
        LinearLayout folderRow = row(SIDEBAR);
        folderRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView folder = label("⌄  scripts", 12, TEXT, true);
        folderLabel = folder;
        folder.setPadding(dp(16), 0, 0, 0);
        folderRow.addView(folder, new LinearLayout.LayoutParams(0, -1, 1));
        TextView add = label("＋", 19, MUTED, false);
        add.setGravity(Gravity.CENTER);
        add.setContentDescription("新建脚本");
        interactive(add, v -> promptNew());
        folderRow.addView(add, size(dp(36), -1));
        panel.addView(folderRow, size(-1, dp(32)));
        ScrollView fileScroll = new ScrollView(this);
        fileScroll.setFillViewport(true);
        fileScroll.setVerticalScrollBarEnabled(true);
        fileList = column(SIDEBAR);
        fileList.setFocusable(true);
        fileScroll.addView(fileList);
        interactive(folder, v -> fileList.setVisibility(
                fileList.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        panel.addView(fileScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.addView(divider(false));
        if (modernUi()) {
            TextView task = label("任务与最近结果", 12, TEXT, true);
            task.setPadding(dp(16), dp(12), 0, dp(12));
            interactive(task, v -> showTasks());
            panel.addView(task, size(-1, dp(40)));
        }
        TextView outline = label("›  大纲", 12, TEXT, true);
        outline.setGravity(Gravity.CENTER_VERTICAL);
        outline.setPadding(dp(16), 0, 0, 0);
        interactive(outline, v -> showOutline());
        panel.addView(outline, size(-1, dp(35)));
        panel.addView(divider(false));
        TextView timeline = label("›  时间线", 12, TEXT, true);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        timeline.setPadding(dp(16), 0, 0, 0);
        interactive(timeline, v -> showHistory());
        panel.addView(timeline, size(-1, dp(35)));
        return panel;
    }

    private LinearLayout buildEditorPane() {
        LinearLayout pane = column(EDITOR);
        if (modernUi()) return buildModernEditorPane(pane);
        LinearLayout tabBar = row(SURFACE);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        filenameView = label("Py  main.py", 12, TEXT, false);
        filenameView.setGravity(Gravity.CENTER_VERTICAL);
        filenameView.setPadding(dp(12), 0, dp(8), 0);
        filenameView.setBackgroundColor(EDITOR);
        tabBar.addView(filenameView, size(-2, -1));
        TextView close = label("×", 19, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundColor(EDITOR);
        close.setContentDescription("关闭当前标签");
        interactive(close, v -> {
            if (saveScript(false)) {
                editorArea.setVisibility(View.INVISIBLE);
                filenameView.setText("未打开文件");
                breadcrumbView.setText("");
            }
        });
        tabBar.addView(close, size(dp(29), -1));
        tabBar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView mapToggle = titleIcon("◫", "小地图", v -> minimap.setVisibility(
                minimap.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        tabBar.addView(mapToggle, size(dp(37), -1));
        TextView more = titleIcon("⋯", "更多编辑器操作", v -> promptOpen());
        tabBar.addView(more, size(dp(37), -1));
        pane.addView(tabBar, size(-1, dp(36)));
        pane.addView(divider(false));
        breadcrumbView = label("Py  main.py", 12, Color.rgb(188, 188, 188), false);
        breadcrumbView.setGravity(Gravity.CENTER_VERTICAL);
        breadcrumbView.setPadding(dp(14), 0, 0, 0);
        pane.addView(breadcrumbView, size(-1, dp(26)));

        LinearLayout codeRow = createCodeRow();
        pane.addView(codeRow, new LinearLayout.LayoutParams(-1, 0, 1));
        return pane;
    }

    private LinearLayout createCodeRow() {
        LinearLayout codeRow = row(modernUi() ? Color.TRANSPARENT : EDITOR);
        editorArea = codeRow;
        int fontSize = getPreferences(MODE_PRIVATE).getInt(FONT_SIZE_PREF, 15);
        lineNumbers = label("1", fontSize, modernUi() ? MUTED : Color.rgb(38, 158, 203), false);
        lineNumbers.setTypeface(Typeface.MONOSPACE);
        lineNumbers.setGravity(Gravity.TOP | Gravity.RIGHT);
        lineNumbers.setPadding(0, dp(7), dp(15), dp(7));
        if (modernUi()) lineNumbers.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downY = event.getY(); return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (Math.abs(event.getY() - downY) < dp(12)
                            && canDebugFile(filename) && lineNumbers.getLayout() != null) {
                        int row = lineNumbers.getLayout().getLineForVertical(
                                Math.round(event.getY()) + lineNumbers.getScrollY()
                                        - lineNumbers.getTotalPaddingTop());
                        toggleBreakpoint(filename, row + 1, editor.getText().toString(),
                                workspaceKey());
                        refreshDebugGutter();
                    }
                    return true;
                }
                return true;
            }
        });
        codeRow.addView(lineNumbers, size(dp(modernUi() ? 72 : 57), -1));
        EditorEditText code = new EditorEditText(this);
        if (modernUi()) code.setLineHighlightColor("light".equals(
                getPreferences(MODE_PRIVATE).getString("ide_theme", "dark"))
                ? Color.argb(35, 31, 126, 101) : Color.argb(50, 107, 148, 131));
        editor = code;
        completions = new EditorCompletionPopup(this, code, completionCatalog());
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(fontSize);
        editor.setTextColor(modernUi() && syntaxPalette != null ? syntaxPalette.plain : TEXT);
        editor.setHintTextColor(MUTED);
        editor.setHint(modernUi() ? "在这里编写代码" : "# 在这里编写 Python 代码");
        editor.setBackgroundColor(modernUi() ? Color.TRANSPARENT : EDITOR);
        editor.setPadding(dp(5), dp(7), dp(22), dp(7));
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setTypeface(Typeface.MONOSPACE);
        if (modernUi()) applyEditorTypeface();
        editor.setIncludeFontPadding(false);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(true);
        editor.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            lineNumbers.scrollTo(0, y);
            if (minimap != null) minimap.invalidate();
            if (completions != null) completions.dismiss();
        });
        code.setSelectionListener(() -> { updateCursor(); scheduleCompletion(); });
        code.setCompletionKeyListener((keyCode, event) -> completions.onKey(keyCode, event));
        code.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) completions.dismiss();
        });
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLineNumbers(s);
            }
            @Override public void afterTextChanged(Editable s) {
                boolean light = modernUi() && "light".equals(
                        getPreferences(MODE_PRIVATE).getString("ide_theme", "dark"));
                if (filename.endsWith(".c") || filename.endsWith(".cpp")
                        || filename.endsWith(".cc") || filename.endsWith(".cxx")
                        || filename.endsWith(".h") || filename.endsWith(".hpp")) {
                    if (modernUi() && customSyntaxPalette)
                        CppHighlighter.apply(s, syntaxPalette);
                    else CppHighlighter.apply(s, light);
                } else {
                    if (modernUi() && customSyntaxPalette)
                        PythonHighlighter.apply(s, syntaxPalette);
                    else PythonHighlighter.apply(s, light);
                }
                if (minimap != null) minimap.setSource(s.toString());
                updateCursor();
                scheduleCompletion();
            }
        });
        codeRow.addView(editor, new LinearLayout.LayoutParams(0, -1, 1));
        minimap = new CodeMinimapView(this);
        minimap.setEditor(editor);
        if (modernUi()) minimap.setVisibility(View.GONE);
        if (!compact) codeRow.addView(minimap, size(dp(65), -1));
        return codeRow;
    }

    private LinearLayout buildModernEditorPane(LinearLayout pane) {
        LinearLayout tabBar = row(SURFACE);
        android.widget.HorizontalScrollView tabScroll = new android.widget.HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabs = row(SURFACE);
        tabScroll.addView(tabs);
        tabBar.addView(tabScroll, new LinearLayout.LayoutParams(0, -1, 1));
        TextView add = toolbarAction("＋", v -> promptNew());
        add.setContentDescription("新建文件");
        tabBar.addView(add, size(dp(42), -1));
        pane.addView(tabBar, size(-1, dp(39)));
        pane.addView(divider(false));
        debugControls = createDebugControls();
        if (debugControls != null) {
            debugControls.setVisibility(View.GONE);
            pane.addView(debugControls, size(-1, dp(38)));
        }
        filenameView = label("", 12, MUTED, false);
        filenameView.setVisibility(View.GONE);
        breadcrumbView = label("", 12, MUTED, false);
        breadcrumbView.setGravity(Gravity.CENTER_VERTICAL);
        breadcrumbView.setPadding(dp(17), 0, 0, 0);
        pane.addView(breadcrumbView, size(-1, dp(30)));
        editorBackdrop = new FrameLayout(this);
        editorBackdrop.setBackgroundColor(EDITOR);
        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        editorBackdrop.addView(wallpaperView, new FrameLayout.LayoutParams(-1, -1));
        emptyHint = label("打开工作区或新建文件，开始编辑", 16, MUTED, false);
        emptyHint.setGravity(Gravity.CENTER);
        editorBackdrop.addView(emptyHint, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout codeRow = createCodeRow();
        editorArea = codeRow;
        editorBackdrop.addView(codeRow, new FrameLayout.LayoutParams(-1, -1));
        pane.addView(editorBackdrop, new LinearLayout.LayoutParams(-1, 0, 1));
        return pane;
    }

    private LinearLayout buildConsolePane() {
        if (modernUi()) return buildModernConsolePane();
        LinearLayout pane = column(EDITOR);
        pane.addView(divider(false));
        LinearLayout heading = row(EDITOR);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("问题      输出      终端", 12, TEXT, true);
        title.setPadding(dp(18), 0, 0, 0);
        heading.addView(title);
        heading.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        consoleState = label("就绪", 11, MUTED, false);
        heading.addView(consoleState);
        TextView clear = label("清空", 12, MUTED, false);
        clear.setGravity(Gravity.CENTER);
        interactive(clear, v -> { outputView.setText(""); consoleState.setText("已清空"); });
        heading.addView(clear, size(dp(64), -1));
        TextView close = label("×", 21, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭输出面板");
        interactive(close, v -> toggleConsole());
        heading.addView(close, size(dp(42), -1));
        pane.addView(heading, size(-1, dp(38)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        outputView = label("", 13, TEXT, false);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextIsSelectable(true);
        outputView.setPadding(dp(18), dp(10), dp(16), dp(16));
        scroll.addView(outputView);
        pane.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return pane;
    }

    private LinearLayout buildModernConsolePane() {
        LinearLayout pane = column(SURFACE);
        LinearLayout heading = row(SURFACE);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        for (String name : new String[]{"输出", "问题", "构建记录", "调试"}) {
            TextView item = toolbarAction("调试".equals(name) ? "调试控制台" : name,
                    v -> showConsoleTab(name));
            heading.addView(item, size(-2, -1));
        }
        heading.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        consoleState = label("就绪", 11, MUTED, false);
        heading.addView(consoleState);
        cancelTaskButton = toolbarAction("取消任务", v -> cancelRun());
        heading.addView(cancelTaskButton, size(-2, -1));
        heading.addView(toolbarAction("清空", v -> {
            if ("调试".equals(consoleTab)) clearDebugOutput();
            else {
                lastOutput = ""; lastProblem = "尚无问题"; buildRecords.clear();
                showConsoleTab(consoleTab);
            }
        }), size(-2, -1));
        heading.addView(toolbarAction("关闭", v -> toggleConsole()), size(-2, -1));
        pane.addView(heading, size(-1, dp(40)));
        pane.addView(divider(false));
        consoleOutputScroll = new ScrollView(this);
        consoleOutputScroll.setFillViewport(true);
        outputView = label("", 13, TEXT, false);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextIsSelectable(true);
        outputView.setPadding(dp(18), dp(10), dp(16), dp(16));
        consoleOutputScroll.addView(outputView);
        pane.addView(consoleOutputScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        debugPanel = createDebugPanel();
        if (debugPanel != null) {
            debugPanel.setVisibility(View.GONE);
            pane.addView(debugPanel, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        return pane;
    }

    private void showConsoleTab(String name) {
        consoleTab = name;
        if (consolePane.getVisibility() != View.VISIBLE) toggleConsole();
        if (modernUi() && debugPanel != null && consolePane.getLayoutParams() != null) {
            int debugHeight = Math.min(Math.max(consoleHeightDp, 340),
                    Math.max(150, getResources().getConfiguration().screenHeightDp / 2));
            consolePane.getLayoutParams().height = dp("调试".equals(name)
                    ? debugHeight : consoleHeightDp);
            consolePane.requestLayout();
        }
        if (cancelTaskButton != null) cancelTaskButton.setVisibility(
                "调试".equals(name) ? View.GONE : View.VISIBLE);
        if ("调试".equals(name) && debugPanel != null) {
            consoleOutputScroll.setVisibility(View.GONE);
            debugPanel.setVisibility(View.VISIBLE);
            return;
        }
        if (debugPanel != null) debugPanel.setVisibility(View.GONE);
        if (consoleOutputScroll != null) consoleOutputScroll.setVisibility(View.VISIBLE);
        if ("输出".equals(name)) outputView.setText(lastOutput);
        else if ("问题".equals(name)) outputView.setText(lastProblem);
        else outputView.setText(buildRecords.isEmpty() ? "尚无构建记录" :
                android.text.TextUtils.join("\n\n", buildRecords));
    }

    private void toggleSidebar() {
        boolean visible = sidebar.getVisibility() == View.VISIBLE;
        sidebar.setVisibility(visible ? View.GONE : View.VISIBLE);
        fileDivider.setVisibility(visible ? View.GONE : View.VISIBLE);
        if (compact) workspace.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void refreshFiles() {
        if (fileList == null) return;
        int generation = ++fileRefreshGeneration;
        if (pendingFileRefresh != null) pendingFileRefresh.cancel(true);
        fileList.removeAllViews();
        if (workspaceFiles != null) {
            TextView loading = label("正在读取工作区…", 12, MUTED, false);
            loading.setPadding(dp(16), 0, 0, 0);
            fileList.addView(loading, size(-1, dp(40)));
            WorkspaceFiles selected = workspaceFiles;
            Set<String> expanded = new HashSet<>(expandedFolders);
            pendingFileRefresh = fileWorker.submit(() -> {
                try {
                    List<WorkspaceFiles.Entry> entries = selected.listVisibleTree(expanded);
                    runOnUiThread(() -> {
                        if (generation != fileRefreshGeneration || selected != workspaceFiles
                                || isFinishing() || isDestroyed()) return;
                        fileList.removeAllViews();
                        renderWorkspaceFiles(entries, 0, generation);
                    });
                } catch (IOException e) {
                    runOnUiThread(() -> {
                        if (generation == fileRefreshGeneration && selected == workspaceFiles
                                && !isFinishing() && !isDestroyed()) {
                            fileList.removeAllViews();
                            showError("读取工作区失败：" + e.getMessage());
                        }
                    });
                }
            });
            return;
        }
        renderLocalFiles();
    }

    private void renderWorkspaceFiles(List<WorkspaceFiles.Entry> entries, int start,
            int generation) {
                int end = Math.min(entries.size(), start + 80);
                for (int index = start; index < end; index++) {
                    WorkspaceFiles.Entry entry = entries.get(index);
                    if (!visibleInTree(entry.path)) continue;
                    boolean active = entry.path.equals(filename);
                    LinearLayout itemRow = row(SIDEBAR);
                    itemRow.setGravity(Gravity.CENTER_VERTICAL);
                    if (active) itemRow.setBackground(rounded(modernUi() ?
                            Color.argb(42, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT))
                            : Color.rgb(55, 55, 61), 4));
                    int depth = entry.path.split("/").length - 1;
                    String symbol = entry.directory
                            ? (expandedFolders.contains(entry.path) ? "⌄" : "›")
                            : (modernUi() ? fileMark(entry.name())
                            : (entry.path.endsWith(".py") ? "Py" : "▫"));
                    TextView icon = label(symbol, entry.directory ? 17 : 11,
                            entry.directory ? MUTED : (modernUi() ? ACCENT : Color.rgb(207, 164, 83)), true);
                    icon.setGravity(Gravity.CENTER);
                    itemRow.addView(icon, size(dp(29 + depth * 15), -1));
                    TextView item = label(entry.name(), 12, modernUi() ? TEXT
                                    : (active ? TEXT : Color.rgb(204, 204, 204)),
                            entry.directory);
                    item.setGravity(Gravity.CENTER_VERTICAL);
                    item.setSingleLine(true);
                    item.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    itemRow.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
                    interactive(itemRow, v -> {
                        if (entry.directory) {
                            currentFolder = entry.path;
                            if (!expandedFolders.add(entry.path)) expandedFolders.remove(entry.path);
                            refreshFiles();
                        } else if (!entry.path.equals(filename) && saveScript(false)) {
                            if (editableFile(entry.path)) loadScript(entry.path);
                            else showError("该文件不是受支持的文本类型，无法在编辑器中打开");
                            if (compact && sidebar.getVisibility() == View.VISIBLE) toggleSidebar();
                        }
                    });
                    if (modernUi() && !entry.directory) attachFileActions(itemRow, entry.path);
                    LinearLayout.LayoutParams rowSize = size(-1, dp(modernUi() ? 40 : 31));
                    rowSize.leftMargin = dp(7);
                    rowSize.rightMargin = dp(7);
                    fileList.addView(itemRow, rowSize);
                }
        if (end < entries.size()) fileList.post(() -> {
            if (generation == fileRefreshGeneration && !isFinishing() && !isDestroyed())
                renderWorkspaceFiles(entries, end, generation);
        });
    }

    private void renderLocalFiles() {
        String[] names = scriptsDir.list((dir, name) -> localFile(name));
        if (names == null) return;
        Arrays.sort(names);
        for (String name : names) {
            boolean active = name.equals(filename);
            LinearLayout row = row(SIDEBAR);
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (active) row.setBackground(rounded(modernUi() ?
                    Color.argb(42, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT))
                    : Color.rgb(55, 55, 61), 4));
            TextView icon = label(modernUi() ? fileMark(name) : "Py", 11,
                    modernUi() ? ACCENT : Color.rgb(207, 164, 83), true);
            icon.setGravity(Gravity.CENTER);
            row.addView(icon, size(dp(31), -1));
            TextView item = label(name, 12, modernUi() ? TEXT
                    : (active ? TEXT : Color.rgb(204, 204, 204)), false);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setSingleLine(true);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setPadding(dp(2), 0, dp(8), 0);
            row.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
            interactive(row, v -> {
                if (!name.equals(filename) && saveScript(false)) loadScript(name);
                if (compact) toggleSidebar();
            });
            if (modernUi()) attachFileActions(row, name);
            LinearLayout.LayoutParams itemSize = size(-1, dp(modernUi() ? 40 : 31));
            itemSize.leftMargin = dp(7);
            itemSize.rightMargin = dp(7);
            fileList.addView(row, itemSize);
        }
    }

    private void attachFileActions(View row, String path) {
        row.setOnLongClickListener(view -> {
            showFileActions(view, path);
            return true;
        });
        row.setOnContextClickListener(view -> {
            showFileActions(view, path);
            return true;
        });
    }

    private void showFileActions(View anchor, String path) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("删除文件…");
        menu.setOnMenuItemClickListener(item -> {
            promptDeleteFile(path);
            return true;
        });
        menu.show();
    }

    private boolean visibleInTree(String path) {
        int slash = path.lastIndexOf('/');
        while (slash >= 0) {
            if (!expandedFolders.contains(path.substring(0, slash))) return false;
            slash = path.lastIndexOf('/', slash - 1);
        }
        return true;
    }

    private String fileMark(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "DOC";
        String extension = name.substring(dot + 1).toUpperCase(java.util.Locale.ROOT);
        return extension.length() > 3 ? extension.substring(0, 3) : extension;
    }

    private void updateLineNumbers(CharSequence source) {
        int count = 1;
        for (int i = 0; i < source.length(); i++) if (source.charAt(i) == '\n') count++;
        StringBuilder numbers = new StringBuilder(count * 4);
        String key = modernUi() ? workspaceKey() : "";
        int active = modernUi() ? debugActiveLine(filename) : 0;
        for (int i = 1; i <= count; i++) {
            if (i > 1) numbers.append('\n');
            if (modernUi()) {
                numbers.append(active == i ? "▶" :
                        hasBreakpoint(filename, i, key) ? "●" : " ").append(' ');
            }
            numbers.append(i);
        }
        lineNumbers.setText(numbers);
    }

    private String workspaceKey() {
        return workspaceFiles == null ? "internal:scripts" : workspaceFiles.treeUri().toString();
    }

    private void updateCursor() {
        if (editor == null || cursorView == null) return;
        int position = Math.max(0, editor.getSelectionStart());
        CharSequence source = editor.getText();
        int line = 1, lastBreak = -1;
        for (int i = 0; i < Math.min(position, source.length()); i++) {
            if (source.charAt(i) == '\n') { line++; lastBreak = i; }
        }
        cursorView.setText("行 " + line + "，列 " + (position - lastBreak));
    }

    private void scheduleCompletion() {
        if (editor == null || completions == null || completions.isInserting()) return;
        editor.removeCallbacks(completionTask);
        completionGeneration++;
        if (pendingCompletion != null) pendingCompletion.cancel(true);
        if (!filename.endsWith(".py")) completions.dismiss();
        editor.postDelayed(completionTask, 120);
    }

    private void requestPackCompletion() {
        if (!editor.hasFocus() || editor.getSelectionStart() != editor.getSelectionEnd()) return;
        String name = filename;
        String source = editor.getText().toString();
        int cursor = editor.getSelectionStart();
        int start = cursor;
        while (start > 0 && (Character.isLetterOrDigit(source.charAt(start - 1))
                || source.charAt(start - 1) == '_')) start--;
        if (start == cursor || !Character.isLetter(source.charAt(start))
                && source.charAt(start) != '_') return;
        if (cursor < source.length() && (Character.isLetterOrDigit(source.charAt(cursor))
                || source.charAt(cursor) == '_')) return;
        int generation = ++completionGeneration;
        int wordStart = start;
        WorkspaceFiles selected = workspaceFiles;
        pendingCompletion = completionWorker.submit(() -> {
            File root = new File(getCacheDir(), "completion-" + generation + "-" + System.nanoTime());
            try {
                Files.createDirectories(root.toPath());
                if (selected != null) {
                    long bytes = 0;
                    int count = 0;
                    for (WorkspaceFiles.Entry file : selected.listTree()) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (file.directory || !completionSource(file.path) || file.size > 1024 * 1024)
                            continue;
                        if (++count > 100 || bytes + file.size > 6L * 1024 * 1024) break;
                        File target = new File(root, file.path);
                        if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator))
                            continue;
                        Files.createDirectories(target.getParentFile().toPath());
                        byte[] content = selected.read(file, 1024 * 1024);
                        bytes += content.length;
                        Files.write(target.toPath(), content);
                    }
                } else {
                    File[] files = scriptsDir.listFiles();
                    if (files != null) for (File file : files) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (file.isFile() && completionSource(file.getName())
                                && file.length() <= 1024 * 1024)
                            Files.copy(file.toPath(), new File(root, file.getName()).toPath());
                    }
                }
                if (Thread.currentThread().isInterrupted()) return;
                File entry = new File(root, name);
                Files.createDirectories(entry.getParentFile().toPath());
                Files.write(entry.toPath(), source.getBytes(StandardCharsets.UTF_8));
                JSONObject response = new JSONObject(completeBackend(entry.getAbsolutePath(),
                        root.getAbsolutePath(), cursor));
                if (!response.optBoolean("ok") || Thread.currentThread().isInterrupted()) return;
                ArrayList<PythonCompletions.Item> items = new ArrayList<>();
                for (String value : response.optString("stdout").split("\\R")) {
                    if (value.matches("[A-Za-z_][A-Za-z0-9_]*"))
                        items.add(new PythonCompletions.Item(value, "语言包", false));
                    if (items.size() >= 8) break;
                }
                PythonCompletions.Result result =
                        new PythonCompletions.Result(wordStart, cursor, items);
                runOnUiThread(() -> {
                    if (generation != completionGeneration || !name.equals(filename)
                            || !source.equals(editor.getText().toString())
                            || editor.getSelectionStart() != cursor
                            || editor.getSelectionEnd() != cursor) return;
                    completions.show(result);
                });
            } catch (Exception ignored) {
                // Automatic suggestions must never interrupt editing with an error dialog.
            } finally {
                try { WorkspaceFiles.removeMirror(root); } catch (IOException ignored) { }
            }
        });
    }

    private static boolean completionSource(String path) {
        return path.endsWith(".py") || cppFilename(path)
                || path.endsWith(".pocket-cpp.json");
    }

    private static boolean cppFilename(String path) {
        return path.endsWith(".c") || path.endsWith(".cpp") || path.endsWith(".cc")
                || path.endsWith(".cxx") || path.endsWith(".h") || path.endsWith(".hpp");
    }

    private static boolean cppDialect(String path) {
        return path.endsWith(".cpp") || path.endsWith(".cc")
                || path.endsWith(".cxx") || path.endsWith(".hpp");
    }

    private TextView action(LinearLayout parent, String title, boolean primary, View.OnClickListener click) {
        TextView button = label(title, 13, primary ? Color.WHITE : TEXT, primary);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(13), 0, dp(13), 0);
        if (primary) button.setBackground(rounded(ACCENT, 4));
        interactive(button, click);
        LinearLayout.LayoutParams params = size(-2, dp(44));
        params.leftMargin = dp(6);
        parent.addView(button, params);
        return button;
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setIncludeFontPadding(false);
        return text;
    }

    private void interactive(View view, View.OnClickListener listener) {
        view.setClickable(true);
        view.setFocusable(true);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        if (ripple.resourceId != 0) view.setForeground(getDrawable(ripple.resourceId));
        view.setOnClickListener(listener);
    }

    private LinearLayout row(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private LinearLayout column(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private View divider(boolean vertical) {
        View line = new View(this);
        line.setBackgroundColor(BORDER);
        line.setLayoutParams(size(vertical ? dp(1) : -1, vertical ? -1 : dp(1)));
        return line;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateFilename() {
        String shown = filename.isEmpty() ? "未打开文件" : filename;
        if (filenameView != null) filenameView.setText((modernUi() ? "" : "Py  ") + shown);
        if (breadcrumbView != null) breadcrumbView.setText(
                workspaceFiles == null ? "scripts  /  " + shown : workspaceFiles.name() + "  /  " + shown);
        if (folderLabel != null) folderLabel.setText("⌄  "
                + (workspaceFiles == null ? "scripts" : workspaceFiles.name()));
        if (commandCenter != null) commandCenter.setText("⌕   "
                + (workspaceFiles == null ? "scripts" : workspaceFiles.name()));
        if (capabilityView != null) capabilityView.setText(capabilitySummary());
        if (emptyHint != null && editorArea != null)
            emptyHint.setVisibility(editorArea.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        if (modernUi()) renderTabs();
        if (modernUi()) onEditorFileChanged(filename, workspaceKey());
        refreshDebugGutter();
    }

    protected final void refreshCapabilityStatus() {
        if (capabilityView != null) capabilityView.setText(capabilitySummary());
    }

    private void renderTabs() {
        if (tabs == null) return;
        tabs.removeAllViews();
        for (String name : new ArrayList<>(openTabs)) {
            LinearLayout tab = row(name.equals(filename) ? EDITOR : SURFACE);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = toolbarAction(name.substring(name.lastIndexOf('/') + 1), v -> {
                if (!name.equals(filename) && saveScript(false)) loadScript(name, false);
            });
            tab.addView(title, size(-2, -1));
            TextView close = toolbarAction("×", v -> closeTab(name));
            close.setContentDescription("关闭 " + name);
            tab.addView(close, size(dp(32), -1));
            tabs.addView(tab, size(-2, -1));
            tabs.addView(divider(true));
        }
    }

    private void closeTab(String name) {
        if (name.equals(filename) && !saveScript(false)) return;
        int index = openTabs.indexOf(name);
        openTabs.remove(name);
        if (name.equals(filename)) {
            if (openTabs.isEmpty()) {
                filename = "";
                editorArea.setVisibility(View.INVISIBLE);
                editor.setText("");
            } else loadScript(openTabs.get(Math.max(0, Math.min(index, openTabs.size() - 1))), false);
        }
        updateFilename();
        refreshFiles();
    }

    private void promptDeleteFile(String path) {
        if (path == null || path.isEmpty()) { showError("请先打开要删除的文件"); return; }
        if (activeRun != null && !activeRun.isDone()) {
            showError("请先等待或取消当前任务，再删除文件");
            return;
        }
        new AlertDialog.Builder(this).setTitle("删除文件")
                .setMessage("确定永久删除 “" + path + "” 吗？删除后无法撤销；若它正在编辑，未保存的内容也会丢失。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> performFileDeletion(path)).show();
    }

    private void performFileDeletion(String path) {
        if (activeRun != null && !activeRun.isDone()) {
            showError("请先等待或取消当前任务，再删除文件");
            return;
        }
        try {
            if (workspaceFiles != null) workspaceFiles.deleteFile(path);
            else {
                if (path.contains("/") || path.contains("\\") || !localFile(path))
                    throw new IOException("无效的私有文件名");
                Files.delete(scriptFile(path).toPath());
            }
            int tabIndex = openTabs.indexOf(path);
            openTabs.remove(path);
            for (int i = history.size() - 1; i >= 0; i--) {
                if (path.equals(history.get(i))) {
                    history.remove(i);
                    if (i <= historyIndex) historyIndex--;
                }
            }
            historyIndex = Math.min(historyIndex, history.size() - 1);
            if (path.equals(filename)) {
                if (completions != null) completions.dismiss();
                filename = "";
                loadedHash = null;
                editor.setText("");
                editorArea.setVisibility(View.INVISIBLE);
                if (!openTabs.isEmpty())
                    loadScript(openTabs.get(Math.max(0,
                            Math.min(tabIndex, openTabs.size() - 1))), false);
                else { updateFilename(); refreshFiles(); }
            } else { updateFilename(); refreshFiles(); }
            Toast.makeText(this, "已删除 " + path, Toast.LENGTH_SHORT).show();
        } catch (IOException e) { showError("删除失败：" + e.getMessage()); }
    }

    private File scriptFile(String name) { return new File(scriptsDir, name); }

    private boolean validName(String name) {
        return name.matches("[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_]+")
                && name.length() <= 80 && localFile(name);
    }

    private boolean validWorkspaceName(String name) {
        return !name.isEmpty() && !name.equals(".") && !name.equals("..")
                && !name.contains("/") && !name.contains("\\") && name.length() <= 100;
    }

    private boolean editableFile(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        return extraEditableFile(lower) || lower.endsWith(".py") || lower.endsWith(".pyi") || lower.endsWith(".txt")
                || lower.endsWith(".md") || lower.endsWith(".json") || lower.endsWith(".toml")
                || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".ini")
                || lower.endsWith(".cfg") || lower.endsWith(".csv") || lower.endsWith(".sh")
                || lower.endsWith(".xml") || lower.endsWith(".gradle")
                || lower.endsWith(".properties") || lower.endsWith("requirements")
                || lower.endsWith("dockerfile") || lower.endsWith(".gitignore");
    }

    private void openFolder() {
        if (!saveScript(false)) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (workspaceFiles != null)
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                    workspaceFiles.treeUri());
        try { startActivityForResult(intent, OPEN_FOLDER_REQUEST); }
        catch (android.content.ActivityNotFoundException e) {
            showError("设备没有可用的系统文件夹选择器");
        }
    }

    private void importScript() {
        if (workspaceFiles != null) {
            showError("请先关闭工作区，再导入到本应用私有 scripts；外部工作区可直接重新授权打开");
            return;
        }
        if (!saveScript(false)) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, IMPORT_SCRIPT_REQUEST); }
        catch (android.content.ActivityNotFoundException e) {
            showError("设备没有可用的系统文件选择器");
        }
    }

    private void acceptImportedScript(Uri uri) {
        try {
            String name = null;
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            }
            if (name == null || !validName(name))
                throw new IOException("请选择文件名为英文字母、数字、下划线或连字符的 .py 文件");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("提供者拒绝读取文件");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 2 * 1024 * 1024)
                        throw new IOException("导入文件超过 2 MB 编辑限制");
                    bytes.write(buffer, 0, count);
                }
            }
            byte[] content = bytes.toByteArray();
            File destination = scriptFile(name);
            if (destination.exists()) {
                String stem = name.substring(0, name.length() - 3);
                String alternate = null;
                for (int i = 1; i <= 999; i++) {
                    String candidate = stem + "_imported_" + i + ".py";
                    if (validName(candidate) && !scriptFile(candidate).exists()) {
                        alternate = candidate;
                        break;
                    }
                }
                if (alternate == null) throw new IOException("无法生成不冲突的文件名");
                String chosen = alternate;
                new AlertDialog.Builder(this).setTitle("同名脚本已存在")
                        .setMessage(name + " 已存在。原文件不会被覆盖。")
                        .setNegativeButton("跳过", null)
                        .setPositiveButton("另存为 " + chosen, (dialog, which) ->
                                writeImportedScript(chosen, content)).show();
            } else writeImportedScript(name, content);
        } catch (Exception e) { showError("导入失败：" + e.getMessage()); }
    }

    private void writeImportedScript(String name, byte[] content) {
        try {
            Files.write(scriptFile(name).toPath(), content,
                    java.nio.file.StandardOpenOption.CREATE_NEW);
            loadScript(name);
            refreshFiles();
            Toast.makeText(this, "已导入 " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) { showError("导入失败：" + e.getMessage()); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FONT_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                importEditorFont(data.getData());
            return;
        }
        if (requestCode == WALLPAPER_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    getContentResolver().takePersistableUriPermission(data.getData(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getPreferences(MODE_PRIVATE).edit().putString("ide_wallpaper_uri",
                            data.getData().toString()).apply();
                    wallpaperCache().delete();
                    loadWallpaper();
                } catch (Exception problem) { showError("背景图片授权失败：" + problem.getMessage()); }
            }
            return;
        }
        if (requestCode == IMPORT_SCRIPT_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                acceptImportedScript(data.getData());
            return;
        }
        if (requestCode == CREATE_FILE_REQUEST) {
            String folder = pendingCreateFolder;
            Uri tree = pendingCreateTree;
            String requestedName = pendingCreateName;
            pendingCreateFolder = null;
            pendingCreateTree = null;
            pendingCreateName = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            if (workspaceFiles == null || folder == null || tree == null || requestedName == null
                    || !tree.equals(workspaceFiles.treeUri())) {
                showError("工作区已变化，请重新创建文件");
                return;
            }
            try { activateCreatedFile(workspaceFiles.resolveCreatedDocument(
                    folder, data.getData(), requestedName)); }
            catch (Exception e) { showError("新建文件失败：" + e.getMessage()); }
            return;
        }
        if (requestCode != OPEN_FOLDER_REQUEST || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        Uri tree = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (flags != (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
            showError("所选文件夹没有读写授权");
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(tree, flags);
            WorkspaceFiles next = new WorkspaceFiles(this, tree);
            workspaceFiles = next;
            getPreferences(MODE_PRIVATE).edit().putString(WORKSPACE_PREF, tree.toString()).apply();
            history.clear();
            openTabs.clear();
            historyIndex = -1;
            expandedFolders.clear();
            currentFolder = "";
            filename = "";
            loadedHash = null;
            editor.setText("");
            outputView.setText("");
            editorArea.setVisibility(View.INVISIBLE);
            updateFilename();
            refreshFiles();
            if (compact && sidebar.getVisibility() != View.VISIBLE) toggleSidebar();
            Toast.makeText(this, "已打开工作区：" + next.name(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) { showError("打开文件夹失败：" + e.getMessage()); }
    }

    private void closeFolder() {
        if (workspaceFiles == null) return;
        if (!saveScript(false)) return;
        workspaceFiles = null;
        getPreferences(MODE_PRIVATE).edit().remove(WORKSPACE_PREF).apply();
        history.clear();
        openTabs.clear();
        historyIndex = -1;
        expandedFolders.clear();
        currentFolder = "";
        loadScript("main.py");
        updateFilename();
        refreshFiles();
    }

    private void expandParents(String path) {
        int slash = path.lastIndexOf('/');
        while (slash >= 0) {
            expandedFolders.add(path.substring(0, slash));
            slash = path.lastIndexOf('/', slash - 1);
        }
    }

    private void promptNewFolder() {
        if (workspaceFiles == null) {
            showError("请先通过“文件 → 打开文件夹”选择工作区");
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("文件夹名称");
        new AlertDialog.Builder(this).setTitle("在 " + workspaceFiles.name()
                + (currentFolder.isEmpty() ? "" : "/" + currentFolder) + " 中新建文件夹")
                .setView(input).setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!validWorkspaceName(name)) { showError("无效的文件夹名称"); return; }
                    try {
                        WorkspaceFiles.Entry created = workspaceFiles.create(currentFolder, name, true);
                        expandedFolders.add(created.path);
                        refreshFiles();
                    } catch (IOException e) { showError("新建文件夹失败：" + e.getMessage()); }
                }).show();
    }

    private void promptNew() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("script.py");
        new AlertDialog.Builder(this).setTitle(workspaceFiles == null ? "新建脚本" :
                        "在 " + workspaceFiles.name() + (currentFolder.isEmpty() ? "" : "/" + currentFolder)
                                + " 中新建文件")
                .setView(input).setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (workspaceFiles != null) {
                        if (!validWorkspaceName(name) || !editableFile(name)) {
                            showError("请输入受支持的文本文件名，例如 main.py 或 README.md");
                            return;
                        }
                        if (!saveScript(false)) return;
                        try {
                            WorkspaceFiles.Entry created = workspaceFiles.create(currentFolder, name, false);
                            activateCreatedFile(created);
                        } catch (WorkspaceFiles.CreationUnavailableException e) {
                            createWithSystemPicker(name);
                        } catch (IOException e) { showError("新建文件失败：" + e.getMessage()); }
                        return;
                    }
                    if (!validName(name)) {
                        showError("文件名需使用受支持的扩展名；请选择已启用的语言包");
                        return;
                    }
                    if (scriptFile(name).exists()) { showError("脚本已存在"); return; }
                    if (!saveScript(false)) return;
                    filename = name;
                    editor.setText("");
                    outputView.setText("");
                    editorArea.setVisibility(View.VISIBLE);
                    updateFilename();
                    saveScript(false);
                    recordHistory(name);
                    refreshFiles();
                    if (compact && sidebar.getVisibility() == View.VISIBLE) toggleSidebar();
                }).show();
    }

    private void createWithSystemPicker(String name) {
        try {
            WorkspaceFiles.Entry parent = workspaceFiles.find(currentFolder);
            if (parent == null || !parent.directory) throw new IOException("目标文件夹不存在");
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(WorkspaceFiles.mimeType(name));
            intent.putExtra(Intent.EXTRA_TITLE, name);
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, parent.uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            pendingCreateFolder = currentFolder;
            pendingCreateTree = workspaceFiles.treeUri();
            pendingCreateName = name;
            startActivityForResult(intent, CREATE_FILE_REQUEST);
        } catch (Exception e) {
            pendingCreateFolder = null;
            pendingCreateTree = null;
            pendingCreateName = null;
            showError("系统文件选择器无法创建文件：" + e.getMessage());
        }
    }

    private void activateCreatedFile(WorkspaceFiles.Entry created) {
        filename = created.path;
        loadedHash = hash(new byte[0]);
        editor.setText("");
        editorArea.setVisibility(View.VISIBLE);
        outputView.setText("");
        updateFilename();
        recordHistory(filename);
        refreshFiles();
        if (compact && sidebar.getVisibility() == View.VISIBLE) toggleSidebar();
    }

    private void promptOpen() {
        if (workspaceFiles != null) {
            try {
                ArrayList<String> names = new ArrayList<>();
                for (WorkspaceFiles.Entry entry : workspaceFiles.listTree())
                    if (!entry.directory && editableFile(entry.path)) names.add(entry.path);
                if (names.isEmpty()) { showError("工作区没有可打开的文本文件"); return; }
                new AlertDialog.Builder(this).setTitle("打开工作区文件")
                        .setItems(names.toArray(new String[0]), (dialog, which) -> {
                            if (saveScript(false)) { expandParents(names.get(which)); loadScript(names.get(which)); }
                        }).setNegativeButton("取消", null).show();
            } catch (IOException e) { showError("读取工作区失败：" + e.getMessage()); }
            return;
        }
        String[] names = scriptsDir.list((dir, name) -> localFile(name));
        if (names == null || names.length == 0) { showError("没有可打开的脚本"); return; }
        Arrays.sort(names);
        new AlertDialog.Builder(this).setTitle("打开脚本")
                .setItems(names, (dialog, which) -> { if (saveScript(false)) loadScript(names[which]); })
                .setNegativeButton("取消", null).show();
    }

    private void loadScript(String name) { loadScript(name, true); }

    private void loadScript(String name, boolean record) {
        try {
            if (completions != null) completions.dismiss();
            byte[] bytes;
            if (workspaceFiles == null) bytes = Files.readAllBytes(scriptFile(name).toPath());
            else {
                WorkspaceFiles.Entry entry = workspaceFiles.find(name);
                if (entry == null) throw new IOException("文件不存在：" + name);
                bytes = workspaceFiles.read(entry, 2 * 1024 * 1024);
            }
            filename = name;
            if (modernUi() && !openTabs.contains(name)) openTabs.add(name);
            loadedHash = hash(bytes);
            editor.setText(new String(bytes, StandardCharsets.UTF_8));
            editor.setSelection(0);
            editorArea.setVisibility(View.VISIBLE);
            updateFilename();
            refreshFiles();
            if (record) recordHistory(name);
        } catch (IOException e) { showError("打开失败：" + e.getMessage()); }
    }

    private void recordHistory(String name) {
        if (modernUi() && !openTabs.contains(name)) { openTabs.add(name); renderTabs(); }
        if (historyIndex >= 0 && history.get(historyIndex).equals(name)) return;
        while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
        history.add(name);
        historyIndex = history.size() - 1;
    }

    private boolean saveScript() { return saveScript(true); }

    private boolean saveScript(boolean notify) {
        if (filename.isEmpty() || editorArea.getVisibility() != View.VISIBLE) return true;
        try {
            byte[] bytes = editor.getText().toString().getBytes(StandardCharsets.UTF_8);
            if (workspaceFiles == null) {
                Files.write(scriptFile(filename).toPath(), bytes);
                loadedHash = hash(bytes);
            }
            else {
                // Opening a new file should not rewrite the unchanged current document.
                if (loadedHash != null && Arrays.equals(loadedHash, hash(bytes))) {
                    if (notify) Toast.makeText(this, "没有未保存的更改", Toast.LENGTH_SHORT).show();
                    return true;
                }
                WorkspaceFiles.Entry entry = workspaceFiles.find(filename);
                if (entry == null) throw new IOException("文件已从工作区移除");
                if (loadedHash != null && !Arrays.equals(loadedHash,
                        hash(workspaceFiles.read(entry, 2 * 1024 * 1024))))
                    throw new IOException("文件已被外部修改；当前编辑内容尚未覆盖它");
                workspaceFiles.write(entry, bytes);
                loadedHash = hash(bytes);
            }
            if (notify) Toast.makeText(this, "已保存 " + filename, Toast.LENGTH_SHORT).show();
            return true;
        } catch (IOException e) { showError("保存失败：" + e.getMessage()); return false; }
    }

    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    private long packageUpdateTime() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return -1;
        }
    }

    private void promptRestoreDraft(String name, String source, int selection) {
        pendingRecoveryFilename = name;
        pendingRecoverySource = source;
        new AlertDialog.Builder(this).setTitle("发现旧编辑草稿")
                .setMessage("已从工作区读取 “" + name + "”。安装前保存的编辑缓存与文件内容不同。"
                        + "当前显示工作区文件；如需找回未保存的修改，可恢复草稿后手动保存。")
                .setNegativeButton("保留工作区文件", (dialog, which) -> {
                    pendingRecoverySource = null;
                    pendingRecoveryFilename = null;
                })
                .setPositiveButton("恢复草稿", (dialog, which) -> {
                    if (name.equals(filename)) {
                        editor.setText(source);
                        editor.setSelection(Math.min(selection, editor.length()));
                    }
                    pendingRecoverySource = null;
                    pendingRecoveryFilename = null;
                }).setCancelable(false).show();
    }

    private void debugCurrent() {
        if (editorArea.getVisibility() != View.VISIBLE) {
            showError("请先打开要调试的文件");
            return;
        }
        if (!canDebugFile(filename)) {
            showError("此语言包不支持调试");
            return;
        }
        if (activeRun != null && !activeRun.isDone()) {
            showError("请先结束当前运行任务");
            return;
        }
        if (debugSessionActive()) {
            showDebugPanel();
            return;
        }
        if (!saveScript(false)) return;
        WorkspaceFiles selected = workspaceFiles;
        String key = selected == null ? "internal:scripts"
                : selected.treeUri().toString();
        String entry = debugEntryForCurrent(filename, key);
        File root = selected == null ? scriptsDir : new File(new File(getFilesDir(),
                "workspace-runs"), "debug-" + System.currentTimeMillis()
                        + "-" + System.nanoTime());
        if (selected == null) {
            launchDebugger(root, entry, key, false);
            return;
        }
        Toast.makeText(this, "正在准备调试工作区…", Toast.LENGTH_SHORT).show();
        fileWorker.submit(() -> {
            try {
                selected.copyToMirror(root, true, false);
                runOnUiThread(() -> launchDebugger(root, entry, key, true));
            } catch (Exception failed) {
                try { WorkspaceFiles.removeMirror(root); }
                catch (IOException ignored) { }
                runOnUiThread(() -> showError("调试工作区准备失败：" + failed.getMessage()));
            }
        });
    }

    private void runScript() {
        if (debugSessionActive()) { showError("请先结束当前调试会话"); return; }
        if (!hasRuntime()) { showError("未安装运行语言包；可编辑和保存，运行需安装兼容语言包"); return; }
        if (activeRun != null && !activeRun.isDone()) {
            showError("当前脚本仍在运行；可从运行菜单取消");
            return;
        }
        if (editorArea.getVisibility() != View.VISIBLE) {
            showError("请先在资源管理器中打开脚本");
            return;
        }
        if (!canRunFile(filename)) {
            showError("当前文件没有通过验证的运行语言包");
            return;
        }
        if (!saveScript(false)) return;
        String source = editor.getText().toString();
        String currentName = filename;
        WorkspaceFiles selectedWorkspace = workspaceFiles;
        boolean packInput = packWorkspace();
        boolean packWritable = packInput && packWorkspaceWriteback();
        File runRoot = selectedWorkspace == null ? scriptsDir
                : new File(new File(getFilesDir(), "workspace-runs"),
                        "run-" + System.currentTimeMillis() + "-" + System.nanoTime());
        String path = selectedWorkspace == null ? scriptFile(currentName).getAbsolutePath()
                : new File(runRoot, currentName).getAbsolutePath();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean syncing = new AtomicBoolean(false);
        activeCancellation = cancelled;
        activeSync = syncing;
        long generation = ++runGeneration;
        if (consolePane.getVisibility() != View.VISIBLE) toggleConsole();
        consoleState.setText("运行中…");
        outputView.setText("正在执行 " + currentName + " …\n");
        if (modernUi()) { consoleTab = "输出"; lastOutput = outputView.getText().toString(); }
        activeRun = runner.submit(() -> {
            String result = "";
            boolean ok = false;
            boolean backendReturned = false;
            WorkspaceFiles.Snapshot snapshot = null;
            try {
                if (selectedWorkspace != null)
                    snapshot = selectedWorkspace.copyToMirror(runRoot, packInput, packWritable);
                if (cancelled.get()) throw new InterruptedException("运行已取消");
                result = runBackend(source, path, runRoot.getAbsolutePath());
                backendReturned = true;
                JSONObject json = new JSONObject(result);
                String out = json.optString("stdout") + json.optString("stderr");
                ok = json.optBoolean("ok");
                String detail = json.optString("error");
                if (out.isEmpty()) out = detail;
                else if (!ok && !detail.isEmpty() && !out.contains(detail)) out += "\n" + detail;
                result = out.isEmpty() ? "[无输出]\n" : out;
                result += ok ? "\n[运行完成]" : "\n[运行失败]";
            } catch (Throwable e) { result = "解释器错误：" + e.getMessage(); }
            boolean wasCancelled = cancelled.get() || Thread.currentThread().isInterrupted();
            if (wasCancelled) { result = "[已取消；工作区运行副本未写回]"; ok = false; }
            boolean syncInput = !packInput || (packWritable && backendReturned);
            if (snapshot != null && !wasCancelled && syncInput) {
                syncing.set(true);
                wasCancelled = cancelled.get() || Thread.currentThread().isInterrupted();
                if (wasCancelled) { result = "[已取消；工作区运行副本未写回]"; ok = false; }
            }
            if (snapshot != null && !wasCancelled && syncInput) {
                try {
                    WorkspaceFiles.SyncResult sync = selectedWorkspace.copyChangesBack(runRoot, snapshot);
                    if (sync.written > 0) result += "\n[已写回工作区 " + sync.written + " 个文件]";
                    if (sync.conflicts > 0) {
                        result += "\n[" + sync.conflicts + " 个文件被外部修改，未覆盖；运行副本已保留]";
                        ok = false;
                    } else {
                        WorkspaceFiles.removeMirror(runRoot);
                    }
                } catch (Throwable e) {
                    result += "\n[工作区写回失败：" + e.getMessage() + "；运行副本已保留]";
                    ok = false;
                }
            }
            if (selectedWorkspace != null && packInput
                    && (!packWritable || wasCancelled || !backendReturned)) {
                try { WorkspaceFiles.removeMirror(runRoot); }
                catch (IOException ignored) { /* Keep the run result visible. */ }
            }
            String finalResult = result;
            boolean finalOk = ok;
            runOnUiThread(() -> {
                if (runGeneration != generation) return;
                outputView.setText(finalResult);
                if (modernUi()) {
                    lastOutput = finalResult;
                    lastProblem = finalOk ? "尚无问题" : finalResult;
                    if (!"输出".equals(consoleTab)) showConsoleTab(consoleTab);
                }
                consoleState.setText(finalOk ? "已完成" : "失败");
                activeRun = null;
                activeCancellation = null;
                activeSync = null;
                if (workspaceFiles == selectedWorkspace) refreshFiles();
            });
        });
    }

    private void buildCurrent() {
        if (editorArea.getVisibility() != View.VISIBLE) {
            showError("请先打开文件再构建"); return;
        }
        if (!canBuildFile(filename)) {
            String message = "当前文件没有通过验证的构建语言包；可继续编辑。请从语言包入口查看能力。";
            buildRecords.add("未构建 · " + filename + " · " + message);
            lastProblem = message;
            showConsoleTab("构建记录");
            consoleState.setText("无法构建");
            return;
        }
        if (activeRun != null && !activeRun.isDone()) {
            showError("已有任务在运行，可先取消"); return;
        }
        if (!saveScript(false)) return;
        String name = filename;
        String source = editor.getText().toString();
        WorkspaceFiles selected = workspaceFiles;
        boolean packInput = packWorkspace();
        File root = selected == null ? scriptsDir : new File(new File(getFilesDir(),
                "workspace-runs"), "build-" + System.currentTimeMillis() + "-" + System.nanoTime());
        String path = selected == null ? scriptFile(name).getAbsolutePath()
                : new File(root, name).getAbsolutePath();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeCancellation = cancelled;
        activeSync = new AtomicBoolean(false);
        activeBuildName = name;
        long generation = ++runGeneration;
        showConsoleTab("构建记录");
        consoleState.setText("构建中…");
        buildRecords.add("构建中 · " + name);
        outputView.setText(android.text.TextUtils.join("\n\n", buildRecords));
        activeRun = runner.submit(() -> {
            String message;
            boolean ok = false;
            WorkspaceFiles.Snapshot snapshot = null;
            try {
                if (selected != null) snapshot = selected.copyToMirror(root, packInput);
                if (cancelled.get()) throw new InterruptedException("构建已取消");
                JSONObject result = new JSONObject(buildBackend(source, path, root.getAbsolutePath()));
                ok = result.optBoolean("ok");
                message = result.optString("stdout") + result.optString("stderr");
                if (message.isEmpty()) message = result.optString("error", "[无输出]");
                else if (!ok && !result.optString("error").isEmpty()
                        && !message.contains(result.optString("error")))
                    message += "\n" + result.optString("error");
                if (snapshot != null && !cancelled.get() && !packInput) {
                    activeSync.set(true);
                    WorkspaceFiles.SyncResult sync = selected.copyChangesBack(root, snapshot);
                    message += "\n写回 " + sync.written + " 个文件；冲突 " + sync.conflicts + " 个";
                    if (sync.conflicts == 0) WorkspaceFiles.removeMirror(root);
                    else ok = false;
                }
            } catch (Throwable problem) { message = "构建失败：" + problem.getMessage(); }
            finally {
                if (selected != null && packInput) try { WorkspaceFiles.removeMirror(root); }
                catch (IOException ignored) { /* Keep the build result visible. */ }
            }
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                message = "构建已取消；工作区运行副本未写回"; ok = false;
            }
            String report = message;
            boolean success = ok;
            runOnUiThread(() -> {
                if (runGeneration != generation) return;
                buildRecords.remove("构建中 · " + name);
                buildRecords.add((success ? "成功" : "失败") + " · " + name + "\n" + report);
                if (!success) lastProblem = report;
                consoleState.setText(success ? "构建完成" : "构建失败");
                activeRun = null;
                activeCancellation = null;
                activeSync = null;
                activeBuildName = null;
                showConsoleTab("构建记录");
                if (workspaceFiles == selected) refreshFiles();
            });
        });
    }

    private void completeCurrent() {
        if (editorArea.getVisibility() != View.VISIBLE || !canCompleteFile(filename)) {
            showError("当前文件没有通过验证的补全语言包");
            return;
        }
        editor.requestFocus();
        if (cppFilename(filename))
            completions.show(CppCompletions.suggest(editor.getText().toString(),
                    editor.getSelectionStart(), cppDialect(filename)));
        requestPackCompletion();
    }

    private void checkCurrent() {
        if (editorArea.getVisibility() != View.VISIBLE) { showError("请先打开文件"); return; }
        if (!canAnalyzeFile(filename)) {
            showError("当前文件没有通过验证的检查语言包；可继续编辑和保存"); return;
        }
        if (activeRun != null && !activeRun.isDone()) {
            showError("已有任务在运行，可先取消"); return;
        }
        if (!saveScript(false)) return;
        String name = filename;
        WorkspaceFiles selected = workspaceFiles;
        boolean packInput = packWorkspace();
        File root = selected == null ? scriptsDir : new File(new File(getFilesDir(),
                "workspace-runs"), "check-" + System.currentTimeMillis() + "-" + System.nanoTime());
        String path = selected == null ? scriptFile(name).getAbsolutePath()
                : new File(root, name).getAbsolutePath();
        showConsoleTab("问题");
        consoleState.setText("检查中…");
        outputView.setText("正在检查 " + name + " …");
        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeCancellation = cancelled;
        activeSync = new AtomicBoolean(false);
        long generation = ++runGeneration;
        activeRun = runner.submit(() -> {
            String report;
            boolean success = false;
            try {
                if (selected != null) selected.copyToMirror(root, packInput);
                if (cancelled.get()) throw new InterruptedException("检查已取消");
                JSONObject result = new JSONObject(analyzeBackend(path, root.getAbsolutePath()));
                success = result.optBoolean("ok");
                report = result.optString("stdout") + result.optString("stderr");
                if (report.isEmpty()) report = result.optString("error", success ? "未发现问题" : "检查失败");
                if (result.has("line")) report += "\n位置：" + result.optInt("line")
                        + ":" + result.optInt("column");
            } catch (Throwable problem) { report = "检查失败：" + problem.getMessage(); }
            finally {
                if (selected != null) try { WorkspaceFiles.removeMirror(root); }
                catch (IOException ignored) { /* A failed cleanup does not hide the check result. */ }
            }
            if (cancelled.get() || Thread.currentThread().isInterrupted()) report = "检查已取消";
            String finalReport = report;
            boolean finalSuccess = success && !cancelled.get();
            runOnUiThread(() -> {
                if (runGeneration != generation) return;
                lastProblem = finalReport;
                consoleState.setText(finalSuccess ? "检查完成" : "检查失败");
                activeRun = null;
                activeCancellation = null;
                activeSync = null;
                showConsoleTab("问题");
            });
        });
    }

    private void cancelRun() {
        if (activeRun == null || activeRun.isDone()) {
            Toast.makeText(this, "当前没有运行任务", Toast.LENGTH_SHORT).show();
            return;
        }
        if (activeSync != null && activeSync.get()) {
            Toast.makeText(this, "正在写回工作区，暂不能取消", Toast.LENGTH_SHORT).show();
            return;
        }
        if (activeCancellation != null) activeCancellation.set(true);
        if (activeBuildName != null) {
            buildRecords.remove("构建中 · " + activeBuildName);
            buildRecords.add("已取消 · " + activeBuildName);
            activeBuildName = null;
        }
        activeRun.cancel(true);
        activeRun = null;
        activeCancellation = null;
        activeSync = null;
        runGeneration++;
        if (consolePane.getVisibility() != View.VISIBLE) toggleConsole();
        consoleState.setText("已取消");
        outputView.append("\n[已取消；工作区运行副本不会写回]\n");
        if (modernUi()) {
            lastOutput = outputView.getText().toString();
            showConsoleTab(consoleTab);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (modernUi() && event.getAction() == KeyEvent.ACTION_DOWN
                && handleModernKey(event.getKeyCode(), event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean handleModernKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && compact
                && sidebar.getVisibility() == View.VISIBLE) {
            toggleSidebar(); editor.requestFocus(); return true;
        }
        if (event.isCtrlPressed() && !event.isAltPressed()) {
            if (keyCode == KeyEvent.KEYCODE_TAB) {
                if (!openTabs.isEmpty()) {
                    int index = openTabs.indexOf(filename);
                    int next = (index + (event.isShiftPressed() ? -1 : 1) + openTabs.size())
                            % openTabs.size();
                    if (saveScript(false)) loadScript(openTabs.get(next), false);
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_B && event.isShiftPressed()) { buildCurrent(); return true; }
            if (keyCode == KeyEvent.KEYCODE_F && event.isShiftPressed()) { promptProjectSearch(); return true; }
            if (!event.isShiftPressed()) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_N: promptNew(); return true;
                    case KeyEvent.KEYCODE_O: promptOpen(); return true;
                    case KeyEvent.KEYCODE_P: promptOpen(); return true;
                    case KeyEvent.KEYCODE_F: promptSearch(); return true;
                    case KeyEvent.KEYCODE_B: toggleSidebar(); return true;
                    case KeyEvent.KEYCODE_J: toggleConsole(); return true;
                    case KeyEvent.KEYCODE_S: saveScript(); return true;
                    case KeyEvent.KEYCODE_ENTER: runScript(); return true;
                }
            }
        }
        if (event.isAltPressed() && !event.isCtrlPressed()) {
            if (keyCode == KeyEvent.KEYCODE_1) { editor.requestFocus(); return true; }
            if (keyCode == KeyEvent.KEYCODE_2) {
                if (sidebar.getVisibility() != View.VISIBLE) toggleSidebar();
                fileList.requestFocus(); return true;
            }
            if (keyCode == KeyEvent.KEYCODE_3) {
                showConsoleTab("输出"); outputView.requestFocus(); return true;
            }
        }
        return false;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_S) { saveScript(); return true; }
        if ((event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_ENTER)
                || keyCode == KeyEvent.KEYCODE_F5) { runScript(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (modernUi()) outState.putStringArrayList("openTabs", new ArrayList<>(openTabs));
        outState.putString("pendingCreateFolder", pendingCreateFolder);
        outState.putString("pendingCreateName", pendingCreateName);
        outState.putString("pendingCreateTree", pendingCreateTree == null ? null : pendingCreateTree.toString());
        outState.putString("filename", filename);
        outState.putString("source", editor.getText().toString());
        outState.putByteArray("loadedHash", loadedHash);
        outState.putLong("packageUpdateTime", packageUpdateTime());
        outState.putString("pendingRecoverySource", pendingRecoverySource);
        outState.putString("pendingRecoveryFilename", pendingRecoveryFilename);
        outState.putInt("selection", editor.getSelectionStart());
        outState.putString("output", outputView.getText().toString());
        super.onSaveInstanceState(outState);
    }

    private void showError(String message) {
        if (modernUi()) {
            lastProblem = message;
            if ("问题".equals(consoleTab) && outputView != null) outputView.setText(lastProblem);
        }
        new AlertDialog.Builder(this).setTitle("错误")
                .setMessage(message).setPositiveButton("确定", null).show();
    }

    @Override protected void onPause() {
        completionGeneration++;
        if (pendingCompletion != null) pendingCompletion.cancel(true);
        if (completions != null) completions.dismiss();
        super.onPause();
    }

    @Override protected void onDestroy() {
        editor.removeCallbacks(completionTask);
        if (pendingCompletion != null) pendingCompletion.cancel(true);
        if (pendingFileRefresh != null) pendingFileRefresh.cancel(true);
        completionWorker.shutdownNow();
        fileWorker.shutdownNow();
        runner.shutdown();
        searchWorker.shutdown();
        wallpaperWorker.shutdown();
        super.onDestroy();
    }
}
