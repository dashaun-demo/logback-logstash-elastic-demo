
#!/bin/bash
# setup.sh - Setup script for the log performance test

echo "Starting Elasticsearch and Logstash..."
rm -rf .docker/elastic
docker compose up -d

# Wait for Elasticsearch to be ready
echo "Waiting for Elasticsearch to be ready..."
until curl -s http://localhost:9200/_cluster/health | grep -q '"status":"green\|yellow"'; do
    sleep 5
    echo "Waiting for Elasticsearch..."
done

# For ES 8.x/9.x - Create index template using the new API
echo "Creating Elasticsearch index template..."
curl -X PUT "localhost:9200/_index_template/logs-template" \
  -H 'Content-Type: application/json' \
  -d '{
    "index_patterns": ["logs-*"],
    "template": {
      "settings": {
        "number_of_shards": 3,
        "number_of_replicas": 0,
        "refresh_interval": "30s",
        "index.translog.durability": "async",
        "index.translog.sync_interval": "30s",
        "index.translog.flush_threshold_size": "1gb"
      },
      "mappings": {
        "properties": {
          "@timestamp": {
            "type": "date"
          },
          "level": {
            "type": "keyword"
          },
          "logger": {
            "type": "keyword"
          },
          "thread": {
            "type": "keyword"
          },
          "message": {
            "type": "text",
            "index": false
          },
          "counter": {
            "type": "long"
          }
        }
      }
    },
    "priority": 500
  }'

echo "Setup complete!"
