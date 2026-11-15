package com.sacco.communication.dto;

import com.sacco.communication.entity.WhatsAppMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class WhatsAppBulkSendRequest {

    @NotEmpty(message = "recipients are required")
    @Size(max = 200, message = "at most 200 recipients are allowed per request")
    private List<@NotBlank @Size(max = 20) String> recipients = new ArrayList<>();

    private WhatsAppMessage.WhatsAppMessageType type =
            WhatsAppMessage.WhatsAppMessageType.TEXT;

    @Size(max = 4096, message = "message is too long")
    private String message;

    @Size(max = 512, message = "templateName is too long")
    private String templateName;

    @Size(max = 20, message = "templateLanguage is too long")
    private String templateLanguage;

    @Size(max = 20, message = "at most 20 template parameters are allowed")
    private List<@Size(max = 1024, message = "a template parameter is too long") String>
            templateParameters = new ArrayList<>();

    @Size(max = 2048, message = "mediaUrl is too long")
    private String mediaUrl;

    @Size(max = 128, message = "mediaId is too long")
    private String mediaId;

    @Size(max = 1024, message = "caption is too long")
    private String caption;

    private boolean immediate = false;
}
