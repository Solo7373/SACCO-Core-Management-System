package com.sacco.communication.exception;

import lombok.Getter;

/**
 * A failed Meta Graph API call. The retryable flag tells the outbox whether to
 * back off and retry or to fail the row for staff review.
 */
@Getter
public class WhatsAppException extends RuntimeException {

    private final boolean retryable;
    private final Integer code;
    private final Integer subcode;
    private final String title;
    private final String fbtraceId;

    public WhatsAppException(String message, boolean retryable) {
        this(message, retryable, null, null, null, null, null);
    }

    public WhatsAppException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, null, null, null, null, cause);
    }

    public WhatsAppException(String message,
                             boolean retryable,
                             Integer code,
                             Integer subcode,
                             String title,
                             String fbtraceId,
                             Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.code = code;
        this.subcode = subcode;
        this.title = title;
        this.fbtraceId = fbtraceId;
    }
}
