package com.sacco.communication.exception;

/** The OTP is missing, expired, already used or does not match. */
public class OtpVerificationException extends RuntimeException {
    public OtpVerificationException(String message) {
        super(message);
    }
}
