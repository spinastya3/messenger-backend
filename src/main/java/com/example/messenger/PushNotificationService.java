package com.example.messenger;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    // 🎯 Главный метод отправки пуша по адресу-токену
    public void sendPushNotification(String targetToken, String title, String body) {
        if (targetToken == null || targetToken.isBlank()) {
            System.out.println("🟨 Пуш не улетит: у пользователя нет сохраненного токена-паспорта в базе.");
            return;
        }

        try {
            // Собираем красивую визуальную плашку уведомления
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            // Упаковываем посылку: пишем адрес (токен), текст и заголовок
            Message message = Message.builder()
                    .setToken(targetToken)
                    .setNotification(notification)
                    .build();

            // 🚀 ДЕЛАЕМ ТОТ САМЫЙ СЕКРЕТНЫЙ ВЫСТРЕЛ В СЕРВЕРА GOOGLE!
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("🟩 ПУШ УСПЕШНО ОТПРАВЛЕН В GOOGLE! Ответ сервера: " + response);

        } catch (Exception e) {
            System.err.println("🟥 ОШИБКА ОТПРАВКИ ПУША ЧЕРЕЗ FIREBASE: " + e.getMessage());
        }
    }
}
