package com.mingo.backend.upload.storage;

import com.mingo.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev-mode default: writes uploads to a local directory served back via {@code /uploads/**}.
 * Fine for docker-compose, but the filesystem is ephemeral on most free-tier hosts — use
 * {@link R2UploadStorage} in production.
 */
public class LocalDiskUploadStorage implements UploadStorage {

    private final Path uploadDir;
    private final String baseUrl;

    public LocalDiskUploadStorage(String dir, String baseUrl) {
        this.uploadDir = Path.of(dir);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo thư mục lưu tệp: " + this.uploadDir, e);
        }
    }

    @Override
    public String store(byte[] content, String filename, String contentType) {
        Path target = uploadDir.resolve(filename);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu tệp, vui lòng thử lại");
        }
        return baseUrl + "/uploads/" + filename;
    }
}
