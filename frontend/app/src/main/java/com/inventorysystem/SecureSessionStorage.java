package com.inventorysystem;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureSessionStorage {
    private static final String KEY_ALIAS = "inventory_session_aes_v1";
    private static final String PREFIX = "enc:v1:";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private final SharedPreferences preferences;

    SecureSessionStorage(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    synchronized void putString(String key, String value) {
        if (value == null) {
            preferences.edit().remove(key).apply();
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[cipher.getIV().length + encrypted.length];
            System.arraycopy(cipher.getIV(), 0, combined, 0, cipher.getIV().length);
            System.arraycopy(encrypted, 0, combined, cipher.getIV().length, encrypted.length);
            preferences.edit().putString(key,
                    PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)).apply();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect session data", exception);
        }
    }

    synchronized String getString(String key) {
        Object storedValue = preferences.getAll().get(key);
        if (storedValue == null) return null;

        String value = String.valueOf(storedValue);
        if (!value.startsWith(PREFIX)) {
            putString(key, value);
            return value;
        }

        try {
            byte[] combined = Base64.decode(value.substring(PREFIX.length()), Base64.NO_WRAP);
            if (combined.length <= 12) throw new IllegalArgumentException("Encrypted value is incomplete");
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            preferences.edit().remove(key).apply();
            return null;
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        KeyStore.Entry existing = keyStore.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
