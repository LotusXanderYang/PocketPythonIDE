package com.example.pocketpythonide;

import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded Content-Length DAP transport over a Binder-transferred socket pair. */
final class DapTransport implements Closeable {
    private static final int MAX_MESSAGE = 1024 * 1024;
    private final ParcelFileDescriptor socket;
    private final InputStream input;
    private final OutputStream output;
    private final AtomicInteger sequence = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<JSONObject>> pending = new ConcurrentHashMap<>();
    private final BlockingQueue<JSONObject> events = new LinkedBlockingQueue<>(1000);
    private volatile IOException failure;

    DapTransport(ParcelFileDescriptor socket) throws IOException {
        this.socket = socket;
        input = new FileInputStream(socket.getFileDescriptor());
        output = new FileOutputStream(socket.getFileDescriptor());
        Thread reader = new Thread(this::readLoop, "dap-reader");
        reader.setDaemon(true);
        reader.start();
    }

    JSONObject request(String command, JSONObject arguments, long timeoutSeconds)
            throws Exception {
        if (failure != null) throw failure;
        int id = sequence.getAndIncrement();
        CompletableFuture<JSONObject> reply = new CompletableFuture<>();
        pending.put(id, reply);
        try {
            JSONObject message = new JSONObject().put("seq", id).put("type", "request")
                    .put("command", command).put("arguments",
                            arguments == null ? new JSONObject() : arguments);
            byte[] payload = message.toString().getBytes(StandardCharsets.UTF_8);
            if (payload.length > MAX_MESSAGE) throw new IOException("DAP request too large");
            synchronized (output) {
                output.write(("Content-Length: " + payload.length + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                output.write(payload);
                output.flush();
            }
            JSONObject response = reply.get(timeoutSeconds, TimeUnit.SECONDS);
            if (!response.optBoolean("success")) {
                String detail = response.optString("message");
                JSONObject body = response.optJSONObject("body");
                JSONObject error = body == null ? null : body.optJSONObject("error");
                if (detail.isEmpty() && error != null)
                    detail = error.optString("format", error.optString("id"));
                if (detail.isEmpty()) detail = response.toString();
                throw new IOException("DAP " + command + ": " + detail);
            }
            return response.optJSONObject("body") == null
                    ? new JSONObject() : response.getJSONObject("body");
        } catch (ExecutionException failed) {
            throw new IOException("DAP connection closed", failed.getCause());
        } catch (TimeoutException timedOut) {
            throw new IOException("DAP " + command + " timed out", timedOut);
        } finally { pending.remove(id); }
    }

    JSONObject nextEvent(long timeoutSeconds) throws InterruptedException {
        return events.poll(timeoutSeconds, TimeUnit.SECONDS);
    }

    private void readLoop() {
        try {
            while (true) {
                JSONObject message = readMessage();
                if ("response".equals(message.optString("type"))) {
                    CompletableFuture<JSONObject> reply = pending.get(message.optInt("request_seq"));
                    if (reply != null) reply.complete(message);
                } else if ("event".equals(message.optString("type"))) {
                    if (!events.offer(message)) throw new IOException("DAP event queue overflow");
                }
            }
        } catch (Exception ended) {
            failure = ended instanceof IOException ? (IOException) ended
                    : new IOException("DAP transport failed", ended);
            for (CompletableFuture<JSONObject> waiting : pending.values())
                waiting.completeExceptionally(failure);
            events.offer(new JSONObject()); // Wake a waiting UI worker.
        }
    }

    private JSONObject readMessage() throws Exception {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int value = input.read();
            if (value < 0) throw new IOException("DAP peer disconnected");
            header.write(value);
            if (header.size() > 4096) throw new IOException("DAP header too large");
            byte[] delimiter = {'\r', '\n', '\r', '\n'};
            matched = value == delimiter[matched] ? matched + 1
                    : value == delimiter[0] ? 1 : 0;
        }
        String[] lines = header.toString(StandardCharsets.US_ASCII.name()).split("\r\n");
        int length = -1;
        for (String line : lines) if (line.toLowerCase(java.util.Locale.ROOT)
                .startsWith("content-length:")) {
            if (length != -1) throw new IOException("Duplicate DAP length");
            length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
        }
        if (length <= 0 || length > MAX_MESSAGE) throw new IOException("Invalid DAP length");
        byte[] payload = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(payload, offset, length - offset);
            if (count < 0) throw new IOException("DAP message truncated");
            offset += count;
        }
        return new JSONObject(new String(payload, StandardCharsets.UTF_8));
    }

    @Override public void close() throws IOException { socket.close(); }
}
