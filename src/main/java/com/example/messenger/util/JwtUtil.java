package com.example.messenger.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    private static final String SECRET_KEY = "ElisMessenger_Ultra_Secret_Crypto_Key_2026_Secure_Token";

    // Срок действия токена 30 дней
    private static final long EXPIRATION_TIME = 1024L * 1024 * 1024 * 1000;

    // Генерируем токен:
    public String generateToken(String username, Long userId) {
        return JWT.create()
                .withSubject(username)
                .withClaim("userId", userId)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(Algorithm.HMAC256(SECRET_KEY));
    }

    public String extractUsername(String token) {
        DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(SECRET_KEY))
                .build()
                .verify(token);

        return decodedJWT.getSubject();
    }
}
