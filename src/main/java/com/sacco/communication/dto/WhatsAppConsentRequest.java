package com.sacco.communication.dto;

import com.sacco.communication.entity.WhatsAppConsent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WhatsAppConsentRequest {

    @NotBlank(message = "phone is required")
    @Size(max = 20, message = "phone is too long")
    private String phone;

    @NotNull(message = "status is required")
    private WhatsAppConsent.ConsentStatus status;

    @Size(max = 50, message = "source is too long")
    private String source;

    private Long memberId;
}
