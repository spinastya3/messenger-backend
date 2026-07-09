package com.example.messenger;

import com.google.firebase.messaging.*;
import org.springframework.stereotype.Service;

import static com.example.messenger.util.AppConstants.*;

@Service
public class PushNotificationService {

    // 🎯 Главный метод отправки пуша теперь принимает еще ID и Имя отправителя!
    public void sendPushNotification(String targetToken, String title, String body, Long senderId, String senderName) {
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

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH) // 🔥 ВЫСШИЙ ПРИОРИТЕТ! Пробивает Doze Mode телефона!
                    .setNotification(AndroidNotification.builder()
                            .setSound("default") // Добавляем стандартный звук пули, чтобы телефон пищал!
                            .setDefaultSound(true)
                            .build())
                    .build();


            // 🥷 Упаковываем посылку: пишем адрес, визуальную плашку
            // И ВШИВАЕМ КЛЮЧИ СТРОГО КРУПНЫМИ БУКВАМИ, как их ждет наша мобилка по умолчанию!
            Message message = Message.builder()
                    .setToken(targetToken)
                    .setNotification(notification)
                    .setAndroidConfig(androidConfig)
                    .putData(KEY_TARGET_USER_ID, String.valueOf(senderId)) // Переводим Long в String, Firebase ест только текст!
                    .putData(KEY_TARGET_USER_NAME, senderName)
                    .build();

            // 🚀 ДЕЛАЕМ ТОТ САМЫЙ СЕКРЕТНЫЙ ВЫСТРЕЛ В СЕРВЕРА GOOGLE!
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("🟩 ПУШ УСПЕШНО ОТПРАВЛЕН В GOOGLE! Ответ сервера: " + response);

        } catch (Exception e) {
            System.err.println("🟥 ОШИБКА ОТПРАВКИ ПУША ЧЕРЕЗ FIREBASE: " + e.getMessage());
        }
    }
}

