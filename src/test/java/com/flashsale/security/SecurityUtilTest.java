package com.flashsale.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilTest {

    private final SecurityUtil securityUtil = new SecurityUtil(null);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isAdminWhenRoleAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(securityUtil.isAdmin()).isTrue();
    }

    @Test
    void isNotAdminWhenRoleUserOrAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("demo", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThat(securityUtil.isAdmin()).isFalse();

        SecurityContextHolder.clearContext();
        assertThat(securityUtil.isAdmin()).isFalse();
    }
}
