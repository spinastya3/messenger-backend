package com.example.messenger;

import com.example.messenger.controller.FileController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.http.HttpHeaders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
public class FileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    private static final String UPLOAD_DIR = "/data/uploads/";
    private static final String TEST_VIDEO_NAME = "mock_video.mp4";
    private static final String TEST_PHOTO_NAME = "mock_photo.jpg";

    @Test
    public void shouldReturnPartialContentForVideoRangeRequest() throws Exception {
        // Локально создаём папку и закидываем фейковое видео
        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        Path testVideoPath = Paths.get(UPLOAD_DIR + TEST_VIDEO_NAME);
        if (!Files.exists(testVideoPath)) {
            Files.write(testVideoPath, new byte[100]);
        }

        // 🟢 ИСПРАВИЛИ ПУТЬ: Стучимся на новый каноничный эндпоинт видео!
        mockMvc.perform(get("/api/files/uploads/videos/" + TEST_VIDEO_NAME)
                        .header(RANGE, "bytes=0-10"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(CONTENT_TYPE, "video/mp4"))
                .andExpect(header().exists(CONTENT_RANGE));

        // Экологичная уборка за собой
        Files.deleteIfExists(testVideoPath);
        cleanDirectoryIfEmpty();
    }

    @Test
    public void shouldReturnFullPhotoResource() throws Exception {
        // Локально создаём фейковую картинку
        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        Path testPhotoPath = Paths.get(UPLOAD_DIR + TEST_PHOTO_NAME);
        if (!Files.exists(testPhotoPath)) {
            Files.write(testPhotoPath, new byte[50]); // 50 байт фейкового фото
        }
        // Стучимся на специализированный эндпоинт раздачи фото
        mockMvc.perform(get("/api/files/uploads/photos/" + TEST_PHOTO_NAME))
                // 🟢 ЖЕЛЕЗНЫЙ ЧЕК: Картинка должна возвращать классический HTTP 200 OK!
                .andExpect(status().isOk())
                .andExpect(header().string(CONTENT_TYPE, "image/jpeg"));

        // Уборка за собой
        Files.deleteIfExists(testPhotoPath);
        cleanDirectoryIfEmpty();
    }

    private void cleanDirectoryIfEmpty() {
        File dir = new File(UPLOAD_DIR);
        if (dir.exists() && dir.list() != null && dir.list().length == 0) {
            dir.delete();
        }
    }
}
