package com.snaplake.domain.exception

import com.snaplake.domain.vo.DatasourceId
import com.snaplake.domain.vo.SnapshotId

sealed class DomainException(
    val code: String,
    override val message: String,
) : RuntimeException(message)

class SystemAlreadyInitializedException : DomainException(
    code = "SYSTEM_ALREADY_INITIALIZED",
    message = "System has already been initialized",
)

class DatasourceNotFoundException(id: DatasourceId) : DomainException(
    code = "DATASOURCE_NOT_FOUND",
    message = "Datasource not found: ${id.value}",
)

class SnapshotNotFoundException(id: SnapshotId) : DomainException(
    code = "SNAPSHOT_NOT_FOUND",
    message = "Snapshot not found: ${id.value}",
)

class SnapshotAlreadyRunningException(datasourceId: DatasourceId) : DomainException(
    code = "SNAPSHOT_ALREADY_RUNNING",
    message = "A snapshot is already running for datasource: ${datasourceId.value}",
)

class DatasourceConnectionFailedException(name: String, cause: String) : DomainException(
    code = "DATASOURCE_CONNECTION_FAILED",
    message = "Failed to connect to datasource '$name': $cause",
)

class StorageConnectionFailedException(cause: String) : DomainException(
    code = "STORAGE_CONNECTION_FAILED",
    message = "Failed to connect to storage: $cause",
)

class InvalidCredentialsException : DomainException(
    code = "INVALID_CREDENTIALS",
    message = "Invalid username or password",
)

class QueryExecutionFailedException(cause: String) : DomainException(
    code = "QUERY_EXECUTION_FAILED",
    message = cause,
)
