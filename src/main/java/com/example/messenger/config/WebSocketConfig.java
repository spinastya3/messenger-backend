package com.example.messenger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@Profile("!test")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 🚀 СЕНЬОРСКИЙ ВЫСТРЕЛ ПО ЗАМОРОЗКЕ АМВЕРЫ:
        // 1. Добавляем в префиксы брокера "/queue" (чтобы работали персональные очереди, если понадобятся)
        // 2. Включаем принудительный Heartbeat (Пинг-Понг) со стороны сервера каждые 10 секунд (10000 миллисекунд)
        // 3. Регистрируем для этого встроенный планировщик задач Spring TaskScheduler
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000}) // Пинг от сервера к клиенту и обратно раз в 10 сек
                .setTaskScheduler(new ThreadPoolTaskScheduler()); // Включаем таймер пинга

        // Префикс адреса, на который телефон шлет сообщение
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Регистрируем точку входа /ws и разрешаем подключение любым устройствам (*)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}