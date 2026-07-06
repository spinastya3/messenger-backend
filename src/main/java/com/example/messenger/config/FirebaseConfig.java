package com.example.messenger.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initializeFirebase() {
        try {
            // 📖 Красиво читаем наш секретный файл из папки resources
            InputStream serviceAccount = new ClassPathResource("firebase-service-account.json").getInputStream();

            // Упаковываем доступы для Гугла
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            // 🔥 Проверяем, если Firebase еще не запущен — запускаем его генеральный шлюз!
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("🟩 GOOGLE FIREBASE ADMIN SDK УСПЕШНО ИНИЦИАЛИЗИРОВАН В ОБЛАКЕ!");
            }
        } catch (IOException e) {
            System.err.println("🟥 ОШИБКА ИНИЦИАЛИЗИЦИИ FIREBASE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
