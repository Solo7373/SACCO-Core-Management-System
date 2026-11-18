package com.sacco.setup.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillingSetupRequest {

    @Size(max = 100)
    private String provider;

    @Size(max = 200)
    private String accountName;

    @Size(max = 100)
    private String accountNumber;

    @Size(max = 10)
    private String currency;

    @Email(message = "Billing contact email must be valid")
    @Size(max = 200)
    private String contactEmail;

    /** SACCO or MEMBER. */
    private String chargeBearer;

    @DecimalMin(value = "0.0", message = "SMS unit cost cannot be negative")
    private BigDecimal smsUnitCost;

    @DecimalMin(value = "0.0", message = "WhatsApp unit cost cannot be negative")
    private BigDecimal whatsappUnitCost;

    @DecimalMin(value = "0.0", message = "Email unit cost cannot be negative")
    private BigDecimal emailUnitCost;

    @DecimalMin(value = "0.0", message = "Voice unit cost cannot be negative")
    private BigDecimal voiceUnitCost;

    @DecimalMin(value = "0.0", message = "Low balance threshold cannot be negative")
    private BigDecimal lowBalanceThreshold;

    @DecimalMin(value = "0.0", message = "Prepaid balance cannot be negative")
    private BigDecimal prepaidBalance;
}
