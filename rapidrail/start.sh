#!/bin/bash
# EventPass one-command startup: starts MySQL (Docker) if needed, then Spring Boot.
# Usage: ./start.sh            (from the rapidrail/ directory)
# Optional env overrides: DB_PASSWORD, RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET
set -e

DB_PASSWORD="${DB_PASSWORD:-***REMOVED_DB_PASSWORD***}"
CONTAINER="eventpass_mysql"

if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "MySQL container '${CONTAINER}' already running."
elif docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "Starting existing MySQL container '${CONTAINER}'..."
  docker start "${CONTAINER}" > /dev/null
else
  echo "Creating MySQL container '${CONTAINER}'..."
  docker run -d --name "${CONTAINER}" \
    -e MYSQL_ROOT_PASSWORD="${DB_PASSWORD}" \
    -e MYSQL_DATABASE=eventpass \
    -p 3306:3306 mysql:8.0 > /dev/null
fi

echo "Waiting for MySQL on localhost:3306..."
for i in $(seq 1 30); do
  if (echo > /dev/tcp/localhost/3306) 2>/dev/null; then
    echo "MySQL is up."
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "MySQL did not start in time. Aborting."
    exit 1
  fi
  sleep 2
done

export DB_PASSWORD
if [ -n "${RAZORPAY_KEY_ID:-}" ] && [ -n "${RAZORPAY_KEY_SECRET:-}" ]; then
  echo "Using Razorpay keys from environment."
  export RAZORPAY_KEY_ID RAZORPAY_KEY_SECRET
else
  echo "Using built-in Razorpay test keys (override with RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET)."
fi
echo "Starting EventPass on http://localhost:8081 ..."
mvn spring-boot:run
