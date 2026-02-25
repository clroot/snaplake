import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Skeleton } from "@/components/ui/skeleton"
import { cn } from "@/lib/utils"

interface ColumnStat {
  column: string
  leftDistinctCount: number
  rightDistinctCount: number
  leftNullCount: number
  rightNullCount: number
}

interface StatsResult {
  leftRowCount: number
  rightRowCount: number
  columnStats: ColumnStat[]
}

interface CompareStatsProps {
  leftSnapshotId: string
  rightSnapshotId: string
  tableName: string
}

export function CompareStats({
  leftSnapshotId,
  rightSnapshotId,
  tableName,
}: CompareStatsProps) {
  const { data, isLoading } = useQuery({
    queryKey: ["compare-stats", leftSnapshotId, rightSnapshotId, tableName],
    queryFn: () =>
      api.post<StatsResult>("/api/compare/stats", {
        leftSnapshotId,
        rightSnapshotId,
        tableName,
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

  const rowDiff = data.rightRowCount - data.leftRowCount

  return (
    <div className="space-y-6">
      {/* Row count summary */}
      <div className="grid grid-cols-3 gap-4">
        <div className="rounded-xl border p-4 text-center">
          <p className="text-sm text-muted-foreground">Left</p>
          <p className="text-2xl font-bold">
            {data.leftRowCount.toLocaleString()}
          </p>
          <p className="text-xs text-muted-foreground">rows</p>
        </div>
        <div className="rounded-xl border p-4 text-center">
          <p className="text-sm text-muted-foreground">Right</p>
          <p className="text-2xl font-bold">
            {data.rightRowCount.toLocaleString()}
          </p>
          <p className="text-xs text-muted-foreground">rows</p>
        </div>
        <div className="rounded-xl border p-4 text-center">
          <p className="text-sm text-muted-foreground">Difference</p>
          <p
            className={cn(
              "text-2xl font-bold",
              rowDiff > 0
                ? "text-green-600"
                : rowDiff < 0
                  ? "text-destructive"
                  : "",
            )}
          >
            {rowDiff > 0 ? "+" : ""}
            {rowDiff.toLocaleString()}
          </p>
          <p className="text-xs text-muted-foreground">rows</p>
        </div>
      </div>

      {/* Column stats */}
      <div className="rounded-xl border overflow-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Column</TableHead>
              <TableHead className="text-right">Left Distinct</TableHead>
              <TableHead className="text-right">Right Distinct</TableHead>
              <TableHead className="text-right">Left Nulls</TableHead>
              <TableHead className="text-right">Right Nulls</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.columnStats.map((stat) => (
              <TableRow key={stat.column}>
                <TableCell className="font-medium">{stat.column}</TableCell>
                <TableCell className="text-right font-mono tabular-nums">
                  {stat.leftDistinctCount.toLocaleString()}
                </TableCell>
                <TableCell
                  className={cn(
                    "text-right font-mono tabular-nums",
                    stat.rightDistinctCount !== stat.leftDistinctCount &&
                      "font-bold text-amber-600",
                  )}
                >
                  {stat.rightDistinctCount.toLocaleString()}
                </TableCell>
                <TableCell className="text-right font-mono tabular-nums">
                  {stat.leftNullCount.toLocaleString()}
                </TableCell>
                <TableCell
                  className={cn(
                    "text-right font-mono tabular-nums",
                    stat.rightNullCount !== stat.leftNullCount &&
                      "font-bold text-amber-600",
                  )}
                >
                  {stat.rightNullCount.toLocaleString()}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
