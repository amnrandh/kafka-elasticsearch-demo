package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;

public class ProjectSerde implements Serde<Project> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Serializer<Project> serializer() {
        return (topic, data) -> {
            try {
                return mapper.writeValueAsBytes(data);
            } catch (JsonProcessingException e) {
                throw new SerializationException(e);
            }
        };
    }

    @Override
    public Deserializer<Project> deserializer() {
        return (topic, data) -> {
            try {
                return mapper.readValue(data, Project.class);
            } catch (IOException e) {
                throw new SerializationException(e);
            }
        };
    }
}
