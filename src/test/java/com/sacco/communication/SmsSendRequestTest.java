package com.sacco.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.dto.SmsSendRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsSendRequestTest {

    @Test
    void bindsImmediateFlagFromJson() throws Exception {
        SmsSendRequest request = new ObjectMapper().readValue(
                "{\"recipient\":\"+256772123456\",\"message\":\"test\",\"immediate\":true}",
                SmsSendRequest.class);

        assertThat(request.isImmediate()).isTrue();
    }
}
