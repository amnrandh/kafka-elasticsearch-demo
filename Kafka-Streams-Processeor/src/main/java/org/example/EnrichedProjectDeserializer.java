package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;

public class EnrichedProjectDeserializer implements Deserializer<EnrichedProject> {
    @Override
    public EnrichedProject deserialize(String topic, byte[] data) {
        if (data == null) return null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(data, EnrichedProject.class);
        } catch (IOException e) {
            System.out.println("❌ Error deserializing EnrichedProject: " + e.getMessage());
            return null;
        }
    }
}

