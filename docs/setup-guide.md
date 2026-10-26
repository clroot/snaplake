# Setup Guide

On first launch, the setup wizard walks you through initial configuration:

| Step | Description |
|---|---|
| ![Welcome](screenshots/setup/setup-01-initial.png) | **1. Welcome** — Start the setup process |
| ![Account](screenshots/setup/setup-02-account.png) | **2. Account** — Create your admin account |
| ![Storage](screenshots/setup/setup-04-storage.png) | **3. Storage** — Configure local or S3 storage |
| ![Datasource](screenshots/setup/setup-06-datasource.png) | **4. Datasource** — Optionally register your first database |
| ![Complete](screenshots/setup/setup-08-complete.png) | **5. Complete** — Finalize and start using Snaplake |

## SQLite contention and scheduled snapshot failures

Metadata connections use WAL mode and a 1,000 ms `busy_timeout`. Snapshot metadata saves retry SQLite `BUSY`/`LOCKED` errors up to five total attempts, waiting 100, 200, 400, and 800 ms between attempts. Each attempt uses a new transaction after the previous attempt rolls back. WAL and the timeout reduce contention; they do not replace retries. Other database errors fail immediately. Retries apply only to snapshot metadata saves; snapshot deletion and datasource settings rely on `busy_timeout` alone.

WAL mode creates `snaplake.db-wal` and `snaplake.db-shm` next to `snaplake.db`. If you back up the data directory at the file level, stop Snaplake and copy all three files together.

Retries retain the snapshot ID and do not repeat database dumps or storage uploads. Requests for a datasource already being captured by this application instance are rejected, including while its initial metadata save is retrying. Run one Snaplake instance per metadata database; this guard does not coordinate multiple application instances. Source reads and storage I/O run outside metadata write transactions.

If the initial metadata save or snapshot preparation fails, Snaplake attempts to save a `FAILED` record using the same ID and the same bounded retry policy. If saving the final `COMPLETED` or `FAILED` state fails after the dump, Snaplake saves that state once more with the same policy so the database does not keep a `RUNNING` record. The run is still reported as failed even if that second save succeeds. A persistent lock can also prevent that record from being saved. Configure your log monitoring to alert on `event=scheduled_snapshot_failed`; it includes the datasource ID and, when available, the snapshot ID. `event=snapshot_failure_persistence_failed` means the failure could not be persisted, so the snapshot list may not show that run. Failed results are never logged as completed scheduled snapshots. When a scheduled run is skipped because the datasource already has a snapshot in progress, Snaplake logs an `event=scheduled_snapshot_skipped` warning instead of an error.

After retries are exhausted, that execution stops; the next cron run remains scheduled. Investigate the database lock and trigger a manual snapshot if an earlier replacement is needed. Snaplake does not replay missed cron runs after a restart.
