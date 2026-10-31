package org.idecpp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs one packaged native process with bounded streaming, deadline and kill. */
final class CppProcess {
    static final class Result {
        final int exit;
        final String diagnostic;
        final boolean timedOut;
        final boolean outputLimit;
        Result(int exit, String diagnostic, boolean timedOut, boolean outputLimit) {
            this.exit = exit;
            this.diagnostic = diagnostic;
            this.timedOut = timedOut;
            this.outputLimit = outputLimit;
        }
    }

    interface Active { void set(Process process); boolean cancelled(); }

    private CppProcess() { }

    static Result run(List<String> command, File directory, Toolchain tools,
            byte[] stdin, OutputStream output, Active active, long deadline)
            throws Exception {
        if (active.cancelled()) throw new InterruptedException("cancelled");
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory);
        builder.environment().put("LD_LIBRARY_PATH", tools.prefix() + "/lib:"
                + tools.compiler().getParent());
        builder.environment().put("TMPDIR", directory.getAbsolutePath());
        Process process = builder.start();
        active.set(process);
        LimitedOutput limited = new LimitedOutput(output, process);
        ByteArrayOutputStream diagnostic = new ByteArrayOutputStream();
        Thread stdout = pump("stdout", process.getInputStream(), limited, null, false);
        Thread stderr = pump("stderr", process.getErrorStream(), limited, diagnostic, true);
        Thread input = new Thread(() -> {
            try (OutputStream stream = process.getOutputStream()) {
                if (stdin != null) stream.write(stdin);
            } catch (IOException ignored) { }
        }, "cpp-process-stdin");
        input.start();
        boolean timedOut = false;
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !process.waitFor(remaining, TimeUnit.NANOSECONDS)) {
                timedOut = true;
                process.destroyForcibly();
            }
            stdout.join(3000);
            stderr.join(3000);
            input.join(1000);
            if (active.cancelled() || Thread.currentThread().isInterrupted())
                throw new InterruptedException("cancelled");
            return new Result(timedOut ? -1 : process.exitValue(),
                    diagnostic.toString(StandardCharsets.UTF_8.name()),
                    timedOut, limited.exceeded);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            active.set(null);
        }
    }

    private static Thread pump(String label, InputStream input, LimitedOutput output,
            ByteArrayOutputStream diagnostic, boolean isError) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            boolean tagged = false;
            try (InputStream stream = input) {
                int length;
                while ((length = stream.read(buffer)) != -1) {
                    if (diagnostic != null && diagnostic.size() < 64 * 1024)
                        diagnostic.write(buffer, 0, Math.min(length, 64 * 1024 - diagnostic.size()));
                    if (isError && !tagged) {
                        output.write("[stderr]\n".getBytes(StandardCharsets.UTF_8));
                        tagged = true;
                    }
                    output.write(buffer, 0, length);
                }
            } catch (IOException ignored) { }
        }, "cpp-process-" + label);
        thread.start();
        return thread;
    }

    private static final class LimitedOutput {
        final OutputStream stream;
        final Process process;
        int written;
        volatile boolean exceeded;
        LimitedOutput(OutputStream stream, Process process) {
            this.stream = stream;
            this.process = process;
        }
        synchronized void write(byte[] bytes) throws IOException { write(bytes, 0, bytes.length); }
        synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            if (exceeded) return;
            if (written + length > 3_500_000) {
                exceeded = true;
                process.destroyForcibly();
                return;
            }
            stream.write(bytes, offset, length);
            written += length;
        }
    }
}
