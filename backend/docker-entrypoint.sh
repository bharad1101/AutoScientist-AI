#!/bin/sh
set -eu

UPLOAD_DIR="${FILE_UPLOAD_DIR:-/app/uploads/datasets}"

if [ "$(id -u)" = "0" ]; then
    mkdir -p "$UPLOAD_DIR" /app/data
    # Render and most container platforms mount persistent disks root-owned,
    # which replaces the ownership set when the image was built.
    chown -R appuser:appuser "$UPLOAD_DIR" /app/data
    exec gosu appuser "$0" "$@"
fi

if [ ! -w "$UPLOAD_DIR" ]; then
    echo "ERROR: upload directory $UPLOAD_DIR is not writable by uid $(id -u)." >&2
    echo "Mount a writable persistent disk at /app/uploads or fix its ownership." >&2
    exit 1
fi

exec java -XX:MaxRAMPercentage=75.0 -DSERVER_PORT="${PORT:-8080}" -jar /app/app.jar
