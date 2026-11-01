#!/bin/sh
set -eu

UPLOAD_DIR="${FILE_UPLOAD_DIR:-/app/uploads/datasets}"
BACKEND="${STORAGE_BACKEND:-local}"

if [ "$(id -u)" = "0" ]; then
    mkdir -p "$UPLOAD_DIR" /app/data
    # Render and most container platforms mount persistent disks root-owned,
    # which replaces the ownership set when the image was built.
    chown -R appuser:appuser "$UPLOAD_DIR" /app/data
    exec gosu appuser "$0" "$@"
fi

# When using S3-compatible storage, the local upload directory is not needed.
# Skip the write-check to allow the container to start without a mounted disk.
if [ "$BACKEND" != "s3" ]; then
    if [ ! -w "$UPLOAD_DIR" ]; then
        echo "ERROR: upload directory $UPLOAD_DIR is not writable by uid $(id -u)." >&2
        echo "Set STORAGE_BACKEND=s3 to use S3-compatible storage, or mount a" >&2
        echo "writable persistent disk at /app/uploads and fix its ownership." >&2
        exit 1
    fi
fi

exec java -XX:MaxRAMPercentage=75.0 -DSERVER_PORT="${PORT:-8080}" -jar /app/app.jar
