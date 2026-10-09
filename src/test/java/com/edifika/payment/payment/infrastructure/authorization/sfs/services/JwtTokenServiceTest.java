package com.edifika.payment.payment.infrastructure.authorization.sfs.services;

import com.edifika.payment.payment.infrastructure.authorization.sfs.model.AuthenticatedUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de {@link JwtTokenService}: lectura de identidad y roles desde los claims del JWT de IAM.
 */
class JwtTokenServiceTest {

    private static final String SECRET = "edifika-secret-key-must-be-at-least-32-characters-long";

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET);

    @Test
    void shouldReadUserIdEmailAndRolesFromClaims() {
        String token = sign(SECRET, "vecino", Map.of("user_id", 7, "email", "vecino@edifika.com", "roles", List.of("ADMIN")));

        AuthenticatedUser user = jwtTokenService.authenticate(token).orElseThrow();

        assertEquals(7L, user.userId());
        assertEquals("vecino@edifika.com", user.email());
        assertTrue(user.isAdmin());
    }

    @Test
    void shouldUseNumericSubjectAsUserIdAndDefaultRole() {
        String token = sign(SECRET, "15", Map.of());

        AuthenticatedUser user = jwtTokenService.authenticate(token).orElseThrow();

        assertEquals(15L, user.userId());
        assertFalse(user.isAdmin());
        assertTrue(user.authorities().contains("ROLE_USER"));
    }

    @Test
    void shouldRejectTokenSignedWithAnotherSecret() {
        String token = sign("another-secret-key-that-is-at-least-32-characters", "vecino", Map.of());

        Optional<AuthenticatedUser> user = jwtTokenService.authenticate(token);

        assertTrue(user.isEmpty());
    }

    @Test
    void shouldOnlyAllowOwnerOrAdminToAccessData() {
        AuthenticatedUser resident = jwtTokenService.authenticate(sign(SECRET, "3", Map.of())).orElseThrow();

        assertTrue(resident.canAccessDataOf(3L));
        assertFalse(resident.canAccessDataOf(4L));
    }

    private static String sign(String secret, String subject, Map<String, ?> claims) {
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
