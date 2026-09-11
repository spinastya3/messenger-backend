package com.example.messenger.crypto;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SessionKeyManager {

    /** Сколько секунд ключ живёт после разрыва сокета, если юзер не вернётся. */
    private static final int GRACE_PERIOD_SECONDS = 30;

    // Хранилище: Username -> Временный AES Ключ сессии (в Base64)
    private final Map<String, String> userKeys = new ConcurrentHashMap<>();

    // 🛡️ Планировщик отложенного удаления ключей (grace period после разрыва)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, ScheduledFuture<?>> pendingRemovals = new ConcurrentHashMap<>();

    public void saveKey(String username, String base64Key) {
        userKeys.put(username, base64Key);
        // 🛡️ Новый ключ — отменяем все запланированные удаления
        cancelScheduledRemoval(username);
    }

    public String getKey(String username) {
        return userKeys.get(username);
    }

    /**
     * Жёсткое удаление ключа. Использовать ТОЛЬКО при явном logout.
     * При разрыве сокета используйте scheduleRemoval().
     */
    public void removeKey(String username) {
        userKeys.remove(username);
        cancelScheduledRemoval(username);
    }

    /**
     * Планирует удаление ключа через GRACE_PERIOD_SECONDS.
     * Если юзер за это время переподключится и сделает refreshSession —
     * saveKey() отменит запланированное удаление, ключ останется.
     *
     * Решает проблему: при разрыве сокета (1000/1006) ключ стирался сразу,
     * клиент переподключался за 1 сек, но ключа уже не было → 500 и шифры.
     */
    public void scheduleRemoval(String username) {
        if (username == null) return;

        // Если уже запланировано — отменяем старое
        ScheduledFuture<?> existing = pendingRemovals.remove(username);
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            userKeys.remove(username);
            pendingRemovals.remove(username);
            int anonymizedId = Math.abs(username.hashCode() % 10000);
            System.out.println("🧹 [SOCKET-CRYPTO] Плановое удаление ключа для anonymizedId: " + anonymizedId);
        }, GRACE_PERIOD_SECONDS, TimeUnit.SECONDS);

        pendingRemovals.put(username, future);

        int anonymizedId = Math.abs(username.hashCode() % 10000);
        System.out.println("🟨 [SOCKET-CRYPTO] Ключ для anonymizedId " + anonymizedId
                + " будет удалён через " + GRACE_PERIOD_SECONDS + " сек (если не переподключится)");
    }

    /**
     * Отменяет запланированное удаление ключа.
     * Вызывается из saveKey() при новом handshake.
     */
    public void cancelScheduledRemoval(String username) {
        if (username == null) return;

        ScheduledFuture<?> future = pendingRemovals.remove(username);
        if (future != null) {
            future.cancel(false);
            int anonymizedId = Math.abs(username.hashCode() % 10000);
            System.out.println("🛡️ [SOCKET-CRYPTO] Удаление ключа для anonymizedId " + anonymizedId + " отменено");
        }
    }
}
