package io.praporets.controlplane.config;

import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;

public class Jackson3JsonFormatMapper extends AbstractJsonFormatMapper {
    private final JsonMapper mapper;

    public Jackson3JsonFormatMapper(JsonMapper jsonMapper) {
        this.mapper = jsonMapper;
    }

    @Override
    protected <T> T fromString(CharSequence charSequence, Type type) {
        return mapper.readValue(charSequence.toString(), mapper.constructType(type));
    }

    @Override
    protected <T> String toString(T value, Type type) {
        return mapper.writerFor(mapper.constructType(type)).writeValueAsString(value);
    }
}
