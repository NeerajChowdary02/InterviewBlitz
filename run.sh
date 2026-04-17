#!/usr/bin/env bash
# Loads secrets from .env and starts the Spring Boot app.
# Usage: ./run.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -f "$SCRIPT_DIR/.env" ]; then
  echo "Error: .env file not found. Copy .env.example or create .env with your credentials."
  exit 1
fi

# Export every non-comment line in .env as an environment variable
set -a
# shellcheck source=.env
source "$SCRIPT_DIR/.env"
set +a

mvn -f "$SCRIPT_DIR/pom.xml" spring-boot:run
