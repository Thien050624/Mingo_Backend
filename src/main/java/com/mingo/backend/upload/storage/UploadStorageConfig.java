package com.mingo.backend.upload.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class UploadStorageConfig {

    @Bean
    public UploadStorage uploadStorage(
            @Value("${R2_BUCKET:}") String r2Bucket,
            @Value("${R2_ENDPOINT:}") String r2Endpoint,
            @Value("${R2_ACCESS_KEY_ID:}") String r2AccessKey,
            @Value("${R2_SECRET_ACCESS_KEY:}") String r2SecretKey,
            @Value("${R2_PUBLIC_URL:}") String r2PublicUrl,
            @Value("${app.upload.dir}") String localDir,
            @Value("${app.upload.base-url}") String localBaseUrl) {
        if (r2Bucket.isBlank()) {
            return new LocalDiskUploadStorage(localDir, localBaseUrl);
        }

        S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(r2Endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(r2AccessKey, r2SecretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        return new R2UploadStorage(s3Client, r2Bucket, r2PublicUrl);
    }
}
