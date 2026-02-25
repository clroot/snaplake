package com.snaplake.adapter.inbound.web

import com.snaplake.adapter.inbound.web.dto.*
import com.snaplake.application.port.inbound.*
import com.snaplake.domain.model.DatabaseType
import com.snaplake.domain.model.RetentionPolicy
import com.snaplake.domain.vo.DatasourceId
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
) {
    @GetMapping
    fun getAll(): ResponseEntity<List<DatasourceResponse>> {
        val datasources = getDatasourceUseCase.getAll()
        return ResponseEntity.ok(datasources.map { DatasourceResponse.from(it) })
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): ResponseEntity<DatasourceResponse> {
        val datasource = getDatasourceUseCase.getById(DatasourceId(id))
        return ResponseEntity.ok(DatasourceResponse.from(datasource))
    }

    @PostMapping
    fun register(@RequestBody request: RegisterDatasourceRequest): ResponseEntity<DatasourceResponse> {
        val datasource = registerDatasourceUseCase.register(
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
                retentionPolicy = RetentionPolicy(
                    dailyMaxCount = request.retentionDaily,
                    monthlyMaxCount = request.retentionMonthly,
                ),
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(DatasourceResponse.from(datasource))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdateDatasourceRequest,
    ): ResponseEntity<DatasourceResponse> {
        val datasource = updateDatasourceUseCase.update(
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
                retentionPolicy = RetentionPolicy(
                    dailyMaxCount = request.retentionDaily,
                    monthlyMaxCount = request.retentionMonthly,
                ),
            )
        )
        return ResponseEntity.ok(DatasourceResponse.from(datasource))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        deleteDatasourceUseCase.delete(DatasourceId(id))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/test")
    fun testConnection(@PathVariable id: String): ResponseEntity<ConnectionTestResponse> {
        val result = testDatasourceConnectionUseCase.test(DatasourceId(id))
        return ResponseEntity.ok(ConnectionTestResponse(success = result.success, message = result.message))
    }
}
