# Snaplake

## Traps

- **CodeMirror 커스텀 키바인딩** — 반드시 `Prec.highest(keymap.of([...]))` 사용. `basicSetup`/`autocompletion` 플러그인이 default 우선순위로 먼저 등록되므로, 단순 `keymap.of()`로는 커스텀 바인딩이 가려짐. 페이지 전역 단축키는 `document` 레벨 `keydown` 리스너로 별도 등록.

## Extension Points

새 기능 추가 시 구현할 인터페이스:
- `DatabaseDialect` — 새 DB 지원 추가 (현재: PostgreSQL, MySQL)
- `StorageProvider` — 새 스토리지 백엔드 추가 (현재: Local, S3)
