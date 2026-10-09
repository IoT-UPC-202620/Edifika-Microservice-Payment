package com.edifika.payment.payment.infrastructure.authorization.sfs.services;

import com.edifika.payment.payment.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.payment.payment.infrastructure.authorization.sfs.model.Roles;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Valida los JWT emitidos por el microservicio IAM (mismo secreto HMAC) y los
 * traduce a un {@link AuthenticatedUser}.
 * <p>
 * Claims soportados: id en {@code user_id}, {@code userId} o {@code id} (o un subject numérico);
 * correo en {@code email} (o subject/username con formato de correo);
 * roles en {@code roles} o {@code authorities}.
 */
@Slf4j
@Service
public class JwtTokenService {

    private final SecretKey signingKey;

    public JwtTokenService(@Value("${jwt.secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return el usuario autenticado, o vacío si el token es inválido o expiró.
     */
    public Optional<AuthenticatedUser> authenticate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            return Optional.of(toAuthenticatedUser(claims));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT inválido: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private AuthenticatedUser toAuthenticatedUser(Claims claims) {
        String username = firstText(claims.getSubject(), claims.get("username"));
        return new AuthenticatedUser(
                resolveUserId(claims),
                username,
                resolveEmail(claims, username),
                resolveAuthorities(claims)
        );
    }

    private Long resolveUserId(Claims claims) {
        for (String claimName : new String[]{"user_id", "userId", "id"}) {
            Long value = toLong(claims.get(claimName));
            if (value != null) return value;
        }
        return toLong(claims.getSubject());
    }

    private String resolveEmail(Claims claims, String username) {
        String email = firstText(claims.get("email"));
        if (email != null) return email;
        return (username != null && username.contains("@")) ? username : null;
    }

    private Set<String> resolveAuthorities(Claims claims) {
        Object roles = claims.get("roles") != null ? claims.get("roles") : claims.get("authorities");
        if (!(roles instanceof Collection<?> collection) || collection.isEmpty()) {
            return Set.of(Roles.toAuthority(Roles.DEFAULT));
        }
        return collection.stream()
                .map(String::valueOf)
                .filter(role -> !role.isBlank())
                .map(Roles::toAuthority)
                .collect(Collectors.toSet());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && text.matches("\\d+")) return Long.parseLong(text);
        return null;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }
}
