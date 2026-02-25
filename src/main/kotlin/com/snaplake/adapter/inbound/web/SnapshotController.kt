package com.snaplake.adapter.inbound.web

import com.snaplake.adapter.inbound.web.dto.SnapshotResponse
import com.snaplake.application.port.inbound.DeleteSnapshotUseCase
import com.snaplake.application.port.inbound.GetSnapshotUseCase
import com.snaplake.application.port.inbound.TakeSnapshotUseCase
import com.snaplake.domain.vo.DatasourceId
import com.snaplake.domain.vo.SnapshotId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class SnapshotController(
    private val takeSnapshotUseCase: TakeSnapshotUseCase,
    private val getSnapshotUseCase: GetSnapshotUseCase,
    private val deleteSnapshotUseCase: DeleteSnapshotUseCase,
) {
    @GetMapping("/snapshots")
    fun getAll(
        @RequestParam(required = false) datasourceId: String?,
    ): ResponseEntity<List<SnapshotResponse>> {
        val snapshots = if (datasourceId != null) {
            getSnapshotUseCase.getByDatasourceId(DatasourceId(datasourceId))
        } else {
            getSnapshotUseCase.getAll()
        }
        return ResponseEntity.ok(snapshots.map { SnapshotResponse.from(it) })
    }

    @GetMapping("/snapshots/{id}")
    fun getById(@PathVariable id: String): ResponseEntity<SnapshotResponse> {
        val snapshot = getSnapshotUseCase.getById(SnapshotId(id))
        return ResponseEntity.ok(SnapshotResponse.from(snapshot))
    }

    @DeleteMapping("/snapshots/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        deleteSnapshotUseCase.delete(SnapshotId(id))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/datasources/{id}/snapshot")
    fun triggerSnapshot(@PathVariable id: String): ResponseEntity<SnapshotResponse> {
        val snapshot = takeSnapshotUseCase.takeSnapshot(DatasourceId(id))
        return ResponseEntity.ok(SnapshotResponse.from(snapshot))
    }
}
