package io.clroot.snaplake.adapter.inbound.web

import io.clroot.snaplake.adapter.inbound.web.dto.ConnectionTestResponse
import io.clroot.snaplake.adapter.inbound.web.dto.DatasourceResponse
import io.clroot.snaplake.adapter.inbound.web.dto.RegisterDatasourceRequest
import io.clroot.snaplake.adapter.inbound.web.dto.UpdateDatasourceRequest
import io.clroot.snaplake.application.port.inbound.*
import io.clroot.snaplake.domain.model.DatabaseType
import io.clroot.snaplake.domain.model.RetentionPolicy
import io.clroot.snaplake.domain.vo.DatasourceId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/datasources")
class DatasourceController(
    private val registerDatasourceUseCase: RegisterDatasourceUseCase,
    private val updateDatasourceUseCase: UpdateDatasourceUseCase,
    private val deleteDatasourceUseCase: DeleteDatasourceUseCase,
    private val getDatasourceUseCase: GetDatasourceUseCase,
    private val testDatasourceConnectionUseCase: TestDatasourceConnectionUseCase,
    private val listDatasourceTablesUseCase: ListDatasourceTablesUseCase,
) {
    @GetMapping
    fun getAll(): ResponseEntity<List<DatasourceResponse>> {
        val datasources = getDatasourceUseCase.getAll()
        return ResponseEntity.ok(datasources.map { DatasourceResponse.from(it) })
    }

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: String,
    ): ResponseEntity<DatasourceResponse> {
        val datasource = getDatasourceUseCase.getById(DatasourceId(id))
        return ResponseEntity.ok(DatasourceResponse.from(datasource))
    }

    @PostMapping
    fun register(
        @RequestBody @Valid request: RegisterDatasourceRequest,
    ): ResponseEntity<DatasourceResponse> {
        val datasource =
            registerDatasourceUseCase.register(
                RegisterDatasourceUseCase.Command(
                    name = request.name,
                    type = DatabaseType.valueOf(request.type.uppercase()),
                    host = request.host,
                    port = request.port,
                    database = request.database,
                    username = request.username,
                    password = request.password,
                    schemas = request.schemas,
                    cronExpression = request.cronExpression,
                    retentionPolicy =
                        RetentionPolicy(
                            dailyMaxCount = request.retentionDaily,
                            monthlyMaxCount = request.retentionMonthly,
                        ),
                    includedTables = request.includedTables,
                ),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(DatasourceResponse.from(datasource))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody @Valid request: UpdateDatasourceRequest,
    ): ResponseEntity<DatasourceResponse> {
        val datasource =
            updateDatasourceUseCase.update(
                DatasourceId(id),
                UpdateDatasourceUseCase.Command(
                    name = request.name,
                    type = DatabaseType.valueOf(request.type.uppercase()),
                    host = request.host,
                    port = request.port,
                    database = request.database,
                    username = request.username,
                    password = request.password,
                    schemas = request.schemas,
                    cronExpression = request.cronExpression,
                    retentionPolicy =
                        RetentionPolicy(
                            dailyMaxCount = request.retentionDaily,
                            monthlyMaxCount = request.retentionMonthly,
                        ),
                    includedTables = request.includedTables,
                ),
            )
        return ResponseEntity.ok(DatasourceResponse.from(datasource))
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        deleteDatasourceUseCase.delete(DatasourceId(id))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/test")
    fun testConnection(
        @PathVariable id: String,
    ): ResponseEntity<ConnectionTestResponse> {
        val result = testDatasourceConnectionUseCase.test(DatasourceId(id))
        return ResponseEntity.ok(ConnectionTestResponse(success = result.success, message = result.message))
    }

    @GetMapping("/{id}/tables")
    fun listTables(
        @PathVariable id: String,
    ): ResponseEntity<Map<String, List<String>>> {
        val tables = listDatasourceTablesUseCase.listTables(DatasourceId(id))
        return ResponseEntity.ok(tables)
    }
}
