# Snaplake

Self-hosted DB snapshot management platform. Captures database snapshots as Parquet files, stores them locally or on S3, and enables SQL querying via DuckDB.

## Tech Stack

- **Backend:** Kotlin, Spring Boot 3.4.3, Java 21, SQLite (metadata), DuckDB (query engine)
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui, TanStack Router + Query
- **Build:** Gradle (backend), bun (frontend)
- **Storage:** Local filesystem or S3-compatible object storage
- **Auth:** JWT + Argon2 password hashing

## Project Structure

```
snaplake/
├── src/main/kotlin/com/snaplake/
│   ├── domain/              # Domain models and value objects
│   │   ├── model/           # Datasource, SnapshotMeta, StorageConfig, User
│   │   ├── vo/              # Value objects (Identifiers)
│   │   └── exception/       # Domain exceptions
│   ├── application/
│   │   ├── port/
│   │   │   ├── inbound/     # UseCase interfaces + Command/Query objects
│   │   │   └── outbound/    # Port interfaces (Repositories, StorageProvider, etc.)
│   │   └── service/         # UseCase implementations
│   ├── adapter/
│   │   ├── inbound/
│   │   │   ├── web/         # REST controllers + DTOs
│   │   │   ├── cli/         # CLI commands
│   │   │   └── scheduler/   # Cron-based snapshot scheduler
│   │   └── outbound/
│   │       ├── persistence/  # JPA entities, mappers, adapters
│   │       ├── database/     # DatabaseDialect implementations (Postgres, MySQL)
│   │       ├── query/        # DuckDB query engine
│   │       └── storage/      # Local/S3 storage adapters
│   └── config/              # Spring configuration
├── frontend/
│   └── src/
│       ├── components/      # React components (layout, common, domain-specific, ui)
│       ├── pages/           # Page components
│       ├── routes/          # TanStack Router config
│       ├── hooks/           # Custom hooks
│       └── lib/             # Utilities (api, auth, theme, etc.)
├── build.gradle.kts
├── Dockerfile
└── docker-compose.yml
```

## Build & Run

```bash
# Backend (includes frontend build)
./gradlew build

# Backend test only
./gradlew test

# Frontend dev server (with proxy to backend at :8080)
cd frontend && bun run dev

# Frontend build only
cd frontend && bun run build

# Frontend lint
cd frontend && bun run lint

# Docker
docker compose up --build
```

## Architecture

Hexagonal Architecture with three layers:

1. **Domain** - Pure Kotlin domain models. No framework dependencies.
2. **Application** - UseCase interfaces (inbound ports) and outbound port interfaces. Services implement UseCases.
3. **Adapter** - Framework-specific implementations (Spring MVC controllers, JPA repositories, S3 client, etc.)

Dependency direction: `adapter → application → domain`

Key extension points:
- `DatabaseDialect` - Add support for new databases (currently PostgreSQL, MySQL)
- `StorageProvider` - Add new storage backends (currently Local, S3)

## Coding Conventions

- **Kotlin** - Follow Kotlin coding conventions, `freeCompilerArgs = -Xjsr305=strict`
- **Testing** - Kotest `DescribeSpec` style with `describe/context/it` blocks, MockK for mocking
- **UseCase pattern** - Define interfaces in `application/port/inbound/`, implement in `application/service/`
- **Command/Query objects** - Defined inside UseCase interfaces
- **Domain models** - Pure Kotlin classes, no JPA annotations. Separate JPA entities in adapter layer.
- **Value Objects** - Use `@JvmInline value class` for type-safe identifiers
- **Frontend** - React functional components, TanStack Router for routing, TanStack Query for server state
- **Package manager** - bun for frontend

## API Endpoints

All API endpoints are prefixed with `/api/`. Key routes:

- `POST /api/setup/initialize` - Initial setup (create admin user + storage config)
- `POST /api/auth/login` - JWT authentication
- `/api/datasources/**` - CRUD for database connections
- `/api/snapshots/**` - Snapshot management (create, list, preview)
- `POST /api/query` - Run SQL queries against snapshots via DuckDB
- `/api/compare/**` - Compare two snapshots
- `/api/storage/**` - Storage configuration
- `/health` - Health check
