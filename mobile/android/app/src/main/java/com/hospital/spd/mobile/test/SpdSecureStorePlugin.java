package com.hospital.spd.mobile.test;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts online signing recovery records; an unsuccessful durable save prevents a business request. */
@CapacitorPlugin(name = "SpdSecureStore")
public class SpdSecureStorePlugin extends Plugin {
    private static final String ALIAS = "spd_signing_journal_v1";
    private SharedPreferences records() {
        return getContext().getSharedPreferences("spd_signing_journal", Context.MODE_PRIVATE);
    }
    private String key(PluginCall call) throws Exception {
        String key = call.getString("key");
        if (key == null || key.isEmpty() || key.length() > 8192) throw new IllegalArgumentException("Invalid journal key");
        return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }
    private synchronized SecretKey secret() throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        if (keys.containsAlias(ALIAS)) return (SecretKey) keys.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false).build());
        return generator.generateKey();
    }
    @PluginMethod public void get(PluginCall call) {
        try {
            String encrypted = records().getString(key(call), null);
            JSObject result = new JSObject();
            if (encrypted == null) { result.put("value", org.json.JSONObject.NULL); call.resolve(result); return; }
            String[] parts = encrypted.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid encrypted record");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            result.put("value", new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
            call.resolve(result);
        } catch (Exception ex) { call.reject("无法读取本机作业记录，请联系管理员。原记录已保留", "JOURNAL_READ_FAILED", ex); }
    }
    @PluginMethod public void set(PluginCall call) {
        try {
            String value = call.getString("value");
            if (value == null || value.length() > 262144) throw new IllegalArgumentException("Invalid journal payload");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secret());
            String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            if (!records().edit().putString(key(call), encrypted).commit()) throw new IllegalStateException("Journal save failed");
            call.resolve();
        } catch (Exception ex) { call.reject("本机作业记录保存失败，已停止本次操作", "JOURNAL_WRITE_FAILED", ex); }
    }
    @PluginMethod public void remove(PluginCall call) {
        try {
            if (!records().edit().remove(key(call)).commit()) throw new IllegalStateException("Journal removal failed");
            call.resolve();
        } catch (Exception ex) { call.reject("本机记录清理失败", "JOURNAL_REMOVE_FAILED", ex); }
    }
}
