package com.sacco.communication;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.sacco.communication.exception.SmsValidationException;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Normalizes every phone number to E.164 before it reaches the provider or the
 * database. This prevents duplicate members, failed sends and inconsistent
 * delivery-report matching.
 */
@Service
public class PhoneNumberService {

    private static final Pattern INTERNATIONAL_DIGITS = Pattern.compile("\\d{11,15}");

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    private final AfricasTalkingProperties properties;

    public PhoneNumberService(AfricasTalkingProperties properties) {
        this.properties = properties;
    }

    /**
     * Accepts local (0770123456), international without plus (256770123456)
     * and E.164 (+256770123456) input and returns E.164.
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SmsValidationException("A phone number is required.");
        }

        String cleaned = raw.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");

        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2);
        }

        PhoneNumber parsed = parse(cleaned);
        if (!phoneNumberUtil.isValidNumber(parsed)) {
            throw new SmsValidationException(
                    "Invalid phone number. Use E.164 format, for example +256700123456.");
        }
        return phoneNumberUtil.format(parsed, PhoneNumberFormat.E164);
    }

    private PhoneNumber parse(String cleaned) {
        try {
            // A bare 256770123456 is ambiguous for national parsing; try it as
            // an international number first, then fall back to the default region.
            if (!cleaned.startsWith("+") && INTERNATIONAL_DIGITS.matcher(cleaned).matches()) {
                try {
                    return phoneNumberUtil.parse("+" + cleaned, "ZZ");
                } catch (NumberParseException ignored) {
                    // Fall through to national parsing below.
                }
            }
            return phoneNumberUtil.parse(cleaned, properties.getDefaultCountryCode());
        } catch (NumberParseException exception) {
            throw new SmsValidationException(
                    "Invalid phone number. Use E.164 format, for example +256700123456.");
        }
    }

    /** Masks a number for logs and API responses: +2567****456. */
    public String mask(String e164) {
        if (e164 == null || e164.length() < 7) {
            return "***";
        }
        return e164.substring(0, 5) + "****" + e164.substring(e164.length() - 3);
    }
}
