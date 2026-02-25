import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import { CellDisplay } from "./CellDisplay"
import { ArrowDown, ArrowUp, ArrowUpDown, Download } from "lucide-react"

export interface Column {
  name: string
  type: string
}

interface DataTableProps {
  columns: Column[]
  rows: unknown[][]
  totalRows: number
  page: number
  pageSize: number
  onPageChange: (page: number) => void
  sortColumn?: string
  sortDirection?: "asc" | "desc"
  onSort?: (column: string) => void
  onExport?: (format: "csv" | "json") => void
  onRowClick?: (rowIndex: number) => void
  onCellClick?: (rowIndex: number, colIndex: number) => void
}

export function DataTable({
  columns,
  rows,
  totalRows,
  page,
  pageSize,
  onPageChange,
  sortColumn,
  sortDirection,
  onSort,
  onExport,
  onRowClick,
  onCellClick,
}: DataTableProps) {
  const totalPages = Math.max(1, Math.ceil(totalRows / pageSize))
  const startRow = page * pageSize + 1
  const endRow = Math.min((page + 1) * pageSize, totalRows)

  return (
    <div className="min-w-0 space-y-4">
      {/* Export buttons */}
      {onExport && (
        <div className="flex justify-end gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => onExport("csv")}
          >
            <Download className="mr-1 h-3 w-3" />
            CSV
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => onExport("json")}
          >
            <Download className="mr-1 h-3 w-3" />
            JSON
          </Button>
        </div>
      )}

      {/* Table */}
      <div className="rounded-xl border overflow-auto">
        <Table>
          <TableHeader>
            <TableRow>
              {columns.map((col) => (
                <TableHead
                  key={col.name}
                  className={onSort ? "cursor-pointer select-none" : ""}
                  onClick={() => onSort?.(col.name)}
                >
                  <div className="flex items-center gap-1">
                    <span>{col.name}</span>
                    <span className="text-xs text-muted-foreground">
                      {col.type}
                    </span>
                    {onSort &&
                      (sortColumn === col.name ? (
                        sortDirection === "asc" ? (
                          <ArrowUp className="h-3 w-3" />
                        ) : (
                          <ArrowDown className="h-3 w-3" />
                        )
                      ) : (
                        <ArrowUpDown className="h-3 w-3 text-muted-foreground/50" />
                      ))}
                  </div>
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="py-8 text-center text-muted-foreground"
                >
                  No data
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row, i) => (
                <TableRow
                  key={i}
                  className={onRowClick ? "cursor-pointer hover:bg-muted/50" : ""}
                  onClick={() => onRowClick?.(i)}
                >
                  {row.map((cell, j) => (
                    <TableCell
                      key={j}
                      className="max-w-xs truncate"
                      onClick={
                        onCellClick
                          ? (e) => {
                              e.stopPropagation()
                              onCellClick(i, j)
                            }
                          : undefined
                      }
                    >
                      <CellDisplay value={cell} />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {totalRows > 0
            ? `Showing ${startRow}-${endRow} of ${totalRows.toLocaleString()} rows`
            : "No rows"}
        </p>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
          >
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => onPageChange(page + 1)}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  )
}
