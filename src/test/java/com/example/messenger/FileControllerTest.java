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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
public class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String UPLOAD_DIR = "/data/uploads/";
    private static final String TEST_FILE_NAME = "mock_video.mp4";

    @Test
    public void shouldReturnPartialContentForVideoRangeRequest() throws Exception {
        // 🚀 ГОТОВИМ ОКРУЖЕНИЕ ПРЯМО ЗДЕСЬ:
        // Локально создаём папку и закидываем фейковые 100 байт видео ровно ОДИН РАЗ
        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        Path testFilePath = Paths.get(UPLOAD_DIR + TEST_FILE_NAME);
        if (!Files.exists(testFilePath)) {
            Files.write(testFilePath, new byte[100]);
        }

        // Имитируем запрос ExoPlayer: "Дай мне байты с 0 по 10"
        mockMvc.perform(get("/api/files/uploads/" + TEST_FILE_NAME)
                        .header(HttpHeaders.RANGE, "bytes=0-10"))

                // Проверяем каноничный статус 206 Partial Content
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().exists(HttpHeaders.CONTENT_RANGE));

        Files.deleteIfExists(testFilePath); // Намертво стираем фейковое видео

        File dir = new File(UPLOAD_DIR);
        if (dir.exists() && dir.list() != null && dir.list().length == 0) {
            dir.delete(); // Удаляем саму папку, если в ней больше ничего нет!
        }
    }
}
