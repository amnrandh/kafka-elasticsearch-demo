# Scaling SQL with Elasticsearch & CDC

# Prerequisites

Before you begin, ensure that you have the following tools and services installed and configured:

## Software

- **Docker**: To run containers for PostgreSQL, Kafka, Zookeeper, Elasticsearch, and other services.
  - Install Docker: [https://docs.docker.com/get-docker/](https://docs.docker.com/get-docker/)
  
- **Docker Compose**: To manage multi-container Docker applications.
  - Install Docker Compose: [https://docs.docker.com/compose/install/](https://docs.docker.com/compose/install/)
  
- **Kafka**: A distributed event streaming platform to handle Change Data Capture (CDC).
  - Install Kafka: [https://kafka.apache.org/quickstart](https://kafka.apache.org/quickstart)
  
- **Debezium**: A set of distributed services for capturing changes from databases.
  - Learn more about Debezium: [https://debezium.io/](https://debezium.io/)

- **Elasticsearch**: A distributed search and analytics engine for storing and querying data.
  - Install Elasticsearch: [https://www.elastic.co/downloads/elasticsearch](https://www.elastic.co/downloads/elasticsearch)
  
## Configuration & Access

- **PostgreSQL Database**: A database that contains the normalized data tables.
  - Ensure that PostgreSQL is running and accessible from within your Docker environment.
  - Set up replication to enable Change Data Capture (CDC) through Debezium.

- **Kafka Connect**: A tool for integrating Kafka with other systems such as databases or Elasticsearch.
  - Ensure that Kafka Connect is set up and configured to stream data to Elasticsearch.

- **Basic familiarity** with SQL, Kafka, and Elasticsearch will help understand the interactions in this system.

## Tools

- **cURL**: A command-line tool for making HTTP requests (required for interacting with REST APIs, such as Kafka Connect and Elasticsearch).
  - Install cURL: [https://curl.se/](https://curl.se/)

- **jq**: A lightweight and flexible command-line JSON processor (optional but helpful for inspecting Docker container details).
  - Install jq: [https://stedolan.github.io/jq/download/](https://stedolan.github.io/jq/download/)
  
- **Java**: Required to run any Java applications, such as those built with Kafka Streams.
  - Install Java: [https://adoptopenjdk.net/](https://adoptopenjdk.net/)

## Access

- Ensure that your system is able to connect to `localhost` (or any custom ports you've configured) for all services like Kafka, PostgreSQL, and Elasticsearch.
- Ensure Docker containers are running and properly configured using `docker-compose` commands.



## Overview

This project shows how to move costly SQL queries to Elasticsearch. It uses Change Data Capture (CDC) with Kafka and Debezium. The goal is to improve query performance in a multi-tenant system. This is done by syncing SQL changes to Elasticsearch in near real-time. Then, we use efficient full-text and aggregation queries for quick analytics.

### Key Features:
- **SQL Offloading**: Move high-latency SQL queries to Elasticsearch.
- **CDC Sync**: Use Kafka and Debezium to stream SQL changes (inserts, updates, deletes) in near real-time.
- **Query Optimization**: Route queries based on dataset size and multi-tenant considerations.
- **Multi-Tenancy**: Efficient handling of tenants with both small and large datasets.
- **Monitoring**: Track query performance, consumer lag, and sync health.

## Architecture Overview
graph TD
    A[PostgreSQL Database] --> B[Debezium Connector (via Kafka Connect)]
    B --> C[Kafka Broker]
    C --> D[Kafka Consumer (Custom Consumer)]
    D --> E[Elasticsearch]
    E --> F[Kibana (Optional)]
    A -->|Change Data Capture| B
    B -->|Streams Changes| C
    C -->|Processes CDC Events| D
    D -->|Denormalized Data| E
    E -->|Fast Queries| F

### PostgreSQL Database:
- Contains normalized data tables (e.g., Customers, Projects, TimeEntries).
- Generates data changes that are captured by Debezium.

### Debezium Connector (via Kafka Connect):
- Monitors changes in PostgreSQL using logical replication.
- Publishes change events to Kafka topics (e.g., dbserver1.public.customers, dbserver1.public.projects, dbserver1.public.time_entries).

### Kafka Broker:
- Acts as the intermediary to buffer CDC events.
- Consumer groups process these messages and commit offsets for reliable, at-least-once delivery.

### Elasticsearch:
- A sink for denormalized data derived from SQL changes.
- Provides fast full-text search and aggregations.
- **Kibana (optional)** can be used for monitoring and visualizations.

### Custom Consumer (Kafka Streams App):
- Processes Kafka messages, transforms them, and updates the project_summary index in Elasticsearch.
- Manages offsets and ensures idempotency using unique document IDs.
> **Note:** This is a Kafka Streams application. If you're new to Streams or need a refresher on how it handles things like joins, you can check out this guide from Confluent:  
> [Understanding Joins in Kafka Streams](https://docs.confluent.io/platform/current/streams/developer-guide/dsl-api.html#joins)

## Getting Started

### Prerequisites
- Docker and Docker Compose installed on your machine.
- Basic familiarity with SQL, Kafka, Elasticsearch, and REST APIs.
- Access to a terminal (or Postman) for running curl commands.

### Environment Setup

#### Docker Compose Setup:
Use the provided `docker-compose.yml` to set up the following services:
- PostgreSQL (with CDC enabled using logical replication)
- Zookeeper
- Kafka (with separate INSIDE and OUTSIDE listeners)
- Elasticsearch
- Kafka Connect with Debezium

#### Spin Up the Environment:
From your project directory, run:

```
docker-compose up -d

#### Verify Service Health:

- **PostgreSQL**: `docker-compose logs db`
- **Kafka**: `docker-compose logs kafka`
- **Zookeeper**: `docker-compose logs zookeeper`
- **Elasticsearch**: `curl -X GET "localhost:9200/_cluster/health?pretty"`
- **Kafka Connect**: `curl http://localhost:8083/connectors/`  
  *(If Kafka Connect isn’t running, see troubleshooting below.)*

### Creating & Populating the Database (Phase 2)

#### Schema Setup:
Define tables for Customers, Projects, and TimeEntries in PostgreSQL.

#### Data Generation:
Use tools like Mockaroo or custom Python/SQL scripts to generate 1M+ rows of sample data.

#### Data Insertion:
Insert the generated data into your PostgreSQL database.

### Enabling CDC & Streaming Data to Kafka (Phase 3)

#### Enable CDC:
Ensure that PostgreSQL is set up with `wal_level=logical` and that a publication is created for the tables of interest.

#### Debezium Connector Configuration:
Create a JSON configuration file (e.g., `debezium-connector-config.json`) similar to:

```json
{
  "name": "dbserver1",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "tasks.max": "1",
    "database.hostname": "db",
    "database.port": "5432",
    "database.user": "user",
    "database.password": "password",
    "database.dbname": "project_management",
    "database.server.name": "dbserver1",
    "plugin.name": "pgoutput",
    "slot.name": "debezium_slot",
    "publication.name": "debezium_publication",
    "table.include.list": "public.customers,public.projects,public.time_entries",
    "snapshot.mode": "initial"
  }
}
```

## Querying Elasticsearch

Elasticsearch provides a variety of query types to search, filter, and analyze data. Below are commonly used query patterns.

### 1. Full-Text Search

Allows searching content within text fields using analyzed tokens.

- **Match Query** – Searches for text in analyzed fields:
  ```json
  {
    "query": {
      "match": {
        "field_name": "search text"
      }
    }
  }
  ```

- **Fuzzy Matching** – Handles typos and similar terms using fuzziness:
  ```json
  {
    "query": {
      "match": {
        "field_name": {
          "query": "serch",
          "fuzziness": "AUTO"
        }
      }
    }
  }
  ```

### 2. Exact Match

Used with non-analyzed keyword fields for precise matches.

- **Term Query**:
  ```json
  {
    "query": {
      "term": {
        "field_name": "exact_value"
      }
    }
  }
  ```

### 3. Boolean Queries

Combines multiple queries using logical operators (`must`, `should`, `must_not`).

- **Example**:
  ```json
  {
    "query": {
      "bool": {
        "must": [
          { "match": { "field_name": "value" } }
        ],
        "should": [
          { "term": { "field_name": "optional_value" } }
        ]
      }
    }
  }
  ```

### 4. Range Queries

Filters documents based on numerical or date field ranges.

- **Example**:
  ```json
  {
    "query": {
      "range": {
        "date_field": {
          "gte": "2021-01-01",
          "lte": "2021-12-31"
        }
      }
    }
  }
  ```

---

### Learn More

- [Elasticsearch Full-Text Search Documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/full-text.html)  
- [Elasticsearch Analysis](https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis.html)  
- [Elasticsearch Query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)



# Data Integrity and Backfilling

## No Retroactive Updates for Project/Customer Names
Our system does **not** retroactively update `project_name` or `customer_name` fields if they are changed in the source system after an event has already been processed. This design decision ensures we maintain data consistency without introducing complex backfill logic.

## Event-Driven Data Changes
Once a Kafka event is processed, the record in the downstream system (e.g., Elasticsearch) reflects the state of the data at the time of processing. If the `project_name` or `customer_name` changes later, it will only be reflected in new events after the change has occurred.

## Backfilling Data
Backfilling, or replaying events to correct data, is an **expensive operation** and should be performed sparingly. When backfilling:
- Ensure your consumer app can handle **idempotency** to avoid duplicates.
- Be cautious about **resource utilization** during the backfill process.


# Operational Considerations

## Kafka Streams: Delivery Semantics and Idempotency
- Kafka Streams provides **at-least-once delivery** semantics by default.  
  If using external systems like Elasticsearch, **idempotency** is key. This ensures that duplicate records from Kafka do not result in duplicate writes in Elasticsearch.

- **Exactly-once semantics (EOS)** can be enabled with additional setup, but comes with performance overhead. Read more on EOS in Kafka [here](https://docs.confluent.io/platform/current/streams/exactly-once.html).

## Data Enrichment and Caching
- Enriching Kafka messages with reference data (e.g., `projects`, `customers`) directly from databases can introduce significant latency and scalability concerns.  
- For static but large datasets, consider using an **in-memory store like Redis** to hold reference data for fast lookups during stream processing.

## State Management and RocksDB
- Kafka Streams relies on **RocksDB** for local state storage. Ensure you understand the implications of stateful operations, such as joins and aggregations, as they can increase resource usage.
- **State retention**: Understand your retention settings to prevent Kafka Streams from holding excessive state and potentially overusing storage.

## Scaling and Performance Considerations
- Scaling Kafka Streams apps typically involves increasing parallelism (e.g., increasing the number of partitions in your Kafka topics). However, be aware of the **resource constraints** on your application, especially around stateful processing.
- For high throughput, **optimize the number of threads** and **manage memory carefully**.
- Consider partitioning your topics based on **data access patterns** and application logic to avoid skewed load and uneven processing.

## Failover and Recovery
- Kafka Streams apps can recover from failures by replaying messages. However, ensure **offset management** and **state restoration** are set up correctly to handle restarts gracefully.
- You can use **Kafka’s built-in stream processing recovery mechanisms**, but it’s important to **monitor state consistency** during app restarts.

## External System Considerations
- Kafka Streams apps should avoid making **slow external calls (DB/API)** during processing. Instead, enrich messages with external data in a batch pre-processing step or use Redis to store the enriched data for quick lookup.
- **Handling backpressure** from external systems should be part of your overall stream processing strategy.

---

> For more in-depth reading, refer to:
- [Kafka Streams Developer Guide](https://docs.confluent.io/platform/current/streams/index.html)
- [Handling Duplicates and Idempotency](https://www.confluent.io/blog/kafka-exactly-once-semantics/)



## Troubleshooting Commands:
### Kafka & Kafka Connect Commands
```
# List Kafka Topics:
kafka-topics.sh --bootstrap-server <broker_host>:<port> --list

# Describe Consumer Group:
kafka-consumer-groups.sh --bootstrap-server <broker_host>:<port> --describe --group <group_name>

# Consume Messages from a Topic:
kafka-console-consumer.sh --bootstrap-server <broker_host>:<port> --topic <topic_name> --from-beginning

# Delete Kafka Topic:
kafka-topics.sh --bootstrap-server <broker_host>:<port> --delete --topic <topic_name>

# Reset Kafka Streams Application:
kafka-streams-application-reset.sh --application-id <application_id> \
  --bootstrap-servers <broker_host>:<port> \
  --input-topics <topic_name> \
  --to-earliest

# List Kafka Topics (Alternate Broker):
kafka-topics.sh --list --bootstrap-server <alternate_broker_host>:<port>
```

### Docker Commands
```
# Access a Docker Container:
docker exec -it <container_name_or_id> bash

# List Running Docker Containers:
docker ps

# Inspect Docker Container (Health):
docker inspect <container_name> | jq '.[0].State.Health'

# View Docker Logs for a Service:
docker logs <container_name_or_id>

# Remove Temporary Kafka Streams Data:
rm -rf /tmp/kafka-streams/*
```

### Elasticsearch & HTTP Commands
```
# Check Elasticsearch Cluster Health:
curl http://<elasticsearch_host>:9200/_cluster/health?pretty

# Delete Kafka Connect Connector:
curl -X DELETE http://<connect_host>:<port>/connectors/<connector_name>

# Get Kafka Connector Status:
curl -X GET http://<connect_host>:<port>/connectors/<connector_name>/status

# Health Check for Kafka Connect:
curl http://<connect_host>:<port>
```

### General Commands
```
# View Kafka Connectors:
curl http://<connect_host>:<port>/connectors/

# Start the Docker Environment:
docker-compose up -d

# Stop Docker Environment:
docker-compose down
```

### Miscellaneous
```
# Run Java Application:
java -jar <your_file_name>.jar
```