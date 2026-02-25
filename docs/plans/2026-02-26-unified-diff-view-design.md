# Unified/Split Diff View Design

## Goal

Compare 페이지의 Rows/Diff 탭을 하나의 Diff 탭으로 통합. GitHub unified diff 스타일로 행 변경을 표시하고, unified/split 뷰 토글을 제공한다.

## Changes

### 1. Snapshot PK Metadata Capture

스냅샷 생성 시 원본 DB에서 PK 정보를 함께 캡처하여 저장.

**Domain:**
- `TableMeta`에 `primaryKeys: List<String>` 필드 추가

**Persistence:**
- `snapshot_tables` 테이블에 `primary_keys` 컬럼 추가 (comma-separated 또는 JSON)
- `SnapshotTableEntity`, `EntityMappers` 업데이트

**DatabaseDialect:**
- `listPrimaryKeys(connection, schema, table): List<String>` 메서드 추가
- PostgreSQL: `information_schema.table_constraints` + `key_column_usage` 쿼리
- MySQL: 동일 표준 SQL 쿼리

**SnapshotService:**
- 테이블 스냅샷 시 `listPrimaryKeys()` 호출하여 `TableMeta`에 포함

### 2. Backend: New Unified Diff API

**`POST /api/compare/unified-diff`**

Request:
```json
{
  "leftSnapshotId": "...",
  "rightSnapshotId": "...",
  "tableName": "...",
  "limit": 100,
  "offset": 0
}
```

Response:
```json
{
  "columns": [{ "name": "id", "type": "INTEGER" }, ...],
  "primaryKeys": ["id"],
  "rows": [
    { "diffType": "ADDED", "values": [1, "new", ...] },
    { "diffType": "REMOVED", "values": [2, "old", ...] },
    {
      "diffType": "CHANGED",
      "left": [3, "before", ...],
      "right": [3, "after", ...],
      "changedColumns": [1]
    }
  ],
  "totalRows": 42,
  "summary": { "added": 10, "removed": 5, "changed": 27 }
}
```

Logic:
- PK를 스냅샷 메타데이터에서 자동 조회
- PK 있는 경우: FULL OUTER JOIN으로 ADDED/REMOVED/CHANGED 구분, PK 순 정렬
- PK 없는 경우: EXCEPT 기반 fallback (ADDED/REMOVED만), 전체 행 순 정렬
- CHANGED 행: left/right 두 값 + 변경된 컬럼 인덱스 목록

UseCase:
- 기존 `CompareRowsUseCase`, `CompareDiffUseCase` → 새 `CompareUnifiedDiffUseCase`로 대체

### 3. Frontend: Tab Restructure

**ComparePage.tsx:**
- `Stats | Rows | Diff` → `Stats | Diff`

**New `CompareDiffView.tsx`:**
- 상단 toolbar: unified/split 토글 버튼 + 변경 요약 (`+10 -5 ~27`)
- Unified mode:
  - 단일 테이블, 첫 컬럼에 `+`/`-` 기호
  - ADDED 행: 초록 배경 (`bg-green-50`, `border-l-green-500`)
  - REMOVED 행: 빨강 배경 (`bg-red-50`, `border-l-red-500`)
  - CHANGED 행: `-` 행(빨강) 바로 아래 `+` 행(초록), 변경된 셀만 진한 하이라이트 (`bg-red-100`/`bg-green-100`)
- Split mode:
  - 좌우 2개 테이블 (50%/50%)
  - 왼쪽: LEFT 스냅샷 기준 (REMOVED + CHANGED의 이전값)
  - 오른쪽: RIGHT 스냅샷 기준 (ADDED + CHANGED의 이후값)
  - 변경된 셀 하이라이트
  - 대응하는 행 없는 경우 빈 행으로 높이 맞춤

**삭제:**
- `CompareRows.tsx`
- `CompareDiff.tsx`

### 4. PK Fallback

PK가 없는 테이블의 경우:
- EXCEPT 기반으로 ADDED/REMOVED만 표시
- CHANGED 감지 불가 안내 메시지 표시
- "PK가 없어 변경된 행을 감지할 수 없습니다" 배너

## Out of Scope

- PK 수동 선택 UI (PK 없는 테이블에 대한 향후 개선)
- 컬럼 필터링/검색
- diff 결과 export
