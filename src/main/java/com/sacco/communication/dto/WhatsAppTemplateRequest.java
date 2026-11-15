package com.sacco.communication.dto;

import com.sacco.communication.entity.WhatsAppTemplate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registers or updates the local mirror of a Meta template. In production the
 * WABA sync is authoritative; this endpoint exists for mock development and
 * for pre-registering a template immediately after Meta approves it.
 */
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppTemplateRequest {

    @NotBlank(message = "name is required")
    @Size(max = 512, message = "name is too long")
    private String name;

    @Size(max = 20, message = "language is too long")
    private String language;

    @Size(max = 64, message = "metaTemplateId is too long")
    private String metaTemplateId;

    private WhatsAppTemplate.TemplateCategory category =
            WhatsAppTemplate.TemplateCategory.UTILITY;

    private WhatsAppTemplate.TemplateStatus status =
            WhatsAppTemplate.TemplateStatus.PENDING;

    /** Raw Meta component JSON, retained for audit and diagnostics. */
    private String components;

    @Min(value = 0, message = "parameterCount cannot be negative")
    @Max(value = 20, message = "parameterCount cannot exceed 20")
    private int parameterCount = 0;
}
