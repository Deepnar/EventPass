#!/bin/bash
# EventPass one-command startup: starts MySQL (Docker) if needed, then Spring Boot.
# Usage: ./start.sh            (from the rapidrail/ directory)
#
# Secrets live in rapidrail/.env (gitignored, created automatically) or in the
# environment — never in the repo. Supported keys:
#   DB_PASSWORD, RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET
set -e
cd "$(dirname "$0")"

CONTAINER="eventpass_mysql"

# Load local secrets if present.
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

container_exists() {
  docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER}$"
}

container_running() {
  docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"
}

if container_running; then
  echo "MySQL container '${CONTAINER}' already running."
  if [ ! -f .env ] || ! grep -q "^DB_PASSWORD=" .env 2>/dev/null; then
    echo "ERROR: container exists but rapidrail/.env has no DB_PASSWORD for it."
    echo "Either find the password this container was created with and put"
    echo "  DB_PASSWORD='<it>'  in rapidrail/.env,"
    echo "or wipe it and start over (DESTROYS dev data, reseeds on boot):"
    echo "  docker rm -f ${CONTAINER} && rm -f rapidrail/.env && ./start.sh"
    exit 1
  fi
elif container_exists; then
  echo "Starting existing MySQL container '${CONTAINER}'..."
  docker start "${CONTAINER}" > /dev/null
else
  if [ -z "${DB_PASSWORD:-}" ]; then
    if command -v openssl > /dev/null; then
      DB_PASSWORD="$(openssl rand -hex 12)"
    else
      DB_PASSWORD="$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')"
    fi
    echo "Generated a MySQL root password into rapidrail/.env (gitignored)."
  fi
  # persist the password: keep any existing non-password keys, DB_PASSWORD wins
  : > .env.new
  printf 'DB_PASSWORD=%s\n' "$DB_PASSWORD" >> .env.new
  if [ -f .env ]; then
    grep -v "^DB_PASSWORD=" .env >> .env.new 2>/dev/null || true
  fi
  mv .env.new .env
  chmod 600 .env
  echo "Creating MySQL container '${CONTAINER}'..."
  docker run -d --name "${CONTAINER}" \
    -e MYSQL_ROOT_PASSWORD="${DB_PASSWORD}" \
    -e MYSQL_DATABASE=eventpass \
    -p 3306:3306 mysql:8.0 > /dev/null
fi

echo "Waiting for MySQL on localhost:3306..."
for i in $(seq 1 40); do
  # Real login check (TCP-open alone fires while mysqld is still initializing).
  if docker exec "${CONTAINER}" mysqladmin ping -uroot -p"${DB_PASSWORD}" --silent 2>/dev/null; then
    echo "MySQL is up."
    break
  fi
  if [ "$i" -eq 40 ]; then
    echo "MySQL did not start in time. Last container logs:"
    docker logs --tail 5 "${CONTAINER}"
    exit 1
  fi
  sleep 3
done

if [ -z "${DB_PASSWORD:-}" ]; then
  echo "ERROR: DB_PASSWORD is not set (no .env and nothing in environment). Aborting."
  exit 1
fi
export DB_PASSWORD
if [ -n "${RAZORPAY_KEY_ID:-}" ] && [ -n "${RAZORPAY_KEY_SECRET:-}" ]; then
  echo "Razorpay keys detected — online top-ups enabled."
  export RAZORPAY_KEY_ID RAZORPAY_KEY_SECRET
else
  echo "No Razorpay keys — demo top-up only. (Add RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET to .env to enable.)"
fi
echo "Starting EventPass on http://localhost:8081 ..."
mvn spring-boot:run
