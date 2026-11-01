#!/bin/sh
set -eu

UPLOAD_DIR="${FILE_UPLOAD_DIR:-/app/uploads/datasets}"

if [ "$(id -u)" = "0" ]; then
    mkdir -p "$UPLOAD_DIR" /app/data
    chown -R appuser:appuser "$UPLOAD_DIR" /app/data
    exec gosu appuser "$0" "$@"
fi

# Verify the upload directory is writable before starting the JVM.
# On Render's free tier the filesystem is ephemeral — files do not survive
# redeployments, but the directory must still be writable at runtime.
if [ ! -w "$UPLOAD_DIR" ]; then
    echo "ERROR: upload directory $UPLOAD_DIR is not writable by uid $(id -u)." >&2
    echo "Check the Dockerfile creates and chowns this directory, or mount a" >&2
    echo "writable volume at /app/uploads." >&2
    exit 1
fi

exec java -XX:MaxRAMPercentage=75.0 -DSERVER_PORT="${PORT:-8080}" -jar /app/app.jar
