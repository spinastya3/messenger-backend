package com.example.messenger;

import com.example.messenger.crypto.CryptoHandshakeUtil;
import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.service.AuthService;
import com.example.messenger.service.EmailService;
import com.example.messenger.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private  EmailService emailService;

    @Mock
    private CryptoHandshakeUtil cryptoHandshakeUtil;

    @InjectMocks
    private AuthService authService;

    private static final String MOCK_FCM_TOKEN = "mock_firebase_token_2026_secure";


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void register_Success() {
        String username = "Гарри";
        String rawPassword = "test";
        String email = "garry@potter.com";
        String encodedPassword = "secret_string";

        String mockClientPublicKey = "mock_base64_client_public_key";

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        Map<String, String> response = authService.register(username, rawPassword, email, MOCK_FCM_TOKEN, mockClientPublicKey);        assertNotNull(response);
        assertEquals("Поздравляю! Вы в ElisMessenger!", response.get("message"));
        verify(userRepository, times(1)).save(any(User.class));
        verify(cryptoHandshakeUtil, never()).processHandshake(anyString(), any(), any(Map.class));    }

    @Test
    void register_ThrowsException_WhenEmailIsInvalid() {
        String emailWithoutAt = "garrypotter.com";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                // 🔥 Добавили null в самом конце, чтобы совпала сигнатура метода
                authService.register("Гарри", "test", emailWithoutAt, MOCK_FCM_TOKEN, null)
        );
        assertEquals("Введите корректный Email!", exception.getMessage());
    }

    @Test
    void register_ThrowsException_WhenUsernameExists() {
        String username = "exist_user";
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(new User()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                // 🔥 Добавили null в самом конце, чтобы совпала новая сигнатура метода
                authService.register(username, "test", "test@mail.com", MOCK_FCM_TOKEN, null)
        );
        assertEquals("Пользователь с таким логином уже существует!", exception.getMessage());
    }

    @Test
    void login_Success() {
        String username = "Рон";
        String rawPassword = "correct_password";
        User mockUser = User.builder().id(7L).username(username).password("hash").build();

        // 🔥 Мокаем публичный ключ от мобилки
        String mockClientPublicKey = "mock_base64_client_public_key";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(rawPassword, "hash")).thenReturn(true);
        when(jwtUtil.generateToken(username, 7L)).thenReturn("mocked_jwt_token_string");

        // 🔥 ДОБАВИЛИ mockClientPublicKey четвертым аргументом
        Map<String, String> response = authService.login(username, rawPassword, MOCK_FCM_TOKEN, mockClientPublicKey);

        assertEquals("mocked_jwt_token_string", response.get("token"));
        assertEquals("7", response.get("userId"));
        assertEquals("С возвращением в ElisMessenger!", response.get("message"));

        // 🔥 ПРОВЕРЯЕМ, ЧТО УТИЛИТА РУКОПОЖАТИЯ БЫЛА ВЫЗВАНА
        verify(cryptoHandshakeUtil, times(1)).processHandshake(eq(username), eq(mockClientPublicKey), any(Map.class));
    }

    @Test
    void login_ThrowsException_WhenPasswordIsWrong() {
        String username = "Рон";
        User mockUser = User.builder().username(username).password("correct_hash").build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrong_password", "correct_hash")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                // 🔥 Добавили null в конце
                authService.login(username, "wrong_password", MOCK_FCM_TOKEN, null)
        );
        assertEquals("Неверный пароль!", exception.getMessage());
    }

    @Test
    void register_ThrowsException_WhenFieldsAreBlank() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                // 🔥 Добавили null в конце
                authService.register("   ", "test", "test@mail.com", MOCK_FCM_TOKEN, null)
        );
        assertEquals("Введите логин!", exception.getMessage());
    }

    @Test
    void register_ThrowsException_WhenEmailAlreadyExists() {
        String email = "duplicate@mail.com";
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByEmail(email)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                // 🔥 Добавили null в конце
                authService.register("new_user", "test", email, MOCK_FCM_TOKEN, null)
        );
        assertEquals("Пользователь с такой почтой уже существует!", exception.getMessage());
    }

    @Test
    void requestPasswordReset_Success() {
        String email = "mama@mail.com";
        User mockUser = User.builder().username("mama").email(email).build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));

        Map<String, String> response = authService.requestPasswordReset(email);

        assertNotNull(response);
        assertEquals("Код восстановления отправлен на вашу почту!", response.get("message"));
        assertNotNull(response.get("debugCode"));
    }

    @Test
    void resetPassword_ThrowsException_WhenCodeIsInvalid() {
        String email = "test@mail.com";
        String wrongCode = "000000";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                authService.resetPassword(email, wrongCode, "new_password123")
        );
        assertEquals("Неверный или просроченный код восстановления!", exception.getMessage());
    }
}
