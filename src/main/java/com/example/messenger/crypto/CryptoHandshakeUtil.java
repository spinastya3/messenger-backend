package com.example.messenger.crypto;

import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;

@Component
public class CryptoHandshakeUtil {

    private final DhCryptoService dhCryptoService;
    private final SessionKeyManager sessionKeyManager;

    public CryptoHandshakeUtil(DhCryptoService dhCryptoService, SessionKeyManager sessionKeyManager) {
        this.dhCryptoService = dhCryptoService;
        this.sessionKeyManager = sessionKeyManager;
    }

    public void processHandshake(String username, String clientPublicKey, Map<String, String> responseMap) {
        if (clientPublicKey == null || clientPublicKey.trim().isEmpty()) {
            return;
        }
        try {
            // 1. Генерируем временную пару ключей сервера
            KeyPair serverKeyPair = dhCryptoService.generateServerKeyPair();

            // 2. Вычисляем общий секрет AES-256
            String sharedKeyBase64 = dhCryptoService.computeSharedSecret(serverKeyPair.getPrivate(), clientPublicKey);

            // 3. Сохраняем сессионный ключ в оперативку сервера
            sessionKeyManager.saveKey(username, sharedKeyBase64);

            // 4. Кодируем публичный ключ сервера в Base64 и подкладываем в JSON-ответ для мобилки
            String serverPublicKeyBase64 = Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded());
            responseMap.put("serverPublicKey", serverPublicKeyBase64);

            int anonymizedId = (username != null) ? Math.abs(username.hashCode() % 10000) : 0;

            System.out.println("🟩 [ECDH-UTIL] Сессионный ключ для юзера " + anonymizedId + " успешно сгенерирован и упакован.");
        } catch (Exception e) {
            throw new IllegalArgumentException("Критическая ошибка криптографии ECDH во время рукопожатия: " + e.getMessage(), e);
        }
    }
}
