package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.Serdes;

import java.util.Map;

public class ProjectKeySerde implements Serde<Map<String, Integer>> {

    private final Serde<Map<String, Integer>> inner;

    public ProjectKeySerde() {
        this.inner = Serdes.serdeFrom(
                new JacksonJsonSerde.JsonSerializer<>(),
                new JacksonJsonSerde.JsonDeserializer<>(new TypeReference<Map<String, Integer>>() {})
        );
    }

    @Override
    public Serializer<Map<String, Integer>> serializer() {
        return inner.serializer();
    }

    @Override
    public Deserializer<Map<String, Integer>> deserializer() {
        return inner.deserializer();
    }
}
