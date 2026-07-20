package com.example.messenger.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.springframework.http.HttpStatus.*;

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

            String prefix = uniqueFileName.toLowerCase().endsWith(".mp4") ? "/uploads/videos/" : "/uploads/photos/";
            String fileDownloadUrl = prefix + uniqueFileName;

            // Возвращаем JSON (imageUrl подхватит и полная ссылка на мобилке)
            return ResponseEntity.ok(Map.of("imageUrl", fileDownloadUrl));

        } catch (IOException e) {
            return ResponseEntity
                    .status(INTERNAL_SERVER_ERROR)
                    .body("Ошибка сохранения файла на диск: " + e.getMessage());
        }
    }

    @GetMapping("/uploads/photos/{filename:.+}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String filename) throws IOException {
        Path filePath = Paths.get(UPLOAD_DIR).resolve(filename);
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = java.nio.file.Files.probeContentType(filePath);
        if (contentType == null) contentType = "image/jpeg";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
    // 📡 МУЛЬТИМЕДИЙНЫЙ ШЛЮЗ РАЗДАЧИ: Теперь ExoPlayer сможет читать видео кусочками (Range-запросы)!
    @GetMapping("/uploads/videos/{filename:.+}")
    public ResponseEntity<ResourceRegion> getVideo(
            @PathVariable String filename,
            @RequestHeader HttpHeaders headers) throws java.io.IOException {

        Path filePath = Paths.get(UPLOAD_DIR).resolve(filename);
        Resource video = new UrlResource(filePath.toUri());

        if (!video.exists()) {
            return ResponseEntity.notFound().build();
        }

        long contentLength = video.contentLength();
        long chunkSize = Math.min(1024 * 1024L, contentLength); // Стримим шагами по 1 МБ
        ResourceRegion region;

        List<HttpRange> ranges = headers.getRange();
        HttpRange range = ranges.isEmpty() ? null : ranges.get(0);

        if (range != null) {
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(chunkSize, end - start + 1);
            region = new ResourceRegion(video, start, rangeLength);
        } else {
            region = new ResourceRegion(video, 0, Math.min(chunkSize, contentLength));
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "video/mp4";

        return ResponseEntity.status(PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .body(region);
    }

    @PostMapping("/upload-chunk")
    public ResponseEntity<?> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("fileName") String fileName,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("isLast") boolean isLast) {

        try {
            // Путь к вечной папке на диске Амверы
            String uploadDir = "/data/uploads/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            // Временный файл для конкретного кусочка (например, video_123.mp4.part0)
            File chunkFile = new File(uploadDir + fileName + ".part" + chunkIndex);
            chunk.transferTo(chunkFile);

            // Если это последний кусок — запускаем конвейер склейки!
            if (isLast) {
                File finalFile = new File(uploadDir + fileName);
                try (FileOutputStream fos = new FileOutputStream(finalFile, true)) {
                    // Последовательно собираем все кусочки по их номерам
                    for (int i = 0; i <= chunkIndex; i++) {
                        File currentChunk = new File(uploadDir + fileName + ".part" + i);
                        if (currentChunk.exists()) {
                            java.nio.file.Files.copy(currentChunk.toPath(), fos);
                            currentChunk.delete(); // Сразу удаляем мусорный кусочек с диска!
                        }
                    }
                }

                // Возвращаем привычный для Андроида формат ссылки
                String fileDownloadUrl = "/uploads/" + fileName;
                return ResponseEntity.ok(Map.of("imageUrl", fileDownloadUrl));
            }

            // Если это промежуточный кусок - просто говорим Андроиду "Жду следующий!"
            return ResponseEntity.ok(Map.of("status", "chunk_saved", "index", chunkIndex));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Ошибка склейки чанка: " + e.getMessage());
        }
    }
}

