package com.example.messenger; // 🟢 Проверь свой бэкенд-пакет!

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.http.HttpHeaders.RANGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.example.messenger.controller.FileController;
import com.example.messenger.service.FileSecurityService;
import com.example.messenger.util.AppConstants;
import com.example.messenger.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
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

    @MockBean
    private FileSecurityService fileSecurityService;

    private static final String TEST_VIDEO_NAME = "mock_video.mp4";
    private static final String TEST_PHOTO_NAME = "mock_photo.jpg";
    private static final String BOOT_UPLOAD_DIR = AppConstants.UPLOAD_DIR;

    @Test
    public void shouldReturnPartialContentForVideoRangeRequest() throws Exception {
        // Обучаем мок-сервис: на любой запрос этого файла отвечать null (доступ РАЗРЕШЕН)
        Mockito.doNothing().when(fileSecurityService).checkFileAccess(eq(TEST_VIDEO_NAME), any());

        new File(BOOT_UPLOAD_DIR).mkdirs();
        new File(BOOT_UPLOAD_DIR + "videos/").mkdirs();

        Path rootVideoPath = Paths.get(BOOT_UPLOAD_DIR + TEST_VIDEO_NAME);
        Files.write(rootVideoPath, new byte[100]);

        Path subVideoPath = Paths.get(BOOT_UPLOAD_DIR + "videos/" + TEST_VIDEO_NAME);
        Files.write(subVideoPath, new byte[100]);

        mockMvc.perform(get("/api/files/uploads/videos/" + TEST_VIDEO_NAME)
                        .header(HttpHeaders.RANGE, "bytes=0-10")
                        .principal(() -> "testuser")) // 🚀 ПОДЛОЖИЛИ ЮЗЕРА НАПРЯМУЮ
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"));

        Files.deleteIfExists(rootVideoPath);
        Files.deleteIfExists(subVideoPath);
    }

    @Test
    public void shouldReturnFullPhotoResource() throws Exception {
        // Обучаем мок-сервис: на любой запрос этого фото отвечать null (доступ РАЗРЕШЕН)
        Mockito.doNothing().when(fileSecurityService).checkFileAccess(eq(TEST_PHOTO_NAME), any());
        new File(BOOT_UPLOAD_DIR).mkdirs();
        new File(BOOT_UPLOAD_DIR + "photos/").mkdirs();

        Path rootPhotoPath = Paths.get(BOOT_UPLOAD_DIR + TEST_PHOTO_NAME);
        Files.write(rootPhotoPath, new byte[50]);

        Path subPhotoPath = Paths.get(BOOT_UPLOAD_DIR + "photos/" + TEST_PHOTO_NAME);
        Files.write(subPhotoPath, new byte[50]);

        mockMvc.perform(get("/api/files/uploads/photos/" + TEST_PHOTO_NAME)
                        .principal(() -> "testuser")) // 🚀 ПОДЛОЖИЛИ ЮЗЕРА НАПРЯМУЮ
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));

        Files.deleteIfExists(rootPhotoPath);
        Files.deleteIfExists(subPhotoPath);
    }
}
