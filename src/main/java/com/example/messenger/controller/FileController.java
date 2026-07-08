package com.example.messenger.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@RestController
@RequestMapping("/api/files")
@Tag(name = "Файловый менеджер", description = "Ручки для загрузки и хранения медиа-файлов чата на диске Амверы")
public class FileController {

    // Путь к нашей вечной папке на Амвере
    private static final String UPLOAD_DIR = "/data/uploads/";

    @Operation(
            summary = "Загрузить фотографию на сервер",
            description = "Принимает бинарный файл картинки с мобилки (Multipart), генерирует уникальное имя, сохраняет на вечный диск Амверы и возвращает внутренний URL-путь."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Файл успешно сохранен. В теле ответа прилетает JSON с ключом imageUrl"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос: передан пустой файл или неверный формат"),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера: не удалось физически записать файл на диск Амверы")
    })
    // 🥷 УЛЬТИМАТИВНЫЙ СИНТАКСИС: Сваггер нарисует интерактивную кнопку «Choose File» для выбора картинок мышкой!
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Файл пустой или не передан в запросе"));
        }

        try {
            // Создаем папку, если её ещё нет на диске
            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Генерируем уникальное имя файла, чтобы картинки не затирали друг друга
            String uniqueFileName = randomUUID().toString() + "_" + file.getOriginalFilename();
            File destFile = new File(UPLOAD_DIR + uniqueFileName);

            // 🚀 Сохраняем бинарный файл на диск Амверы!
            file.transferTo(destFile);

            // Формируем прямую ссылку, по которой мобилка сможет скачать эту фотку обратно
            String fileDownloadUrl = "/uploads/" + uniqueFileName;

            // Возвращаем мобилке JSON с готовым адресом картинки
            return ResponseEntity.ok(Map.of("imageUrl", fileDownloadUrl));

        } catch (IOException e) {
            return ResponseEntity
                    .status(INTERNAL_SERVER_ERROR)
                    .body("Ошибка сохранения файла на диск: " + e.getMessage());
        }
    }
}