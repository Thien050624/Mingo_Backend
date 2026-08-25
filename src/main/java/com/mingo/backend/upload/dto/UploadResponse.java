package com.mingo.backend.upload.dto;

public record UploadResponse(String url, String name, String contentType, long size) {
}
