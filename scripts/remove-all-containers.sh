#!/bin/bash

# Script to remove all Docker containers (stopped and running)
# Works on Mac and Linux

echo "Removing all Docker containers..."

# Get all container IDs (running and stopped)
CONTAINERS=$(docker ps -aq)

if [ -z "$CONTAINERS" ]; then
    echo "No containers found."
    exit 0
fi

# Stop all running containers
echo "Stopping running containers..."
docker stop $(docker ps -q) 2>/dev/null || echo "No running containers to stop."

# Remove all containers
echo "Removing all containers..."
docker rm $(docker ps -aq)

echo "All containers removed successfully."
