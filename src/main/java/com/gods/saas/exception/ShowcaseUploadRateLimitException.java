package com.gods.saas.exception;

public class ShowcaseUploadRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public ShowcaseUploadRateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}