# Persistent Storage Readiness

Uploads are stored on the backend filesystem through `FILE_UPLOAD_DIR`. The service generates UUID filenames, normalizes resolved paths, and constrains paths to its configured directory. Dataset records remain owner-scoped in application services. Reports are generated for download rather than retained as durable uploaded files.

The image creates `/app/uploads`, declares `VOLUME ["/app/uploads"]`, and starts through an entrypoint that prepares the configured upload directory and drops privileges to uid 10001. This makes a container-local directory writable; Docker's `VOLUME` declaration does **not** prove the hosting platform mounts durable storage.

## Provider action

Attach the provider's persistent disk (or approved object storage integration) at `/app/uploads`, configure `FILE_UPLOAD_DIR=/app/uploads/datasets`, and confirm uid 10001 can write. Restart/redeploy after mount changes. Upload a dataset, record its ID, redeploy the backend, and verify profiling/download still works. Also confirm backups include the upload volume and database together.

If the host has no persistent disk, do not run production uploads on ephemeral storage; select a durable object-storage implementation or provider first. Actual provider capability is unknown from repository files.
