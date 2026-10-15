package kz.jazz.lms.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Автоконфигурация: сервису достаточно добавить зависимость на common:security,
 * и фильтр подключится сам (см. META-INF/spring/...AutoConfiguration.imports).
 * Секреты берутся из application.yml: jwt.secret — тот же, что у gateway и user-service,
 * jwt.gateway-token — метка «запрос пришёл через gateway».
 */
@AutoConfiguration
public class ServiceSecurityAutoConfiguration {

    @Bean
    public FilterRegistrationBean<ServiceJwtFilter> serviceJwtFilter(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.gateway-token}") String gatewayToken) {
        FilterRegistrationBean<ServiceJwtFilter> bean = new FilterRegistrationBean<>(new ServiceJwtFilter(secret, gatewayToken));
        bean.setOrder(1);          // раньше всех остальных фильтров
        bean.addUrlPatterns("/*");
        return bean;
    }
}
