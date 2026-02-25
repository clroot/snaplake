import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { DataTable, type Column } from "@/components/common/DataTable"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Plus, Minus } from "lucide-react"

interface QueryResult {
  columns: Column[]
  rows: unknown[][]
  totalRows: number
}

interface RowsCompareResult {
  added: QueryResult
  removed: QueryResult
}

interface CompareRowsProps {
  leftSnapshotId: string
  rightSnapshotId: string
  tableName: string
}

const PAGE_SIZE = 100

export function CompareRows({
  leftSnapshotId,
  rightSnapshotId,
  tableName,
}: CompareRowsProps) {
  const [activeTab, setActiveTab] = useState("added")
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: [
      "compare-rows",
      leftSnapshotId,
      rightSnapshotId,
      tableName,
      page,
    ],
    queryFn: () =>
      api.post<RowsCompareResult>("/api/compare/rows", {
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

  if (!data) return null

  return (
    <Tabs
      value={activeTab}
      onValueChange={(v) => {
        setActiveTab(v)
        setPage(0)
      }}
      className="space-y-4"
    >
      <TabsList>
        <TabsTrigger value="added" className="gap-1">
          <Plus className="h-3 w-3" />
          Added ({data.added.totalRows.toLocaleString()})
        </TabsTrigger>
        <TabsTrigger value="removed" className="gap-1">
          <Minus className="h-3 w-3" />
          Removed ({data.removed.totalRows.toLocaleString()})
        </TabsTrigger>
      </TabsList>

      <TabsContent value="added">
        <DataTable
          columns={data.added.columns}
          rows={data.added.rows}
          totalRows={data.added.totalRows}
          page={page}
          pageSize={PAGE_SIZE}
          onPageChange={setPage}
        />
      </TabsContent>

      <TabsContent value="removed">
        <DataTable
          columns={data.removed.columns}
          rows={data.removed.rows}
          totalRows={data.removed.totalRows}
          page={page}
          pageSize={PAGE_SIZE}
          onPageChange={setPage}
        />
      </TabsContent>
    </Tabs>
  )
}
