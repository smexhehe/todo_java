package ru.todo.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenServiceTest {
    @Test
    void issuedTokenRequiresTheSamePasswordAndAnIntactSignature() {
        TokenService service = new TokenService("secret");
        String token = service.issue();
        assertTrue(service.valid(token));
        assertFalse(new TokenService("different").valid(token));
        assertFalse(service.valid(token + "x"));
        assertFalse(service.valid("bad-token"));
    }
}
