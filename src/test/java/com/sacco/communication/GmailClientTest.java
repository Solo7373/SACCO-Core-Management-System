package com.sacco.communication.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.storage.drive.GoogleDriveCredentialsService;
import com.sacco.storage.google.GmailSettings;
import com.sacco.storage.google.GoogleOAuthTokenService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GmailClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> sendBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gmail/v1/users/me/profile", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {
                      "emailAddress": "sacco@gmail.com",
                      "messagesTotal": 12,
                      "threadsTotal": 7,
                      "historyId": "99"
                    }
                    """);
        });
        server.createContext("/gmail/v1/users/me/messages/send", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"id": "message-1", "threadId": "thread-1", "labelIds": ["SENT"]}
                    """);
        });
        server.createContext("/token", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, """
                    {"access_token": "oauth-access-token", "expires_in": 3600, "token_type": "Bearer"}
                    """);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mockModeSendsMimeMessageAndReturnsProviderId() throws Exception {
        GmailClient client = clientWith(new GoogleOAuthTokenService(objectMapper, null));
        GmailSettings settings = settings("MOCK", null);

        GmailSendResult result = client.send(settings,
                "member@example.com", "Loan reminder", "Hello member");

        assertThat(result.messageId()).isEqualTo("message-1");
        assertThat(result.threadId()).isEqualTo("thread-1");
        assertThat(result.to()).isEqualTo("member@example.com");
        assertThat(result.from()).isEqualTo("sacco@gmail.com");
        assertThat(authorization.get()).isEqualTo("Bearer mock-access-token");

        JsonNode payload = objectMapper.readTree(sendBody.get());
        String mime = new String(Base64.getUrlDecoder().decode(payload.path("raw").asText()),
                StandardCharsets.UTF_8);
        assertThat(mime).contains("From: \"Example SACCO\" <sacco@gmail.com>");
        assertThat(mime).contains("To: member@example.com");
        assertThat(mime).contains("Reply-To: replies@example.com");
        assertThat(mime).contains("Subject: Loan reminder");
        assertThat(mime).contains("Content-Type: text/plain; charset=UTF-8");
        // Base64 of "Hello member".
        assertThat(mime).contains("SGVsbG8gbWVtYmVy");
    }

    @Test
    void profileTestReportsTheConnectedAccount() {
        GmailClient client = clientWith(new GoogleOAuthTokenService(objectMapper, null));

        GmailConnectionResult result = client.testConnection(settings("MOCK", null));

        assertThat(result.connected()).isTrue();
        assertThat(result.emailAddress()).isEqualTo("sacco@gmail.com");
        assertThat(result.messagesTotal()).isEqualTo(12);
        assertThat(authorization.get()).isEqualTo("Bearer mock-access-token");
    }

    @Test
    void oauthModeReusesTheRefreshTokenService() throws Exception {
        Path credentials = Files.createTempFile("gmail-oauth", ".json");
        Files.writeString(credentials, """
                {
                  "client_id": "client-id",
                  "client_secret": "client-secret",
                  "refresh_token": "refresh-token"
                }
                """, StandardCharsets.UTF_8);

        GoogleDriveCredentialsService credentialsService =
                new GoogleDriveCredentialsService(objectMapper);
        GoogleOAuthTokenService tokenService =
                new GoogleOAuthTokenService(objectMapper, credentialsService);
        GmailClient client = clientWith(tokenService);

        GmailSendResult result = client.send(
                settings("OAUTH", credentials.toString()),
                "member@example.com", "OAuth test", "Sent with a refresh token.");

        assertThat(result.messageId()).isEqualTo("message-1");
        assertThat(authorization.get()).isEqualTo("Bearer oauth-access-token");
        Files.deleteIfExists(credentials);
    }

    @Test
    void serviceAccountModeCannotSendFromAMailbox() {
        GmailClient client = clientWith(new GoogleOAuthTokenService(objectMapper, null));

        assertThatThrownBy(() -> client.send(
                settings("SERVICE_ACCOUNT", null),
                "member@example.com", "Reminder", "Body"))
                .isInstanceOf(EmailNotConfiguredException.class)
                .hasMessageContaining("OAuth");
    }

    @Test
    void rejectsHeaderInjectionInTheSubject() {
        GmailClient client = clientWith(new GoogleOAuthTokenService(objectMapper, null));

        assertThatThrownBy(() -> client.send(
                settings("MOCK", null),
                "member@example.com",
                "Reminder\r\nBcc: attacker@example.com",
                "Body"))
                .isInstanceOf(EmailValidationException.class)
                .hasMessageContaining("line breaks");
    }

    @Test
    void rejectsAnInvalidRecipient() {
        GmailClient client = clientWith(new GoogleOAuthTokenService(objectMapper, null));

        assertThatThrownBy(() -> client.send(
                settings("MOCK", null),
                "not-an-email", "Reminder", "Body"))
                .isInstanceOf(EmailValidationException.class)
                .hasMessageContaining("valid email");
    }

    private GmailClient clientWith(GoogleOAuthTokenService tokenService) {
        return new GmailClient(objectMapper, tokenService);
    }

    private GmailSettings settings(String authMode, String credentialsPath) {
        return new GmailSettings(
                authMode,
                baseUrl,
                baseUrl + "/token",
                credentialsPath,
                "sacco@gmail.com",
                "Example SACCO",
                "replies@example.com",
                5000);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
