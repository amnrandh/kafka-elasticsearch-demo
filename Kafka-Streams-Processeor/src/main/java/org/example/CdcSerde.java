package org.example;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import java.io.IOException;

public class CdcSerde<T> implements Serde<CdcEnvelope<T>> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JavaType type;

    public CdcSerde(Class<T> innerType) {
        type = mapper.getTypeFactory()
            .constructParametricType(CdcEnvelope.class, innerType);
    }

    @Override
    public Serializer<CdcEnvelope<T>> serializer() {
        return (topic, data) -> {
            try {
                return mapper.writeValueAsBytes(data);
            } catch (JsonProcessingException e) {
                throw new SerializationException(e);
            }
        };
    }

    @Override
    public Deserializer<CdcEnvelope<T>> deserializer() {
        return (topic, data) -> {
            try {
                return mapper.readValue(data, type);
            } catch (IOException e) {
                throw new SerializationException(e);
            }
        };
    }
}