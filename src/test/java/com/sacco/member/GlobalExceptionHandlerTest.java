package com.sacco.member;

import com.sacco.communication.entity.SmsMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that client mistakes are reported as 4xx responses rather than
 * falling through to the generic 500 handler.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void malformedJsonBodyReturnsBadRequestWithoutJacksonDetails() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("Unexpected character", (HttpInputMessage) null);

        var response = handler.handleUnreadableBody(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("Malformed or unsupported request body.");
    }

    @Test
    void invalidQueryEnumReturnsBadRequest() {
        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException(
                        "BOGUS", SmsMessage.SmsStatus.class, "status", null,
                        new IllegalArgumentException("bad enum"));

        var response = handler.handleTypeMismatch(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("'status'");
    }

    @Test
    void missingParameterReturnsBadRequest() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("recipient", "String");

        var response = handler.handleMissingParameter(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("'recipient'");
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("PUT");

        var response = handler.handleMethodNotSupported(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() {
        HttpMediaTypeNotSupportedException exception =
                new HttpMediaTypeNotSupportedException("text/plain");

        var response = handler.handleMediaTypeNotSupported(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("application/json");
    }

    @Test
    void unknownPathReturnsNotFound() {
        NoResourceFoundException exception =
                new NoResourceFoundException(HttpMethod.GET, "api/does-not-exist");

        var response = handler.handleNoResource(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Resource not found.");
    }
}
