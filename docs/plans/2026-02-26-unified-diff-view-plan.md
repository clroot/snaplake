# Unified/Split Diff View Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Compare 페이지의 Rows/Diff 탭을 하나의 Diff 탭으로 통합하여 GitHub unified diff 스타일로 행 변경을 표시하고, unified/split 뷰 토글을 제공한다.

**Architecture:** 백엔드에서 PK 메타데이터를 스냅샷 시 캡처하고, 새 unified-diff API로 ADDED/REMOVED/CHANGED 행을 반환한다. 프론트에서는 이를 GitHub diff 스타일 테이블로 렌더링하며 unified/split 토글을 제공한다.

**Tech Stack:** Kotlin + Spring Boot, DuckDB, Liquibase, React + TanStack Query + Tailwind CSS + shadcn/ui

---

## Task 1: DatabaseDialect에 PK 조회 메서드 추가

**Files:**
- Modify: `src/main/kotlin/com/snaplake/application/port/outbound/DatabaseDialect.kt`
- Modify: `src/main/kotlin/com/snaplake/adapter/outbound/database/PostgresDialect.kt`
- Modify: `src/main/kotlin/com/snaplake/adapter/outbound/database/MySqlDialect.kt`

**Step 1: DatabaseDialect 인터페이스에 메서드 추가**

`DatabaseDialect.kt`에 추가:

```kotlin
fun listPrimaryKeys(connection: Connection, schema: String, table: String): List<String>
```

**Step 2: PostgresDialect 구현**

`PostgresDialect.kt`에 추가:

```kotlin
override fun listPrimaryKeys(connection: Connection, schema: String, table: String): List<String> {
    val sql = """
        SELECT kcu.column_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name = kcu.constraint_name
          AND tc.table_schema = kcu.table_schema
        WHERE tc.constraint_type = 'PRIMARY KEY'
          AND tc.table_schema = ?
          AND tc.table_name = ?
        ORDER BY kcu.ordinal_position
    """.trimIndent()

    return connection.prepareStatement(sql).use { stmt ->
        stmt.setString(1, schema)
        stmt.setString(2, table)
        stmt.executeQuery().use { rs ->
            val columns = mutableListOf<String>()
            while (rs.next()) {
                columns.add(rs.getString("column_name"))
            }
            columns
        }
    }
}
```

**Step 3: MySqlDialect 구현**

`MySqlDialect.kt`에 추가:

```kotlin
override fun listPrimaryKeys(connection: Connection, schema: String, table: String): List<String> {
    val sql = """
        SELECT COLUMN_NAME
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = ?
          AND TABLE_NAME = ?
          AND CONSTRAINT_NAME = 'PRIMARY'
        ORDER BY ORDINAL_POSITION
    """.trimIndent()

    return connection.prepareStatement(sql).use { stmt ->
        stmt.setString(1, schema)
        stmt.setString(2, table)
        stmt.executeQuery().use { rs ->
            val columns = mutableListOf<String>()
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"))
            }
            columns
        }
    }
}
```

**Step 4: 빌드 확인**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
feat: add listPrimaryKeys to DatabaseDialect
```

---

## Task 2: TableMeta에 PK 필드 추가 + DB 마이그레이션

**Files:**
- Modify: `src/main/kotlin/com/snaplake/domain/model/SnapshotMeta.kt`
- Modify: `src/main/kotlin/com/snaplake/adapter/outbound/persistence/entity/SnapshotTableEntity.kt`
- Modify: `src/main/kotlin/com/snaplake/adapter/outbound/persistence/mapper/EntityMappers.kt`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml`

**Step 1: Liquibase 마이그레이션 추가**

`db.changelog-master.yaml` 끝에 추가:

```yaml
  - changeSet:
      id: 7-add-primary-keys-to-snapshot-tables
      author: snaplake
      changes:
        - addColumn:
            tableName: snapshot_tables
            columns:
              - column:
                  name: primary_keys
                  type: TEXT
                  defaultValue: "[]"
```

**Step 2: TableMeta에 primaryKeys 필드 추가**

`SnapshotMeta.kt`의 `TableMeta`:

```kotlin
data class TableMeta(
    val schema: String,
    val table: String,
    val rowCount: Long,
    val sizeBytes: Long,
    val storagePath: String,
    val primaryKeys: List<String> = emptyList(),
)
```

**Step 3: SnapshotTableEntity에 컬럼 추가**

```kotlin
@Column(name = "primary_keys", nullable = false)
val primaryKeys: String = "[]",
```

**Step 4: EntityMappers 업데이트**

`SnapshotMapper.toEntity`에서 `SnapshotTableEntity` 생성 시:

```kotlin
primaryKeys = objectMapper.writeValueAsString(table.primaryKeys),
```

`SnapshotMapper.toDomain`에서 `TableMeta` 생성 시:

```kotlin
primaryKeys = objectMapper.readValue(tableEntity.primaryKeys, object : TypeReference<List<String>>() {}),
```

**Step 5: 빌드 확인**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```
feat: add primaryKeys field to TableMeta with DB migration
```

---

## Task 3: SnapshotService에서 PK 캡처

**Files:**
- Modify: `src/main/kotlin/com/snaplake/application/service/SnapshotService.kt`

**Step 1: takeSnapshot에서 PK 조회 추가**

`SnapshotService.kt`의 `takeSnapshot` 메서드, 테이블 스냅샷 루프 내에서 `snapshot.addTable` 호출 전에 PK를 조회:

```kotlin
val primaryKeys = try {
    dialect.listPrimaryKeys(conn, table.schema, table.name)
} catch (e: Exception) {
    log.warn("Failed to get PKs for {}.{}: {}", table.schema, table.name, e.message)
    emptyList()
}

snapshot.addTable(
    TableMeta(
        schema = table.schema,
        table = table.name,
        rowCount = result.rowCount,
        sizeBytes = result.data.size.toLong(),
        storagePath = storagePath,
        primaryKeys = primaryKeys,
    )
)
```

`copyToMonthly`에서도 PK 전달:

```kotlin
monthlySnapshot.addTable(
    TableMeta(
        schema = table.schema,
        table = table.table,
        rowCount = table.rowCount,
        sizeBytes = table.sizeBytes,
        storagePath = monthlyPath,
        primaryKeys = table.primaryKeys,
    )
)
```

**Step 2: 빌드 확인**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
feat: capture primary keys during snapshot creation
```

---

## Task 4: Unified Diff UseCase + Service 구현

**Files:**
- Modify: `src/main/kotlin/com/snaplake/application/port/inbound/CompareUseCases.kt`
- Modify: `src/main/kotlin/com/snaplake/application/service/CompareService.kt`

**Step 1: UseCase 인터페이스 정의**

`CompareUseCases.kt`에 추가:

```kotlin
enum class DiffType { ADDED, REMOVED, CHANGED }

sealed class DiffRow(val diffType: DiffType) {
    class Added(val values: List<Any?>) : DiffRow(DiffType.ADDED)
    class Removed(val values: List<Any?>) : DiffRow(DiffType.REMOVED)
    class Changed(
        val left: List<Any?>,
        val right: List<Any?>,
        val changedColumns: List<Int>,
    ) : DiffRow(DiffType.CHANGED)
}

data class UnifiedDiffResult(
    val columns: List<ColumnSchema>,
    val primaryKeys: List<String>,
    val rows: List<DiffRow>,
    val totalRows: Long,
    val summary: DiffSummary,
)

data class DiffSummary(val added: Long, val removed: Long, val changed: Long)

interface CompareUnifiedDiffUseCase {
    fun compareUnifiedDiff(command: Command): UnifiedDiffResult

    data class Command(
        val leftSnapshotId: SnapshotId,
        val rightSnapshotId: SnapshotId,
        val tableName: String,
        val limit: Int = 100,
        val offset: Int = 0,
    )
}
```

`ColumnSchema`는 `QueryEngine.kt`에 이미 `com.snaplake.application.port.outbound`에 정의되어 있으므로 import하여 사용.

**Step 2: CompareService에 구현 추가**

`CompareService`가 `CompareUnifiedDiffUseCase`를 추가 구현.

PK 있는 경우 (FULL OUTER JOIN):
```kotlin
override fun compareUnifiedDiff(command: CompareUnifiedDiffUseCase.Command): UnifiedDiffResult {
    val leftUri = resolveTableUri(command.leftSnapshotId, command.tableName)
    val rightUri = resolveTableUri(command.rightSnapshotId, command.tableName)
    val storageConfig = loadStorageConfigPort.find()

    val leftSnapshot = loadSnapshotPort.findById(command.leftSnapshotId)
        ?: throw SnapshotNotFoundException(command.leftSnapshotId)
    val rightSnapshot = loadSnapshotPort.findById(command.rightSnapshotId)
        ?: throw SnapshotNotFoundException(command.rightSnapshotId)

    val leftTable = leftSnapshot.tables.find {
        "${it.schema}.${it.table}" == command.tableName || it.table == command.tableName
    } ?: throw IllegalArgumentException("Table not found in left snapshot")

    val primaryKeys = leftTable.primaryKeys
    val columns = queryEngine.describeTable(leftUri, storageConfig)

    return if (primaryKeys.isNotEmpty()) {
        compareWithPK(leftUri, rightUri, storageConfig, columns, primaryKeys, command.limit, command.offset)
    } else {
        compareWithExcept(leftUri, rightUri, storageConfig, columns, command.limit, command.offset)
    }
}
```

`compareWithPK` — FULL OUTER JOIN으로 left/right 값 모두 조회:
```kotlin
private fun compareWithPK(
    leftUri: String, rightUri: String, storageConfig: StorageConfig?,
    columns: List<ColumnSchema>, primaryKeys: List<String>, limit: Int, offset: Int,
): UnifiedDiffResult {
    val pkJoin = primaryKeys.joinToString(" AND ") { "l.\"$it\" = r.\"$it\"" }
    val firstPk = primaryKeys.first()
    val nonPkCols = columns.map { it.name }.filter { it !in primaryKeys }

    val changeCondition = if (nonPkCols.isNotEmpty()) {
        nonPkCols.joinToString(" OR ") { "l.\"$it\" IS DISTINCT FROM r.\"$it\"" }
    } else "FALSE"

    val leftCols = columns.joinToString(", ") { "l.\"${it.name}\" as \"l_${it.name}\"" }
    val rightCols = columns.joinToString(", ") { "r.\"${it.name}\" as \"r_${it.name}\"" }
    val orderBy = primaryKeys.joinToString(", ") {
        "COALESCE(l.\"$it\", r.\"$it\")"
    }

    val sql = """
        SELECT
            CASE
                WHEN l."$firstPk" IS NULL THEN 'ADDED'
                WHEN r."$firstPk" IS NULL THEN 'REMOVED'
                ELSE 'CHANGED'
            END as _diff_type,
            $leftCols, $rightCols
        FROM '$leftUri' l
        FULL OUTER JOIN '$rightUri' r ON $pkJoin
        WHERE l."$firstPk" IS NULL
           OR r."$firstPk" IS NULL
           OR ($changeCondition)
        ORDER BY $orderBy
    """.trimIndent()

    val result = queryEngine.executeQuery(sql, storageConfig, limit, offset)
    val colCount = columns.size

    // Summary query (counts without limit/offset)
    val summarySql = """
        SELECT
            SUM(CASE WHEN l."$firstPk" IS NULL THEN 1 ELSE 0 END) as added,
            SUM(CASE WHEN r."$firstPk" IS NULL THEN 1 ELSE 0 END) as removed,
            SUM(CASE WHEN l."$firstPk" IS NOT NULL AND r."$firstPk" IS NOT NULL THEN 1 ELSE 0 END) as changed
        FROM '$leftUri' l
        FULL OUTER JOIN '$rightUri' r ON $pkJoin
        WHERE l."$firstPk" IS NULL
           OR r."$firstPk" IS NULL
           OR ($changeCondition)
    """.trimIndent()

    val summaryResult = queryEngine.executeQuery(summarySql, storageConfig, 1, 0)
    val summaryRow = summaryResult.rows.firstOrNull()
    val summary = DiffSummary(
        added = (summaryRow?.get(0) as? Number)?.toLong() ?: 0,
        removed = (summaryRow?.get(1) as? Number)?.toLong() ?: 0,
        changed = (summaryRow?.get(2) as? Number)?.toLong() ?: 0,
    )

    val diffRows = result.rows.map { row ->
        val diffType = row[0] as String
        // row[0] = _diff_type, row[1..colCount] = left cols, row[colCount+1..2*colCount] = right cols
        val leftValues = row.subList(1, 1 + colCount)
        val rightValues = row.subList(1 + colCount, 1 + 2 * colCount)

        when (diffType) {
            "ADDED" -> DiffRow.Added(rightValues)
            "REMOVED" -> DiffRow.Removed(leftValues)
            else -> {
                val changedCols = columns.indices.filter { i ->
                    leftValues[i]?.toString() != rightValues[i]?.toString()
                }
                DiffRow.Changed(leftValues, rightValues, changedCols)
            }
        }
    }

    return UnifiedDiffResult(
        columns = columns,
        primaryKeys = primaryKeys,
        rows = diffRows,
        totalRows = result.totalRows,
        summary = summary,
    )
}
```

`compareWithExcept` — PK 없는 fallback:
```kotlin
private fun compareWithExcept(
    leftUri: String, rightUri: String, storageConfig: StorageConfig?,
    columns: List<ColumnSchema>, limit: Int, offset: Int,
): UnifiedDiffResult {
    val addedSql = "SELECT 'ADDED' as _diff_type, * FROM (SELECT * FROM '$rightUri' EXCEPT SELECT * FROM '$leftUri')"
    val removedSql = "SELECT 'REMOVED' as _diff_type, * FROM (SELECT * FROM '$leftUri' EXCEPT SELECT * FROM '$rightUri')"
    val unionSql = "$addedSql UNION ALL $removedSql"

    val result = queryEngine.executeQuery(unionSql, storageConfig, limit, offset)

    // Summary
    val addedCount = queryEngine.executeQuery(
        "SELECT COUNT(*) FROM (SELECT * FROM '$rightUri' EXCEPT SELECT * FROM '$leftUri')",
        storageConfig, 1, 0,
    ).rows.firstOrNull()?.get(0) as? Number ?: 0
    val removedCount = queryEngine.executeQuery(
        "SELECT COUNT(*) FROM (SELECT * FROM '$leftUri' EXCEPT SELECT * FROM '$rightUri')",
        storageConfig, 1, 0,
    ).rows.firstOrNull()?.get(0) as? Number ?: 0

    val diffRows = result.rows.map { row ->
        val diffType = row[0] as String
        val values = row.subList(1, row.size)
        when (diffType) {
            "ADDED" -> DiffRow.Added(values)
            else -> DiffRow.Removed(values)
        }
    }

    return UnifiedDiffResult(
        columns = columns,
        primaryKeys = emptyList(),
        rows = diffRows,
        totalRows = result.totalRows,
        summary = DiffSummary(
            added = addedCount.toLong(),
            removed = removedCount.toLong(),
            changed = 0,
        ),
    )
}
```

**Step 3: 빌드 확인**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: implement CompareUnifiedDiffUseCase with PK-based and EXCEPT fallback
```

---

## Task 5: Unified Diff API 엔드포인트

**Files:**
- Modify: `src/main/kotlin/com/snaplake/adapter/inbound/web/dto/CompareDtos.kt`
- Modify: `src/main/kotlin/com/snaplake/adapter/inbound/web/CompareController.kt`

**Step 1: DTO 추가**

`CompareDtos.kt`에 추가:

```kotlin
data class UnifiedDiffRequest(
    val leftSnapshotId: String,
    val rightSnapshotId: String,
    val tableName: String,
    val limit: Int = 100,
    val offset: Int = 0,
)

data class UnifiedDiffResponse(
    val columns: List<ColumnResponse>,
    val primaryKeys: List<String>,
    val rows: List<DiffRowResponse>,
    val totalRows: Long,
    val summary: DiffSummaryResponse,
)

data class ColumnResponse(val name: String, val type: String)

data class DiffSummaryResponse(val added: Long, val removed: Long, val changed: Long)

sealed class DiffRowResponse(val diffType: String) {
    data class Added(val values: List<Any?>) : DiffRowResponse("ADDED")
    data class Removed(val values: List<Any?>) : DiffRowResponse("REMOVED")
    data class Changed(
        val left: List<Any?>,
        val right: List<Any?>,
        val changedColumns: List<Int>,
    ) : DiffRowResponse("CHANGED")
}
```

Jackson으로 sealed class를 직렬화하려면 `@JsonTypeInfo`/`@JsonSubTypes` 대신 수동 변환이 깔끔:

```kotlin
// UnifiedDiffResponse companion
companion object {
    fun from(result: UnifiedDiffResult): UnifiedDiffResponse = UnifiedDiffResponse(
        columns = result.columns.map { ColumnResponse(it.name, it.type) },
        primaryKeys = result.primaryKeys,
        rows = result.rows.map { row ->
            when (row) {
                is DiffRow.Added -> DiffRowResponse.Added(row.values)
                is DiffRow.Removed -> DiffRowResponse.Removed(row.values)
                is DiffRow.Changed -> DiffRowResponse.Changed(row.left, row.right, row.changedColumns)
            }
        },
        totalRows = result.totalRows,
        summary = DiffSummaryResponse(result.summary.added, result.summary.removed, result.summary.changed),
    )
}
```

**Step 2: Controller 엔드포인트 추가**

`CompareController`에 `CompareUnifiedDiffUseCase` 주입 추가 + 엔드포인트:

```kotlin
@PostMapping("/unified-diff")
fun unifiedDiff(@RequestBody request: UnifiedDiffRequest): ResponseEntity<UnifiedDiffResponse> {
    val result = compareUnifiedDiffUseCase.compareUnifiedDiff(
        CompareUnifiedDiffUseCase.Command(
            leftSnapshotId = SnapshotId(request.leftSnapshotId),
            rightSnapshotId = SnapshotId(request.rightSnapshotId),
            tableName = request.tableName,
            limit = request.limit,
            offset = request.offset,
        )
    )
    return ResponseEntity.ok(UnifiedDiffResponse.from(result))
}
```

**Step 3: 빌드 확인**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: add POST /api/compare/unified-diff endpoint
```

---

## Task 6: 백엔드 테스트

**Files:**
- Modify: `src/test/kotlin/com/snaplake/application/service/CompareServiceTest.kt`

**Step 1: 기존 테스트의 TableMeta에 primaryKeys 추가**

`beforeTest` 블록의 `TableMeta` 생성에 `primaryKeys = listOf("id")` 추가.

**Step 2: unified diff 테스트 추가**

```kotlin
describe("compareUnifiedDiff") {
    context("PK가 있는 테이블") {
        it("ADDED, REMOVED, CHANGED 행을 올바르게 반환한다") {
            val result = sut.compareUnifiedDiff(
                CompareUnifiedDiffUseCase.Command(leftSnapshotId, rightSnapshotId, "users"),
            )

            result.primaryKeys shouldBe listOf("id")
            result.summary.added shouldBe 1   // Charlie
            result.summary.removed shouldBe 1  // Bob
            result.summary.changed shouldBe 1  // Alice (score 95.5 → 96.0)

            val added = result.rows.filterIsInstance<DiffRow.Added>()
            added.size shouldBe 1

            val removed = result.rows.filterIsInstance<DiffRow.Removed>()
            removed.size shouldBe 1

            val changed = result.rows.filterIsInstance<DiffRow.Changed>()
            changed.size shouldBe 1
            changed.first().changedColumns.isNotEmpty() shouldBe true
        }
    }

    context("PK가 없는 테이블") {
        it("EXCEPT fallback으로 ADDED/REMOVED만 반환한다") {
            // PK 없는 스냅샷 mock 설정
            val noPkLeftSnapshot = SnapshotMeta.reconstitute(
                id = leftSnapshotId,
                datasourceId = DatasourceId.generate(),
                datasourceName = "test",
                snapshotType = SnapshotType.DAILY,
                snapshotDate = LocalDate.now().minusDays(1),
                startedAt = Instant.now(),
                status = SnapshotStatus.COMPLETED,
                completedAt = Instant.now(),
                errorMessage = null,
                tables = listOf(
                    TableMeta("public", "users", 2, 100, "left/public.users.parquet", emptyList()),
                ),
            )
            every { loadSnapshotPort.findById(leftSnapshotId) } returns noPkLeftSnapshot

            val result = sut.compareUnifiedDiff(
                CompareUnifiedDiffUseCase.Command(leftSnapshotId, rightSnapshotId, "users"),
            )

            result.primaryKeys shouldBe emptyList()
            result.summary.changed shouldBe 0
            result.rows.none { it is DiffRow.Changed } shouldBe true
        }
    }
}
```

**Step 3: 테스트 실행**

Run: `./gradlew test --tests "com.snaplake.application.service.CompareServiceTest"`
Expected: ALL PASS

**Step 4: Commit**

```
test: add unified diff comparison tests
```

---

## Task 7: 프론트엔드 — CompareDiffView 컴포넌트 (Unified Mode)

**Files:**
- Create: `frontend/src/components/compare/CompareDiffView.tsx`
- Modify: `frontend/src/pages/ComparePage.tsx`

**Step 1: CompareDiffView 컴포넌트 생성**

API 응답 타입:

```typescript
interface DiffColumn { name: string; type: string }
interface DiffSummary { added: number; removed: number; changed: number }

type DiffRow =
  | { diffType: "ADDED"; values: unknown[] }
  | { diffType: "REMOVED"; values: unknown[] }
  | { diffType: "CHANGED"; left: unknown[]; right: unknown[]; changedColumns: number[] }

interface UnifiedDiffResponse {
  columns: DiffColumn[]
  primaryKeys: string[]
  rows: DiffRow[]
  totalRows: number
  summary: DiffSummary
}
```

컴포넌트:
- 상단 toolbar: unified/split 토글 (`ToggleGroup`), 변경 요약 (`+N -N ~N`)
- PK 없는 경우 info 배너
- Unified mode 테이블:
  - 첫 컬럼: diff indicator (`+`/`-`)
  - ADDED 행: `bg-green-50 border-l-4 border-l-green-500`
  - REMOVED 행: `bg-red-50 border-l-4 border-l-red-500`
  - CHANGED 행: `-` 행(red) + `+` 행(green) 쌍, 변경된 셀 `bg-red-100`/`bg-green-100`
- 페이지네이션

**Step 2: ComparePage 탭 구조 변경**

`ComparePage.tsx`:
- `CompareRows` import 및 탭 제거
- `CompareDiff` import 및 탭 제거
- `CompareDiffView` import 및 `Diff` 탭 추가
- 탭: `Stats | Diff`

**Step 3: lint 확인**

Run: `cd frontend && bun run lint`
Expected: no errors

**Step 4: Commit**

```
feat: add CompareDiffView with unified diff mode
```

---

## Task 8: 프론트엔드 — Split Mode

**Files:**
- Modify: `frontend/src/components/compare/CompareDiffView.tsx`

**Step 1: Split 모드 렌더링 추가**

`viewMode === "split"` 일 때:
- 좌우 2개 테이블 (`grid grid-cols-2 gap-4`)
- 왼쪽 테이블: REMOVED 행 + CHANGED의 left값, 변경 셀 `bg-red-100`
- 오른쪽 테이블: ADDED 행 + CHANGED의 right값, 변경 셀 `bg-green-100`
- ADDED 행은 왼쪽에 빈 행, REMOVED 행은 오른쪽에 빈 행 (높이 맞춤)
- 스크롤 동기화: 한쪽 스크롤 시 다른 쪽도 동기화

**Step 2: lint 확인**

Run: `cd frontend && bun run lint`
Expected: no errors

**Step 3: Commit**

```
feat: add split diff view mode
```

---

## Task 9: 기존 파일 정리

**Files:**
- Delete: `frontend/src/components/compare/CompareRows.tsx`
- Delete: `frontend/src/components/compare/CompareDiff.tsx`

**Step 1: 파일 삭제**

두 파일 삭제. `ComparePage.tsx`에서 이미 Task 7에서 import를 제거했으므로 빌드 에러 없어야 함.

**Step 2: 기존 backend UseCase 정리 여부 확인**

`CompareRowsUseCase`, `CompareDiffUseCase`는 기존 API(`/api/compare/rows`, `/api/compare/diff`)에서 아직 사용 중. 프론트에서 더 이상 호출하지 않으므로 삭제해도 되지만, 하위호환을 위해 이번에는 유지. (별도 태스크로 정리 가능)

**Step 3: 빌드 확인**

Run: `cd frontend && bun run build`
Expected: BUILD SUCCESS

**Step 4: Commit**

```
refactor: remove CompareRows and CompareDiff components
```

---

## Task 10: 최종 검증

**Step 1: 전체 백엔드 테스트**

Run: `./gradlew test`
Expected: ALL PASS

**Step 2: 프론트엔드 빌드**

Run: `cd frontend && bun run build`
Expected: BUILD SUCCESS

**Step 3: 수동 검증 (선택)**

1. 앱 실행 후 `/compare` 페이지 접근
2. 스냅샷 선택 후 Diff 탭 확인
3. Unified/Split 토글 동작 확인
4. ADDED(초록)/REMOVED(빨강)/CHANGED(쌍 + 셀 하이라이트) 표시 확인
