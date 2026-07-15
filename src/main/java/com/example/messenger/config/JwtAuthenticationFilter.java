package com.example.messenger.config;

import com.example.messenger.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // Отрезаем слово "Bearer " (7 символов)

            try {
                // Извлекаем имя пользователя из токена с помощью твоей утилиты JwtUtil!
                String username = jwtUtil.extractUsername(token);

                // Если имя на месте, а в текущем потоке Спринга авторизация еще не выставлена
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // Собираем легальный системный токен Спринг-Секьюрити с ролью ROLE_USER
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            username, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 🔥 ГЛАВНЫЙ КЛЮЧ: Укладываем авторизацию в системный контекст!
                    // Теперь Спринг официально признаёт этот запрос легальным!
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Если токен протух или кривой — просто логируем, вышибала разберётся сам
                System.err.println("❌ Ошибка валидации токена в фильтре: " + e.getMessage());
            }
        }

        // Пропускаем запрос дальше по цепочке фильтров
        filterChain.doFilter(request, response);
    }
}
