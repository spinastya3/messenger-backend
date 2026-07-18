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
    public ThreadPoolTaskScheduler messageBrokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-thread-");
        scheduler.initialize(); // 🟩 Принудительно оживляем потоки таймера!
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 🚀 СЕНЬОРСКИЙ ВЫСТРЕЛ №2: Включаем 10-секундный Пинг-Понг,
        // подставляя наш стопроцентно инициализированный бин планировщика!
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000}) // Пинг раз в 10 сек (пробиваем прокси Амверы!)
                .setTaskScheduler(messageBrokerTaskScheduler()); // 🟩 Подвязали живой таймер!

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