package org.example;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.ArrayList;

public class SimpleCustomerStreamProcessor {

    public static void main(String[] args) throws InterruptedException {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "cdc-stream-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9094");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put("log.level", "ERROR");
        // 1. UNCOMMENT EXCEPTION HANDLER
        props.put(
            StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
            LogAndContinueExceptionHandler.class
        );

        StreamsBuilder builder = new StreamsBuilder();

        // 2. ADD DEBUG PEEK TO SEE RAW RECORDS
        // Create a GlobalKTable for Projects (We don't need to create a KStream for Projects)
        GlobalKTable<Integer, Project> projectTable = builder.globalTable(
                "dbserver1.public.projects", // Kafka topic for Projects
                Consumed.with(Serdes.Integer(), new JsonSerde<>(Project.class))  // Use JsonSerde directly for the Project class
        );

        KStream<String, CdcEnvelope<TimeEntry>> cdcStream = builder
            .stream("dbserver1.public.time_entries",
                   Consumed.with(Serdes.String(), new CdcSerde<>(TimeEntry.class)));
            //.peek((k, v)  -> System.out.println("RAW CDC: " + v));
        // 3. FIX HOURS_WORKED HANDLING (STRING TO DOUBLE)
        KStream<String, Double> hourDeltas = cdcStream.flatMap(
                (key, envelope) -> {
                    List<KeyValue<String, Double>> deltas = new ArrayList<>();
                    TimeEntry before = envelope.getBefore();
                    TimeEntry after = envelope.getAfter();

                    // Skip tombstones and null records
                    if (after == null && before == null) return deltas;

                    // EXCLUSIVE handling of snapshot records
                    if ("r".equals(envelope.getOp())) {
                        double hours = after.getHoursWorked();
                        System.out.println("[SNAPSHOT] Project: " + after.getProjectId() + " Hours: " + hours);
                        deltas.add(new KeyValue<>(String.valueOf(after.getProjectId()), hours));
                        return deltas; // CRITICAL: Return early to skip other processing
                    }

                    // Normal CDC operation processing (only for non-snapshot records)
                    if (before != null && after != null) {
                        // Update operation
                        double difference = after.getHoursWorked() - before.getHoursWorked();
                        System.out.println("[UPDATE] Project: " + after.getProjectId() + " Delta: " + difference);
                        deltas.add(new KeyValue<>(String.valueOf(after.getProjectId()), difference));
                    }
                    else if (after != null) {
                        // Insert operation
                        System.out.println("[INSERT] Project: " + after.getProjectId() + " Hours: " + after.getHoursWorked());
                        deltas.add(new KeyValue<>(String.valueOf(after.getProjectId()), after.getHoursWorked()));
                    }
                    else if (before != null) {
                        // Delete operation
                        System.out.println("[DELETE] Project: " + before.getProjectId() + " Hours: -" + before.getHoursWorked());
                        deltas.add(new KeyValue<>(String.valueOf(before.getProjectId()), -before.getHoursWorked()));
                    }

                    return deltas;
                }
        );

        hourDeltas
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
                .aggregate(
                        () -> 0.0,
                        (projectId, delta, total) -> {
                            //System.out.printf("Updating %s with %.2f%n", projectId, delta);
                            return total + delta;
                        },
                        Materialized.<String, Double, KeyValueStore<Bytes, byte[]>>as("project-hours-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Double())
                )
                .toStream()
                .peek((k, v) -> System.out.println("Aggregated: " + k + " => " + v));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);

        // 6. PROPER SHUTDOWN HOOK
        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Exception e) {
            System.exit(1);
        }
        System.exit(0);
    }
}