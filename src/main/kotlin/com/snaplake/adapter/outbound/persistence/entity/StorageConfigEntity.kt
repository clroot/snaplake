package com.snaplake.adapter.outbound.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "storage_config")
class StorageConfigEntity(
    @Id
    val id: Int = 1,

    @Column(name = "type", nullable = false)
    val type: String,

    @Column(name = "local_path")
    val localPath: String?,

    @Column(name = "s3_bucket")
    val s3Bucket: String?,

    @Column(name = "s3_region")
    val s3Region: String?,

    @Column(name = "s3_endpoint")
    val s3Endpoint: String?,

    @Column(name = "s3_access_key")
    val s3AccessKey: String?,

    @Column(name = "s3_secret_key")
    val s3SecretKey: String?,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: String,
)
