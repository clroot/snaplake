import { DataTable, type Column } from "@/components/common/DataTable"
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

export function QueryResult({
  columns,
  rows,
  totalRows,
  page,
  pageSize,
  onPageChange,
  executionTime,
}: QueryResultProps) {
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
      />
    </div>
  )
}
