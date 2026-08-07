package com.flashsale.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-secret-key-test-secret-key-0123456789abcdef".getBytes());

    private JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000L);

    @Test
    void generateAndValidateRoundTrip() {
        String token = jwtUtil.generateToken(42L, "alice", "USER");

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(42L);
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("alice");
        assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo("USER");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtUtil.generateToken(1L, "alice", "USER");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }
}
