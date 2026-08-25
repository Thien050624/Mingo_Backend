package com.mingo.backend.upload.storage;

import com.mingo.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Production storage: uploads go to a Cloudflare R2 bucket (S3-compatible API) instead of the
 * container's local disk, so files survive redeploys on ephemeral-filesystem hosts.
 */
public class R2UploadStorage implements UploadStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public R2UploadStorage(S3Client s3Client, String bucket, String publicBaseUrl) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Override
    public String store(byte[] content, String filename, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(filename).contentType(contentType).build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu tệp, vui lòng thử lại");
        }
        return publicBaseUrl + "/" + filename;
    }
}
