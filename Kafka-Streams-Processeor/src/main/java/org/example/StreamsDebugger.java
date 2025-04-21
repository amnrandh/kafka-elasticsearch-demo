package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class StreamsDebugger {

    public static void main(String[] args) throws InterruptedException {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-debugger");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9094");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class);

        StreamsBuilder builder = new StreamsBuilder();

        System.out.println("========== STARTING STREAMS DEBUGGER ==========");
        // Step 1: Try raw strings first to see what’s coming in
        /*
        KStream<String, String> rawStream = builder
                .stream("dbserver1.public.projects", Consumed.with(Serdes.String(), Serdes.String()))
                .peek((key, value) -> {
                    System.out.println("💬 RAW EVENT");
                    System.out.println("Key:   " + key);
                    System.out.println("Value: " + value);
                    System.out.println("──────────────────────────────────────────────");
                });
        */
        KStream<String, CdcEnvelope<TimeEntry>> cdcStream = builder
                .stream("dbserver1.public.time_entries",
                        Consumed.with(Serdes.String(), new CdcSerde<>(TimeEntry.class)))
        .peek((k, v)  -> {
            System.out.println("RAW CDC:");
            System.out.println("Op: " + v.getOp());
            System.out.println("Before: " + v.getBefore());
            System.out.println("After: " + v.getAfter());
        });



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

        KTable<String, Double> projectHoursTable = hourDeltas
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Double()))
                .aggregate(
                        () -> 0.0,
                        (projectId, delta, total) -> total + delta,
                        Materialized.<String, Double, KeyValueStore<Bytes, byte[]>>as("project-hours-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Double())
                );
        projectHoursTable
                .toStream()
                .peek((k, v) -> System.out.println("Aggregated: " + k + " => " + v));

        ObjectMapper mapper = new ObjectMapper();

        KTable<String, Project> projectsTable = builder
                .stream("dbserver1.public.projects",
                        Consumed.with(Serdes.String(), new CdcSerde<>(Project.class)))
                .mapValues(CdcEnvelope::getAfter)
                .filter((k, v) -> v != null)
                .selectKey((k, v) -> {
                    try {
                        JsonNode keyNode = mapper.readTree(k); // {"id":1}
                        String parsedKey = keyNode.get("id").asText(); // "1"
                        System.out.println("🎯 Re-Keyed Project ID: " + parsedKey);
                        return parsedKey;
                    } catch (Exception e) {
                        System.out.println("❌ Error parsing key JSON: " + e.getMessage());
                        return null;
                    }
                })
                .filter((k, v) -> k != null)
                .peek((k, v) -> System.out.println("✅ Stream Project Key: " + k + ", Name: " + v.getName() + " customer id: " + v.getCustomerId()))
                .toTable(Materialized.<String, Project, KeyValueStore<Bytes, byte[]>>as("projects-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(new ProjectSerde())); // Use your serde

        KTable<String, String> customerTable = builder
                .stream("dbserver1.public.customers",
                        Consumed.with(Serdes.String(), new CdcSerde<>(Customer.class)))
                .mapValues(CdcEnvelope::getAfter)
                .filter((k, v) -> v != null)
                .selectKey((k, v) -> {
                    try {
                        JsonNode keyNode = mapper.readTree(k); // {"id":"abc-123"}
                        String parsedKey = keyNode.get("id").asText(); // extract customer_id
                        System.out.println("🎯 Re-Keyed Customer ID: " + parsedKey);
                        return parsedKey;
                    } catch (Exception e) {
                        System.out.println("❌ Error parsing customer key JSON: " + e.getMessage());
                        return null;
                    }
                })
                .filter((k, v) -> k != null)
                .mapValues(customer -> customer.getName()) // Assuming you just want name for now
                .peek((k, v) -> System.out.println("✅ Streamed Customer: " + k + ", Name: " + v))
                .toTable(Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("customer-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.String()));

        KTable<String, Project> projectsByCustomerId = projectsTable
                .toStream()
                .selectKey((projectId, project) -> project.getCustomerId())
                .toTable();

        projectsByCustomerId
                .toStream()
                .peek((key, value) -> System.out.println("📁 Project with Customer ID: " + key + " | Name: " + value.getName()));

        /*
        KTable<String, EnrichedProject> enrichedWithCustomer = projectsByCustomerId.join(
                        customerTable,
                        (project, customerName) -> new EnrichedProject(
                                project.getId(),
                                project.getName(),
                                project.getCustomerId(),
                                customerName,
                                0.0  // Placeholder for totalHours
                        )
                )
                .toStream()  // Convert to KStream to use peek
                .peek((key, value) -> System.out.println("Enriched Project (before adding hours): " + value))
                .toTable();  // Convert back to KTable
        */
        KTable<String, String> enrichedProjects = projectsTable.join(
                projectHoursTable,
                (project, totalHours) -> {
                    // Combine the Project and totalHours into a string (or a new enriched object)
                    if (project == null) return "❌ Missing project";
                    return "📁 " + project.getName() + " | 🕒 Total Hours: " + totalHours;
                }
        );

        enrichedProjects
                .toStream()
                .peek((key, value) -> System.out.println("🧩 Joined Project: " + key + " => " + value));


        KStream<String, Project> customerProjectsStream = projectsByCustomerId.toStream();

        customerProjectsStream.peek((k, v) -> System.out.println("✅ Enriched Customer Output: " + v));

        KTable<String, EnrichedProject> projectsWithCustomer = projectsTable
                .join(customerTable, (project, customerName) -> {
                    return new EnrichedProject(
                            project.getId(),
                            project.getName(),
                            project.getCustomerId(),
                            customerName,
                            0.0 // We'll add hours in next join
                    );
                });

        KTable<String, EnrichedProject> enrichedProjectView = projectsWithCustomer
                .join(projectHoursTable, (enriched, hoursObj) -> {
                    // Since hoursObj is already a Double, directly assign it to totalHours
                    double totalHours = hoursObj;  // No need for parsing, it's already a Double

                    enriched.setTotalHours(totalHours); // Set the total hours on the enriched project
                    return enriched;
                });
        enrichedProjectView
                .toStream()
                .peek((key, enriched) -> System.out.println("✅ Final Enriched Output: " + enriched))
                .to("enriched-projects", Produced.with(Serdes.String(), new EnrichedProjectSerde()));

        /*
        KTable<String, EnrichedProject> finalEnrichedProjects = enrichedWithCustomer
                .join(
                        projectHoursTable,
                        (enrichedProject, totalHours) -> new EnrichedProject(
                                enrichedProject.getProjectId(),
                                enrichedProject.getProjectName(),
                                enrichedProject.getCustomerId(),
                                enrichedProject.getCustomerName(),
                                totalHours // Update the totalHours field
                        )
                );

        finalEnrichedProjects
                .toStream()
                .peek((key, value) -> System.out.println("Enriched Project with customer and hours: " + value));
          */
        /*
        builder
                .stream("dbserver1.public.projects", Consumed.with(Serdes.String(), new CdcSerde<>(Project.class)))
                .mapValues(CdcEnvelope::getAfter)
                .filter((k, v) -> v != null)
                .selectKey((k, v) -> {
                    try {
                        JsonNode keyNode = mapper.readTree(k); // original key like {"id":1}
                        String parsedKey = keyNode.get("id").asText(); // "1"
                        System.out.println("🎯 Re-Keyed Project ID: " + parsedKey);
                        return parsedKey;
                    } catch (Exception e) {
                        System.out.println("❌ Error parsing key JSON: " + e.getMessage());
                        return null;
                    }
                })
                .filter((k, v) -> k != null)
                .peek((k, v) -> System.out.println("✅ Stream Project Key: " + k + ", Name: " + v.getName()));





        KTable<String, Project> projectsTable = builder
                .table("dbserver1.public.projects",
                        Consumed.with(Serdes.String(), new CdcSerde<>(Project.class)))
                .mapValues(envelope -> envelope.getAfter());
        projectsTable
                .toStream()
                .peek((k, v) -> {
                    if (v != null) {
                        System.out.println("📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁");
                        System.out.println("📁 Project Table: " + k + " => " + v.getName());
                    } else {
                        System.out.println("📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁📁");
                        System.out.println("📁 Project Table: " + k + " => null");
                    }
                });


        KStream<String, CdcEnvelope<Project>> cdcPRojectStream = builder
                .stream("dbserver1.public.projects",
                        Consumed.with(Serdes.String(), new CdcSerde<>(Project.class)))
                .peek((k, v)  -> {
                    System.out.println("+++++++++++++++++++++++++");
                    System.out.println("RAW Project CDC:");
                    System.out.println("Op: " + v.getOp());
                    System.out.println("Before: " + v.getBefore());
                    System.out.println("After: " + v.getAfter());
                    System.out.println("+++++++++++++++++++++++++");
                });



        KTable<String, String> enriched = projectHoursTable.join(
                projectsTable,
                (hours, project) -> {
                    if (project == null) return "UNKNOWN => " + hours;
                    return project.getName() + " => " + hours;
                }
        );

        enriched
                .toStream()
                .peek((k, v) -> System.out.println("📊 Final Output: " + k + " => " + v));
        */
        KafkaStreams streams = new KafkaStreams(builder.build(), props);

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
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
