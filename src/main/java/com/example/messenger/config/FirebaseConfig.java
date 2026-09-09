package com.example.messenger.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initializeFirebase() {
        try {
            // 🚀 Читаем зашифрованную Base64-строку из переменных окружения Amvera
            String base64Credentials = System.getenv("FIREBASE_CREDENTIALS_BASE64");

            if (base64Credentials == null || base64Credentials.trim().isEmpty()) {
                throw new IllegalStateException("Переменная окружения FIREBASE_CREDENTIALS_BASE64 не задана!");
            }

            // 🔓 Декодируем её обратно в нормальный JSON-массив байт прямо в памяти сервера
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials.trim());
            InputStream serviceAccount = new ByteArrayInputStream(decodedBytes);

            // Упаковываем доступы для Гугла (код без изменений)
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            // 🔥 Проверяем, если Firebase еще не запущен — запускаем его генеральный шлюз!
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("🟩 GOOGLE FIREBASE ADMIN SDK УСПЕШНО ИНИЦИАЛИЗИРОВАН ИЗ BASE64 ПЕРЕМЕННОЙ!");
            }
        } catch (Exception e) { // Меняем на общий Exception, чтобы ловить и ошибки декодирования
            System.err.println("🟥 ОШИБКА ИНИЦИАЛИЗИЦИИ FIREBASE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

//@Configuration
//public class FirebaseConfig {
//
//    @PostConstruct
//    public void initializeFirebase() {
//        try {
//            // 📖 Красиво читаем наш секретный файл из папки resources
//            InputStream serviceAccount = new ClassPathResource("firebase-service-account.json").getInputStream();
//
//            // Упаковываем доступы для Гугла
//            FirebaseOptions options = FirebaseOptions.builder()
//                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
//                    .build();
//
//            // 🔥 Проверяем, если Firebase еще не запущен — запускаем его генеральный шлюз!
//            if (FirebaseApp.getApps().isEmpty()) {
//                FirebaseApp.initializeApp(options);
//                System.out.println("🟩 GOOGLE FIREBASE ADMIN SDK УСПЕШНО ИНИЦИАЛИЗИРОВАН В ОБЛАКЕ!");
//            }
//        } catch (IOException e) {
//            System.err.println("🟥 ОШИБКА ИНИЦИАЛИЗИЦИИ FIREBASE: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//}
