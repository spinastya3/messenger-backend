package com.example.messenger.service;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@EnableScheduling
public class MediaCleanupScheduler {

    private static final String UPLOAD_DIR = "/data/uploads/";
    private static final long THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000;

    // Срабатывает каждую ночь в 3 часа ночи по серверному времени
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldMedia() {
        File baseFolder = new File(UPLOAD_DIR);
        if (!baseFolder.exists() || !baseFolder.isDirectory()) return;

        int deletedCount = 0;
        long now = System.currentTimeMillis();

        deletedCount += scanAndCleanup(baseFolder, now);

        if (deletedCount > 0) {
            System.out.println("🟩 [CLEANUP] Режим Snapchat сработал идеально! Удалено старых медиафайлов: " + deletedCount);
        }
    }

    private int scanAndCleanup(File folder, long now) {
        File[] files = folder.listFiles();
        if (files == null) return 0;

        int count = 0;

        for (File file : files) {
            if (file.isDirectory()) {
                count += scanAndCleanup(file, now);
            } else if (file.isFile()) {
                long fileTime = file.lastModified();

                if (now - fileTime > THREE_DAYS_MS) {
                    if (file.delete()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
