package com.example.demo.llmprovider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.config.LlmProviderProperties;
import com.example.demo.error.ApplicationException;
import org.junit.jupiter.api.Test;

class LlmProviderCryptoServiceTest {

    @Test
    void encryptsAndDecryptsApiKey() {
        LlmProviderProperties properties = new LlmProviderProperties();
        properties.setSecretKey("test-secret-key");
        LlmProviderCryptoService cryptoService = new LlmProviderCryptoService(properties);

        String ciphertext = cryptoService.encrypt("secret-api-key");

        assertNotEquals("secret-api-key", ciphertext);
        assertEquals("secret-api-key", cryptoService.decrypt(ciphertext));
    }

    @Test
    void encryptsSameApiKeyWithDifferentCiphertexts() {
        LlmProviderProperties properties = new LlmProviderProperties();
        properties.setSecretKey("test-secret-key");
        LlmProviderCryptoService cryptoService = new LlmProviderCryptoService(properties);

        String first = cryptoService.encrypt("secret-api-key");
        String second = cryptoService.encrypt("secret-api-key");

        assertNotEquals(first, second);
        assertEquals("secret-api-key", cryptoService.decrypt(first));
        assertEquals("secret-api-key", cryptoService.decrypt(second));
    }

    @Test
    void rejectsApiKeyStorageWithoutSecretKey() {
        LlmProviderCryptoService cryptoService = new LlmProviderCryptoService(new LlmProviderProperties());

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> cryptoService.encrypt("secret-api-key")
        );

        assertEquals("llm_provider.secret_key_missing", exception.getCode());
    }

    @Test
    void decryptFailsClosedWithoutSecretKey() {
        LlmProviderCryptoService writer = cryptoService("test-secret-key");
        LlmProviderCryptoService reader = new LlmProviderCryptoService(new LlmProviderProperties());
        String ciphertext = writer.encrypt("secret-api-key");

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> reader.decrypt(ciphertext)
        );

        assertEquals("llm_provider.secret_key_missing", exception.getCode());
    }

    @Test
    void rejectsInvalidCiphertext() {
        LlmProviderCryptoService cryptoService = cryptoService("test-secret-key");

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> cryptoService.decrypt("v1:not-base64:not-base64")
        );

        assertEquals("llm_provider.crypto_invalid_payload", exception.getCode());
    }

    @Test
    void trimsApiKeyBeforeEncrypting() {
        LlmProviderCryptoService cryptoService = cryptoService("test-secret-key");

        String ciphertext = cryptoService.encrypt("  secret-api-key  ");

        assertEquals("secret-api-key", cryptoService.decrypt(ciphertext));
    }

    private LlmProviderCryptoService cryptoService(String secretKey) {
        LlmProviderProperties properties = new LlmProviderProperties();
        properties.setSecretKey(secretKey);
        return new LlmProviderCryptoService(properties);
    }
}
