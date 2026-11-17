package com.coplay.user.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract of the resend-code command record kept for the future web layer.
 */
class SendCodeCommandTest {

    @Test
    @DisplayName("SendCodeCommand carries the target email")
    void carriesEmail() {
        SendCodeCommand command = new SendCodeCommand("alice@coplay.edu");

        assertEquals("alice@coplay.edu", command.email());
    }
}
