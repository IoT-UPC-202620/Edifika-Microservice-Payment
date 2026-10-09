package com.edifika.payment.payment.infrastructure.authorization.sfs.model;

/**
 * Roles reconocidos por el microservicio Payment. Deben coincidir con los que emite
 * el microservicio IAM en el claim {@code roles} del JWT (con o sin prefijo "ROLE_").
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String DEFAULT = "USER";

    private static final String AUTHORITY_PREFIX = "ROLE_";

    private Roles() {
    }

    /**
     * Normaliza un rol al formato de authority de Spring Security (ej. "admin" → "ROLE_ADMIN").
     */
    public static String toAuthority(String role) {
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith(AUTHORITY_PREFIX) ? normalized : AUTHORITY_PREFIX + normalized;
    }
}
