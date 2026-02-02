#!/usr/bin/env pwsh

# Script to remove all Docker containers (stopped and running)
# Works on Windows, Mac, and Linux with PowerShell

Write-Host "Removing all Docker containers..."

# Get all container IDs
$containers = docker ps -aq

if (-not $containers) {
    Write-Host "No containers found."
    exit 0
}

# Stop all running containers
Write-Host "Stopping running containers..."
$runningContainers = docker ps -q
if ($runningContainers) {
    docker stop $runningContainers
} else {
    Write-Host "No running containers to stop."
}

# Remove all containers
Write-Host "Removing all containers..."
docker rm $containers

Write-Host "All containers removed successfully."
