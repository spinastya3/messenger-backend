package com.example.messenger.dto; // Укажи свой правильный пакет

public class RefreshSessionResponse {
    private String serverPublicKey;
    private String userId;

    public RefreshSessionResponse() {
    }

    public RefreshSessionResponse(String serverPublicKey, String userId) {
        this.serverPublicKey = serverPublicKey;
        this.userId = userId;
    }

    public String getServerPublicKey() {
        return serverPublicKey;
    }

    public void setServerPublicKey(String serverPublicKey) {
        this.serverPublicKey = serverPublicKey;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}

