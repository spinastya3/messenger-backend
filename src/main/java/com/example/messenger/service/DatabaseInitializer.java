package com.example.messenger.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired(required = false)
    private DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        // Если база данных не подключена (как в тестах контроллеров), просто выходим
        if (dataSource == null) {
            return;
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // 🚀 Сносим старое ограничение начисто!
            statement.execute("ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_status_check;");

            System.out.println("🚀 [DATABASE] Старый check constraint успешно удален!");
        } catch (Exception e) {
            System.out.println("ℹ️ [DATABASE] Не удалось удалить ограничение: " + e.getMessage());
        }
    }
}
