import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { DataTable, type Column } from "@/components/common/DataTable"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Skeleton } from "@/components/ui/skeleton"

interface QueryResult {
  columns: Column[]
  rows: unknown[][]
  totalRows: number
}

interface CompareDiffProps {
  leftSnapshotId: string
  rightSnapshotId: string
  tableName: string
}

const PAGE_SIZE = 100

export function CompareDiff({
  leftSnapshotId,
  rightSnapshotId,
  tableName,
}: CompareDiffProps) {
  const [primaryKeys, setPrimaryKeys] = useState<string[]>([])
  const [submittedPKs, setSubmittedPKs] = useState<string[]>([])
  const [page, setPage] = useState(0)

  // Fetch table schema to show column names for PK selection
  const { data: schema } = useQuery({
    queryKey: ["table-schema", leftSnapshotId, tableName],
    queryFn: () =>
      api.get<Column[]>(
        `/api/snapshots/${leftSnapshotId}/tables/${tableName}/schema`,
      ),
  })

  const { data: diffResult, isLoading: diffLoading } = useQuery({
    queryKey: [
      "compare-diff",
      leftSnapshotId,
      rightSnapshotId,
      tableName,
      submittedPKs,
      page,
    ],
    queryFn: () =>
      api.post<QueryResult>("/api/compare/diff", {
        leftSnapshotId,
        rightSnapshotId,
        tableName,
        primaryKeys: submittedPKs,
        limit: PAGE_SIZE,
        offset: page * PAGE_SIZE,
      }),
    enabled: submittedPKs.length > 0,
  })

  function togglePK(column: string) {
    setPrimaryKeys((prev) =>
      prev.includes(column)
        ? prev.filter((c) => c !== column)
        : [...prev, column],
    )
  }

  function handleCompare() {
    setSubmittedPKs([...primaryKeys])
    setPage(0)
  }

  return (
    <div className="space-y-6">
      {/* PK Selection */}
      <div className="rounded-xl border p-4 space-y-4">
        <div>
          <Label className="text-base font-semibold">
            Select Primary Key Columns
          </Label>
          <p className="text-sm text-muted-foreground">
            Choose columns that uniquely identify each row to compare changes.
          </p>
        </div>
        {schema ? (
          <div className="flex flex-wrap gap-4">
            {schema.map((col) => (
              <label
                key={col.name}
                className="flex cursor-pointer items-center gap-2"
              >
                <Checkbox
                  checked={primaryKeys.includes(col.name)}
                  onCheckedChange={() => togglePK(col.name)}
                />
                <span className="text-sm">
                  {col.name}{" "}
                  <span className="text-xs text-muted-foreground">
                    ({col.type})
                  </span>
                </span>
              </label>
            ))}
          </div>
        ) : (
          <Skeleton className="h-8 w-full" />
        )}
        <Button
          onClick={handleCompare}
          disabled={primaryKeys.length === 0}
          size="sm"
        >
          Compare Diff
        </Button>
      </div>

      {/* Diff Results */}
      {diffLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-8 w-full" />
          <Skeleton className="h-48 w-full" />
        </div>
      ) : diffResult ? (
        <DataTable
          columns={diffResult.columns}
          rows={diffResult.rows}
          totalRows={diffResult.totalRows}
          page={page}
          pageSize={PAGE_SIZE}
          onPageChange={setPage}
        />
      ) : submittedPKs.length > 0 ? null : (
        <p className="text-center text-muted-foreground">
          Select primary key columns and click Compare Diff.
        </p>
      )}
    </div>
  )
}
