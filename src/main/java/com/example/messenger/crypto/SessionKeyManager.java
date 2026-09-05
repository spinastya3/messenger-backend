package com.example.messenger.crypto;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionKeyManager {
    // Хранилище: Username -> Временный AES Ключ сессии (в Base64)
    private final Map<String, String> userKeys = new ConcurrentHashMap<>();

    public void saveKey(String username, String base64Key) {
        userKeys.put(username, base64Key);
    }

    public String getKey(String username) {
        return userKeys.get(username);
    }

    public void removeKey(String username) {
        userKeys.remove(username);
    }
}
