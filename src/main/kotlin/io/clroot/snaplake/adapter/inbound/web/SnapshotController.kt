package io.clroot.snaplake.adapter.inbound.web

import io.clroot.snaplake.adapter.inbound.web.dto.SnapshotResponse
import io.clroot.snaplake.adapter.inbound.web.dto.UpdateSnapshotMetadataRequest
import io.clroot.snaplake.application.port.inbound.DeleteSnapshotUseCase
import io.clroot.snaplake.application.port.inbound.GetSnapshotUseCase
import io.clroot.snaplake.application.port.inbound.TakeSnapshotUseCase
import io.clroot.snaplake.application.port.inbound.UpdateSnapshotMetadataUseCase
import io.clroot.snaplake.domain.vo.DatasourceId
import io.clroot.snaplake.domain.vo.SnapshotId
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class SnapshotController(
    private val takeSnapshotUseCase: TakeSnapshotUseCase,
    private val getSnapshotUseCase: GetSnapshotUseCase,
    private val deleteSnapshotUseCase: DeleteSnapshotUseCase,
    private val updateSnapshotMetadataUseCase: UpdateSnapshotMetadataUseCase,
) {
    @GetMapping("/snapshots")
    fun getAll(
        @RequestParam(required = false) datasourceId: String?,
    ): ResponseEntity<List<SnapshotResponse>> {
        val snapshots =
            if (datasourceId != null) {
                getSnapshotUseCase.getByDatasourceId(DatasourceId(datasourceId))
            } else {
                getSnapshotUseCase.getAll()
            }
        return ResponseEntity.ok(snapshots.map { SnapshotResponse.from(it) })
    }

    @GetMapping("/snapshots/{id}")
    fun getById(
        @PathVariable id: String,
    ): ResponseEntity<SnapshotResponse> {
        val snapshot = getSnapshotUseCase.getById(SnapshotId(id))
        return ResponseEntity.ok(SnapshotResponse.from(snapshot))
    }

    @DeleteMapping("/snapshots/{id}")
    fun delete(
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        deleteSnapshotUseCase.delete(SnapshotId(id))
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/snapshots/{id}/metadata")
    fun updateMetadata(
        @PathVariable id: String,
        @RequestBody @Valid request: UpdateSnapshotMetadataRequest,
    ): ResponseEntity<SnapshotResponse> {
        val snapshot =
            updateSnapshotMetadataUseCase.updateMetadata(
                UpdateSnapshotMetadataUseCase.Command(
                    snapshotId = SnapshotId(id),
                    tags = request.tags,
                    memo = request.memo,
                ),
            )
        return ResponseEntity.ok(SnapshotResponse.from(snapshot))
    }

    @PostMapping("/datasources/{id}/snapshot")
    fun triggerSnapshot(
        @PathVariable id: String,
    ): ResponseEntity<SnapshotResponse> {
        val snapshot = takeSnapshotUseCase.takeSnapshot(DatasourceId(id))
        return ResponseEntity.ok(SnapshotResponse.from(snapshot))
    }
}
