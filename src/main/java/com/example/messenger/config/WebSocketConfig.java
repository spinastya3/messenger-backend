package com.example.messenger.config;

import com.example.messenger.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@Profile("!test")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    // Внедряем утилиту для проверки токенов
    public WebSocketConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public ThreadPoolTaskScheduler customStompHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-thread-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(customStompHeartbeatScheduler());

        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    // 🚀 НОВЫЙ МЕТОД: Настраиваем перехватчик входящих сообщений сокета
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                // Если клиент отправляет команду CONNECT (первичное подключение трубы)
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Вытаскиваем заголовок Authorization
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            // Проверяем токен через наш JwtUtil и вытаскиваем имя пользователя
                            String username = jwtUtil.extractUsername(token);

                            if (username != null) {
                                // Создаем объект аутентификации Spring Security
                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(username, null, List.of());

                                // Привязываем пользователя к текущей WebSocket-сессии!
                                accessor.setUser(authentication);
                                System.out.println("🟩 [SOCKET-AUTH] Пользователь " + username + " успешно авторизован в сокете!");
                            }
                        } catch (Exception e) {
                            System.err.println("🟥 [SOCKET-AUTH] Ошибка валидации токена в сокете: " + e.getMessage());
                            // Если токен "левый" — выбрасываем ошибку, соединение сбросится
                            throw new IllegalArgumentException("Невалидный JWT токен для WebSocket");
                        }
                    } else {
                        System.err.println("🟥 [SOCKET-AUTH] Попытка подключения к сокету без заголовка Authorization!");
                        throw new IllegalArgumentException("Отсутствует токен авторизации");
                    }
                }
                return message;
            }
        });
    }
}