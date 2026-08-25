package com.mingo.backend.upload.storage;

public interface UploadStorage {

    /**
     * Persists {@code content} under {@code filename} and returns the publicly reachable URL
     * clients should use to fetch it.
     */
    String store(byte[] content, String filename, String contentType);
}
