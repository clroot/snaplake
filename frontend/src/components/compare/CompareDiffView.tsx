import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { CellDisplay } from "@/components/common/CellDisplay"
import { AlertCircle, Columns2, Rows3, Info } from "lucide-react"
import { cn } from "@/lib/utils"

interface DiffColumn {
  name: string
  type: string
}

interface DiffSummary {
  added: number
  removed: number
  changed: number
}

type DiffRow =
  | { diffType: "ADDED"; values: unknown[] }
  | { diffType: "REMOVED"; values: unknown[] }
  | {
      diffType: "CHANGED"
      left: unknown[]
      right: unknown[]
      changedColumns: number[]
    }

interface UnifiedDiffResponse {
  columns: DiffColumn[]
  primaryKeys: string[]
  rows: DiffRow[]
  totalRows: number
  summary: DiffSummary
}

interface CompareDiffViewProps {
  leftSnapshotId: string
  rightSnapshotId: string
  tableName: string
}

const PAGE_SIZE = 100

export function CompareDiffView({
  leftSnapshotId,
  rightSnapshotId,
  tableName,
}: CompareDiffViewProps) {
  const [viewMode, setViewMode] = useState<"unified" | "split">("unified")
  const [page, setPage] = useState(0)

  useEffect(() => {
    setPage(0)
  }, [leftSnapshotId, rightSnapshotId, tableName])

  const { data, isLoading, isError } = useQuery({
    queryKey: [
      "compare-unified-diff",
      leftSnapshotId,
      rightSnapshotId,
      tableName,
      page,
    ],
    queryFn: () =>
      api.post<UnifiedDiffResponse>("/api/compare/unified-diff", {
        leftSnapshotId,
        rightSnapshotId,
        tableName,
        limit: PAGE_SIZE,
        offset: page * PAGE_SIZE,
      }),
  })

  if (isLoading) {
    return (
      <div className="space-y-2">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center gap-2 py-12 text-destructive">
        <AlertCircle className="h-8 w-8" />
        <p className="text-sm">Failed to load diff data</p>
      </div>
    )
  }

  if (!data) return null

  const totalPages = Math.max(1, Math.ceil(data.totalRows / PAGE_SIZE))
  const startRow = page * PAGE_SIZE + 1
  const endRow = Math.min((page + 1) * PAGE_SIZE, data.totalRows)

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-sm">
            <span className="font-medium text-green-700">
              +{data.summary.added}
            </span>
            <span className="font-medium text-red-700">
              -{data.summary.removed}
            </span>
            {data.summary.changed > 0 && (
              <span className="font-medium text-amber-700">
                ~{data.summary.changed}
              </span>
            )}
          </div>
          {data.primaryKeys.length === 0 && (
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Info className="h-3 w-3" />
              <span>No PK — changed rows cannot be detected</span>
            </div>
          )}
        </div>
        <div className="flex gap-1 rounded-lg border p-0.5">
          <Button
            variant={viewMode === "unified" ? "secondary" : "ghost"}
            size="sm"
            className="h-7 gap-1.5 px-2.5"
            onClick={() => setViewMode("unified")}
          >
            <Rows3 className="h-3.5 w-3.5" />
            Unified
          </Button>
          <Button
            variant={viewMode === "split" ? "secondary" : "ghost"}
            size="sm"
            className="h-7 gap-1.5 px-2.5"
            onClick={() => setViewMode("split")}
          >
            <Columns2 className="h-3.5 w-3.5" />
            Split
          </Button>
        </div>
      </div>

      {/* Diff Table */}
      {data.rows.length === 0 ? (
        <div className="py-12 text-center text-muted-foreground">
          No differences found
        </div>
      ) : viewMode === "unified" ? (
        <UnifiedView columns={data.columns} rows={data.rows} />
      ) : (
        <SplitView columns={data.columns} rows={data.rows} />
      )}

      {/* Pagination */}
      {data.totalRows > 0 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Showing {startRow}-{endRow} of {data.totalRows.toLocaleString()}{" "}
            changes
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage(page + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

function UnifiedView({
  columns,
  rows,
}: {
  columns: DiffColumn[]
  rows: DiffRow[]
}) {
  return (
    <div className="overflow-auto rounded-xl border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-8" />
            {columns.map((col) => (
              <TableHead key={col.name}>
                <div className="flex items-center gap-1">
                  <span>{col.name}</span>
                  <span className="text-xs text-muted-foreground">
                    {col.type}
                  </span>
                </div>
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.flatMap((row, i) => {
            if (row.diffType === "ADDED") {
              return [
                <TableRow key={i} className="border-l-4 border-l-green-500 bg-green-50">
                  <TableCell className="w-8 text-center font-mono text-green-700">
                    +
                  </TableCell>
                  {row.values.map((cell, j) => (
                    <TableCell key={j} className="max-w-xs truncate">
                      <CellDisplay value={cell} />
                    </TableCell>
                  ))}
                </TableRow>,
              ]
            }
            if (row.diffType === "REMOVED") {
              return [
                <TableRow key={i} className="border-l-4 border-l-red-500 bg-red-50">
                  <TableCell className="w-8 text-center font-mono text-red-700">
                    -
                  </TableCell>
                  {row.values.map((cell, j) => (
                    <TableCell key={j} className="max-w-xs truncate">
                      <CellDisplay value={cell} />
                    </TableCell>
                  ))}
                </TableRow>,
              ]
            }
            // CHANGED: two rows
            const changedSet = new Set(row.changedColumns)
            return [
              <TableRow key={`${i}-left`} className="border-l-4 border-l-red-500 bg-red-50">
                <TableCell className="w-8 text-center font-mono text-red-700">
                  -
                </TableCell>
                {row.left.map((cell, j) => (
                  <TableCell
                    key={j}
                    className={cn(
                      "max-w-xs truncate",
                      changedSet.has(j) && "bg-red-100 font-medium",
                    )}
                  >
                    <CellDisplay value={cell} />
                  </TableCell>
                ))}
              </TableRow>,
              <TableRow key={`${i}-right`} className="border-l-4 border-l-green-500 bg-green-50">
                <TableCell className="w-8 text-center font-mono text-green-700">
                  +
                </TableCell>
                {row.right.map((cell, j) => (
                  <TableCell
                    key={j}
                    className={cn(
                      "max-w-xs truncate",
                      changedSet.has(j) && "bg-green-100 font-medium",
                    )}
                  >
                    <CellDisplay value={cell} />
                  </TableCell>
                ))}
              </TableRow>,
            ]
          })}
        </TableBody>
      </Table>
    </div>
  )
}

function SplitView({
  columns,
  rows,
}: {
  columns: DiffColumn[]
  rows: DiffRow[]
}) {
  const leftRows: { values: unknown[]; type: "removed" | "changed"; changedCols?: Set<number> }[] = []
  const rightRows: { values: unknown[]; type: "added" | "changed"; changedCols?: Set<number> }[] = []

  for (const row of rows) {
    if (row.diffType === "ADDED") {
      leftRows.push({ values: columns.map(() => null), type: "removed" })
      rightRows.push({ values: row.values, type: "added" })
    } else if (row.diffType === "REMOVED") {
      leftRows.push({ values: row.values, type: "removed" })
      rightRows.push({ values: columns.map(() => null), type: "added" })
    } else {
      const changedCols = new Set(row.changedColumns)
      leftRows.push({ values: row.left, type: "changed", changedCols })
      rightRows.push({ values: row.right, type: "changed", changedCols })
    }
  }

  const header = (
    <TableRow>
      {columns.map((col) => (
        <TableHead key={col.name}>
          <div className="flex items-center gap-1">
            <span>{col.name}</span>
            <span className="text-xs text-muted-foreground">{col.type}</span>
          </div>
        </TableHead>
      ))}
    </TableRow>
  )

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      {/* Left (old) */}
      <div className="overflow-auto rounded-xl border">
        <Table>
          <TableHeader>{header}</TableHeader>
          <TableBody>
            {leftRows.map((row, i) => {
              const isBlank = row.type === "removed" && row.values.every((v) => v === null)
              const isChanged = row.type === "changed"
              return (
                <TableRow
                  key={i}
                  className={cn(
                    isBlank && "bg-muted/30",
                    !isBlank && !isChanged && "border-l-4 border-l-red-500 bg-red-50",
                    isChanged && "border-l-4 border-l-amber-500 bg-red-50",
                  )}
                >
                  {row.values.map((cell, j) => (
                    <TableCell
                      key={j}
                      className={cn(
                        "max-w-xs truncate",
                        isChanged && row.changedCols?.has(j) && "bg-red-100 font-medium",
                      )}
                    >
                      {!isBlank && <CellDisplay value={cell} />}
                    </TableCell>
                  ))}
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      {/* Right (new) */}
      <div className="overflow-auto rounded-xl border">
        <Table>
          <TableHeader>{header}</TableHeader>
          <TableBody>
            {rightRows.map((row, i) => {
              const isBlank = row.type === "added" && row.values.every((v) => v === null)
              const isChanged = row.type === "changed"
              return (
                <TableRow
                  key={i}
                  className={cn(
                    isBlank && "bg-muted/30",
                    !isBlank && !isChanged && "border-l-4 border-l-green-500 bg-green-50",
                    isChanged && "border-l-4 border-l-amber-500 bg-green-50",
                  )}
                >
                  {row.values.map((cell, j) => (
                    <TableCell
                      key={j}
                      className={cn(
                        "max-w-xs truncate",
                        isChanged && row.changedCols?.has(j) && "bg-green-100 font-medium",
                      )}
                    >
                      {!isBlank && <CellDisplay value={cell} />}
                    </TableCell>
                  ))}
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
