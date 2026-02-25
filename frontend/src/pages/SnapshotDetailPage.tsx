import { useNavigate, useParams } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { SnapshotLayout } from "@/components/snapshot/SnapshotLayout"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, Database, Calendar, Clock } from "lucide-react"

interface SnapshotResponse {
  id: string
  datasourceId: string
  datasourceName: string
  snapshotType: string
  snapshotDate: string
  status: string
  startedAt: string
  completedAt: string | null
  tables: {
    schema: string
    table: string
    rowCount: number
    sizeBytes: number
  }[]
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B"
  const k = 1024
  const sizes = ["B", "KB", "MB", "GB"]
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

export function SnapshotDetailPage() {
  const { snapshotId } = useParams({
    from: "/authenticated/snapshots/$snapshotId",
  })
  const navigate = useNavigate()

  const { data: snapshot, isLoading } = useQuery({
    queryKey: ["snapshots", snapshotId],
    queryFn: () => api.get<SnapshotResponse>(`/api/snapshots/${snapshotId}`),
  })

  function handleSelectSnapshot(sid: string) {
    navigate({
      to: "/snapshots/$snapshotId",
      params: { snapshotId: sid },
    })
  }

  function handleSelectTable(sid: string, tableName: string) {
    const dotIndex = tableName.indexOf(".")
    const schema = tableName.substring(0, dotIndex)
    const table = tableName.substring(dotIndex + 1)
    navigate({
      to: "/snapshots/$snapshotId/$schema/$table",
      params: { snapshotId: sid, schema, table },
    })
  }

  return (
    <SnapshotLayout
      onSelectTable={handleSelectTable}
      onSelectSnapshot={handleSelectSnapshot}
      selectedSnapshotId={snapshotId}
    >
      {isLoading ? (
        <div className="space-y-4">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      ) : snapshot ? (
        <div className="space-y-6">
          <div>
            <h2 className="text-lg font-semibold">{snapshot.datasourceName}</h2>
            <p className="text-sm text-muted-foreground">
              {snapshot.snapshotDate} &middot;{" "}
              {snapshot.snapshotType.toLowerCase()}
            </p>
          </div>

          <div className="flex flex-wrap gap-4">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Database className="h-4 w-4" />
              <span>{snapshot.datasourceName}</span>
            </div>
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Calendar className="h-4 w-4" />
              <span>{snapshot.snapshotDate}</span>
            </div>
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Clock className="h-4 w-4" />
              <span>{snapshot.snapshotType.toLowerCase()}</span>
            </div>
            <Badge variant="secondary">{snapshot.status}</Badge>
          </div>

          <div className="space-y-3">
            <h3 className="text-sm font-medium text-muted-foreground">
              Tables ({snapshot.tables.length})
            </h3>
            <div className="grid gap-2">
              {snapshot.tables.map((t) => (
                <Card
                  key={`${t.schema}.${t.table}`}
                  className="cursor-pointer transition-colors hover:bg-accent"
                  onClick={() =>
                    navigate({
                      to: "/snapshots/$snapshotId/$schema/$table",
                      params: {
                        snapshotId: snapshot.id,
                        schema: t.schema,
                        table: t.table,
                      },
                    })
                  }
                >
                  <CardContent className="flex items-center justify-between p-4">
                    <div className="flex items-center gap-3">
                      <Table className="h-4 w-4 text-muted-foreground" />
                      <div>
                        <p className="text-sm font-medium">{t.table}</p>
                        <p className="text-xs text-muted-foreground">
                          {t.schema}
                        </p>
                      </div>
                    </div>
                    <div className="flex gap-4 text-xs text-muted-foreground">
                      <span>{t.rowCount.toLocaleString()} rows</span>
                      <span>{formatBytes(t.sizeBytes)}</span>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </div>
        </div>
      ) : null}
    </SnapshotLayout>
  )
}
