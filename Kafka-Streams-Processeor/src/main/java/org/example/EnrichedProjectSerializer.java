package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serializer;

public class EnrichedProjectSerializer implements Serializer<EnrichedProject> {
    @Override
    public byte[] serialize(String topic, EnrichedProject data) {
        if (data == null) return null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            System.out.println("❌ Error serializing EnrichedProject: " + e.getMessage());
            return null;
        }
    }
}