# Snaplake

Self-hosted database snapshot management platform. Captures database snapshots as Parquet files, stores them locally or on S3, and enables SQL querying via DuckDB.

## Features

- **Database Snapshots** - Capture point-in-time snapshots from PostgreSQL and MySQL databases as Parquet files
- **Scheduled Snapshots** - Configure cron-based automatic snapshots per datasource
- **Retention Policies** - Set daily/monthly retention limits to manage storage automatically
- **SQL Query Engine** - Query any snapshot using standard SQL powered by DuckDB
- **Snapshot Comparison** - Compare two snapshots side-by-side with row-level diff and statistics
- **Flexible Storage** - Store snapshots on local filesystem or S3-compatible object storage (AWS S3, MinIO, etc.)
- **Setup Wizard** - Guided initial setup for admin account, storage, and first datasource
- **Dark Mode** - Full dark/light theme support

## Quick Start

### Docker (Recommended)

```bash
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080) and follow the setup wizard.

### Local Development

**Prerequisites:** Java 21, [Bun](https://bun.sh)

```bash
# Start backend (port 8080)
./gradlew bootRun

# Start frontend dev server (port 5173, proxied to backend)
cd frontend && bun install && bun run dev
```

## Configuration

All configuration is done via environment variables:

| Variable | Default | Description |
|---|---|---|
| `SNAPLAKE_DATA_DIR` | `./data` | Directory for SQLite metadata DB and local snapshots |
| `SNAPLAKE_PORT` | `8080` | Server port |
| `SNAPLAKE_JWT_SECRET` | (auto-generated) | JWT signing secret |
| `SNAPLAKE_ENCRYPTION_KEY` | (auto-generated) | AES key for encrypting datasource passwords |

Storage (Local or S3) is configured through the web UI during setup.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Kotlin, Spring Boot 3.4, Java 21 |
| Metadata DB | SQLite |
| Query Engine | DuckDB |
| Snapshot Format | Apache Parquet |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui |
| Routing / State | TanStack Router, TanStack Query |
| Auth | JWT + Argon2 |
| Storage | Local filesystem / S3-compatible |

## Architecture

Hexagonal Architecture with clear separation of concerns:

```
adapter/inbound     (Web, CLI, Scheduler)
        |
application/port    (UseCase interfaces, Port interfaces)
application/service (UseCase implementations)
        |
domain/model        (Pure Kotlin domain models)
        |
adapter/outbound    (JPA, DuckDB, S3, Local Storage)
```

Dependency direction: `adapter -> application -> domain`

### Extension Points

- **DatabaseDialect** - Add support for new database types (currently PostgreSQL, MySQL)
- **StorageProvider** - Add new storage backends (currently Local, S3)

## Build

```bash
# Full build (backend + frontend)
./gradlew build

# Run tests
./gradlew test

# Frontend only
cd frontend && bun run build

# Frontend lint
cd frontend && bun run lint
```

## License

This project is not yet licensed. All rights reserved.
