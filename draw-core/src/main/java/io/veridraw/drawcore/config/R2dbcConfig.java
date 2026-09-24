package io.veridraw.drawcore.config;

import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.ConnectionFactory;
import io.veridraw.shared.event.DomainEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory factory, ObjectMapper objectMapper) {
        return R2dbcCustomConversions.of(
                DialectResolver.getDialect(factory),
                List.of(new DomainEventToJsonConverter(objectMapper), new JsonToDomainEventConverter(objectMapper)));
    }

    @WritingConverter
    @RequiredArgsConstructor
    public static class DomainEventToJsonConverter implements Converter<DomainEvent, Json> {

        private final ObjectMapper objectMapper;

        @Override
        public Json convert(DomainEvent source) {
            try {
                String jsonString = objectMapper.writeValueAsString(source);
                return Json.of(jsonString);
            } catch (JacksonException e) {
                throw new IllegalStateException("Failed to serialize DomainEvent to JSON", e);
            }
        }
    }

    @ReadingConverter
    @RequiredArgsConstructor
    public static class JsonToDomainEventConverter implements Converter<Json, DomainEvent> {

        private final ObjectMapper objectMapper;

        @Override
        public DomainEvent convert(Json source) {
            try {
                return objectMapper.readValue(source.asString(), DomainEvent.class);
            } catch (JacksonException e) {
                throw new IllegalStateException("Failed to deserialize JSON to DomainEvent", e);
            }
        }
    }
}
