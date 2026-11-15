package com.sacco.communication.dto;

import java.time.LocalDateTime;

public record WhatsAppTemplateSyncResponse(
        int fetched,
        int created,
        int updated,
        int approved,
        LocalDateTime syncedAt) {
}
