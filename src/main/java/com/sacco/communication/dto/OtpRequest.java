package com.sacco.communication.dto;

import com.sacco.communication.entity.PhoneVerification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OtpRequest {

    @NotBlank(message = "phone is required")
    @Size(max = 20, message = "phone is too long")
    private String phone;

    @NotNull(message = "purpose is required")
    private PhoneVerification.OtpPurpose purpose;
}
