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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.*;
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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Файл пустой или не передан в запросе"));
        }

        try {
            // 1. Определяем подпапку в зависимости от типа файла
            String subDir = file.getOriginalFilename().toLowerCase().endsWith(".mp4") ? "videos/" : "photos/";

            // Полный путь на диске: /data/uploads/photos/ или /data/uploads/videos/
            File directory = new File(UPLOAD_DIR + subDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Генерируем уникальное имя файла
            String uniqueFileName = randomUUID().toString() + "_" + file.getOriginalFilename();
            File destFile = new File(directory, uniqueFileName);

            // 🚀 Сохраняем бинарный файл в правильную подпапку на диск Амверы!
            file.transferTo(destFile);

            // Получаем текущий домен
            String currentDomain = ServletUriComponentsBuilder
                    .fromCurrentContextPath()
                    .build()
                    .toUriString();

            if (currentDomain.startsWith("http://") && !currentDomain.contains("localhost")) {
                currentDomain = currentDomain.replace("http://", "https://");
            }

            String fullDownloadUrl = currentDomain + "/api/files/uploads/" + subDir + uniqueFileName;

            System.out.println("🟩 Файл успешно сохранен! Ссылка для мобилки: " + fullDownloadUrl);

            return ResponseEntity.ok(Map.of("imageUrl", fullDownloadUrl));

        } catch (IOException e) {
            return ResponseEntity
                    .status(INTERNAL_SERVER_ERROR)
                    .body("Ошибка сохранения файла на диск: " + e.getMessage());
        }
    }

    @GetMapping("/uploads/photos/{filename:.+}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String filename) throws IOException {
        Path filePath = Paths.get(UPLOAD_DIR, "photos").resolve(filename);
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

    @GetMapping("/uploads/videos/{filename:.+}")
    public ResponseEntity<ResourceRegion> getVideo(
            @PathVariable String filename,
            @RequestHeader HttpHeaders headers) throws java.io.IOException {

        Path filePath = Paths.get(UPLOAD_DIR, "videos").resolve(filename);
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

    private static final Object lock = new Object();

    @PostMapping("/upload-chunk")
    public ResponseEntity<?> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("fileName") String fileName,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("isLast") boolean isLast) {

        try {
            String baseUploadDir = "/data/uploads/";
            String videoUploadDir = baseUploadDir + "videos/";

            File videoDir = new File(videoUploadDir);
            if (!videoDir.exists()) videoDir.mkdirs();

            // Безопасное сохранение чанка через поток, чтобы избежать блокировок transferTo
            File chunkFile = new File(baseUploadDir + fileName + ".part" + chunkIndex);
            try (InputStream is = chunk.getInputStream();
                 FileOutputStream fos = new FileOutputStream(chunkFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            // Если это последний кусок — склеиваем строго в ОДИН поток (синхронно)
            if (isLast) {
                synchronized (lock) {
                    File finalFile = new File(videoUploadDir + fileName);

                    // Пересоздаем файл с нуля, чтобы не накладывать дубли при повторных попытках
                    if (finalFile.exists()) finalFile.delete();

                    try (FileOutputStream fos = new FileOutputStream(finalFile, true)) {
                        byte[] buffer = new byte[65536];

                        for (int i = 0; i <= chunkIndex; i++) {
                            File currentChunk = new File(baseUploadDir + fileName + ".part" + i);

                            // ⏱ Важнейшая проверка: если кусок сети задержался, ждем его пару секунд
                            int attempts = 0;
                            while (!currentChunk.exists() && attempts < 10) {
                                Thread.sleep(500); // Ждем 0.5 секунды, если файл еще пишется
                                attempts++;
                            }

                            if (!currentChunk.exists()) {
                                return ResponseEntity.status(500)
                                        .body("Ошибка: Чанк №" + i + " так и не появился на диске. Сборка отменена.");
                            }

                            try (FileInputStream fis = new FileInputStream(currentChunk)) {
                                int bytesRead;
                                while ((bytesRead = fis.read(buffer)) != -1) {
                                    fos.write(buffer, 0, bytesRead);
                                }
                            }
                            currentChunk.delete(); // Стираем временный кусок
                        }
                    }
                }

                // Формируем URL
                String currentDomain = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                        .fromCurrentContextPath().build().toUriString();

                if (currentDomain.startsWith("http://") && !currentDomain.contains("localhost")) {
                    currentDomain = currentDomain.replace("http://", "https://");
                }

                String fullAbsoluteVideoUrl = currentDomain + "/api/files/uploads/videos/" + fileName;
                System.out.println("🟩 Видео склеено! URL: " + fullAbsoluteVideoUrl);

                return ResponseEntity.ok(Map.of("imageUrl", fullAbsoluteVideoUrl));
            }

            return ResponseEntity.ok(Map.of("status", "chunk_saved", "index", chunkIndex));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Ошибка склейки: " + e.getMessage());
        }
    }


//@PostMapping("/upload-chunk")
//public ResponseEntity<?> uploadChunk(
//        @RequestParam("file") MultipartFile chunk,
//        @RequestParam("fileName") String fileName,
//        @RequestParam("chunkIndex") int chunkIndex,
//        @RequestParam("isLast") boolean isLast) {
//
//    try {
//        // Базовый путь и папка для хранения готовых видео
//        String baseUploadDir = "/data/uploads/";
//        String videoUploadDir = baseUploadDir + "videos/";
//
//        // Убеждаемся, что папка для видео существует
//        File videoDir = new File(videoUploadDir);
//        if (!videoDir.exists()) videoDir.mkdirs();
//
//        // 1. Временные кусочки сохраняем в корень (чтобы не мешать готовым файлам)
//        File chunkFile = new File(baseUploadDir + fileName + ".part" + chunkIndex);
//        chunk.transferTo(chunkFile);
//
//        // Если это последний кусок — запускаем конвейер склейки в папку videos!
//        if (isLast) {
//            // 🔥 ИСПРАВЛЕНО: Финальный файл собираем СТРОГО внутри папки videos/
//            File finalFile = new File(videoUploadDir + fileName);
//
//            try (FileOutputStream fos = new FileOutputStream(finalFile, true)) {
//                byte[] buffer = new byte[65536]; // Порциями по 64 КБ
//
//                for (int i = 0; i <= chunkIndex; i++) {
//                    File currentChunk = new File(baseUploadDir + fileName + ".part" + i);
//                    if (currentChunk.exists()) {
//                        try (FileInputStream fis = new FileInputStream(currentChunk)) {
//                            int bytesRead;
//                            while ((bytesRead = fis.read(buffer)) != -1) {
//                                fos.write(buffer, 0, bytesRead);
//                            }
//                        }
//                        currentChunk.delete(); // Сразу удаляем мусорный кусочек с диска
//                    }
//                }
//            }
//
//            // Получаем домен для абсолютной ссылки
//            String currentDomain = org.springframework.web.servlet.support.ServletUriComponentsBuilder
//                    .fromCurrentContextPath()
//                    .build()
//                    .toUriString();
//
//            if (currentDomain.startsWith("http://") && !currentDomain.contains("localhost")) {
//                currentDomain = currentDomain.replace("http://", "https://");
//            }
//
//            // Собираем полный путь, который теперь точно совпадет с физическим местом на диске
//            String fullAbsoluteVideoUrl = currentDomain + "/api/files/uploads/videos/" + fileName;
//
//            System.out.println("🟩 Видео успешно склеено в подпапку! Выдаем URL: " + fullAbsoluteVideoUrl);
//            return ResponseEntity.ok(Map.of("imageUrl", fullAbsoluteVideoUrl));
//        }
//
//        return ResponseEntity.ok(Map.of("status", "chunk_saved", "index", chunkIndex));
//
//    } catch (Exception e) {
//        e.printStackTrace();
//        return ResponseEntity.status(500).body("Ошибка склейки чанка: " + e.getMessage());
//    }
//}
}

