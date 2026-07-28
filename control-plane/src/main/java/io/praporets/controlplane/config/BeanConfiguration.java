package io.praporets.controlplane.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class BeanConfiguration {

//    @Bean
    HibernatePropertiesCustomizer jsonFormatMapper(JsonMapper jsonMapper) {
        return props ->
            props.put(AvailableSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper(jsonMapper));
    }
}
