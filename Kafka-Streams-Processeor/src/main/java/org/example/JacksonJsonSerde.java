package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class JacksonJsonSerde {

    public static class JsonSerializer<T> implements Serializer<T> {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public byte[] serialize(String topic, T data) {
            try {
                return mapper.writeValueAsBytes(data);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("JSON serialization error", e);
            }
        }
    }

    public static class JsonDeserializer<T> implements Deserializer<T> {
        private final ObjectMapper mapper = new ObjectMapper();
        private final TypeReference<T> typeRef;

        public JsonDeserializer(TypeReference<T> typeRef) {
            this.typeRef = typeRef;
        }

        @Override
        public T deserialize(String topic, byte[] data) {
            try {
                return mapper.readValue(data, typeRef);
            } catch (Exception e) {
                throw new RuntimeException("JSON deserialization error", e);
            }
        }
    }
}
