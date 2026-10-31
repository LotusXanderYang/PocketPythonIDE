package org.idecpp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Launcher and isolated compiler smoke test; no IDE or Termux data is touched. */
public final class CppInfoActivity extends Activity {
    private static final String TAG = "PocketCppProbe";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("正在验证独立 C/C++ 工具链…");
        setContentView(view);
        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            try {
                Toolchain toolchain = new Toolchain(this);
                long started = System.currentTimeMillis();
                toolchain.ensure();
                report.append("installMs=").append(System.currentTimeMillis() - started).append('\n');
                File project = new File(getCacheDir(), "cpp-probe");
                if (!project.mkdirs() && !project.isDirectory()) throw new Exception("Cannot create project");
                report.append(test(toolchain, project, "hello.c", "#include <stdio.h>\nint main(void){puts(\"independent-c\");return 0;}\n", "c11"));
                report.append(test(toolchain, project, "hello.cpp", "#include <iostream>\nint main(){std::cout << \"independent-cpp\\n\";return 0;}\n", "c++17"));
            } catch (Exception problem) {
                report.append("ERROR ").append(Log.getStackTraceString(problem)).append('\n');
            }
            Log.i(TAG, "RESULT " + report.toString().replace('\n', '|'));
            runOnUiThread(() -> view.setText(report.toString()));
        }, "cpp-pack-smoke").start();
    }

    private String test(Toolchain tools, File project, String name, String source, String standard) throws Exception {
        File file = new File(project, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(source.getBytes(StandardCharsets.UTF_8));
        }
        File binary = new File(project, "lib" + name.replace('.', '_') + ".so");
        List<String> args = new ArrayList<>(Arrays.asList(
                tools.compiler().getAbsolutePath(), "-target", "aarch64-linux-android26",
                "--sysroot=" + getFilesDir().getAbsolutePath(),
                "-resource-dir", new File(tools.prefix(), "lib/clang/21").getAbsolutePath(),
                "-B" + new File(tools.prefix(), "lib").getAbsolutePath(),
                "-L" + new File(tools.prefix(), "lib").getAbsolutePath(),
                "-isystem", new File(tools.prefix(), "include/c++/v1").getAbsolutePath(),
                "-isystem", new File(tools.prefix(), "include").getAbsolutePath(),
                "-fuse-ld=" + tools.linkerWrapper().getAbsolutePath(),
                "-shared", "-fPIC", "-std=" + standard,
                "-o", binary.getAbsolutePath(), file.getAbsolutePath()));
        if (name.endsWith(".cpp")) args.add("-lc++_shared");
        String compile = process(tools, project, args);
        if (!compile.startsWith("exit=0")) return name + " compile " + compile;
        String run = process(tools, project, Arrays.asList(tools.runner().getAbsolutePath(), binary.getAbsolutePath()));
        return name + " compile " + compile + " run " + run;
    }

    private String process(Toolchain tools, File dir, List<String> args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(args).directory(dir).redirectErrorStream(true);
        builder.environment().put("LD_LIBRARY_PATH", tools.prefix() + "/lib:" + getApplicationInfo().nativeLibraryDir);
        builder.environment().put("TMPDIR", dir.getAbsolutePath());
        Process process = builder.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "timeout\n";
        }
        String output = new String(Toolchain.readAll(process.getInputStream()), StandardCharsets.UTF_8);
        return "exit=" + process.exitValue() + " output=" + output.trim() + "\n";
    }
}
