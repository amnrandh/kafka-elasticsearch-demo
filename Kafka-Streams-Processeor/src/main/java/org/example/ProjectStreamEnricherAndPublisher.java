package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.*;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class ProjectStreamEnricherAndPublisher {

    private static final Logger logger = LoggerFactory.getLogger(ProjectStreamEnricherAndPublisher.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String TIME_ENTRIES_TOPIC = "dbserver1.public.time_entries";
    private static final String PROJECTS_TOPIC = "dbserver1.public.projects";
    private static final String CUSTOMERS_TOPIC = "dbserver1.public.customers";

    public static void main(String[] args) throws InterruptedException {
        Properties props = createKafkaProperties();
        StreamsBuilder builder = new StreamsBuilder();

        logger.info("========== STARTING STREAMS ==========");

        KStream<String, CdcEnvelope<TimeEntry>> cdcStream = createCdcStream(builder);
        KStream<String, Double> hourDeltas = processCdcStream(cdcStream);

        KTable<String, Double> projectHoursTable = aggregateProjectHours(hourDeltas);
        KTable<String, Project> projectsTable = processProjects(builder);
        KTable<String, String> customerTable = processCustomers(builder);

        KTable<String, EnrichedProject> enrichedProjectView = joinProjectAndCustomerData(projectsTable, customerTable, projectHoursTable);

        enrichedProjectView.toStream()
                .peek((key, enriched) -> {
                    logger.info("Final Enriched Output: " + enriched);
                    System.out.println("Final Enriched Output: " + enriched);

                })
                .to("enriched-projects", Produced.with(Serdes.String(), new EnrichedProjectSerde()));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        final CountDownLatch latch = new CountDownLatch(1);
        addShutdownHook(streams, latch);

        try {
            streams.start();
            latch.await();
        } catch (Exception e) {
            logger.error("Error running the stream: ", e);
            System.exit(1);
        }
        System.exit(0);
    }


    private static Properties createKafkaProperties() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "ProjectStreamEnricherAndPublisher");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9094");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndContinueExceptionHandler.class);
        return props;
    }

    private static KStream<String, CdcEnvelope<TimeEntry>> createCdcStream(StreamsBuilder builder) {
        return builder
                .stream("dbserver1.public.time_entries", Consumed.with(Serdes.String(), new CdcSerde<>(TimeEntry.class)))
                .peek((k, v) -> logger.info("RAW CDC - Op: {}, Before: {}, After: {}", v.getOp(), v.getBefore(), v.getAfter()));
    }

    private static KStream<String, Double> processCdcStream(KStream<String, CdcEnvelope<TimeEntry>> cdcStream) {
        return cdcStream.flatMap((key, envelope) -> {
            List<KeyValue<String, Double>> deltas = new ArrayList<>();
            TimeEntry before = envelope.getBefore();
            TimeEntry after = envelope.getAfter();

            if (after == null && before == null) return deltas;

            if ("r".equals(envelope.getOp())) {
                double hours = after.getHoursWorked();
                logger.info("[SNAPSHOT] Project: {} Hours: {}", after.getProjectId(), hours);
                deltas.add(new KeyValue<>(String.valueOf(after.getProjectId()), hours));
                return deltas;
            }

            if (before != null && after != null) {
                double difference = after.getHoursWorked() - before.getHoursWorked();
                logger.info("[UPDATE] Project: {} Delta: {}", after.getProjectId(), difference);
                deltas.add(new KeyValue<>(String.valueOf(after.getProjectId()), difference));
            } else if (after != null) {
                logger.info("[INSERT] Project: {} Hours: {}", after.getProjectId(), after.getHoursWorked());
                deltas.add(new KeyValue<>(String.valueOf(after.getProjectId()), after.getHoursWorked()));
            } else if (before != null) {
                logger.info("[DELETE] Project: {} Hours: -{}", before.getProjectId(), before.getHoursWorked());
                deltas.add(new KeyValue<>(String.valueOf(before.getProjectId()), -before.getHoursWorked()));
            }

            return deltas;
        });
    }

    private static KTable<String, Double> aggregateProjectHours(KStream<String, Double> hourDeltas) {
        return hourDeltas
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
                .aggregate(
                        () -> 0.0,
                        (projectId, delta, total) -> total + delta,
                        Materialized.<String, Double, KeyValueStore<Bytes, byte[]>>as("project-hours-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Double())
                );
    }

    private static KTable<String, Project> processProjects(StreamsBuilder builder) {
        ObjectMapper mapper = new ObjectMapper();
        return builder
                .stream("dbserver1.public.projects", Consumed.with(Serdes.String(), new CdcSerde<>(Project.class)))
                .mapValues(CdcEnvelope::getAfter)
                .filter((k, v) -> v != null)
                .selectKey((k, v) -> parseKey(k, "id"))
                .filter((k, v) -> k != null)
                .peek((k, v) -> logger.info("Stream Project Key: {}, Name: {}, Customer ID: {}", k, v.getName(), v.getCustomerId()))
                .toTable(Materialized.<String, Project, KeyValueStore<Bytes, byte[]>>as("projects-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(new ProjectSerde()));
    }

    private static KTable<String, String> processCustomers(StreamsBuilder builder) {
        ObjectMapper mapper = new ObjectMapper();
        return builder
                .stream("dbserver1.public.customers", Consumed.with(Serdes.String(), new CdcSerde<>(Customer.class)))
                .mapValues(CdcEnvelope::getAfter)
                .filter((k, v) -> v != null)
                .selectKey((k, v) -> parseKey(k, "id"))
                .filter((k, v) -> k != null)
                .mapValues(customer -> customer.getName())
                .peek((k, v) -> logger.info("Streamed Customer: {}, Name: {}", k, v))
                .toTable(Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("customer-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.String()));
    }

    private static KTable<String, EnrichedProject> joinProjectAndCustomerData(KTable<String, Project> projectsTable, KTable<String, String> customerTable, KTable<String, Double> projectHoursTable) {
        KTable<String, EnrichedProject> projectsWithCustomer = projectsTable
                .join(customerTable, (project, customerName) -> new EnrichedProject(project.getId(), project.getName(), project.getCustomerId(), customerName, 0.0));

        return projectsWithCustomer
                .join(projectHoursTable, (enriched, hoursObj) -> {
                    enriched.setTotalHours(hoursObj);
                    return enriched;
                });
    }

    private static String parseKey(String k, String fieldName) {
        try {
            JsonNode keyNode = new ObjectMapper().readTree(k);
            return keyNode.get(fieldName).asText();
        } catch (Exception e) {
            logger.error("Error parsing key JSON: ", e);
            return null;
        }
    }

    private static void addShutdownHook(KafkaStreams streams, CountDownLatch latch) {
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });
    }
}
