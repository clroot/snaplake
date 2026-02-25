package com.snaplake.domain.vo

import java.util.UUID

@JvmInline
value class UserId(val value: String) {
    companion object {
        fun generate(): UserId = UserId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class DatasourceId(val value: String) {
    companion object {
        fun generate(): DatasourceId = DatasourceId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class SnapshotId(val value: String) {
    companion object {
        fun generate(): SnapshotId = SnapshotId(UUID.randomUUID().toString())
    }
}
