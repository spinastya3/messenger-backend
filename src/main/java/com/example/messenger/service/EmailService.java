package com.example.messenger.service; // 🟢 Проверь свой бэкенд-пакет!

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // 💌 А. МЕТОД ДЛЯ ПРИВЕТСТВЕННОГО ПИСЬМА
    public void sendWelcomeEmail(String toEmail, String username) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("elismessenger@gmail.com");
            message.setTo(toEmail);
            message.setSubject("Добро пожаловать в ElisMessenger! 🎉");
            message.setText("Привет, " + username + "!\n\n" +
                    "Поздравляем с успешной регистрацией в ElisMessenger!\n\n" +
                    "Меня зовут Настя. Этот чатик создавался для безопасной и ненапряжной связи с членами моей семьи. " +
                    "Я только учусь, экспериментирую, так что обо всех проблемах и пожеланиях прошу писать в чат на имя ВашАдмин. " +
                    "Пароли шифруются, сообщения шифруются, можно пользоваться и не волноваться (если вы готовы поверить мне на слово) :)\n\n" +
                    "База данных с историей переписок периодами будет обнуляться, так как проект еще в процессе разработки. " +
                    "Прошу принять и простить!\n\n" +
                    "Ваша Настя! 🐈✨");

            mailSender.send(message);
            System.out.println("🟩 ПРИВЕТСТВЕННОЕ ПИСЬМО УСПЕШНО ОТПРАВЛЕНО НА: " + toEmail);
        } catch (Exception e) {
            System.err.println("🟥 ОШИБКА ОТПРАВКИ ПРИВЕТСТВЕННОГО ПИСЬМА: " + e.getMessage());
        }
    }

    // 🔐 Б. МЕТОД ДЛЯ КОДА ВОССТАНОВЛЕНИЯ ПАРОЛЯ
    public void sendResetCodeEmail(String toEmail, String username, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("elismessenger@gmail.com");
            message.setTo(toEmail);
            message.setSubject("Код восстановления пароля — ElisMessenger 🔑");
            message.setText("Привет, " + username + "!\n\n" +
                    "Кто-то (надеемся, что ты) запросил сброс пароля в ElisMessenger.\n" +
                    "Твой секретный код восстановления: " + code + "\n\n" +
                    "Код действует 10 минут. Если это не ты, просто проигнорируй это письмо. 😉");

            mailSender.send(message);
            System.out.println("🟩 КОД ВОССТАНОВЛЕНИЯ УСПЕШНО ОТПРАВЛЕН НА: " + toEmail);
        } catch (Exception e) {
            System.err.println("🟥 ОШИБКА ОТПРАВКИ КОДА ВОССТАНОВЛЕНИЯ: " + e.getMessage());
        }
    }
}
