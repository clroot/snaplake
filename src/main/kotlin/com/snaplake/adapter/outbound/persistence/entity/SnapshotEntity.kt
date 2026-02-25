package com.snaplake.adapter.outbound.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "snapshots")
class SnapshotEntity(
    @Id
    val id: String,

    @Column(name = "datasource_id", nullable = false)
    val datasourceId: String,

    @Column(name = "datasource_name", nullable = false)
    val datasourceName: String,

    @Column(name = "snapshot_type", nullable = false)
    val snapshotType: String,

    @Column(name = "snapshot_date", nullable = false)
    val snapshotDate: String,

    @Column(name = "status", nullable = false)
    var status: String,

    @Column(name = "started_at", nullable = false)
    val startedAt: String,

    @Column(name = "completed_at")
    var completedAt: String?,

    @Column(name = "error_message")
    var errorMessage: String?,

    @OneToMany(mappedBy = "snapshot", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val tables: MutableList<SnapshotTableEntity> = mutableListOf(),
)
