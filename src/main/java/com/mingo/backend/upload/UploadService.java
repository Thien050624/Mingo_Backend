package com.mingo.backend.upload;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.upload.dto.UploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class UploadService {

    private static final Map<String, String> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/gif", ".gif"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/webm", ".webm"),
            Map.entry("video/quicktime", ".mov"),
            Map.entry("video/ogg", ".ogv"),
            Map.entry("application/pdf", ".pdf"),
            Map.entry("application/msword", ".doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
            Map.entry("application/vnd.ms-excel", ".xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
            Map.entry("application/vnd.ms-powerpoint", ".ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"),
            Map.entry("text/plain", ".txt"),
            Map.entry("text/csv", ".csv"),
            Map.entry("application/zip", ".zip"),
            Map.entry("application/x-zip-compressed", ".zip"),
            Map.entry("application/x-rar-compressed", ".rar")
    );

    // Verifies the file's actual bytes match what its declared Content-Type claims to be —
    // the Content-Type header is client-supplied and trivially spoofable on its own.
    // Office Open XML formats (docx/xlsx/pptx) are ZIP containers, so they share the ZIP
    // signature; this still catches anything that isn't even a valid ZIP/OLE/media file.
    private static final Map<String, Predicate<byte[]>> SIGNATURE_CHECKS = Map.ofEntries(
            Map.entry("image/jpeg", bytes -> startsWith(bytes, 0xFF, 0xD8, 0xFF)),
            Map.entry("image/png", bytes -> startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
            Map.entry("image/webp", bytes -> startsWith(bytes, 'R', 'I', 'F', 'F') && matchesAt(bytes, 8, 'W', 'E', 'B', 'P')),
            Map.entry("image/gif", bytes -> startsWith(bytes, 'G', 'I', 'F', '8')),
            Map.entry("video/mp4", UploadService::isIsoBaseMediaFile),
            Map.entry("video/quicktime", UploadService::isIsoBaseMediaFile),
            Map.entry("video/webm", bytes -> startsWith(bytes, 0x1A, 0x45, 0xDF, 0xA3)),
            Map.entry("video/ogg", bytes -> startsWith(bytes, 'O', 'g', 'g', 'S')),
            Map.entry("application/pdf", bytes -> startsWith(bytes, '%', 'P', 'D', 'F')),
            Map.entry("application/msword", UploadService::isOleCompoundFile),
            Map.entry("application/vnd.ms-excel", UploadService::isOleCompoundFile),
            Map.entry("application/vnd.ms-powerpoint", UploadService::isOleCompoundFile),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", UploadService::isZip),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", UploadService::isZip),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", UploadService::isZip),
            Map.entry("application/zip", UploadService::isZip),
            Map.entry("application/x-zip-compressed", UploadService::isZip),
            Map.entry("application/x-rar-compressed", bytes -> startsWith(bytes, 'R', 'a', 'r', '!'))
    );

    private final Path uploadDir;
    private final String baseUrl;

    public UploadService(@Value("${app.upload.dir}") String dir, @Value("${app.upload.base-url}") String baseUrl) {
        this.uploadDir = Path.of(dir);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo thư mục lưu tệp: " + this.uploadDir, e);
        }
    }

    public UploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn một tệp");
        }

        String contentType = file.getContentType();
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Định dạng tệp không được hỗ trợ. Hỗ trợ ảnh, video, PDF, Word, Excel, PowerPoint, văn bản và tệp nén");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể đọc tệp, vui lòng thử lại");
        }

        if (!matchesDeclaredType(contentType, content)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Nội dung tệp không khớp với định dạng khai báo (" + contentType + ")");
        }

        String filename = UUID.randomUUID() + extension;
        Path target = uploadDir.resolve(filename);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu tệp, vui lòng thử lại");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = filename;
        }

        return new UploadResponse(baseUrl + "/uploads/" + filename, originalName, contentType, file.getSize());
    }

    private boolean matchesDeclaredType(String contentType, byte[] content) {
        Predicate<byte[]> check = SIGNATURE_CHECKS.get(contentType);
        if (check != null) {
            return check.test(content);
        }
        // text/plain and text/csv have no universal magic number; reject anything that
        // looks like binary data (a null byte in the first chunk is a strong signal).
        int probeLength = Math.min(content.length, 512);
        for (int i = 0; i < probeLength; i++) {
            if (content[i] == 0) return false;
        }
        return true;
    }

    private static boolean isZip(byte[] bytes) {
        return startsWith(bytes, 'P', 'K') && (bytes.length < 3 || bytes[2] == 0x03 || bytes[2] == 0x05 || bytes[2] == 0x07);
    }

    private static boolean isOleCompoundFile(byte[] bytes) {
        return startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
    }

    private static boolean isIsoBaseMediaFile(byte[] bytes) {
        if (bytes.length < 12) return false;
        String boxType = new String(bytes, 4, 4, StandardCharsets.US_ASCII);
        return boxType.equals("ftyp") || boxType.equals("moov") || boxType.equals("free")
                || boxType.equals("mdat") || boxType.equals("wide") || boxType.equals("skip");
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    private static boolean matchesAt(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
