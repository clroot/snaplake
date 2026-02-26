> [한국어](README.ko.md)

# Snaplake

[![Docker Image Version](https://img.shields.io/docker/v/abcdkh1209/snaplake?sort=semver&label=Docker%20Hub)](https://hub.docker.com/r/abcdkh1209/snaplake)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Self-hosted tool for querying past database states without restoring backups. Captures periodic snapshots of PostgreSQL and MySQL tables as Parquet files, and lets you query any point in time with SQL powered by DuckDB.

![Dashboard](docs/screenshots/features/dashboard.png)

## Features

### SQL Query Engine

Query any snapshot with SQL using DuckDB. Supports joins, aggregations, filtering, and CSV/JSON export.

![SQL Query](docs/screenshots/features/query.png)

### Snapshot Comparison

Compare two snapshots side-by-side with row-level diff. Added, removed, and modified rows are color-coded.

![Compare Diff](docs/screenshots/features/compare-diff.png)

### Automatic Snapshots

Capture full table snapshots as Apache Parquet files on a cron schedule. Browse snapshot contents with filtering, sorting, and CSV/JSON export.

![Snapshot Browser](docs/screenshots/features/snapshots.png)

### And More

- **Retention Policies** — Daily/monthly retention limits to manage storage automatically
- **Flexible Storage** — Local filesystem or S3-compatible object storage (AWS S3, MinIO, etc.)
- **Setup Wizard** — Guided initial setup for admin account, storage, and first datasource
- **Dark Mode** — Full dark/light theme support

## Quick Start

### Docker (Recommended)

```bash
docker run -d \
  --name snaplake \
  -p 8080:8080 \
  -v snaplake-data:/app/data \
  -e SNAPLAKE_JWT_SECRET=your-secret-key \
  -e SNAPLAKE_ENCRYPTION_KEY=your-encryption-key \
  abcdkh1209/snaplake:latest
```

> **Note:** Replace `your-secret-key` and `your-encryption-key` with your own secure values. These are used for JWT signing and datasource password encryption respectively. If omitted, random keys are auto-generated on each restart, which will invalidate existing sessions and encrypted data.

Open [http://localhost:8080](http://localhost:8080) and follow the setup wizard.

### Try with Sample Database

A demo compose file is included with a pre-configured PostgreSQL database:

```bash
docker compose -f docker-compose.demo.yml up
```

This starts Snaplake alongside a PostgreSQL instance loaded with sample data (customers, products, orders). Connect to it during setup:

| Field | Value |
|---|---|
| Host | `sample-db` |
| Port | `5432` |
| Database | `sampledb` |
| Username | `demo` |
| Password | `demo1234` |

### Local Development

**Prerequisites:** Java 21, [Bun](https://bun.sh)

```bash
# Start backend (port 8080)
./gradlew bootRun

# Start frontend dev server (port 5173, proxied to backend)
cd frontend && bun install && bun run dev
```

## Setup Guide

On first launch, the setup wizard walks you through initial configuration. See the [Setup Guide](docs/setup-guide.md) for details.

## Configuration

All configuration is done via environment variables:

| Variable | Default | Description |
|---|---|---|
| `SNAPLAKE_DATA_DIR` | `./data` | Directory for SQLite metadata DB and local snapshots |
| `SNAPLAKE_PORT` | `8080` | Server port |
| `SNAPLAKE_JWT_SECRET` | (auto-generated) | JWT signing secret |
| `SNAPLAKE_ENCRYPTION_KEY` | (auto-generated) | AES key for encrypting datasource passwords |

Storage (Local or S3) is configured through the web UI during setup.

## How It Works

```
PostgreSQL / MySQL
        |
   Scheduled or manual trigger
        |
   Snapshot as Parquet files ──→ Local or S3 storage
        |
   DuckDB SQL engine ──→ Query, analyze, compare
```

1. **Capture** — On schedule or manual trigger, Snaplake reads tables and writes them as Parquet files
2. **Store** — Snapshots go to local filesystem or any S3-compatible storage
3. **Query** — DuckDB reads the Parquet files directly — no import, no restoration

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

- **DatabaseDialect** — Add support for new database types (currently PostgreSQL, MySQL)
- **StorageProvider** — Add new storage backends (currently Local, S3)

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Kotlin, Spring Boot 3.4, Java 21 |
| Metadata DB | SQLite |
| Query Engine | DuckDB |
| Snapshot Format | Apache Parquet |
| Frontend | React 19, TypeScript, Vite |
| UI Components | Carbon Design System |
| Routing / State | TanStack Router, TanStack Query |
| Auth | JWT + Argon2 |
| Storage | Local filesystem / S3-compatible |

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

This project is licensed under the [MIT License](LICENSE).
