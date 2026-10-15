package kz.jazz.lms.user.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Учебный эндпоинт для демонстрации HTTP-кодов: GET /api/demo/status/{code}.
 * Вызывать с токеном через gateway (http://localhost:8080) или напрямую в Swagger user-service.
 *
 *  200 OK, 201 Created (+Location), 204 No Content (без тела), 300 Multiple Choices (+варианты),
 *  301 Moved Permanently (+Location, клиент идёт по новому адресу), 403 Forbidden, 500 Internal Server Error
 *  (настоящее исключение: его ловит GlobalExceptionHandler и пишет ERROR в лог — видно в Grafana).
 *  405 Method Not Allowed — отправьте POST вместо GET: здесь объявлен только GET.
 *  401 Unauthorized — уберите заголовок Authorization.
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {
    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    @GetMapping("/status/{code}")
    public ResponseEntity<?> status(@PathVariable int code) {
        log.info("Demo status requested: {}", code);
        return switch (code) {
            case 200 -> ResponseEntity.ok(Map.of("status", 200, "message", "OK: request succeeded, body returned"));
            case 201 -> ResponseEntity.created(URI.create("/api/demo/status/200"))
                    .body(Map.of("status", 201, "message", "Created: resource created, see Location header"));
            case 204 -> ResponseEntity.noContent().build();
            case 300 -> ResponseEntity.status(HttpStatus.MULTIPLE_CHOICES)
                    .location(URI.create("/api/demo/status/200"))
                    .body(Map.of("status", 300, "message", "Multiple Choices: pick one of the alternatives",
                            "alternatives", List.of("/api/demo/status/200", "/api/demo/status/201")));
            case 301 -> ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .location(URI.create("/api/demo/status/200")).build();
            case 403 -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", 403, "message", "Forbidden: you are authenticated, but this action is not allowed for your role"));
            case 500 -> throw new IllegalStateException("Demo failure: something went wrong inside the service");
            default -> ResponseEntity.badRequest()
                    .body(Map.of("status", 400, "message", "Bad Request: supported codes are 200, 201, 204, 300, 301, 403, 500"));
        };
    }
}
