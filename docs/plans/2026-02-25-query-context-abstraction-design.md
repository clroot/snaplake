# Query Context Abstraction Design

## Problem

사용자가 SQL 쿼리 작성 시 파일 URI 전체 경로를 직접 입력해야 한다.

```sql
-- 현재: 경로가 길고 복잡함
SELECT * FROM 'file:///data/snapshots/seogwipo-2026/daily/2026-02-25/uuid/seogwipo_2026.consultings.parquet'

-- 목표: 테이블 이름만으로 쿼리
SELECT * FROM consultings
```

## Solution

**DuckDB VIEW + SCHEMA 방식**으로 테이블 이름을 파일 경로에 매핑한다.

사용자가 스냅샷 컨텍스트를 설정하면, 백엔드가 쿼리 실행 전에 DuckDB VIEW를 자동 생성하여 테이블 이름만으로 접근 가능하게 한다.

## Snapshot Context Model

- **Default snapshot**: 기본 컨텍스트. 테이블 이름만으로 접근 가능.
- **Additional snapshots**: alias를 통해 `alias.tableName`으로 접근.

```sql
-- default 스냅샷의 테이블
SELECT * FROM consultings

-- 추가 스냅샷 (alias: s2)
SELECT * FROM s2.consultings

-- 크로스 스냅샷 JOIN
SELECT a.*, b.*
FROM consultings a
JOIN s2.consultings b ON a.id = b.id
```

### Alias 규칙

- 자동 생성: `s1`, `s2`, `s3`... (추가 순서대로)
- 사용자 편집 가능 (영문 소문자 + 숫자, DuckDB 식별자 규칙 준수)
- 예약어 금지: `main`, `information_schema` 등 DuckDB 예약 스키마명

## API Design

### POST /api/query (변경)

```json
{
  "sql": "SELECT * FROM consultings c JOIN s2.consultings c2 ON c.id = c2.id",
  "limit": 100,
  "offset": 0,
  "context": {
    "default": "snapshot-uuid-1",
    "additional": [
      { "snapshotId": "snapshot-uuid-2", "alias": "s2" }
    ]
  }
}
```

- `context`가 없으면 기존처럼 raw SQL 그대로 실행 (하위 호환)

### 삭제 대상

- `GetTableUriUseCase` + `GET /api/snapshots/{id}/tables/{name}/uri` 제거

## Backend Design

### UseCase 변경

`ExecuteQueryUseCase.Command`에 `SnapshotContext` 추가:

```kotlin
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
```

### QueryService 흐름

1. context가 있으면:
   - default 스냅샷의 모든 TableMeta 로드
   - additional 스냅샷들의 모든 TableMeta 로드
   - 각 테이블의 URI 해석 (`storageProvider.getUri`)
   - VIEW 생성 SQL 목록 빌드
2. DuckDB 커넥션 생성 (기존과 동일, 매번 새 커넥션)
3. VIEW 생성 SQL 실행
4. 사용자 SQL 실행
5. context가 없으면: 기존처럼 raw SQL 그대로 실행

### DuckDbQueryEngine 변경

`executeQuery`에 `viewSetupSql: List<String>` 파라미터 추가. 사용자 SQL 실행 전에 VIEW 생성 SQL을 순서대로 실행.

### VIEW 생성 SQL 빌드 (QueryService 책임)

```sql
-- default 스냅샷 테이블들
CREATE VIEW "consultings" AS SELECT * FROM 'file:///data/.../consultings.parquet';
CREATE VIEW "payments" AS SELECT * FROM 'file:///data/.../payments.parquet';

-- additional 스냅샷 (alias: s2)
CREATE SCHEMA "s2";
CREATE VIEW "s2"."consultings" AS SELECT * FROM 'file:///data/.../other/consultings.parquet';
```

## Frontend Design

### QueryPage 레이아웃

쿼리 에디터 상단에 **Context Panel** 추가:

```
┌─────────────────────────────────────────────────────┐
│ Context Panel (접기/펼치기 가능)                       │
│ ┌─────────────────────────────────────────────────┐ │
│ │ Default: [datasource ▼] / [snapshot ▼]          │ │
│ │ Tables: consultings, reservations, payments     │ │
│ ├─────────────────────────────────────────────────┤ │
│ │ s2 [edit]: seogwipo-2026 / daily / 2026-02-24  │ │
│ │ Tables: consultings, reservations        [✕]    │ │
│ ├─────────────────────────────────────────────────┤ │
│ │ [+ Add Snapshot]                                │ │
│ └─────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────┤
│ Query Editor                                         │
├─────────────────────────────────────────────────────┤
│ Query Result                                         │
└─────────────────────────────────────────────────────┘
```

### Context Panel 기능

- **Default 스냅샷**: 데이터소스 → 스냅샷 2단계 드롭다운. 선택 시 테이블 목록 표시.
- **추가 스냅샷**: "Add Snapshot" → 데이터소스/스냅샷 선택. alias 자동 생성 + 편집 가능.
- **접기/펼치기**: 설정 후 패널을 접어서 에디터 공간 확보. 접힌 상태에서 요약 표시.

### 진입 경로

| 진입 경로 | 동작 |
|-----------|------|
| 직접 (`/query`) | 컨텍스트 비어있음, 사용자가 선택 |
| "Open in Query" | `?snapshotId=xxx` → default 자동 설정, `SELECT * FROM {첫번째 테이블}` 프리필 |

### 상태 관리

```typescript
interface SnapshotContext {
  default: { snapshotId: string; datasourceId: string } | null
  additional: Array<{
    snapshotId: string
    datasourceId: string
    alias: string
  }>
}
```

QueryPage 로컬 state로 관리. `POST /api/query` 호출 시 context를 함께 전송.

## Technical Decisions

- **DuckDB VIEW 방식 채택 이유**: SQL 파싱 불필요, DuckDB 네이티브, 스키마를 통한 네임스페이스 자연스럽게 지원
- **하위 호환**: context 없이 raw SQL도 여전히 지원
- **매 쿼리마다 새 커넥션**: 기존 동작 유지, VIEW는 세션 내에서만 유효하므로 부담 없음
