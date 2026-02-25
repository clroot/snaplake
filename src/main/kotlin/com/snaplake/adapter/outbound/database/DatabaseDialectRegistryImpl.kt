package com.snaplake.adapter.outbound.database

import com.snaplake.application.port.outbound.DatabaseDialect
import com.snaplake.application.port.outbound.DatabaseDialectRegistry
import com.snaplake.domain.model.DatabaseType
import org.springframework.stereotype.Component

@Component
class DatabaseDialectRegistryImpl(
    dialects: List<DatabaseDialect>,
) : DatabaseDialectRegistry {

    private val dialectMap: Map<DatabaseType, DatabaseDialect> =
        dialects.associateBy { it.type }

    override fun getDialect(type: DatabaseType): DatabaseDialect {
        return dialectMap[type]
            ?: throw IllegalArgumentException("No dialect found for database type: $type")
    }
}
