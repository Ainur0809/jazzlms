package kz.jazz.lms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Аутентификация на уровне сервиса. Выполняется для КАЖДОГО HTTP-запроса до контроллера.
 *
 *  1. Публичные пути (login, register, actuator, Swagger) пропускаем.
 *  2. Требуем заголовок "Authorization: Bearer <jwt>", проверяем подпись и срок жизни.
 *     Нет токена / подделан / истёк -> 401 Unauthorized.
 *  3. Пришёл ли запрос через gateway, узнаём по служебному заголовку X-Gateway-Token
 *     (секрет знают только gateway и сервисы). Через gateway роли уже проверены его матрицей.
 *     Напрямую в сервис (Swagger, Postman на порт сервиса) пускаем ТОЛЬКО администраторов:
 *     преподаватель и ученик получают 401 — для них есть приложение.
 *  4. X-User-Id / X-User-Role берём из токена, а не из заголовков клиента: подставить чужой id нельзя.
 */
public class ServiceJwtFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ServiceJwtFilter.class);
    private static final Set<String> ADMIN_ROLES = Set.of("SUPER_ADMIN", "ADMIN");
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/settings",
            "/actuator", "/swagger-ui", "/v3/api-docs", "/error");

    private final SecretKey key;
    private final String gatewayToken;

    public ServiceJwtFilter(String secret, String gatewayToken) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.gatewayToken = gatewayToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod()) || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            reject(response, "Missing bearer token. Log in via POST /api/auth/login and send 'Authorization: Bearer <token>'");
            return;
        }
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(auth.substring(7)).getPayload();
        } catch (JwtException e) {
            log.warn("JWT rejected on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            reject(response, "Invalid or expired token: " + e.getMessage());
            return;
        }
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);
        boolean viaGateway = gatewayToken.equals(request.getHeader("X-Gateway-Token"));

        if (!viaGateway && !ADMIN_ROLES.contains(role)) {
            log.warn("Direct service access denied for role {} on {} {}", role, request.getMethod(), request.getRequestURI());
            reject(response, "Direct access to this service is allowed for administrators only. "
                    + "Instructors and learners use the application through the API gateway (port 8080)");
            return;
        }
        log.debug("Authenticated {} ({}) via {} -> {} {}", claims.get("username"), role,
                viaGateway ? "gateway" : "direct call", request.getMethod(), request.getRequestURI());
        chain.doFilter(new IdentityRequest(request, userId, role), response);
    }

    private static void reject(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
                + detail.replace("\"", "'") + "\"}");
    }

    /** Подменяет X-User-Id / X-User-Role значениями из токена, что бы ни прислал клиент. */
    static final class IdentityRequest extends HttpServletRequestWrapper {
        private final Map<String, String> identity;

        IdentityRequest(HttpServletRequest request, String userId, String role) {
            super(request);
            this.identity = Map.of("x-user-id", userId, "x-user-role", role);
        }

        @Override
        public String getHeader(String name) {
            String v = identity.get(name.toLowerCase());
            return v != null ? v : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String v = identity.get(name.toLowerCase());
            return v != null ? Collections.enumeration(List.of(v)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add("X-User-Id");
            names.add("X-User-Role");
            return Collections.enumeration(names);
        }
    }
}
