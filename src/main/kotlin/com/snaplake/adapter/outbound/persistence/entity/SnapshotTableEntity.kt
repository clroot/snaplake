package com.snaplake.adapter.outbound.persistence.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "snapshot_tables")
class SnapshotTableEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    val snapshot: SnapshotEntity,

    @Column(name = "schema_name", nullable = false)
    val schemaName: String,

    @Column(name = "table_name", nullable = false)
    val tableName: String,

    @Column(name = "row_count", nullable = false)
    val rowCount: Long,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Column(name = "storage_path", nullable = false)
    val storagePath: String,

    @Column(name = "primary_keys", nullable = false)
    val primaryKeys: String = "[]",
)
