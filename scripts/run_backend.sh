#!/usr/bin/env bash
set -e

echo "=========================================="
echo "Starting AutoScientist AI Backend"
echo "=========================================="

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR/backend"

if [ -f ".env" ]; then
    echo "Loading environment variables from backend/.env..."
    set -a
    source .env
    set +a
fi

# Ensure persistent local database directory exists
mkdir -p data/h2
mkdir -p uploads/datasets

# Regenerate the classpath when it is missing or older than the dependency
# definition (pom.xml), so dependency changes are picked up on next start.
CLASSPATH_FILE="target/classpath.txt"
if [ ! -f "$CLASSPATH_FILE" ] || [ "pom.xml" -nt "$CLASSPATH_FILE" ]; then
    echo "Generating classpath..."
    mkdir -p target
    ./mvnw dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE" -q
fi

exec java -cp "target/classes:$(cat "$CLASSPATH_FILE")" com.autoscientist.AutoScientistApplication
