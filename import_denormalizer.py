import csv
import json
from elasticsearch import Elasticsearch, helpers
import os

# Initialize Elasticsearch client
es = Elasticsearch([{'host': 'localhost', 'port': 9200, 'scheme': 'http'}])

# CSV file paths
customer_csv = '../data/customers.csv'
project_csv = '../data/projects.csv'
time_entries_csv = '../data/time_entries.csv'

# Load CSV files
def load_csv(file_path):
    with open(file_path, mode='r', encoding='utf-8') as file:
        reader = csv.DictReader(file)
        return [row for row in reader]

# Load the data
customers = load_csv(customer_csv)
projects = load_csv(project_csv)
time_entries = load_csv(time_entries_csv)

# Denormalize data by merging customer, project, and time entries
def denormalize_data(customers, projects, time_entries):
    # Create a dictionary for customers by ID
    customer_dict = {customer['id']: customer for customer in customers}
    
    # Create a dictionary for projects by ID
    project_dict = {project['id']: project for project in projects}

    denormalized_data = []

    for entry in time_entries:
        customer_id = project_dict[entry['project_id']]['customer_id']
        customer_name = customer_dict.get(customer_id, {}).get('name', 'Unknown')
        project_name = project_dict[entry['project_id']]['name']
        
        denormalized_entry = {
            "_op_type": "index",  # Elasticsearch bulk operation type
            "_index": "project_summary",  # Elasticsearch index name
            "_id": entry['id'],  # Document ID (can be based on time entry ID)
            "_source": {
                "customer_name": customer_name,
                "project_name": project_name,
                "total_entries": 1,
                "total_hours_worked": float(entry['hours_worked']),
                "last_updated": entry['entry_date']
            }
        }
        denormalized_data.append(denormalized_entry)

    return denormalized_data

# Denormalize and prepare data
data_to_index = denormalize_data(customers, projects, time_entries)

# Bulk insert data into Elasticsearch
def bulk_insert_to_elasticsearch(data):
    helpers.bulk(es, data)

# Execute the bulk insert
if __name__ == '__main__':
    print("Starting bulk import...")
    bulk_insert_to_elasticsearch(data_to_index)
    print("Bulk import completed successfully!")
