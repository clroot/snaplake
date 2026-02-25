import { useState, useCallback } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { DataTable, type Column } from "@/components/common/DataTable"
import { CellDisplay } from "@/components/common/CellDisplay"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog"
import { exportToCsv, exportToJson } from "@/lib/export"
import { Filter, SquareTerminal } from "lucide-react"
import { useNavigate } from "@tanstack/react-router"

interface QueryResult {
  columns: Column[]
  rows: unknown[][]
  totalRows: number
}

interface TablePreviewProps {
  snapshotId: string
  tableName: string
}

const PAGE_SIZE = 100

function formatCellValue(value: unknown): string {
  if (value === null || value === undefined) return "NULL"
  if (typeof value === "object") {
    try {
      return JSON.stringify(value, null, 2)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

export function TablePreview({ snapshotId, tableName }: TablePreviewProps) {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [sortColumn, setSortColumn] = useState<string | undefined>()
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc")
  const [whereClause, setWhereClause] = useState("")
  const [appliedWhere, setAppliedWhere] = useState("")
  const [orderByClause, setOrderByClause] = useState("")
  const [appliedOrderBy, setAppliedOrderBy] = useState("")
  const [selectedRow, setSelectedRow] = useState<number | null>(null)
  const [selectedCell, setSelectedCell] = useState<{
    rowIndex: number
    colIndex: number
  } | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: [
      "table-preview",
      snapshotId,
      tableName,
      page,
      appliedWhere,
      appliedOrderBy,
    ],
    queryFn: () => {
      const params = new URLSearchParams({
        limit: String(PAGE_SIZE),
        offset: String(page * PAGE_SIZE),
      })
      if (appliedWhere) params.set("where", appliedWhere)
      if (appliedOrderBy) params.set("orderBy", appliedOrderBy)

      return api.get<QueryResult>(
        `/api/snapshots/${snapshotId}/tables/${tableName}/preview?${params}`,
      )
    },
  })

  const handleSort = useCallback(
    (column: string) => {
      if (sortColumn === column) {
        const newDir = sortDirection === "asc" ? "desc" : "asc"
        setSortDirection(newDir)
        setAppliedOrderBy(`${column} ${newDir}`)
      } else {
        setSortColumn(column)
        setSortDirection("asc")
        setAppliedOrderBy(`${column} asc`)
      }
      setPage(0)
    },
    [sortColumn, sortDirection],
  )

  function applyFilter() {
    setAppliedWhere(whereClause)
    setAppliedOrderBy(orderByClause)
    setPage(0)
  }

  function handleExport(format: "csv" | "json") {
    if (!data) return
    const filename = `${tableName}_${snapshotId}`
    if (format === "csv") {
      exportToCsv(data.columns, data.rows, filename)
    } else {
      exportToJson(data.columns, data.rows, filename)
    }
  }

  return (
    <div className="min-w-0 space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">{tableName}</h2>
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
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-4 rounded-xl border p-4">
        <div className="flex-1 space-y-2">
          <Label className="text-xs">WHERE</Label>
          <Input
            value={whereClause}
            onChange={(e) => setWhereClause(e.target.value)}
            placeholder="column = 'value' AND ..."
            className="font-mono text-sm"
          />
        </div>
        <div className="flex-1 space-y-2">
          <Label className="text-xs">ORDER BY</Label>
          <Input
            value={orderByClause}
            onChange={(e) => setOrderByClause(e.target.value)}
            placeholder="column ASC"
            className="font-mono text-sm"
          />
        </div>
        <Button onClick={applyFilter} size="sm">
          <Filter className="mr-1 h-3 w-3" />
          Apply
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      ) : data ? (
        <DataTable
          columns={data.columns}
          rows={data.rows}
          totalRows={data.totalRows}
          page={page}
          pageSize={PAGE_SIZE}
          onPageChange={setPage}
          sortColumn={sortColumn}
          sortDirection={sortDirection}
          onSort={handleSort}
          onExport={handleExport}
          onRowClick={setSelectedRow}
          onCellClick={(rowIndex, colIndex) =>
            setSelectedCell({ rowIndex, colIndex })
          }
        />
      ) : null}

      {/* Row Detail Dialog */}
      <Dialog
        open={selectedRow !== null}
        onOpenChange={(open) => !open && setSelectedRow(null)}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Row Detail</DialogTitle>
            <DialogDescription>
              Row #{selectedRow !== null ? page * PAGE_SIZE + selectedRow + 1 : ""}
            </DialogDescription>
          </DialogHeader>
          {selectedRow !== null && data && (
            <ScrollArea className="max-h-[60vh]">
              <div className="space-y-3 pr-4">
                {data.columns.map((col, i) => (
                  <div key={col.name} className="grid grid-cols-[140px_1fr] gap-2">
                    <span className="text-sm font-medium text-muted-foreground truncate" title={col.name}>
                      {col.name}
                    </span>
                    <div className="min-w-0 break-all text-sm">
                      <CellDisplay value={data.rows[selectedRow][i]} />
                    </div>
                  </div>
                ))}
              </div>
            </ScrollArea>
          )}
        </DialogContent>
      </Dialog>

      {/* Cell Detail Dialog */}
      <Dialog
        open={selectedCell !== null}
        onOpenChange={(open) => !open && setSelectedCell(null)}
      >
        <DialogContent className="max-w-lg">
          {selectedCell !== null && data && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {data.columns[selectedCell.colIndex].name}
                </DialogTitle>
                <DialogDescription>
                  {data.columns[selectedCell.colIndex].type}
                </DialogDescription>
              </DialogHeader>
              <ScrollArea className="max-h-[60vh]">
                <pre className="whitespace-pre-wrap break-all font-mono text-sm pr-4">
                  {formatCellValue(data.rows[selectedCell.rowIndex][selectedCell.colIndex])}
                </pre>
              </ScrollArea>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
