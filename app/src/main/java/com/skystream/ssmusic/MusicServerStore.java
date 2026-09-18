package com.skystream.ssmusic;

import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

final class MusicServerStore {
    private static final String ALIAS = "ssmusic.music-server.rsa.v1";
    private static final int MAX_RECORD_BYTES = 65536;
    private final Context context;
    private final File directory;

    MusicServerStore(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getNoBackupFilesDir(), "music-server");
    }

    MusicServerAuth.Pending readPending() throws Exception {
        JSONObject data = read("pending");
        return data == null ? null : new MusicServerAuth.Pending(data.getString("origin"),
                data.getString("verifier"), data.getString("state"), data.getLong("startedAt"));
    }

    void savePending(MusicServerAuth.Pending pending) throws Exception {
        write("pending", new JSONObject().put("origin", pending.origin)
                .put("verifier", pending.verifier).put("state", pending.state)
                .put("startedAt", pending.startedAt));
    }

    Session readSession() throws Exception {
        JSONObject data = read("session");
        return data == null ? null : new Session(data.getString("origin"),
                data.getString("token"), data.getLong("expiresAt"));
    }

    void saveSession(Session session) throws Exception {
        write("session", new JSONObject().put("origin", session.origin)
                .put("token", session.token).put("expiresAt", session.expiresAt));
    }

    void clearPending() throws IOException {
        clear("pending");
    }

    void clearSession() throws IOException {
        clear("session");
    }

    void invalidateKey() throws Exception {
        keyStore(false).deleteEntry(ALIAS);
    }

    private void clear(String name) throws IOException {
        File file = new File(directory, name);
        new AtomicFile(file).delete();
        if (file.exists() || new File(directory, name + ".bak").exists()
                || new File(directory, name + ".new").exists()) {
            throw new IOException("Cannot clear saved sign-in.");
        }
    }

    private JSONObject read(String name) throws Exception {
        AtomicFile file = new AtomicFile(new File(directory, name));
        if (!file.getBaseFile().exists() && !new File(directory, name + ".bak").exists()) return null;
        byte[] encoded;
        try (FileInputStream input = file.openRead();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_RECORD_BYTES) throw new IOException("Invalid saved sign-in.");
                output.write(buffer, 0, count);
            }
            encoded = output.toByteArray();
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != 1) throw new IOException("Invalid saved sign-in.");
            int wrappedLength = input.readInt();
            if (wrappedLength != 256) throw new IOException("Invalid saved sign-in.");
            byte[] wrappedKey = new byte[wrappedLength];
            input.readFully(wrappedKey);
            byte[] nonce = new byte[12];
            input.readFully(nonce);
            byte[] encrypted = new byte[input.available()];
            input.readFully(encrypted);
            KeyStore keyStore = keyStore(false);
            Cipher rsa = wrappingCipher();
            rsa.init(Cipher.DECRYPT_MODE, keyStore.getKey(ALIAS, null));
            byte[] key = rsa.doFinal(wrappedKey);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            aes.updateAAD(name.getBytes(StandardCharsets.US_ASCII));
            return new JSONObject(new String(aes.doFinal(encrypted), StandardCharsets.UTF_8));
        }
    }

    private void write(String name, JSONObject data) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot save sign-in.");
        byte[] plain = data.toString().getBytes(StandardCharsets.UTF_8);
        if (plain.length > MAX_RECORD_BYTES - 1024) throw new IOException("Invalid saved sign-in.");
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        aes.updateAAD(name.getBytes(StandardCharsets.US_ASCII));
        Cipher rsa = wrappingCipher();
        rsa.init(Cipher.ENCRYPT_MODE, keyStore(true).getCertificate(ALIAS).getPublicKey());
        byte[] wrappedKey = rsa.doFinal(key.getEncoded());
        byte[] encrypted = aes.doFinal(plain);
        AtomicFile file = new AtomicFile(new File(directory, name));
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            DataOutputStream output = new DataOutputStream(stream);
            output.writeInt(1);
            output.writeInt(wrappedKey.length);
            output.write(wrappedKey);
            output.write(nonce);
            output.write(encrypted);
            output.flush();
            stream.getFD().sync();
            file.finishWrite(stream);
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            throw error;
        }
    }

    @SuppressWarnings("deprecation")
    private KeyStore keyStore(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS) && create) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            if (Build.VERSION.SDK_INT >= 23) {
                generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(2048).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                        .setDigests(KeyProperties.DIGEST_SHA1)
                        .build());
            } else {
                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.add(Calendar.YEAR, 30);
                generator.initialize(new KeyPairGeneratorSpec.Builder(context)
                        .setAlias(ALIAS).setKeySize(2048)
                        .setSubject(new X500Principal("CN=ssMusic Music Server"))
                        .setSerialNumber(BigInteger.ONE).setStartDate(start.getTime())
                        .setEndDate(end.getTime()).build());
            }
            generator.generateKeyPair();
        }
        return store;
    }

    private static Cipher wrappingCipher() throws Exception {
        if (Build.VERSION.SDK_INT >= 23) {
            return Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        }
        // API 21-22 Keystore does not support OAEP. This only wraps a random AES key
        // in an app-private file; decrypt failures are never exposed as a padding oracle.
        return Cipher.getInstance("RSA/ECB/PKCS1Padding");
    }

    static final class Session {
        final String origin;
        final String token;
        final long expiresAt;

        Session(String origin, String token, long expiresAt) throws Exception {
            this.origin = MusicServerAuth.normalizeOrigin(origin);
            if (token == null || token.length() > 8192
                    || !token.matches("[A-Za-z0-9._~+/-]+=*") || expiresAt <= 0) {
                throw new Exception("Invalid server session.");
            }
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
