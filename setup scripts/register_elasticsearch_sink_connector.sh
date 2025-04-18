curl -X POST -H "Content-Type: application/json" \
--data '{
  "name": "elasticsearch-sink-connector",
  "config": {
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "tasks.max": "1",
    "topics": "project_summary",
    "connection.url": "http://elasticsearch:9200",
    "type.name": "_doc",
    "index": "project_summary",
    "consumer.override.group.id": "project_summary_es_consumer",
    "consumer.override.client.id": "project_summary_es_client",
    "max.retries": "3",
    "retry.backoff.ms": "3000",
    
    "key.ignore": "false",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false",
    
    "schema.ignore": "true",
    "delete.enabled": "true",
    "behavior.on.null.values": "DELETE",
    "errors.tolerance": "all"
  }
}' \
http://localhost:8083/connectors
