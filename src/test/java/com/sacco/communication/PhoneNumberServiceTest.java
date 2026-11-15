package com.sacco.communication;

import com.sacco.communication.exception.SmsValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberServiceTest {

    private PhoneNumberService phoneNumbers;

    @BeforeEach
    void setUp() {
        AfricasTalkingProperties properties = new AfricasTalkingProperties();
        properties.setDefaultCountryCode("UG");
        phoneNumbers = new PhoneNumberService(properties);
    }

    @Test
    void normalizesLocalUgandanNumberToE164() {
        assertThat(phoneNumbers.normalize("0772 123 456"))
                .isEqualTo("+256772123456");
    }

    @Test
    void normalizesInternationalNumberWithoutPlus() {
        assertThat(phoneNumbers.normalize("256772123456"))
                .isEqualTo("+256772123456");
    }

    @Test
    void acceptsAlreadyNormalizedE164() {
        assertThat(phoneNumbers.normalize("+256772123456"))
                .isEqualTo("+256772123456");
    }

    @Test
    void rejectsInvalidNumber() {
        assertThatThrownBy(() -> phoneNumbers.normalize("12345"))
                .isInstanceOf(SmsValidationException.class)
                .hasMessageContaining("Invalid phone number");
    }

    @Test
    void rejectsBlankNumber() {
        assertThatThrownBy(() -> phoneNumbers.normalize("  "))
                .isInstanceOf(SmsValidationException.class)
                .hasMessageContaining("required");
    }

    @Test
    void masksNumbersForLogs() {
        assertThat(phoneNumbers.mask("+256772123456"))
                .isEqualTo("+2567****456");
        assertThat(phoneNumbers.mask(null)).isEqualTo("***");
    }
}
