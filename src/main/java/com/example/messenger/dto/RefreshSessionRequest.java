package com.example.messenger.dto; // Укажи свой правильный пакет

public class RefreshSessionRequest {
    private String clientPublicKey;

    // Пустой конструктор для Jackson (чтобы Spring мог распарсить JSON)
    public RefreshSessionRequest() {
    }

    public RefreshSessionRequest(String clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
    }

    public String getClientPublicKey() {
        return clientPublicKey;
    }

    public void setClientPublicKey(String clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
    }
}
