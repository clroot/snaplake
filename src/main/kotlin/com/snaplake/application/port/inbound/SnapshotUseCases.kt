package com.snaplake.application.port.inbound

import com.snaplake.domain.model.SnapshotMeta
import com.snaplake.domain.vo.DatasourceId
import com.snaplake.domain.vo.SnapshotId

interface TakeSnapshotUseCase {
    fun takeSnapshot(datasourceId: DatasourceId): SnapshotMeta
}

interface GetSnapshotUseCase {
    fun getById(id: SnapshotId): SnapshotMeta
    fun getAll(): List<SnapshotMeta>
    fun getByDatasourceId(datasourceId: DatasourceId): List<SnapshotMeta>
}

interface DeleteSnapshotUseCase {
    fun delete(id: SnapshotId)
}
