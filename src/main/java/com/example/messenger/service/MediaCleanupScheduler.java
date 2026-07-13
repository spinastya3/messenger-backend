package com.example.messenger.service; // 🟢 Проверь свой пакет на бэкенде!

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

@Service
@EnableScheduling
public class MediaCleanupScheduler {

    // 🚀 ТВОЙ ВЕЧНЫЙ ДИСК ИЗ FILECONTROLLER:
    private static final String UPLOAD_DIR = "/data/uploads/";
    private static final long THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000;

    // Срабатывает каждую ночь в 3 часа ночи и стирает файлы старше 3 дней
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldMedia() {
        File folder = new File(UPLOAD_DIR);
        if (!folder.exists() || !folder.isDirectory()) return;

        File[] files = folder.listFiles();
        if (files == null) return;

        long now = System.currentTimeMillis();
        int deletedCount = 0;

        for (File file : files) {
            if (file.isFile()) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                    long creationTime = attrs.creationTime().toMillis();

                    if (now - creationTime > THREE_DAYS_MS) {
                        if (file.delete()) {
                            deletedCount++;
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Ошибка чтения атрибутов: " + file.getName());
                }
            }
        }
        if (deletedCount > 0) {
            System.out.println("🟩 [CLEANUP] Snapchat-режим сработал! Удалено старых файлов: " + deletedCount);
        }
    }
}
