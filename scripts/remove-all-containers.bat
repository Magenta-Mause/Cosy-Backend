@echo off
REM Script to remove all Docker containers (stopped and running)
REM Works on Windows

echo Removing all Docker containers...

REM Check if there are any containers
docker ps -aq >nul 2>&1
if %errorlevel% neq 0 (
    echo Docker is not running or no containers found.
    exit /b 0
)

REM Get all container IDs
for /f %%i in ('docker ps -aq') do set CONTAINERS=%%i

if not defined CONTAINERS (
    echo No containers found.
    exit /b 0
)

REM Stop all running containers
echo Stopping running containers...
for /f %%i in ('docker ps -q') do docker stop %%i

REM Remove all containers
echo Removing all containers...
for /f %%i in ('docker ps -aq') do docker rm %%i

echo All containers removed successfully.
