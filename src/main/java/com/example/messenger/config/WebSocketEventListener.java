package com.example.messenger.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    // 📡 1. ШПИОН НА ПОПЫТКУ ПОДКЛЮЧЕНИЯ (Срабатывает в секунду connect)
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        log.info("🔌 [SOCKET] Новая попытка подключения... Session ID: {}", sessionId);
    }

    // 🟢 2. ШПИОН НА УСПЕШНЫЙ КОННЕКТ (Труба открыта!)
    @EventListener
    public void handleWebSocketConnectedListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        log.info("🟩 [SOCKET] УРА! Соединение успешно установлено! Труба онлайн. Session ID: {}", sessionId);
    }

    // 🔴 3. ШПИОН НА ОБРЫВ СВЯЗИ (Вот тут мы поймаем твою улицу!)
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // Вытаскиваем причину закрытия сессии (например, таймаут, закрытие приложения или плохой сигнал)
        Object closeStatus = event.getCloseStatus();

        log.warn("🟥 [SOCKET] ВНИМАНИЕ! Соединение разорвано! Session ID: {}", sessionId);
        if (closeStatus != null) {
            log.warn("⚠️ [SOCKET] Официальная причина отвала от сервера: {}", closeStatus.toString());
        }
    }
}
