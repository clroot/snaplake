package com.snaplake.adapter.outbound.database

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.parquet.column.ParquetProperties
import org.apache.parquet.hadoop.ParquetFileWriter
import org.apache.parquet.hadoop.api.WriteSupport
import org.apache.parquet.io.api.Binary
import org.apache.parquet.io.api.RecordConsumer
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.PrimitiveType
import org.apache.parquet.schema.Type
import org.apache.parquet.schema.Types
import java.math.BigDecimal
import java.nio.file.Files
import java.sql.ResultSet
import java.sql.Types as SqlTypes

object ParquetWriter {

    fun writeResultSetToParquet(rs: ResultSet): ByteArray {
        val metaData = rs.metaData
        val columnCount = metaData.columnCount

        val columns = (1..columnCount).map { i ->
            ColumnDef(
                name = metaData.getColumnLabel(i),
                sqlType = metaData.getColumnType(i),
                nullable = metaData.isNullable(i) != java.sql.ResultSetMetaData.columnNoNulls,
            )
        }

        val schema = buildSchema(columns)
        val rows = mutableListOf<List<Any?>>()

        while (rs.next()) {
            val row = (1..columnCount).map { i ->
                rs.getObject(i)
            }
            rows.add(row)
        }

        return writeParquet(schema, columns, rows)
    }

    private fun buildSchema(columns: List<ColumnDef>): MessageType {
        val fields = columns.map { col ->
            val repetition = if (col.nullable) Type.Repetition.OPTIONAL else Type.Repetition.REQUIRED
            when (col.sqlType) {
                SqlTypes.BOOLEAN, SqlTypes.BIT ->
                    Types.primitive(PrimitiveType.PrimitiveTypeName.BOOLEAN, repetition).named(col.name)
                SqlTypes.TINYINT, SqlTypes.SMALLINT, SqlTypes.INTEGER ->
                    Types.primitive(PrimitiveType.PrimitiveTypeName.INT32, repetition).named(col.name)
                SqlTypes.BIGINT ->
                    Types.primitive(PrimitiveType.PrimitiveTypeName.INT64, repetition).named(col.name)
                SqlTypes.FLOAT, SqlTypes.REAL ->
                    Types.primitive(PrimitiveType.PrimitiveTypeName.FLOAT, repetition).named(col.name)
                SqlTypes.DOUBLE ->
                    Types.primitive(PrimitiveType.PrimitiveTypeName.DOUBLE, repetition).named(col.name)
                SqlTypes.NUMERIC, SqlTypes.DECIMAL ->
                    Types.primitive(PrimitiveType.PrimitiveTypeName.BINARY, repetition)
                        .`as`(LogicalTypeAnnotation.stringType())
                        .named(col.name)
                SqlTypes.BINARY, SqlTypes.VARBINARY, SqlTypes.LONGVARBINARY, SqlTypes.BLOB ->
                    Types.primitive(PrimitiveType.PrimitiveTypeName.BINARY, repetition).named(col.name)
                else ->
                    Types.primitive(PrimitiveType.PrimitiveTypeName.BINARY, repetition)
                        .`as`(LogicalTypeAnnotation.stringType())
                        .named(col.name)
            }
        }
        return MessageType("table", fields)
    }

    private fun writeParquet(schema: MessageType, columns: List<ColumnDef>, rows: List<List<Any?>>): ByteArray {
        val tempFile = Files.createTempFile("snaplake-", ".parquet")
        try {
            Files.delete(tempFile)

            val conf = Configuration()
            val path = Path(tempFile.toString())
            val writeSupport = SimpleGroupWriteSupport(schema)

            val writer = SimpleParquetWriter(path, writeSupport, conf)

            writer.use { w ->
                rows.forEach { row ->
                    val group = SimpleGroup(schema)
                    row.forEachIndexed { i, value ->
                        if (value != null) {
                            writeValue(group, i, value, columns[i].sqlType)
                        }
                    }
                    w.write(group)
                }
            }

            return Files.readAllBytes(tempFile)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun writeValue(group: SimpleGroup, index: Int, value: Any, sqlType: Int) {
        when (sqlType) {
            SqlTypes.BOOLEAN, SqlTypes.BIT ->
                group.add(index, (value as? Boolean) ?: value.toString().toBoolean())
            SqlTypes.TINYINT, SqlTypes.SMALLINT, SqlTypes.INTEGER ->
                group.add(index, (value as Number).toInt())
            SqlTypes.BIGINT ->
                group.add(index, (value as Number).toLong())
            SqlTypes.FLOAT, SqlTypes.REAL ->
                group.add(index, (value as Number).toFloat())
            SqlTypes.DOUBLE ->
                group.add(index, (value as Number).toDouble())
            SqlTypes.NUMERIC, SqlTypes.DECIMAL ->
                group.add(index, Binary.fromString((value as? BigDecimal)?.toPlainString() ?: value.toString()))
            SqlTypes.BINARY, SqlTypes.VARBINARY, SqlTypes.LONGVARBINARY, SqlTypes.BLOB ->
                group.add(index, Binary.fromReusedByteArray(value as ByteArray))
            else ->
                group.add(index, Binary.fromString(value.toString()))
        }
    }

    private data class ColumnDef(val name: String, val sqlType: Int, val nullable: Boolean)
}

class SimpleGroup(private val schema: MessageType) {
    private val values: Array<Any?> = arrayOfNulls(schema.fieldCount)

    fun add(index: Int, value: Int) { values[index] = value }
    fun add(index: Int, value: Long) { values[index] = value }
    fun add(index: Int, value: Float) { values[index] = value }
    fun add(index: Int, value: Double) { values[index] = value }
    fun add(index: Int, value: Boolean) { values[index] = value }
    fun add(index: Int, value: Binary) { values[index] = value }

    fun getSchema(): MessageType = schema
    fun getValue(index: Int): Any? = values[index]
    fun getFieldCount(): Int = schema.fieldCount
}

class SimpleGroupWriteSupport(
    private val schema: MessageType,
) : WriteSupport<SimpleGroup>() {

    private lateinit var recordConsumer: RecordConsumer

    override fun init(configuration: Configuration): WriteContext {
        return WriteContext(schema, emptyMap())
    }

    override fun prepareForWrite(recordConsumer: RecordConsumer) {
        this.recordConsumer = recordConsumer
    }

    override fun write(group: SimpleGroup) {
        recordConsumer.startMessage()
        for (i in 0 until group.getFieldCount()) {
            val value = group.getValue(i) ?: continue
            val fieldName = group.getSchema().getFieldName(i)
            recordConsumer.startField(fieldName, i)
            when (value) {
                is Int -> recordConsumer.addInteger(value)
                is Long -> recordConsumer.addLong(value)
                is Float -> recordConsumer.addFloat(value)
                is Double -> recordConsumer.addDouble(value)
                is Boolean -> recordConsumer.addBoolean(value)
                is Binary -> recordConsumer.addBinary(value)
            }
            recordConsumer.endField(fieldName, i)
        }
        recordConsumer.endMessage()
    }
}

class SimpleParquetWriter(
    path: Path,
    writeSupport: WriteSupport<SimpleGroup>,
    conf: Configuration,
) : org.apache.parquet.hadoop.ParquetWriter<SimpleGroup>(
    path,
    writeSupport,
    org.apache.parquet.hadoop.metadata.CompressionCodecName.SNAPPY,
    DEFAULT_BLOCK_SIZE,
    DEFAULT_PAGE_SIZE,
    DEFAULT_PAGE_SIZE,
    DEFAULT_IS_DICTIONARY_ENABLED,
    DEFAULT_IS_VALIDATING_ENABLED,
    ParquetProperties.WriterVersion.PARQUET_1_0,
    conf,
)
