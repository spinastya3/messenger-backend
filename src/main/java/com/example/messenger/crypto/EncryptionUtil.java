package com.example.messenger.crypto;

import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";

    // 🔒 ШИФРОВАНИЕ ДЛЯ СЕТИ: Теперь принимает текст сообщения и Base64 сессионный ключ пользователя
    public String encrypt(String plainText, String secretKeyBase64) {
        if (plainText == null || plainText.isEmpty() || secretKeyBase64 == null) return plainText;
        try {
            // Декодируем сессионный ключ из Base64 формата в байты
            byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            System.err.println("❌ Ошибка сессионного шифрования: " + e.getMessage());
            return plainText;
        }
    }

    // 🔓 ДЕШИФРОВАНИЕ ДЛЯ СЕТИ: Принимает зашифрованный текст и Base64 сессионный ключ пользователя
    public String decrypt(String encryptedText, String secretKeyBase64) {
        if (encryptedText == null || encryptedText.isEmpty() || secretKeyBase64 == null) return encryptedText;
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("❌ Ошибка сессионного дешифрования: " + e.getMessage());
            return encryptedText;
        }
    }
}
