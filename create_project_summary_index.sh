#!/bin/bash
curl -X PUT "localhost:9200/project_summary" -H 'Content-Type: application/json' -d'
{
  "mappings": {
    "properties": {
      "customer_name": { "type": "keyword" },
      "project_name": { "type": "keyword" },
      "total_entries": { "type": "integer" },
      "total_hours_worked": { "type": "float" },
      "last_updated": { "type": "date" },
      "customer_id": { "type": "keyword" },
      "project_id": { "type": "keyword" }
    }
  }
}
'
