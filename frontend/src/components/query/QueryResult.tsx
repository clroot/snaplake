import { useState } from "react"
import { DataTable, type Column } from "@/components/common/DataTable"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog"
import { ScrollArea } from "@/components/ui/scroll-area"
import { exportToCsv, exportToJson } from "@/lib/export"

interface QueryResultProps {
  columns: Column[]
  rows: unknown[][]
  totalRows: number
  page: number
  pageSize: number
  onPageChange: (page: number) => void
  executionTime?: number
}

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

export function QueryResult({
  columns,
  rows,
  totalRows,
  page,
  pageSize,
  onPageChange,
  executionTime,
}: QueryResultProps) {
  const [selectedCell, setSelectedCell] = useState<{
    rowIndex: number
    colIndex: number
  } | null>(null)

  function handleExport(format: "csv" | "json") {
    const filename = `query_result_${Date.now()}`
    if (format === "csv") {
      exportToCsv(columns, rows, filename)
    } else {
      exportToJson(columns, rows, filename)
    }
  }

  return (
    <div className="space-y-2">
      {executionTime !== undefined && (
        <p className="text-sm text-muted-foreground">
          Executed in {executionTime.toFixed(0)}ms &middot;{" "}
          {totalRows.toLocaleString()} rows
        </p>
      )}
      <DataTable
        columns={columns}
        rows={rows}
        totalRows={totalRows}
        page={page}
        pageSize={pageSize}
        onPageChange={onPageChange}
        onExport={handleExport}
        onCellClick={(rowIndex, colIndex) =>
          setSelectedCell({ rowIndex, colIndex })
        }
      />

      <Dialog
        open={selectedCell !== null}
        onOpenChange={(open) => !open && setSelectedCell(null)}
      >
        <DialogContent className="max-w-lg">
          {selectedCell !== null && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {columns[selectedCell.colIndex].name}
                </DialogTitle>
                <DialogDescription>
                  {columns[selectedCell.colIndex].type}
                </DialogDescription>
              </DialogHeader>
              <ScrollArea className="max-h-[60vh]">
                <pre className="whitespace-pre-wrap break-all font-mono text-sm pr-4">
                  {formatCellValue(
                    rows[selectedCell.rowIndex][selectedCell.colIndex],
                  )}
                </pre>
              </ScrollArea>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
