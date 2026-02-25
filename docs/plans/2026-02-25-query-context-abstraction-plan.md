# Query Context Abstraction Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 사용자가 파일 URI 대신 테이블 이름만으로 SQL 쿼리를 작성할 수 있도록 스냅샷 컨텍스트 기반 추상화를 구현한다.

**Architecture:** DuckDB VIEW + SCHEMA 방식. 쿼리 실행 전 DuckDB 세션에 VIEW를 생성하여 테이블 이름 → 파일 URI 매핑을 처리. 프론트엔드에 Context Panel을 추가하여 스냅샷 선택 및 alias 관리 UI 제공.

**Tech Stack:** Kotlin/Spring Boot (backend), DuckDB VIEW/SCHEMA, React/TypeScript/TanStack (frontend), shadcn/ui

**Design doc:** `docs/plans/2026-02-25-query-context-abstraction-design.md`

---

## Task 1: Backend - ExecuteQueryUseCase에 SnapshotContext 추가

UseCase 인터페이스에 컨텍스트 모델을 추가한다.

**Files:**
- Modify: `src/main/kotlin/com/snaplake/application/port/inbound/QueryUseCases.kt`

**Step 1: QueryUseCases.kt에 SnapshotContext 모델 추가**

`ExecuteQueryUseCase` 인터페이스의 `Command`에 `context` 필드를 추가하고, `SnapshotContext`, `AliasedSnapshot` data class를 내부에 정의한다.

```kotlin
interface ExecuteQueryUseCase {
    fun executeQuery(command: Command): QueryResult

    data class Command(
        val sql: String,
        val limit: Int = 100,
        val offset: Int = 0,
        val context: SnapshotContext? = null,
    )

    data class SnapshotContext(
        val default: SnapshotId,
        val additional: List<AliasedSnapshot> = emptyList(),
    )

    data class AliasedSnapshot(
        val snapshotId: SnapshotId,
        val alias: String,
    )
}
```

**Step 2: GetTableUriUseCase 제거**

`QueryUseCases.kt`에서 `GetTableUriUseCase` 인터페이스를 삭제한다.

```kotlin
// 삭제 대상:
// interface GetTableUriUseCase {
//     fun getUri(snapshotId: SnapshotId, tableName: String): String
// }
```

**Step 3: 빌드 확인**

Run: `./gradlew compileKotlin`
Expected: 컴파일 에러 발생 (QueryService, QueryController에서 GetTableUriUseCase 참조). 이는 다음 Task에서 해결.

---

## Task 2: Backend - QueryEngine 인터페이스에 viewSetupSql 추가

DuckDB 쿼리 엔진이 VIEW 생성 SQL을 실행할 수 있도록 인터페이스를 변경한다.

**Files:**
- Modify: `src/main/kotlin/com/snaplake/application/port/outbound/QueryEngine.kt`
- Modify: `src/main/kotlin/com/snaplake/adapter/outbound/query/DuckDbQueryEngine.kt`

**Step 1: QueryEngine 인터페이스 변경**

`executeQuery` 메서드에 `viewSetupSql` 파라미터를 추가한다.

```kotlin
interface QueryEngine {
    fun executeQuery(
        sql: String,
        storageConfig: StorageConfig?,
        limit: Int = 100,
        offset: Int = 0,
        viewSetupSql: List<String> = emptyList(),
    ): QueryResult
    // ... 나머지 메서드는 변경 없음
}
```

**Step 2: DuckDbQueryEngine 구현 변경**

`executeQuery` 메서드에서 사용자 SQL 실행 전에 `viewSetupSql`을 순서대로 실행한다.

```kotlin
override fun executeQuery(
    sql: String,
    storageConfig: StorageConfig?,
    limit: Int,
    offset: Int,
    viewSetupSql: List<String>,
): QueryResult {
    validateQuery(sql)
    val wrappedSql = "SELECT * FROM ($sql) AS _q LIMIT $limit OFFSET $offset"

    return createConnection(storageConfig).use { conn ->
        conn.createStatement().use { stmt ->
            viewSetupSql.forEach { setupSql ->
                stmt.execute(setupSql)
            }
            stmt.executeQuery(wrappedSql).use { rs ->
                resultSetToQueryResult(rs)
            }
        }
    }
}
```

**Step 3: 빌드 확인**

Run: `./gradlew compileKotlin`
Expected: 컴파일 에러 지속 (QueryService의 GetTableUriUseCase). Task 3에서 해결.

---

## Task 3: Backend - QueryService에 VIEW 빌드 로직 구현

QueryService에서 SnapshotContext를 받아 VIEW 생성 SQL을 빌드하고, GetTableUriUseCase를 제거한다.

**Files:**
- Modify: `src/main/kotlin/com/snaplake/application/service/QueryService.kt`

**Step 1: QueryService에서 GetTableUriUseCase 구현 제거**

클래스 선언에서 `GetTableUriUseCase`를 제거하고, `getUri` override 메서드를 삭제한다.

```kotlin
@Service
class QueryService(
    private val queryEngine: QueryEngine,
    private val loadSnapshotPort: LoadSnapshotPort,
    private val loadStorageConfigPort: LoadStorageConfigPort,
    private val storageProvider: StorageProvider,
) : ExecuteQueryUseCase, DescribeTableUseCase, PreviewTableUseCase {
    // getUri 메서드 삭제
```

**Step 2: buildViewSetupSql 메서드 추가**

```kotlin
private fun buildViewSetupSql(context: ExecuteQueryUseCase.SnapshotContext): List<String> {
    val sqls = mutableListOf<String>()

    val defaultSnapshot = loadSnapshotPort.findById(context.default)
        ?: throw SnapshotNotFoundException(context.default)

    for (table in defaultSnapshot.tables) {
        val uri = storageProvider.getUri(table.storagePath)
        sqls.add("""CREATE VIEW "${table.table}" AS SELECT * FROM '$uri'""")
    }

    for (aliased in context.additional) {
        val snapshot = loadSnapshotPort.findById(aliased.snapshotId)
            ?: throw SnapshotNotFoundException(aliased.snapshotId)
        sqls.add("""CREATE SCHEMA "${aliased.alias}"""")
        for (table in snapshot.tables) {
            val uri = storageProvider.getUri(table.storagePath)
            sqls.add("""CREATE VIEW "${aliased.alias}"."${table.table}" AS SELECT * FROM '$uri'""")
        }
    }

    return sqls
}
```

**Step 3: executeQuery 메서드에서 context 처리**

```kotlin
override fun executeQuery(command: ExecuteQueryUseCase.Command): QueryResult {
    val storageConfig = loadStorageConfigPort.find()
    val viewSetupSql = command.context?.let { buildViewSetupSql(it) } ?: emptyList()

    return queryEngine.executeQuery(
        sql = command.sql,
        storageConfig = storageConfig,
        limit = command.limit,
        offset = command.offset,
        viewSetupSql = viewSetupSql,
    )
}
```

**Step 4: 빌드 확인**

Run: `./gradlew compileKotlin`
Expected: QueryController에서 여전히 에러 (GetTableUriUseCase 참조). Task 4에서 해결.

---

## Task 4: Backend - QueryController 및 DTO 변경

Controller에서 GetTableUriUseCase를 제거하고, 요청 DTO에 context를 추가한다.

**Files:**
- Modify: `src/main/kotlin/com/snaplake/adapter/inbound/web/dto/QueryDtos.kt`
- Modify: `src/main/kotlin/com/snaplake/adapter/inbound/web/QueryController.kt`

**Step 1: ExecuteQueryRequest에 context 추가**

```kotlin
data class ExecuteQueryRequest(
    val sql: String,
    val limit: Int = 100,
    val offset: Int = 0,
    val context: SnapshotContextRequest? = null,
)

data class SnapshotContextRequest(
    val default: String,
    val additional: List<AliasedSnapshotRequest> = emptyList(),
)

data class AliasedSnapshotRequest(
    val snapshotId: String,
    val alias: String,
)
```

**Step 2: QueryController 변경**

- `getTableUriUseCase` 의존성 제거
- `getTableUri` 엔드포인트 메서드 삭제
- `executeQuery`에서 context를 Command로 변환

```kotlin
@RestController
@RequestMapping("/api")
class QueryController(
    private val executeQueryUseCase: ExecuteQueryUseCase,
    private val describeTableUseCase: DescribeTableUseCase,
    private val previewTableUseCase: PreviewTableUseCase,
) {
    @PostMapping("/query")
    fun executeQuery(@RequestBody request: ExecuteQueryRequest): ResponseEntity<QueryResultResponse> {
        val context = request.context?.let { ctx ->
            ExecuteQueryUseCase.SnapshotContext(
                default = SnapshotId(ctx.default),
                additional = ctx.additional.map {
                    ExecuteQueryUseCase.AliasedSnapshot(
                        snapshotId = SnapshotId(it.snapshotId),
                        alias = it.alias,
                    )
                },
            )
        }

        val result = executeQueryUseCase.executeQuery(
            ExecuteQueryUseCase.Command(
                sql = request.sql,
                limit = request.limit,
                offset = request.offset,
                context = context,
            )
        )
        return ResponseEntity.ok(QueryResultResponse.from(result))
    }

    // describeTable, previewTable 메서드는 변경 없음
    // getTableUri 메서드 삭제
```

**Step 3: 빌드 및 테스트**

Run: `./gradlew build`
Expected: 빌드 성공. 기존 테스트 통과.

**Step 4: 커밋**

```bash
git add src/main/kotlin/com/snaplake/application/port/inbound/QueryUseCases.kt \
  src/main/kotlin/com/snaplake/application/port/outbound/QueryEngine.kt \
  src/main/kotlin/com/snaplake/adapter/outbound/query/DuckDbQueryEngine.kt \
  src/main/kotlin/com/snaplake/application/service/QueryService.kt \
  src/main/kotlin/com/snaplake/adapter/inbound/web/dto/QueryDtos.kt \
  src/main/kotlin/com/snaplake/adapter/inbound/web/QueryController.kt
git commit -m "feat: add SnapshotContext to query execution pipeline

Add context parameter to ExecuteQueryUseCase that maps snapshot tables
to DuckDB VIEWs. Remove GetTableUriUseCase as it's no longer needed."
```

---

## Task 5: Backend - DuckDbQueryEngine VIEW 실행 테스트

VIEW 생성 후 테이블 이름으로 쿼리가 작동하는지 검증하는 테스트를 추가한다.

**Files:**
- Modify: `src/test/kotlin/com/snaplake/adapter/outbound/query/DuckDbQueryEngineTest.kt`

**Step 1: VIEW 기반 쿼리 테스트 작성**

```kotlin
describe("executeQuery with viewSetupSql") {
    it("VIEW를 통해 테이블 이름으로 쿼리할 수 있다") {
        val tempParquet = createTestParquet()
        try {
            val uri = tempParquet.toAbsolutePath()
            val viewSetupSql = listOf(
                """CREATE VIEW "test_data" AS SELECT * FROM '$uri'""",
            )

            val result = engine.executeQuery(
                sql = "SELECT * FROM test_data",
                storageConfig = null,
                viewSetupSql = viewSetupSql,
            )
            result.rows shouldHaveSize 3
            result.columns.any { it.name == "id" } shouldBe true
        } finally {
            Files.deleteIfExists(tempParquet)
        }
    }

    it("SCHEMA를 통해 alias.table 형태로 쿼리할 수 있다") {
        val tempParquet = createTestParquet()
        try {
            val uri = tempParquet.toAbsolutePath()
            val viewSetupSql = listOf(
                """CREATE VIEW "test_data" AS SELECT * FROM '$uri'""",
                """CREATE SCHEMA "s2"""",
                """CREATE VIEW "s2"."test_data" AS SELECT * FROM '$uri'""",
            )

            val result = engine.executeQuery(
                sql = "SELECT a.id, b.name FROM test_data a JOIN s2.test_data b ON a.id = b.id",
                storageConfig = null,
                viewSetupSql = viewSetupSql,
            )
            result.rows shouldHaveSize 3
        } finally {
            Files.deleteIfExists(tempParquet)
        }
    }

    it("viewSetupSql이 비어있으면 기존처럼 동작한다") {
        val result = engine.executeQuery(
            sql = "SELECT 1 as val",
            storageConfig = null,
            viewSetupSql = emptyList(),
        )
        result.rows shouldHaveSize 1
    }
}
```

**Step 2: 테스트 실행**

Run: `./gradlew test --tests "com.snaplake.adapter.outbound.query.DuckDbQueryEngineTest"`
Expected: 모든 테스트 통과.

**Step 3: 커밋**

```bash
git add src/test/kotlin/com/snaplake/adapter/outbound/query/DuckDbQueryEngineTest.kt
git commit -m "test: add DuckDB VIEW-based query execution tests"
```

---

## Task 6: Backend - QueryService 단위 테스트

SnapshotContext를 받아 올바른 VIEW SQL이 생성되고 QueryEngine에 전달되는지 테스트한다.

**Files:**
- Create: `src/test/kotlin/com/snaplake/application/service/QueryServiceTest.kt`

**Step 1: QueryService 테스트 작성**

```kotlin
package com.snaplake.application.service

import com.snaplake.application.port.inbound.ExecuteQueryUseCase
import com.snaplake.application.port.outbound.*
import com.snaplake.domain.exception.SnapshotNotFoundException
import com.snaplake.domain.model.*
import com.snaplake.domain.vo.DatasourceId
import com.snaplake.domain.vo.SnapshotId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant
import java.time.LocalDate

class QueryServiceTest : DescribeSpec({

    val queryEngine = mockk<QueryEngine>()
    val loadSnapshotPort = mockk<LoadSnapshotPort>()
    val loadStorageConfigPort = mockk<LoadStorageConfigPort>()
    val storageProvider = mockk<StorageProvider>()

    val sut = QueryService(queryEngine, loadSnapshotPort, loadStorageConfigPort, storageProvider)

    beforeTest { clearAllMocks() }

    describe("executeQuery") {
        context("context가 null인 경우") {
            it("viewSetupSql 없이 쿼리를 실행한다") {
                val expected = QueryResult(emptyList(), emptyList(), 0)
                every { loadStorageConfigPort.find() } returns null
                every {
                    queryEngine.executeQuery(
                        sql = "SELECT 1",
                        storageConfig = null,
                        limit = 100,
                        offset = 0,
                        viewSetupSql = emptyList(),
                    )
                } returns expected

                val result = sut.executeQuery(
                    ExecuteQueryUseCase.Command(sql = "SELECT 1"),
                )

                result shouldBe expected
            }
        }

        context("context가 있는 경우") {
            it("default 스냅샷의 테이블들을 VIEW로 생성하는 SQL을 전달한다") {
                val snapshotId = SnapshotId("snap-1")
                val snapshot = createTestSnapshot(
                    id = snapshotId,
                    tables = listOf(
                        TableMeta("public", "users", 10, 100, "db/daily/2026-01-01/snap-1/public.users.parquet"),
                    ),
                )
                every { loadSnapshotPort.findById(snapshotId) } returns snapshot
                every { loadStorageConfigPort.find() } returns null
                every { storageProvider.getUri("db/daily/2026-01-01/snap-1/public.users.parquet") } returns "file:///data/public.users.parquet"

                val expectedSql = listOf(
                    """CREATE VIEW "users" AS SELECT * FROM 'file:///data/public.users.parquet'""",
                )
                val expected = QueryResult(emptyList(), emptyList(), 0)
                every {
                    queryEngine.executeQuery(
                        sql = "SELECT * FROM users",
                        storageConfig = null,
                        limit = 100,
                        offset = 0,
                        viewSetupSql = expectedSql,
                    )
                } returns expected

                val result = sut.executeQuery(
                    ExecuteQueryUseCase.Command(
                        sql = "SELECT * FROM users",
                        context = ExecuteQueryUseCase.SnapshotContext(default = snapshotId),
                    ),
                )

                result shouldBe expected
            }

            it("additional 스냅샷은 SCHEMA + VIEW를 생성한다") {
                val defaultId = SnapshotId("snap-1")
                val additionalId = SnapshotId("snap-2")

                val defaultSnapshot = createTestSnapshot(
                    id = defaultId,
                    tables = listOf(
                        TableMeta("public", "users", 10, 100, "path/default/public.users.parquet"),
                    ),
                )
                val additionalSnapshot = createTestSnapshot(
                    id = additionalId,
                    tables = listOf(
                        TableMeta("public", "users", 10, 100, "path/additional/public.users.parquet"),
                    ),
                )

                every { loadSnapshotPort.findById(defaultId) } returns defaultSnapshot
                every { loadSnapshotPort.findById(additionalId) } returns additionalSnapshot
                every { loadStorageConfigPort.find() } returns null
                every { storageProvider.getUri("path/default/public.users.parquet") } returns "file:///data/default.parquet"
                every { storageProvider.getUri("path/additional/public.users.parquet") } returns "file:///data/additional.parquet"

                val setupSqlSlot = slot<List<String>>()
                val expected = QueryResult(emptyList(), emptyList(), 0)
                every {
                    queryEngine.executeQuery(
                        sql = any(),
                        storageConfig = null,
                        limit = 100,
                        offset = 0,
                        viewSetupSql = capture(setupSqlSlot),
                    )
                } returns expected

                sut.executeQuery(
                    ExecuteQueryUseCase.Command(
                        sql = "SELECT * FROM users JOIN s2.users ON users.id = s2.users.id",
                        context = ExecuteQueryUseCase.SnapshotContext(
                            default = defaultId,
                            additional = listOf(
                                ExecuteQueryUseCase.AliasedSnapshot(additionalId, "s2"),
                            ),
                        ),
                    ),
                )

                val sqls = setupSqlSlot.captured
                sqls shouldContain """CREATE VIEW "users" AS SELECT * FROM 'file:///data/default.parquet'"""
                sqls shouldContain """CREATE SCHEMA "s2""""
                sqls shouldContain """CREATE VIEW "s2"."users" AS SELECT * FROM 'file:///data/additional.parquet'"""
            }

            it("존재하지 않는 스냅샷이면 SnapshotNotFoundException을 던진다") {
                val snapshotId = SnapshotId("nonexistent")
                every { loadSnapshotPort.findById(snapshotId) } returns null
                every { loadStorageConfigPort.find() } returns null

                shouldThrow<SnapshotNotFoundException> {
                    sut.executeQuery(
                        ExecuteQueryUseCase.Command(
                            sql = "SELECT 1",
                            context = ExecuteQueryUseCase.SnapshotContext(default = snapshotId),
                        ),
                    )
                }
            }
        }
    }
})

private fun createTestSnapshot(
    id: SnapshotId,
    tables: List<TableMeta>,
): SnapshotMeta = SnapshotMeta.reconstitute(
    id = id,
    datasourceId = DatasourceId("ds-1"),
    datasourceName = "test-db",
    snapshotType = SnapshotType.DAILY,
    snapshotDate = LocalDate.of(2026, 1, 1),
    startedAt = Instant.now(),
    status = SnapshotStatus.COMPLETED,
    completedAt = Instant.now(),
    errorMessage = null,
    tables = tables,
)
```

**Step 2: 테스트 실행**

Run: `./gradlew test --tests "com.snaplake.application.service.QueryServiceTest"`
Expected: 모든 테스트 통과.

**Step 3: 커밋**

```bash
git add src/test/kotlin/com/snaplake/application/service/QueryServiceTest.kt
git commit -m "test: add QueryService unit tests for SnapshotContext"
```

---

## Task 7: Frontend - QueryPage 라우트 변경

QueryPage 라우트의 search params를 `sql` → `snapshotId`로 변경한다.

**Files:**
- Modify: `frontend/src/routes/router.tsx`

**Step 1: queryRoute의 validateSearch 변경**

```typescript
const queryRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/query",
  component: QueryPage,
  validateSearch: (search: Record<string, unknown>) => ({
    snapshotId: (search.snapshotId as string) || undefined,
  }),
})
```

**Step 2: lint 확인**

Run: `cd frontend && bun run lint`
Expected: 에러 없음 (QueryPage는 다음 Task에서 수정).

**Step 3: 커밋**

```bash
git add frontend/src/routes/router.tsx
git commit -m "feat: change query route search param from sql to snapshotId"
```

---

## Task 8: Frontend - SnapshotContext 상태 관리 및 Context Panel 구현

QueryPage에 Context Panel 컴포넌트를 추가하고, 스냅샷 컨텍스트 상태를 관리한다.

**Files:**
- Create: `frontend/src/components/query/SnapshotContextPanel.tsx`
- Modify: `frontend/src/pages/QueryPage.tsx`

**Step 1: SnapshotContextPanel 컴포넌트 작성**

데이터소스 목록과 스냅샷 목록을 fetch하여 드롭다운으로 보여주는 컴포넌트. default 스냅샷 선택, 추가 스냅샷 등록/삭제, alias 편집 기능을 포함한다.

필요한 API 호출:
- `GET /api/datasources` — 데이터소스 목록
- `GET /api/datasources/{id}` 또는 `GET /api/snapshots?datasourceId={id}` — 데이터소스별 스냅샷 목록

기존 API를 확인하여 사용 가능한 엔드포인트를 사용한다. 컴포넌트는 다음 인터페이스를 따른다:

```typescript
interface SnapshotContextState {
  default: {
    datasourceId: string
    datasourceName: string
    snapshotId: string
    snapshotLabel: string
    tables: string[]
  } | null
  additional: Array<{
    datasourceId: string
    datasourceName: string
    snapshotId: string
    snapshotLabel: string
    tables: string[]
    alias: string
  }>
}

interface SnapshotContextPanelProps {
  context: SnapshotContextState
  onContextChange: (context: SnapshotContextState) => void
  initialSnapshotId?: string
}
```

UI 구성:
- 접기/펼치기 가능한 패널 (Collapsible 컴포넌트 사용)
- Default 영역: 데이터소스 Select → 스냅샷 Select → 테이블 목록 표시
- Additional 영역: "Add Snapshot" 버튼, alias 인라인 편집, 삭제 버튼
- 접힌 상태에서 한 줄 요약 표시

**Step 2: QueryPage 수정**

- `useSearch`에서 `snapshotId` 받기
- `SnapshotContextState` 로컬 state 추가
- `executeMutation`에서 context를 API로 전달
- 기존 `sql` search param 제거

```typescript
export function QueryPage() {
  const { snapshotId: initialSnapshotId } = useSearch({
    from: "/authenticated/query",
  })

  const [context, setContext] = useState<SnapshotContextState>({
    default: null,
    additional: [],
  })

  // ...

  const executeMutation = useMutation({
    mutationFn: async (params: { sql: string; offset: number }) => {
      const start = performance.now()
      const apiContext = context.default
        ? {
            default: context.default.snapshotId,
            additional: context.additional.map((a) => ({
              snapshotId: a.snapshotId,
              alias: a.alias,
            })),
          }
        : undefined

      const data = await api.post<QueryResultData>("/api/query", {
        sql: params.sql,
        limit: PAGE_SIZE,
        offset: params.offset,
        context: apiContext,
      })
      const duration = performance.now() - start
      return { data, duration }
    },
    // ...
  })
```

QueryPage 레이아웃 변경:

```
Context Panel (상단)
Editor area (중간 - 기존)
Results area (하단 - 기존)
```

**Step 3: lint 및 dev 확인**

Run: `cd frontend && bun run lint`
Run: `cd frontend && bun run dev` → 브라우저에서 /query 접근하여 Context Panel 확인

**Step 4: 커밋**

```bash
git add frontend/src/components/query/SnapshotContextPanel.tsx \
  frontend/src/pages/QueryPage.tsx
git commit -m "feat: add SnapshotContextPanel to QueryPage

Add context panel for selecting default and additional snapshots.
Send snapshot context with query execution requests."
```

---

## Task 9: Frontend - "Open in Query" 버튼 변경

TablePreview의 "Open in Query" 버튼이 URI 대신 snapshotId를 전달하도록 변경한다.

**Files:**
- Modify: `frontend/src/components/snapshot/TablePreview.tsx`

**Step 1: "Open in Query" 버튼 로직 변경**

기존: URI를 fetch한 후 `sql` search param으로 전달.
변경: `snapshotId`만 search param으로 전달.

```typescript
<Button
  variant="outline"
  size="sm"
  onClick={() => {
    navigate({
      to: "/query",
      search: { snapshotId },
    })
  }}
>
  <SquareTerminal className="mr-1 h-3 w-3" />
  Open in Query
</Button>
```

이로써 URI fetch API 호출(`/api/snapshots/{id}/tables/{name}/uri`)이 더 이상 사용되지 않는다.

**Step 2: lint 확인**

Run: `cd frontend && bun run lint`
Expected: 통과.

**Step 3: 커밋**

```bash
git add frontend/src/components/snapshot/TablePreview.tsx
git commit -m "feat: change Open in Query to pass snapshotId instead of URI"
```

---

## Task 10: 통합 검증

전체 흐름을 수동으로 검증한다.

**Step 1: 전체 빌드**

Run: `./gradlew build`
Expected: 백엔드 빌드 및 테스트 통과.

**Step 2: 프론트엔드 빌드**

Run: `cd frontend && bun run build`
Expected: 프론트엔드 빌드 성공.

**Step 3: 수동 테스트 시나리오**

로컬에서 실행 후 다음 시나리오를 확인:

1. `/query` 직접 진입 → Context Panel이 비어있음 → 스냅샷 선택 → 테이블 목록 표시
2. 기본 스냅샷 설정 후 `SELECT * FROM {tableName}` 실행 → 결과 반환
3. "Add Snapshot" → 추가 스냅샷 선택 → alias 표시 → `SELECT * FROM s1.{tableName}` 실행
4. 크로스 스냅샷 JOIN 쿼리 실행
5. alias 직접 편집 후 쿼리에 반영
6. 스냅샷 상세 → TablePreview → "Open in Query" → `/query?snapshotId=xxx`로 이동 → default 자동 설정
7. Context 없이 raw SQL (기존 파일 URI 직접 입력) → 하위 호환 동작

**Step 4: 최종 커밋 (필요시)**

```bash
git add -A
git commit -m "fix: address integration issues from manual testing"
```
