/*
 * common:security — проверка JWT внутри каждого сервиса.
 * Раньше сервисы просто доверяли заголовкам X-User-Id / X-User-Role от gateway, и при прямом
 * обращении к сервису (Swagger на :8081..:8085, Postman) их можно было подставить руками.
 * Теперь каждый сервис сам проверяет подпись токена тем же секретом, что и gateway.
 */
plugins {
    `java-library`
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports { mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES) }
}

dependencies {
    // Spring-классы нужны только на компиляции: версию даёт сервис, который подключает модуль
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    api("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
}
