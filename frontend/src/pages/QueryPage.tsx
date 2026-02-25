import { useState, useCallback, useEffect, useMemo, useRef } from "react"
import { useMutation, useQueries, useQuery } from "@tanstack/react-query"
import { useSearch } from "@tanstack/react-router"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { QueryEditor } from "@/components/query/QueryEditor"
import { QueryResult } from "@/components/query/QueryResult"
import { SnapshotContextBar } from "@/components/query/SnapshotContextBar"
import {
  type SnapshotContextState,
  type SnapshotResponse,
  formatSnapshotLabel,
} from "@/components/query/snapshot-context-utils"
import {
  addQueryHistory,
  getQueryHistory,
  clearQueryHistory,
  type QueryHistoryEntry,
} from "@/lib/query-history"
import { Clock, Loader2, Play, Trash2 } from "lucide-react"

interface Column {
  name: string
  type: string
}

interface QueryResultData {
  columns: Column[]
  rows: unknown[][]
  totalRows: number
}

const PAGE_SIZE = 100

function buildInitialContext(snap: SnapshotResponse): SnapshotContextState {
  return {
    entries: [
      {
        datasourceId: snap.datasourceId,
        snapshotId: snap.id,
        snapshotLabel: formatSnapshotLabel(snap),
        datasourceName: snap.datasourceName,
        alias: "s1",
      },
    ],
  }
}

function buildInitialSql(snap: SnapshotResponse): string {
  const firstTable = snap.tables[0]
  return firstTable ? `SELECT * FROM ${firstTable.table}` : ""
}

export function QueryPage() {
  const { snapshotId: initialSnapshotId } = useSearch({
    from: "/authenticated/query",
  })
  const [sqlText, setSqlText] = useState("")
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<QueryResultData | null>(null)
  const [executionTime, setExecutionTime] = useState<number | undefined>()
  const [error, setError] = useState<string | null>(null)
  const [showHistory, setShowHistory] = useState(false)
  const [history, setHistory] = useState<QueryHistoryEntry[]>(getQueryHistory)
  const [context, setContext] = useState<SnapshotContextState>({
    entries: [],
  })
  const [initialized, setInitialized] = useState(false)

  // Fetch initial snapshot and auto-configure context
  const { data: initialSnapshot } = useQuery({
    queryKey: ["snapshot", initialSnapshotId],
    queryFn: () =>
      api.get<SnapshotResponse>(`/api/snapshots/${initialSnapshotId}`),
    enabled: !!initialSnapshotId && !initialized,
  })

  // Initialize context from fetched snapshot (runs once)
  if (initialSnapshot && !initialized) {
    setInitialized(true)
    setContext(buildInitialContext(initialSnapshot))
    setSqlText(buildInitialSql(initialSnapshot))
  }

  // Fetch schema for each selected snapshot (for autocomplete)
  const validEntries = useMemo(
    () => context.entries.filter((e) => e.snapshotId),
    [context.entries],
  )
  const schemaData = useQueries({
    queries: validEntries.map((entry) => ({
      queryKey: ["snapshotSchema", entry.snapshotId],
      queryFn: () =>
        api.get<{ tables: Record<string, { name: string }[]> }>(
          `/api/snapshots/${entry.snapshotId}/schema`,
        ),
      staleTime: Infinity,
    })),
    combine: (results) => results.map((r) => r.data),
  })

  const editorTables = useMemo(() => {
    const tables: Record<string, string[]> = {}
    for (let i = 0; i < validEntries.length; i++) {
      const data = schemaData[i]
      if (!data) continue
      const alias = validEntries[i].alias
      for (const [tableName, columns] of Object.entries(data.tables)) {
        const columnNames = columns.map((c) => c.name)
        tables[`${alias}.${tableName}`] = columnNames
        // 단일 스냅샷이면 alias 없이도 접근 가능
        if (validEntries.length === 1) {
          tables[tableName] = columnNames
        }
      }
    }
    return Object.keys(tables).length > 0 ? tables : undefined
  }, [validEntries, schemaData])

  // ref로 최신 sqlText를 항상 참조 (CodeMirror 키맵에서의 stale closure 방지)
  const sqlTextRef = useRef(sqlText)
  useEffect(() => {
    sqlTextRef.current = sqlText
  }, [sqlText])

  const executeMutation = useMutation({
    mutationFn: async (params: { sql: string; offset: number }) => {
      const validEntries = context.entries.filter((e) => e.snapshotId)
      const apiContext =
        validEntries.length > 0
          ? {
              snapshots: validEntries.map((e) => ({
                snapshotId: e.snapshotId,
                alias: e.alias,
              })),
            }
          : undefined

      const start = performance.now()
      const data = await api.post<QueryResultData>("/api/query", {
        sql: params.sql,
        limit: PAGE_SIZE,
        offset: params.offset,
        context: apiContext,
      })
      const duration = performance.now() - start
      return { data, duration }
    },
    onSuccess: ({ data, duration }) => {
      setResult(data)
      setExecutionTime(duration)
      setError(null)

      const entry: QueryHistoryEntry = {
        sql: sqlText,
        executedAt: new Date().toISOString(),
        rowCount: data.totalRows,
        durationMs: Math.round(duration),
      }
      addQueryHistory(entry)
      setHistory(getQueryHistory())
    },
    onError: (err: Error) => {
      setError(err.message)
      setResult(null)
    },
  })

  const handleExecute = useCallback(() => {
    const query = sqlTextRef.current
    if (!query.trim()) return
    setPage(0)
    executeMutation.mutate({ sql: query, offset: 0 })
  }, [executeMutation])

  // 페이지 레벨 키보드 단축키
  const handleExecuteRef = useRef(handleExecute)
  useEffect(() => {
    handleExecuteRef.current = handleExecute
  }, [handleExecute])

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      // Cmd+Enter / Ctrl+Enter: 쿼리 실행 (에디터 밖에서도 동작)
      if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
        // CodeMirror 에디터 내부에서는 CM6 keymap이 처리
        if ((e.target as Element)?.closest(".cm-editor")) return
        e.preventDefault()
        handleExecuteRef.current()
      }

      // Escape: 히스토리 패널 닫기
      if (e.key === "Escape") {
        setShowHistory(false)
      }
    }

    document.addEventListener("keydown", handleKeyDown)
    return () => document.removeEventListener("keydown", handleKeyDown)
  }, [])

  function handlePageChange(newPage: number) {
    setPage(newPage)
    executeMutation.mutate({ sql: sqlText, offset: newPage * PAGE_SIZE })
  }

  function handleHistorySelect(entry: QueryHistoryEntry) {
    setSqlText(entry.sql)
    setShowHistory(false)
  }

  function handleClearHistory() {
    clearQueryHistory()
    setHistory([])
  }

  return (
    <div className="flex h-[calc(100vh-3.5rem)] -m-6 flex-col">
      {/* Context Bar */}
      <SnapshotContextBar context={context} onContextChange={setContext} />

      {/* Editor area */}
      <div
        className="flex shrink-0 flex-col border-b"
        style={{ height: "40%" }}
      >
        <div className="flex items-center justify-between border-b px-4 py-2">
          <h1 className="text-lg font-semibold">SQL Query</h1>
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowHistory(!showHistory)}
            >
              <Clock className="mr-1 h-3 w-3" />
              History
            </Button>
            <Button
              onClick={handleExecute}
              disabled={executeMutation.isPending || !sqlText.trim()}
              size="sm"
              title="Execute query (⌘Enter)"
            >
              {executeMutation.isPending ? (
                <Loader2 className="mr-1 h-3 w-3 animate-spin" />
              ) : (
                <Play className="mr-1 h-3 w-3" />
              )}
              Execute
            </Button>
          </div>
        </div>
        <div className="flex-1 overflow-hidden">
          {showHistory ? (
            <div className="h-full overflow-auto p-4">
              <div className="mb-2 flex items-center justify-between">
                <h3 className="font-medium">Query History</h3>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleClearHistory}
                >
                  <Trash2 className="mr-1 h-3 w-3" />
                  Clear
                </Button>
              </div>
              {history.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  No queries yet.
                </p>
              ) : (
                <div className="space-y-2">
                  {history.map((entry, i) => (
                    <button
                      key={i}
                      className="w-full rounded-lg border p-3 text-left hover:bg-accent"
                      onClick={() => handleHistorySelect(entry)}
                    >
                      <code className="block truncate text-sm">
                        {entry.sql}
                      </code>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {new Date(entry.executedAt).toLocaleString()} &middot;{" "}
                        {entry.rowCount} rows &middot; {entry.durationMs}ms
                      </p>
                    </button>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <QueryEditor
              value={sqlText}
              onChange={setSqlText}
              onExecute={handleExecute}
              tables={editorTables}
            />
          )}
        </div>
      </div>

      {/* Results area */}
      <div className="flex-1 overflow-y-auto overflow-x-hidden p-6">
        {executeMutation.isPending ? (
          <div className="space-y-2">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-64 w-full" />
          </div>
        ) : error ? (
          <div className="rounded-xl border border-destructive/50 bg-destructive/10 p-4">
            <p className="font-medium text-destructive">Query Error</p>
            <p className="mt-1 text-sm text-destructive/80">{error}</p>
          </div>
        ) : result ? (
          <QueryResult
            columns={result.columns}
            rows={result.rows}
            totalRows={result.totalRows}
            page={page}
            pageSize={PAGE_SIZE}
            onPageChange={handlePageChange}
            executionTime={executionTime}
          />
        ) : (
          <div className="flex h-full items-center justify-center text-muted-foreground">
            <p>Execute a query to see results</p>
          </div>
        )}
      </div>
    </div>
  )
}
