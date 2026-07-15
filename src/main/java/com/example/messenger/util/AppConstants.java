package com.example.messenger.util; // 🟢 Твой бэкенд-пакет!

public class AppConstants {

    private AppConstants() {} // Запрещаем создавать объекты класса

    // 📸 ФАЙЛОВЫЙ КОНВЕЙЕР: Путь к папке хранения медиафайлов в облаке Linux
    // (Локально на Windows Спринг сам преобразует его в системный темп или корень диска C:\)
    public static final String UPLOAD_DIR = "/data/uploads/";

    // 📡 МАРШРУТЫ И ЭНДПОИНТЫ REST API (Буквица в буквицу, как на мобилке!):
    public static final String ENDPOINT_HISTORY = "/api/chat/history";
    public static final String ENDPOINT_ALL_USERS = "/api/users/all";
    public static final String ENDPOINT_UPLOAD = "/api/files/upload";

    // 🔌 ТОПИКИ И НАПРАВЛЕНИЯ STOMP СОКЕТОВ ДЛЯ МГНОВЕННЫХ СООБЩЕНИЙ:
    public static final String TOPIC_MESSAGES = "/topic/messages";
    public static final String APP_SEND = "/app/chat.send";

    // 🤝 МОБИЛЬНЫЕ ИНТЕНТ-КЛЮЧИ (Твои родные — сохранили!):
    public static final String KEY_TARGET_USER_ID = "TARGET_USER_ID";
    public static final String KEY_TARGET_USER_NAME = "TARGET_USER_NAME";

    // 📝 НАЗВАНИЯ JSON-КЛЮЧЕЙ И ПАРАМЕТРОВ СЕТИ:
    public static final String PARAM_FILE = "file";
    public static final String JSON_IMAGE_URL = "imageUrl";
}
