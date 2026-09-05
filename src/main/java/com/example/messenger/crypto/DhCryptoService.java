package com.example.messenger.crypto;

import org.springframework.stereotype.Service;

import javax.crypto.KeyAgreement;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class DhCryptoService {

    // Генерирует пару ключей для сервера (Приватный + Публичный)
    public KeyPair generateServerKeyPair() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("EC"); // Эллиптические кривые
        keyPairGen.initialize(256); // Размер ключа 256 бит (дает AES-256)
        return keyPairGen.generateKeyPair();
    }

    // Вычисляет общий секрет (AES ключ) на основе приватного ключа сервера и публичного ключа Android
    public String computeSharedSecret(PrivateKey serverPrivateKey, String clientPublicKeyBase64) throws Exception {
        byte[] clientKeyBytes = Base64.getDecoder().decode(clientPublicKeyBase64);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PublicKey clientPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(clientKeyBytes));

        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(serverPrivateKey);
        keyAgreement.doPhase(clientPublicKey, true);

        byte[] sharedSecret = keyAgreement.generateSecret();

        // Берем первые 32 байта для AES-256 (на эллиптических кривых 256 секрет и так будет 32 байта)
        byte[] aesKey = new byte[32];
        System.arraycopy(sharedSecret, 0, aesKey, 0, Math.min(sharedSecret.length, 32));

        return Base64.getEncoder().encodeToString(aesKey);
    }
}
