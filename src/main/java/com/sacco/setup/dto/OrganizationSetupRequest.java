package com.sacco.setup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrganizationSetupRequest {

    @NotBlank(message = "Legal name is required")
    @Size(max = 200)
    private String legalName;

    @Size(max = 200)
    private String tradingName;

    @Size(max = 100)
    private String registrationNumber;

    @Size(max = 100)
    private String taxPin;

    @NotBlank(message = "Physical address is required")
    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String country;

    @NotBlank(message = "Organisation phone number is required")
    @Size(max = 50)
    private String phone;

    @NotBlank(message = "Organisation email is required")
    @Email(message = "Organisation email must be valid")
    @Size(max = 200)
    private String email;

    @Size(max = 255)
    private String website;

    @Size(max = 100)
    private String timezone;

    @Size(max = 10)
    private String currency;
}
