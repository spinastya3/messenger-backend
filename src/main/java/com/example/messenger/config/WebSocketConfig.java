package com.example.messenger.config;

import org.springframework.context.annotation.Bean;
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

    // 🚀 СЕНЬОРСКИЙ ВЫСТРЕЛ №1: Создаем полноценный, управляемый бин планировщика!
    // Спринг сам его инициализирует в памяти Амверы, и контекст больше никогда не упадет!
    @Bean
    public ThreadPoolTaskScheduler customStompHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-thread-");
        scheduler.initialize(); // оживляем потоки таймера
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Подставляем наш переименованный инициализированный планировщик!
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000}) // Пинг раз в 10 секунд (пробиваем прокси!)
                .setTaskScheduler(customStompHeartbeatScheduler()); // 🟩 Вызываем переименованный метод!

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