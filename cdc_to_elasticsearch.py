import json
from kafka import KafkaConsumer
from elasticsearch import Elasticsearch
import time

# Elasticsearch configuration
es = Elasticsearch([{'host': 'localhost', 'port': 9200, 'scheme': 'http'}])

def update_project_summary(project_id, field, value, operation):
    """
    Update an aggregate field in project_summary (like total_hours_worked)
    """
    print(f"Entering update_project_summary for project {project_id}, field {field}, operation {operation}, value {value}")
    try:
        doc = es.get(index="project_summary", id=project_id)["_source"]
        print(f"Fetched document for project {project_id}: {doc}")
        if operation == 'inc':
            doc[field] = round(doc.get(field, 0) + value, 2)
        elif operation == 'dec':
            doc[field] = round(max(0, doc.get(field, 0) - value), 2)
        es.index(index="project_summary", id=project_id, body=doc)
        print(f"Successfully updated {field} for project {project_id} to {doc[field]}")
    except Exception as e:
        print(f"Error updating {field} for project {project_id}: {e}")

def handle_customer_change(record):
    print(f"Entering handle_customer_change")
    after = record.get("after")
    if after:
        customer_id = after["id"]
        customer_name = after["name"]
        print(f"Processing customer change for customer_id {customer_id}, customer_name {customer_name}")

        # Update all matching customer references in project_summary
        query = {
            "script": {
                "source": "ctx._source.customer_name = params.name",
                "lang": "painless",
                "params": {"name": customer_name}
            },
            "query": {
                "term": {"customer_id": customer_id}
            }
        }

        try:
            es.update_by_query(index="project_summary", body=query)
            print(f"Updated customer_name for customer_id {customer_id}")
        except Exception as e:
            print(f"Error updating customer_name for customer_id {customer_id}: {e}")
    else:
        print("No 'after' data in customer record.")

def handle_project_change(record):
    print(f"Entering handle_project_change")
    after = record.get("after")
    if after:
        project_id = after["id"]
        project_name = after["name"]
        customer_id = after["customer_id"]

        # Optionally fetch customer name if not in payload
        customer_name = "Unknown"
        try:
            customer = es.search(index="project_summary", query={"term": {"customer_id": customer_id}})
            hits = customer["hits"]["hits"]
            if hits:
                customer_name = hits[0]["_source"]["customer_name"]
            print(f"Customer name for customer_id {customer_id}: {customer_name}")
        except Exception as e:
            print(f"Error fetching customer name for customer_id {customer_id}: {e}")

        summary_doc = {
            "project_name": project_name,
            "customer_id": customer_id,
            "customer_name": customer_name,
            "total_entries": 0,
            "total_hours_worked": 0.0,
            "last_updated": time.strftime("%Y-%m-%d")
        }

        try:
            es.index(index="project_summary", id=project_id, body=summary_doc)
            print(f"Inserted/Updated project_summary for project_id {project_id}")
        except Exception as e:
            print(f"Error inserting/updating project_summary for project_id {project_id}: {e}")
    else:
        print("No 'after' data in project record.")

def handle_time_entry_change(record):
    print(f"Entering handle_time_entry_change")
    before = record.get("before")
    after = record.get("after")

    project_id = None
    delta = 0

    if record["op"] == "c":  # insert
        if after:
            project_id = after["project_id"]
            delta = after["hours"]
            print(f"Inserting time entry for project_id {project_id}, delta {delta}")
            update_project_summary(project_id, "total_hours_worked", delta, "inc")
            update_project_summary(project_id, "total_entries", 1, "inc")
    elif record["op"] == "d":  # delete
        if before:
            project_id = before["project_id"]
            delta = before["hours"]
            print(f"Deleting time entry for project_id {project_id}, delta {delta}")
            update_project_summary(project_id, "total_hours_worked", delta, "dec")
            update_project_summary(project_id, "total_entries", 1, "dec")
    elif record["op"] == "u":  # update
        if before and after:
            project_id = after["project_id"]
            delta = round(after["hours"] - before["hours"], 2)
            print(f"Updating time entry for project_id {project_id}, delta {delta}")
            if delta > 0:
                update_project_summary(project_id, "total_hours_worked", delta, "inc")
            elif delta < 0:
                update_project_summary(project_id, "total_hours_worked", abs(delta), "dec")
            # total_entries stays the same on update
    else:
        print(f"Unknown operation: {record['op']}")

def process_record(record, topic):
    print(f"Entering process_record for topic {topic}")
    if topic.endswith("customers"):
        handle_customer_change(record)
    elif topic.endswith("projects"):
        handle_project_change(record)
    elif topic.endswith("time_entries"):
        handle_time_entry_change(record)
    else:
        print(f"Unknown topic: {topic}")

# Kafka consumer
topics = ['dbserver1.public.customers', 'dbserver1.public.projects', 'dbserver1.public.time_entries']
print(f"Subscribing to Kafka topics: {topics}")  # This goes here to print the topics you're subscribing to

consumer = KafkaConsumer(
    *topics,
    bootstrap_servers=['localhost:9094'],
    group_id='python-consumer-group',
    auto_offset_reset='earliest',
    enable_auto_commit=True
)

print("Kafka consumer initialized, waiting for messages...")

for message in consumer:
    try:
        print(f"Received message from Kafka topic {message.topic}")
        event = json.loads(message.value)
        print(f"Event content: {json.dumps(event, indent=2)}")  # Debug print the entire event
        print(f"Processing event from topic {message.topic}")
        process_record(event, message.topic)
    except Exception as e:
        print(f"Error processing message: {e}")
