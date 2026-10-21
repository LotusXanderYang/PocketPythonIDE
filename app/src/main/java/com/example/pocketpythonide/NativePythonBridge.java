package com.example.pocketpythonide;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class NativePythonBridge {
    static { System.loadLibrary("nativepythonbridge"); }

    private NativePythonBridge() {}

    private static native String initialize(String pythonHome);
    private static native int run(byte[] utf8Code);

    static String probe(Context context) throws Exception {
        JSONObject command = new JSONObject().put("mode", "probe")
                .put("workdir", new File(context.getFilesDir(), "scripts").getAbsolutePath());
        return execute(context, command);
    }

    static String runCode(Context context, String source, String filename, String workdir)
            throws Exception {
        JSONObject command = new JSONObject()
                .put("mode", "run")
                .put("source", source)
                .put("filename", filename)
                .put("workdir", workdir);
        return execute(context, command);
    }

    private static String execute(Context context, JSONObject command) throws Exception {
        File pythonHome = NativeRuntimeInstaller.install(context);
        String error = initialize(pythonHome.getAbsolutePath());
        if (error != null) throw new IOException(error);
        File input = File.createTempFile("pocketpy-command-", ".json", context.getCacheDir());
        File output = new File(input.getAbsolutePath() + ".result");
        File diagnostic = new File(input.getAbsolutePath() + ".diagnostic");
        try {
            Files.write(input.toPath(), command.toString().getBytes(StandardCharsets.UTF_8));
            String script = "try:\n"
                    + "    import pocketpy_runtime_bridge\n"
                    + "    pocketpy_runtime_bridge.main("
                    + pythonQuote(input.getAbsolutePath()) + ", "
                    + pythonQuote(output.getAbsolutePath()) + ")\n";
            script += "except BaseException:\n"
                    + "    import traceback\n"
                    + "    with open(" + pythonQuote(diagnostic.getAbsolutePath())
                    + ", 'w', encoding='utf-8') as diagnostic:\n"
                    + "        diagnostic.write(traceback.format_exc())\n";
            int nativeResult = run(script.getBytes(StandardCharsets.UTF_8));
            if (nativeResult != 0 || !output.isFile()) {
                String detail = diagnostic.isFile()
                        ? new String(Files.readAllBytes(diagnostic.toPath()), StandardCharsets.UTF_8)
                        : "Embedded Python command failed before producing a result"
                                + " (native result=" + nativeResult + ", script=" + script + ")";
                throw new IOException(detail);
            }
            return new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(input.toPath());
            Files.deleteIfExists(output.toPath());
            Files.deleteIfExists(diagnostic.toPath());
        }
    }

    // JSONObject.quote escapes '/', but Python string literals keep '\/' as a
    // backslash plus slash. Android absolute paths must retain plain slashes.
    private static String pythonQuote(String value) {
        return JSONObject.quote(value).replace("\\/", "/");
    }
}
