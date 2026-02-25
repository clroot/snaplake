package com.snaplake.adapter.outbound.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "datasources")
class DatasourceEntity(
    @Id
    val id: String,

    @Column(name = "name", nullable = false, unique = true)
    val name: String,

    @Column(name = "type", nullable = false)
    val type: String,

    @Column(name = "host", nullable = false)
    val host: String,

    @Column(name = "port", nullable = false)
    val port: Int,

    @Column(name = "database_name", nullable = false)
    val databaseName: String,

    @Column(name = "username", nullable = false)
    val username: String,

    @Column(name = "encrypted_password", nullable = false)
    val encryptedPassword: String,

    @Column(name = "schemas", nullable = false)
    val schemas: String,

    @Column(name = "cron_expression")
    val cronExpression: String?,

    @Column(name = "retention_daily", nullable = false)
    val retentionDaily: Int,

    @Column(name = "retention_monthly", nullable = false)
    val retentionMonthly: Int,

    @Column(name = "enabled", nullable = false)
    val enabled: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: String,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: String,
)
