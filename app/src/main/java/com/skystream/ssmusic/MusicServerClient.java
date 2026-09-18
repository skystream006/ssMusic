package com.skystream.ssmusic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public final class MusicServerClient {
    private static MusicServerClient instance;
    private static final int MAX_RESPONSE_BYTES = 65536;
    private final MusicServerStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MusicServerAuth.Pending pending;
    private MusicServerStore.Session session;
    private boolean busy;
    private boolean exchanging;
    private long generation;
    private HttpsURLConnection active;

    public interface Callback {
        void onComplete(boolean success, String message);
    }

    public static synchronized MusicServerClient get(Context context) {
        if (instance == null) instance = new MusicServerClient(context.getApplicationContext());
        return instance;
    }

    private MusicServerClient(Context context) {
        store = new MusicServerStore(context);
        try {
            session = store.readSession();
        } catch (Exception ignored) {
            clearSession();
        }
        try {
            pending = store.readPending();
        } catch (Exception ignored) {
            clearPending();
        }
        expireRecords();
    }

    public synchronized boolean isLoggedIn() {
        expireRecords();
        return session != null;
    }

    public synchronized String getServerOrigin() {
        expireRecords();
        return pending != null ? pending.origin : session != null ? session.origin : "";
    }

    public synchronized boolean isBusy() {
        expireRecords();
        return busy;
    }

    public synchronized boolean hasPendingLogin() {
        expireRecords();
        return pending != null;
    }

    public synchronized String beginLogin(String origin) throws Exception {
        expireRecords();
        if (busy || pending != null) throw new Exception("A server operation is already in progress.");
        MusicServerAuth.Pending next = MusicServerAuth.newPending(
                origin, System.currentTimeMillis(), new SecureRandom());
        String browserUrl = MusicServerAuth.loginUrl(next);
        session = null;
        try {
            store.clearSession();
            store.savePending(next);
        } catch (Exception ignored) {
            clearPending();
            clearSession();
            throw new Exception("Could not securely save sign-in. Please try again.");
        }
        pending = next;
        generation++;
        return browserUrl;
    }

    public synchronized void cancelLogin() {
        clearPending();
        if (exchanging) {
            generation++;
            busy = false;
            exchanging = false;
            if (active != null) active.disconnect();
            active = null;
        }
    }

    public synchronized void completeLogin(String callbackUri, Callback callback) {
        expireRecords();
        if (busy) {
            deliver(callback, false, "A server operation is already in progress.");
            return;
        }
        if (pending == null) {
            deliver(callback, false, "No sign-in is pending. Please sign in again.");
            return;
        }
        MusicServerAuth.Pending request = pending;
        final String code;
        try {
            code = request.consumeCallback(callbackUri, System.currentTimeMillis());
        } catch (Exception ignored) {
            deliver(callback, false, "Invalid login response. Return to the browser to finish signing in.");
            return;
        }
        pending = null;
        try {
            // Remove the persisted verifier before sending the one-use authorization code.
            store.clearPending();
        } catch (Exception ignored) {
            invalidateStoredRecords();
            deliver(callback, false, "Could not securely complete sign-in. Please try again.");
            return;
        }
        busy = true;
        exchanging = true;
        long operation = ++generation;
        executor.execute(() -> exchange(request, code, operation, callback));
    }

    private void exchange(MusicServerAuth.Pending request, String code, long operation, Callback callback) {
        try {
            JSONObject body = new JSONObject().put("code", code)
                    .put("codeVerifier", request.verifier)
                    .put("redirectUri", MusicServerAuth.REDIRECT_URI);
            Response response = post(request.origin, "/api/auth/app/token", null, body, operation, true);
            if (response.status != 200) {
                finishHttpFailure(operation, response.status, callback, false);
                return;
            }
            JSONObject root = new JSONObject(response.body);
            root.getJSONObject("user");
            JSONObject result = root.getJSONObject("session");
            if (!"Bearer".equals(result.getString("tokenType"))) throw new IOException();
            MusicServerStore.Session authenticated = new MusicServerStore.Session(request.origin,
                    result.getString("token"), MusicServerAuth.parseExpiry(result.getString("expiresAt")));
            if (authenticated.expiresAt <= System.currentTimeMillis()) throw new IOException();
            synchronized (this) {
                if (operation != generation) {
                    deliver(callback, false, "Sign-in was cancelled.");
                    return;
                }
                store.saveSession(authenticated);
                session = authenticated;
                finish(operation, callback, true, "Signed in to the music server.");
            }
        } catch (Exception ignored) {
            finish(operation, callback, false, "Could not sign in. Check the server and try again.");
        }
    }

    public synchronized void submitJob(String url, Callback callback) {
        expireRecords();
        if (busy || pending != null) {
            deliver(callback, false, "A server operation is already in progress.");
            return;
        }
        if (session == null) {
            deliver(callback, false, "Please sign in to your music server first.");
            return;
        }
        if (!validJobUrl(url)) {
            deliver(callback, false, "Enter an HTTPS music.youtube.com URL.");
            return;
        }
        MusicServerStore.Session authenticated = session;
        busy = true;
        long operation = ++generation;
        executor.execute(() -> {
            try {
                Response response = post(authenticated.origin, "/api/jobs", authenticated.token,
                        new JSONObject().put("url", url), operation, false);
                if (response.status == 202) {
                    finish(operation, callback, true, "Music job accepted by the server.");
                } else {
                    finishHttpFailure(operation, response.status, callback, true);
                }
            } catch (Exception ignored) {
                finish(operation, callback, false,
                        "Could not confirm the job. Check the server before trying again to avoid duplicates.");
            }
        });
    }

    public synchronized void logout() {
        generation++;
        busy = false;
        exchanging = false;
        if (active != null) active.disconnect();
        active = null;
        clearPending();
        clearSession();
    }

    private synchronized void expireRecords() {
        long now = System.currentTimeMillis();
        if (session != null && session.expiresAt <= now) clearSession();
        if (pending != null && pending.isExpired(now)) clearPending();
    }

    private void clearPending() {
        pending = null;
        try {
            store.clearPending();
        } catch (Exception ignored) {
            invalidateStoredRecords();
        }
    }

    private void clearSession() {
        session = null;
        try {
            store.clearSession();
        } catch (Exception ignored) {
            invalidateStoredRecords();
        }
    }

    private void invalidateStoredRecords() {
        session = null;
        pending = null;
        try {
            store.invalidateKey();
        } catch (Exception ignored) {
            // Fail closed in memory; no network operation may use these records.
        }
    }

    private synchronized void finishHttpFailure(long operation, int status,
            Callback callback, boolean job) {
        if (operation == generation && status == 401) clearSession();
        String message;
        if (status == 401) {
            message = "Sign-in expired or was rejected. Please sign in again.";
        } else if (status == 403) {
            message = "Access denied. Ask the server administrator to grant your account access.";
        } else if (status == 409 && job) {
            message = "This music job already exists on the server.";
        } else if (status >= 300 && status < 400) {
            message = "The server redirected the request. Use its final HTTPS origin and sign in again.";
        } else {
            message = job ? "The server did not accept the job. Check the server before trying again."
                    : "The server rejected sign-in. Please start a new sign-in.";
        }
        finish(operation, callback, false, message);
    }

    private synchronized void finish(long operation, Callback callback, boolean success, String message) {
        if (operation != generation) {
            deliver(callback, false, "Server operation was cancelled.");
            return;
        }
        busy = false;
        exchanging = false;
        deliver(callback, success, message);
    }

    private void deliver(Callback callback, boolean success, String message) {
        if (callback != null) main.post(() -> callback.onComplete(success, message));
    }

    static boolean validJobUrl(String value) {
        if (value == null || value.length() > 8192) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "music.youtube.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (uri.getRawAuthority().equalsIgnoreCase("music.youtube.com")
                    || uri.getRawAuthority().equalsIgnoreCase("music.youtube.com:443"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private Response post(String origin, String path, String token, JSONObject body,
            long operation, boolean readBody) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpsURLConnection connection = (HttpsURLConnection) new URL(
                MusicServerAuth.normalizeOrigin(origin) + path).openConnection();
        synchronized (this) {
            if (operation != generation) throw new IOException("Cancelled.");
            active = connection;
        }
        long deadline = System.nanoTime() + 45_000_000_000L;
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Connection", "close");
            if (token != null) connection.setRequestProperty("Authorization", "Bearer" + " " + token);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int status = connection.getResponseCode();
            String responseBody = "";
            if (readBody && status == 200) {
                try (InputStream input = connection.getInputStream()) {
                    responseBody = readBounded(input, deadline);
                }
            }
            return new Response(status, responseBody);
        } finally {
            connection.disconnect();
            synchronized (this) {
                if (active == connection) active = null;
            }
        }
    }

    static String readBounded(InputStream input, long deadline) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (System.nanoTime() > deadline || output.size() + count > MAX_RESPONSE_BYTES) {
                throw new IOException("Server response exceeded limits.");
            }
            output.write(buffer, 0, count);
        }
        if (System.nanoTime() > deadline) throw new IOException("Server response timed out.");
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
