package com.example.pocketpythonide;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.pocketide.pack.v1.PackContract;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs the pack's declared fixtures across the real service boundary. */
final class PackValidator {
    static final class Report {
        final String capabilities;
        final String text;
        final boolean initialized;
        Report(String capabilities, String text, boolean initialized) {
            this.capabilities = capabilities;
            this.text = text;
            this.initialized = initialized;
        }
    }

    private PackValidator() {}

    static Report validate(Context context, PackRegistry.Descriptor pack) {
        JSONArray declared = pack.manifest.optJSONArray("capabilities");
        JSONObject fixtures = pack.manifest.optJSONObject("selfTests");
        List<String> passed = new ArrayList<>();
        StringBuilder log = new StringBuilder();
        if (declared == null || fixtures == null)
            return new Report("", "缺少能力声明或 selfTests", false);
        for (int i = 0; i < declared.length(); i++) {
            String ability = declared.optString(i);
            JSONObject fixture = fixtures.optJSONObject(ability);
            if (fixture == null) { log.append(ability).append(": 缺少测试样例\n"); continue; }
            try {
                int retries = 0;
                if ("cancel".equals(ability)) testCancel(context, pack, fixture);
                else if ("debug".equals(ability)) testDebug(context, pack, fixture);
                else if ("initialize".equals(ability))
                    retries = testInitialConnection(context, pack, fixture);
                else testOperation(context, pack, ability, fixture);
                passed.add(ability);
                log.append(ability).append(": 通过");
                if (retries > 0) log.append("（连接重试 ").append(retries).append(" 次）");
                log.append('\n');
            } catch (Exception failure) {
                log.append(ability).append(": 失败 · ").append(detail(failure)).append('\n');
            }
        }
        if (passed.contains("run")) {
            try {
                JSONObject large = fixtures.getJSONObject("largeOutput");
                JSONObject actual = invokeFixture(context, pack, "run", large, 60, null);
                if (!actual.optBoolean("ok") || actual.optString("stdout").getBytes(
                        StandardCharsets.UTF_8).length < large.getInt("minBytes"))
                    throw new Exception("大输出未达到声明字节数");
                log.append("largeOutput: 通过\n");
            } catch (Exception failure) {
                passed.remove("run");
                log.append("largeOutput: 失败 · ").append(detail(failure)).append('\n');
            }
        }
        if (fixtures.has("versionMismatch")) {
            try {
                expectFailure(context, pack, "testVersionMismatch", "PROTOCOL_MISMATCH");
                log.append("versionMismatch: 通过\n");
            } catch (Exception failure) {
                passed.remove("run");
                log.append("versionMismatch: 失败 · ").append(detail(failure)).append('\n');
            }
        }
        if (fixtures.has("crashRecovery")) {
            try {
                expectCrash(context, pack);
                JSONObject restarted = initializeAfterCrash(context, pack);
                if (!restarted.optBoolean("ok")) throw new Exception("崩溃后无法重新绑定");
                log.append("crashRecovery: 通过\n");
            } catch (Exception failure) {
                passed.remove("run");
                log.append("crashRecovery: 失败 · ").append(detail(failure)).append('\n');
            }
        }
        StringBuilder encoded = new StringBuilder();
        for (String ability : passed) encoded.append('|').append(ability).append('|');
        return new Report(encoded.toString(), log.toString(), passed.contains("initialize"));
    }

    private static void testOperation(Context context, PackRegistry.Descriptor pack,
            String ability, JSONObject fixture) throws Exception {
        JSONObject actual = invokeFixture(context, pack, ability, fixture, 30, null);
        String status = fixture.optString("expectStatus", PackContract.OK);
        if (!status.equals(actual.optString("status")))
            throw new Exception("状态 " + actual.optString("status") + "，预期 " + status);
        String expectedOutput = fixture.optString("expectOutput");
        if (!expectedOutput.isEmpty() && !actual.optString("stdout").contains(expectedOutput))
            throw new Exception("输出不含 " + expectedOutput);
        String expectedCode = fixture.optString("expectErrorCode");
        if (!expectedCode.isEmpty() && !expectedCode.equals(actual.optString("errorCode")))
            throw new Exception("错误码不符");
        if (fixture.has("expectLine") && fixture.getInt("expectLine") != actual.optInt("line"))
            throw new Exception("错误行号不符");
        if (fixture.has("expectColumn")
                && fixture.getInt("expectColumn") != actual.optInt("column"))
            throw new Exception("错误列号不符");
    }

    /** A cold service bind may be delayed by the device; never retry a pack result failure. */
    private static int testInitialConnection(Context context, PackRegistry.Descriptor pack,
            JSONObject fixture) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try {
                testOperation(context, pack, "initialize", fixture);
                return attempt;
            } catch (Exception failure) {
                if (attempt >= 2 || !(failure instanceof java.io.IOException)
                        || failure.getMessage() == null
                        || !failure.getMessage().startsWith("PACK_BIND_FAILED:"))
                    throw failure;
                Thread.sleep((attempt + 1) * 500L);
            }
        }
    }

    private static void expectFailure(Context context, PackRegistry.Descriptor pack,
            String operation, String code) throws Exception {
        try {
            invokeFixture(context, pack, operation, new JSONObject(), 10, null);
        } catch (Exception expected) {
            if (String.valueOf(expected).contains(code)) return;
            throw expected;
        }
        throw new Exception(operation + " 未触发 " + code);
    }

    private static void expectCrash(Context context, PackRegistry.Descriptor pack)
            throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                expectFailure(context, pack, "testCrash", "PACK_CRASH");
                return;
            } catch (Exception failure) {
                if (!String.valueOf(failure).contains("PACK_BIND_FAILED")) throw failure;
                last = failure;
                if (attempt < 3) Thread.sleep((attempt + 1) * 2000L);
            }
        }
        throw last;
    }

    private static JSONObject initializeAfterCrash(Context context,
            PackRegistry.Descriptor pack) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                return invokeFixture(context, pack, "initialize", new JSONObject(), 10, null);
            } catch (Exception failure) {
                String detail = String.valueOf(failure);
                if (!detail.contains("PACK_BIND_FAILED")
                        && !detail.contains("PACK_CRASH")) throw failure;
                last = failure;
                if (attempt < 3) Thread.sleep((attempt + 1) * 2000L);
            }
        }
        throw last;
    }

    private static void testCancel(Context context, PackRegistry.Descriptor pack,
            JSONObject fixture) throws Exception {
        CountDownLatch submitted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        String operation = fixture.optString("operation", "run");
        Thread task = new Thread(() -> {
            try { invokeFixture(context, pack, operation, fixture, 30, submitted); }
            catch (Throwable problem) { failure.set(problem); }
        }, "pack-cancel-test");
        task.start();
        if (!submitted.await(10, TimeUnit.SECONDS)) {
            task.interrupt();
            throw new Exception("任务未提交");
        }
        task.interrupt();
        task.join(3000);
        if (task.isAlive() || failure.get() == null
                || !String.valueOf(failure.get()).contains("PACK_CANCELLED"))
            throw new Exception("取消未及时返回");
        // A follow-up call detects a worker that ignored cancellation and stayed blocked.
        // A pack may terminate its own worker process to interrupt native code.
        // The first bind can race that process exit; retry only connection failures.
        Thread.sleep(250);
        JSONObject followup = initializeAfterCrash(context, pack);
        if (!followup.optBoolean("ok")) throw new Exception("取消后服务未恢复");
    }

    private static JSONObject waitDap(DapTransport dap, String name) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            JSONObject event = dap.nextEvent(2);
            if (event != null && name.equals(event.optString("event"))) return event;
        }
        throw new Exception("DAP 未收到 " + name + " 事件");
    }

    private static void testDebug(Context context, PackRegistry.Descriptor pack,
            JSONObject fixture) throws Exception {
        if ("org.idecpp".equals(pack.packId())) {
            testCppDebug(context, pack, fixture);
            return;
        }
        File folder = new File(context.getCacheDir(), "debug-test-" + java.util.UUID.randomUUID());
        if (!folder.mkdirs()) throw new Exception("无法创建调试测试目录");
        String entryPath = fixture.optString("entryPath", "fixture.py");
        String helperPath = fixture.optString("secondaryPath", "helper.py");
        File entry = new File(folder, entryPath);
        File helper = new File(folder, helperPath);
        try {
            if (!entry.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator)
                    || !helper.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator))
                throw new Exception("Invalid debug fixture path");
            if (!entry.getParentFile().mkdirs() && !entry.getParentFile().isDirectory())
                throw new Exception("Cannot create debug fixture folder");
            if (!helper.getParentFile().mkdirs() && !helper.getParentFile().isDirectory())
                throw new Exception("Cannot create debug helper folder");
            Files.write(entry.toPath(), fixture.getString("source").getBytes(StandardCharsets.UTF_8));
            Files.write(helper.toPath(), fixture.optString("secondarySource",
                    fixture.optString("helper")).getBytes(StandardCharsets.UTF_8));
            try (DebugPackClient session = new DebugPackClient(context, pack, folder, entry)) {
                DapTransport dap = session.dap();
                JSONObject capabilities = dap.request("initialize", new JSONObject()
                        .put("clientID", "pocketide-stage8")
                        .put("adapterID", "pocket-python"), 10);
                if (!capabilities.optBoolean("supportsConfigurationDoneRequest"))
                    throw new Exception("DAP 未提供 configurationDone");
                dap.request("launch", new JSONObject().put("program", entryPath), 10);
                JSONArray requested = new JSONArray().put(new JSONObject()
                        .put("line", fixture.getInt("breakpointLine")));
                JSONObject breaks = dap.request("setBreakpoints", new JSONObject()
                        .put("source", new JSONObject().put("path", helperPath))
                        .put("breakpoints", requested), 10);
                if (!breaks.getJSONArray("breakpoints").getJSONObject(0).optBoolean("verified"))
                    throw new Exception("Python 断点未验证");
                dap.request("setExceptionBreakpoints", new JSONObject()
                        .put("filters", new JSONArray().put("all")), 10);
                dap.request("configurationDone", new JSONObject(), 10);
                JSONObject hit = waitDap(dap, "stopped");
                if (!"breakpoint".equals(hit.getJSONObject("body").optString("reason")))
                    throw new Exception("未命中断点");
                JSONArray frames = dap.request("stackTrace", new JSONObject()
                        .put("threadId", 1), 10).getJSONArray("stackFrames");
                if (!helperPath.equals(frames.getJSONObject(0)
                        .getJSONObject("source").getString("path"))
                        || frames.length() < 2 || !entryPath.equals(frames.getJSONObject(1)
                                .getJSONObject("source").getString("path")))
                    throw new Exception("多文件调用栈路径映射错误");
                JSONArray scopes = dap.request("scopes", new JSONObject().put("frameId",
                        frames.getJSONObject(0).getInt("id")), 10).getJSONArray("scopes");
                JSONArray variables = dap.request("variables", new JSONObject()
                        .put("variablesReference", scopes.getJSONObject(0)
                                .getInt("variablesReference")), 10).getJSONArray("variables");
                boolean found = false;
                for (int i = 0; i < variables.length(); i++) {
                    JSONObject value = variables.getJSONObject(i);
                    if (fixture.optString("variableName", "number")
                            .equals(value.optString("name"))
                            && fixture.optString("variableValue", "21")
                                    .equals(value.optString("value"))) found = true;
                }
                if (!found) throw new Exception("未读取到局部变量 number=21");
                dap.request("next", new JSONObject().put("threadId", 1), 10);
                if (!"step".equals(waitDap(dap, "stopped")
                        .getJSONObject("body").getString("reason")))
                    throw new Exception("Python 单步失败");
                dap.request("continue", new JSONObject().put("threadId", 1), 10);
                JSONObject exception = waitDap(dap, "stopped");
                if (!"exception".equals(exception.getJSONObject("body").optString("reason")))
                    throw new Exception("未在异常处停止");
                frames = dap.request("stackTrace", new JSONObject().put("threadId", 1), 10)
                        .getJSONArray("stackFrames");
                if (!entryPath.equals(frames.getJSONObject(0)
                        .getJSONObject("source").getString("path"))
                        || frames.getJSONObject(0).getInt("line") != fixture.getInt("exceptionLine"))
                    throw new Exception("异常位置映射错误");
                if (!fixture.optString("exceptionId", "ValueError")
                        .equals(dap.request("exceptionInfo", new JSONObject()
                        .put("threadId", 1), 10).optString("exceptionId")))
                    throw new Exception("异常类型未返回");
                dap.request("continue", new JSONObject().put("threadId", 1), 10);
                if (waitDap(dap, "exited").getJSONObject("body").getInt("exitCode")
                        != fixture.optInt("exitCode", 1))
                    throw new Exception("异常退出码错误");
                waitDap(dap, "terminated");
                if (!session.awaitFinished(5))
                    throw new Exception("DAP 会话未清理完成");
            }
            File pauseEntry = new File(folder, "pause.py");
            Files.write(pauseEntry.toPath(), fixture.optString("pauseSource",
                    "counter = 0\nwhile counter < 10000000:\n    counter += 1\n")
                    .getBytes(StandardCharsets.UTF_8));
            try (DebugPackClient session = new DebugPackClient(context, pack, folder,
                    pauseEntry)) {
                DapTransport dap = session.dap();
                dap.request("initialize", new JSONObject().put("clientID", "pocketide-stage8")
                        .put("adapterID", "pocket-python"), 10);
                dap.request("launch", new JSONObject().put("program", "pause.py"), 10);
                dap.request("configurationDone", new JSONObject(), 10);
                dap.request("pause", new JSONObject().put("threadId", 1), 10);
                if (!"pause".equals(waitDap(dap, "stopped")
                        .getJSONObject("body").optString("reason")))
                    throw new Exception("无断点时暂停失败");
            }
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(folder.toPath())) {
                for (java.nio.file.Path path : paths.sorted(java.util.Comparator.reverseOrder())
                        .toArray(java.nio.file.Path[]::new)) Files.deleteIfExists(path);
            }
        }
    }

    private static void testCppDebug(Context context, PackRegistry.Descriptor pack,
            JSONObject fixture) throws Exception {
        testCppDebugCase(context, pack, fixture);
        JSONObject cpp = new JSONObject()
                .put("entryPath", "main.cpp")
                .put("source", "extern int twice(int);\nint main() { volatile int answer = twice(21); return answer == 42 ? 0 : 1; }\n")
                .put("secondaryPath", "helper.cpp")
                .put("secondarySource", "int twice(int value) {\n  int result = value * 2;\n  return result;\n}\n")
                .put("breakpointLine", 2)
                .put("variableName", "value")
                .put("variableValue", "21");
        testCppDebugCase(context, pack, cpp);
    }

    private static JSONObject waitCppStop(DapTransport dap) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 45000;
        String lastEvent = "无";
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            JSONObject event = dap.nextEvent(2);
            if (event == null) continue;
            String name = event.optString("event");
            if ("stopped".equals(name)) return event;
            if (!name.isEmpty()) lastEvent = name;
            if ("terminated".equals(name) || "exited".equals(name))
                throw new Exception("C/C++ 在断点前退出：" + event);
        }
        throw new Exception("C/C++ 等待断点超时；最后事件：" + lastEvent);
    }

    private static void testCppDebugCase(Context context, PackRegistry.Descriptor pack,
            JSONObject fixture) throws Exception {
        File folder = new File(context.getCacheDir(), "cpp-debug-test-" + java.util.UUID.randomUUID());
        if (!folder.mkdirs()) throw new Exception("无法创建 C/C++ 调试测试目录");
        String entryPath = fixture.getString("entryPath");
        String helperPath = fixture.getString("secondaryPath");
        try {
            File entry = new File(folder, entryPath);
            File helper = new File(folder, helperPath);
            Files.write(entry.toPath(), fixture.getString("source").getBytes(StandardCharsets.UTF_8));
            Files.write(helper.toPath(), fixture.getString("secondarySource")
                    .getBytes(StandardCharsets.UTF_8));
            Files.write(new File(folder, ".pocket-cpp.json").toPath(),
                    new JSONObject().put("sources", new JSONArray()
                            .put(entryPath).put(helperPath)).toString()
                            .getBytes(StandardCharsets.UTF_8));
            try (DebugPackClient session = new DebugPackClient(context, pack, folder, entry)) {
                DapTransport dap = session.dap();
                JSONObject capabilities = dap.request("initialize", new JSONObject()
                        .put("clientID", "pocketide-stage8").put("adapterID", "lldb")
                        .put("pathFormat", "path").put("linesStartAt1", true)
                        .put("columnsStartAt1", true), 10);
                if (!capabilities.optBoolean("supportsConfigurationDoneRequest"))
                    throw new Exception("LLDB 未提供 configurationDone");
                dap.request("launch", new JSONObject().put("program", entryPath), 10);
                dap.request("setBreakpoints", new JSONObject()
                        .put("source", new JSONObject().put("path", helperPath))
                        .put("breakpoints", new JSONArray().put(new JSONObject()
                                .put("line", fixture.getInt("breakpointLine")))), 10);
                dap.request("configurationDone", new JSONObject(), 10);
                JSONObject hit = waitCppStop(dap);
                if (!"breakpoint".equals(hit.getJSONObject("body").optString("reason")))
                    throw new Exception("C/C++ 跨文件断点未命中");
                int threadId = hit.getJSONObject("body").getInt("threadId");
                JSONArray frames = dap.request("stackTrace", new JSONObject()
                        .put("threadId", threadId), 10).getJSONArray("stackFrames");
                if (frames.length() < 2 || !helperPath.equals(frames.getJSONObject(0)
                        .getJSONObject("source").optString("path"))
                        || !entryPath.equals(frames.getJSONObject(1)
                                .getJSONObject("source").optString("path")))
                    throw new Exception("C/C++ 跨文件调用栈路径错误");
                JSONArray scopes = dap.request("scopes", new JSONObject().put("frameId",
                        frames.getJSONObject(0).getInt("id")), 10).getJSONArray("scopes");
                JSONArray vars = dap.request("variables", new JSONObject()
                        .put("variablesReference", scopes.getJSONObject(0)
                                .getInt("variablesReference")), 10).getJSONArray("variables");
                boolean variable = false;
                for (int i = 0; i < vars.length(); i++) {
                    JSONObject item = vars.getJSONObject(i);
                    if (fixture.getString("variableName").equals(item.optString("name"))
                            && fixture.getString("variableValue").equals(item.optString("value")))
                        variable = true;
                }
                if (!variable) throw new Exception("未读取到 C/C++ 局部变量");
                dap.request("next", new JSONObject().put("threadId", threadId), 10);
                if (!"step".equals(waitDap(dap, "stopped")
                        .getJSONObject("body").optString("reason")))
                    throw new Exception("C/C++ 单步失败");
                dap.request("continue", new JSONObject().put("threadId", threadId), 10);
                if (waitDap(dap, "exited").getJSONObject("body").optInt("exitCode", -1) != 0)
                    throw new Exception("C/C++ 调试退出码错误");
                waitDap(dap, "terminated");
                if (!session.awaitFinished(15)) throw new Exception("C/C++ 调试会话未清理");
            }
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(folder.toPath())) {
                for (java.nio.file.Path path : paths.sorted(java.util.Comparator.reverseOrder())
                        .toArray(java.nio.file.Path[]::new)) Files.deleteIfExists(path);
            }
        }
    }

    private static String detail(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static JSONObject invokeFixture(Context context, PackRegistry.Descriptor pack,
            String operation, JSONObject fixture, long timeout, CountDownLatch submitted)
            throws Exception {
        File folder = new File(context.getCacheDir(), "pack-test-" + java.util.UUID.randomUUID());
        if (!folder.mkdirs()) throw new Exception("无法创建测试目录");
        String extension = pack.manifest.getJSONArray("extensions").getString(0);
        File entry = new File(folder, "fixture" + extension);
        try {
            Files.write(entry.toPath(), fixture.optString("source").getBytes(StandardCharsets.UTF_8));
            return PackClient.invoke(context, pack, operation, folder, entry, timeout, submitted);
        } finally {
            Files.deleteIfExists(entry.toPath());
            Files.deleteIfExists(folder.toPath());
        }
    }
}
