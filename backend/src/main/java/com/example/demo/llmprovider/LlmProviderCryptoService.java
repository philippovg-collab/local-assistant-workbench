package com.example.demo.llmprovider;

import com.example.demo.config.LlmProviderProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LlmProviderCryptoService {

    private static final String PREFIX = "v1";
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final LlmProviderProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public LlmProviderCryptoService(LlmProviderProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        ensureSecretConfigured(
            ErrorType.INVALID_REQUEST,
            "APP_LLM_PROVIDER_SECRET_KEY must be configured before saving LLM provider API keys"
        );
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));
            return PREFIX + ":"
                + Base64.getEncoder().encodeToString(nonce)
                + ":"
                + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "llm_provider.crypto_failed",
                "Unable to encrypt LLM provider API key",
                exception
            );
        }
    }

    public String decrypt(String ciphertext) {
        if (!StringUtils.hasText(ciphertext)) {
            return null;
        }
        ensureSecretConfigured(
            ErrorType.INVALID_CONFIGURATION,
            "APP_LLM_PROVIDER_SECRET_KEY must be configured before reading stored LLM provider API keys"
        );
        String[] parts = ciphertext.split(":", 3);
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw invalidCiphertext(null);
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            if (nonce.length != NONCE_BYTES) {
                throw invalidCiphertext(null);
            }
            byte[] payload = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw invalidCiphertext(exception);
        }
    }

    public boolean canStoreApiKeys() {
        return StringUtils.hasText(properties.getSecretKey());
    }

    private SecretKeySpec key() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(properties.getSecretKey().trim().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "llm_provider.crypto_failed",
                "Unable to initialize LLM provider encryption",
                exception
            );
        }
    }

    private void ensureSecretConfigured(ErrorType errorType, String message) {
        if (!canStoreApiKeys()) {
            throw new ApplicationException(
                errorType,
                "llm_provider.secret_key_missing",
                message
            );
        }
    }

    private ApplicationException invalidCiphertext(Throwable cause) {
        return new ApplicationException(
            ErrorType.INVALID_CONFIGURATION,
            "llm_provider.crypto_invalid_payload",
            "Stored LLM provider API key cannot be decrypted",
            cause
        );
    }
}
