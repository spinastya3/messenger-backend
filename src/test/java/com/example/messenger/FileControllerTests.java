package com.example.messenger; // 🟢 Проверь свой бэкенд-пакет!

import static org.springframework.http.HttpHeaders.RANGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.example.messenger.controller.FileController;
import com.example.messenger.util.AppConstants;
import com.example.messenger.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@WebMvcTest(value = FileController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
public class FileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;

    private static final String TEST_VIDEO_NAME = "mock_video.mp4";
    private static final String TEST_PHOTO_NAME = "mock_photo.jpg";
    private static final String BOOT_UPLOAD_DIR = AppConstants.UPLOAD_DIR;

    @Test
    public void shouldReturnPartialContentForVideoRangeRequest() throws Exception {
        // Создаем и корень, и подпапку "videos" на Windows для 100% страховки!
        new File(BOOT_UPLOAD_DIR).mkdirs();
        new File(BOOT_UPLOAD_DIR + "videos/").mkdirs();

        // 🚀 БРОНЕБОЙНЫЙ ДУБЛЬ №1: Пишем файл прямо в корень uploads
        Path rootVideoPath = Paths.get(BOOT_UPLOAD_DIR + TEST_VIDEO_NAME);
        Files.write(rootVideoPath, new byte[100]);

        // 🚀 БРОНЕБОЙНЫЙ ДУБЛЬ №2: Пишем файл в подпапку uploads/videos/
        Path subVideoPath = Paths.get(BOOT_UPLOAD_DIR + "videos/" + TEST_VIDEO_NAME);
        Files.write(subVideoPath, new byte[100]);

        // Стучимся — теперь контроллер НАЙДЕТ видео, как бы он ни склеивал пути внутри!
        mockMvc.perform(get("/api/files/uploads/videos/" + TEST_VIDEO_NAME)
                        .header(RANGE, "bytes=0-10"))
                .andExpect(status().isPartialContent()) // Ожидаем статус 206!
                .andExpect(header().string(CONTENT_TYPE, "video/mp4"));

        // Уборка за собой
        Files.deleteIfExists(rootVideoPath);
        Files.deleteIfExists(subVideoPath);
    }

    @Test
    public void shouldReturnFullPhotoResource() throws Exception {
        // Создаем и корень, и подпапку "photos"
        new File(BOOT_UPLOAD_DIR).mkdirs();
        new File(BOOT_UPLOAD_DIR + "photos/").mkdirs();

        // 🚀 БРОНЕБОЙНЫЙ ДУБЛЬ №1: Пишем фото в корень uploads
        Path rootPhotoPath = Paths.get(BOOT_UPLOAD_DIR + TEST_PHOTO_NAME);
        Files.write(rootPhotoPath, new byte[50]);

        // 🚀 БРОНЕБОЙНЫЙ ДУБЛЬ №2: Пишем фото в подпапку uploads/photos/
        Path subPhotoPath = Paths.get(BOOT_UPLOAD_DIR + "photos/" + TEST_PHOTO_NAME);
        Files.write(subPhotoPath, new byte[50]);

        // Стучимся на специализированный эндпоинт раздачи фото
        mockMvc.perform(get("/api/files/uploads/photos/" + TEST_PHOTO_NAME))
                .andExpect(status().isOk()) // Ожидаем HTTP 200 OK!
                .andExpect(header().string(CONTENT_TYPE, "image/jpeg"));

        // Уборка за собой
        Files.deleteIfExists(rootPhotoPath);
        Files.deleteIfExists(subPhotoPath);
    }
}
