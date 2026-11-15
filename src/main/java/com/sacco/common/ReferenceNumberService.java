package com.sacco.common;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ReferenceNumberService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    public String next(String prefix) {
        int suffix = RANDOM.nextInt(900000) + 100000;
        return prefix + "-" + LocalDateTime.now().format(DATE_FORMAT) + "-" + suffix;
    }
}
