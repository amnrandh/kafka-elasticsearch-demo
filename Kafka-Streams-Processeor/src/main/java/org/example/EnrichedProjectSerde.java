package org.example;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class EnrichedProjectSerde implements Serde<EnrichedProject> {
    private final Serializer<EnrichedProject> serializer = new EnrichedProjectSerializer();
    private final Deserializer<EnrichedProject> deserializer = new EnrichedProjectDeserializer();

    @Override
    public Serializer<EnrichedProject> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<EnrichedProject> deserializer() {
        return deserializer;
    }
}