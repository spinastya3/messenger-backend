package com.example.messenger.util;

public class ChatUtils {
    public static Long generatePrivateChatId(Long userA, Long userB) {
        long min = Math.min(userA, userB);
        long max = Math.max(userA, userB);
        return min * 1_000_000_000L + max;
    }
}