package org.pocketide.stage5.probe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Tests Android app-process exec restrictions without touching IDE data. */
public final class ProbeActivity extends Activity {
    private static final String TAG = "Stage5ExecProbe";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView output = new TextView(this);
        output.setText("正在测试执行位置…");
        setContentView(output);
        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            File copy = new File(getFilesDir(), "hello_c");
            try (InputStream in = getAssets().open("hello_c");
                    FileOutputStream out = new FileOutputStream(copy)) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
            } catch (Exception problem) {
                report.append("appFiles: ").append(problem).append('\n');
            }
            if (copy.isFile()) {
                if (copy.setExecutable(true, true)) report.append(run("appFiles", copy));
                else report.append("appFiles: chmod failed\n");
            }
            File nativeFile = new File(getApplicationInfo().nativeLibraryDir, "libprobe.so");
            report.append(run("nativeLibraryDir", nativeFile));
            File generated = new File(getFilesDir(), "libentry.so");
            try (InputStream in = getAssets().open("libentry.so");
                    FileOutputStream out = new FileOutputStream(generated)) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
            } catch (Exception problem) {
                report.append("generatedCopy: ").append(problem).append('\n');
            }
            File runner = new File(getApplicationInfo().nativeLibraryDir, "librunner.so");
            if (generated.isFile()) {
                report.append(run("dlopenWritable", runner, generated.getAbsolutePath()));
                generated.setWritable(false, false);
                report.append(run("dlopenReadOnly", runner, generated.getAbsolutePath()));
            }
            File projectRunner = new File(getApplicationInfo().nativeLibraryDir,
                    "libprojectrunner.so");
            for (String name : new String[]{"libproject_c.so", "libproject_c_multi.so",
                    "libproject_cpp.so", "libproject_cpp_multi.so"}) {
                File project = new File(getFilesDir(), name);
                try (InputStream in = getAssets().open(name);
                        FileOutputStream out = new FileOutputStream(project)) {
                    byte[] buffer = new byte[8192];
                    int size;
                    while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
                } catch (Exception problem) {
                    report.append(name).append(": ").append(problem).append('\n');
                }
                if (project.isFile())
                    report.append(run(name, projectRunner, project.getAbsolutePath()));
            }
            Log.i(TAG, "RESULT " + report.toString().replace('\n', '|'));
            runOnUiThread(() -> output.setText(report.toString()));
        }, "stage5-exec-probe").start();
    }

    private static String run(String label, File path, String... arguments) {
        try {
            java.util.ArrayList<String> command = new java.util.ArrayList<>();
            command.add(path.getAbsolutePath());
            java.util.Collections.addAll(command, arguments);
            Process process = new ProcessBuilder(command).start();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return label + ": timeout\n";
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return label + ": exit=" + process.exitValue() + " stdout=" + stdout.trim()
                    + " stderr=" + stderr.trim() + " path=" + path + "\n";
        } catch (Exception problem) {
            return label + ": " + problem + " path=" + path + "\n";
        }
    }
}
