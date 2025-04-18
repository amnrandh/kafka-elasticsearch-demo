#!/bin/bash
curl -X POST -H "Content-Type: application/json" \
  --data '{
    "name": "postgres-connector",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "tasks.max": "1",
      "database.hostname": "kafka-elasticsearch-demo_db_1",
      "database.port": "5432",
      "database.user": "user",
      "database.password": "password",
      "database.dbname": "project_management",
      "database.server.name": "kafka-elasticsearch-demo-db-1",
      "topic.prefix": "dbserver1",
      "table.include.list": "public.customers,public.projects,public.time_entries",
      "plugin.name": "pgoutput",
      "slot.name": "debezium_slot",
      "publication.name": "debezium_pub",
      "snapshot.mode": "initial",
      "slot.drop.on.stop": "true",
      "tombstones.on.delete": "true",
      
      "key.converter": "org.apache.kafka.connect.json.JsonConverter",
      "key.converter.schemas.enable": "false",
      "value.converter": "org.apache.kafka.connect.json.JsonConverter",
      "value.converter.schemas.enable": "false",
      
      "decimal.format": "NUMERIC",
      "json.output.decimal.format": "NUMERIC",
      "decimal.handling.mode": "string"
    }
  }' \
  http://localhost:8083/connectors
