import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useNavigate } from "@tanstack/react-router"
import {
  Database,
  Layers,
  Clock,
  AlertCircle,
  CheckCircle,
  Plus,
} from "lucide-react"

interface DatasourceResponse {
  id: string
  name: string
  type: string
  cronExpression: string | null
  enabled: boolean
}

interface SnapshotResponse {
  id: string
  datasourceId: string
  datasourceName: string
  snapshotType: string
  snapshotDate: string
  status: string
  startedAt: string
  completedAt: string | null
  tables: { schema: string; table: string; rowCount: number; sizeBytes: number }[]
}

export function DashboardPage() {
  const navigate = useNavigate()

  const { data: datasources, isLoading: dsLoading } = useQuery({
    queryKey: ["datasources"],
    queryFn: () => api.get<DatasourceResponse[]>("/api/datasources"),
  })

  const { data: snapshots, isLoading: snapLoading } = useQuery({
    queryKey: ["snapshots"],
    queryFn: () => api.get<SnapshotResponse[]>("/api/snapshots"),
  })

  const recentSnapshots = snapshots
    ?.sort(
      (a, b) =>
        new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime(),
    )
    .slice(0, 10)

  const activeDatasources = datasources?.filter((d) => d.enabled).length ?? 0
  const scheduledDatasources =
    datasources?.filter((d) => d.cronExpression).length ?? 0
  const completedSnapshots =
    snapshots?.filter((s) => s.status === "COMPLETED").length ?? 0
  const failedSnapshots =
    snapshots?.filter((s) => s.status === "FAILED").length ?? 0

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground">
            Overview of your database snapshots
          </p>
        </div>
        <Button onClick={() => navigate({ to: "/datasources" })}>
          <Plus className="mr-2 h-4 w-4" />
          Add Datasource
        </Button>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <SummaryCard
          title="Datasources"
          value={dsLoading ? null : String(activeDatasources)}
          description={`${scheduledDatasources} scheduled`}
          icon={<Database className="h-4 w-4 text-muted-foreground" />}
          onClick={() => navigate({ to: "/datasources" })}
        />
        <SummaryCard
          title="Total Snapshots"
          value={snapLoading ? null : String(snapshots?.length ?? 0)}
          description={`${completedSnapshots} completed`}
          icon={<Layers className="h-4 w-4 text-muted-foreground" />}
          onClick={() => navigate({ to: "/snapshots" })}
        />
        <SummaryCard
          title="Scheduled"
          value={dsLoading ? null : String(scheduledDatasources)}
          description="Active schedules"
          icon={<Clock className="h-4 w-4 text-muted-foreground" />}
          onClick={() => navigate({ to: "/datasources" })}
        />
        <SummaryCard
          title="Failed"
          value={snapLoading ? null : String(failedSnapshots)}
          description="Snapshots with errors"
          icon={<AlertCircle className="h-4 w-4 text-muted-foreground" />}
          onClick={() => navigate({ to: "/snapshots" })}
        />
      </div>

      {/* Recent Snapshots */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Snapshots</CardTitle>
          <CardDescription>Last 10 snapshot operations</CardDescription>
        </CardHeader>
        <CardContent>
          {snapLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : !recentSnapshots?.length ? (
            <p className="py-8 text-center text-muted-foreground">
              No snapshots yet. Trigger one from a datasource.
            </p>
          ) : (
            <div className="space-y-2">
              {recentSnapshots.map((snapshot) => (
                <div
                  key={snapshot.id}
                  className="flex cursor-pointer items-center justify-between rounded-lg border p-3 hover:bg-accent"
                  onClick={() =>
                    navigate({
                      to: "/snapshots/$snapshotId",
                      params: { snapshotId: snapshot.id },
                    })
                  }
                >
                  <div className="flex items-center gap-3">
                    <StatusIcon status={snapshot.status} />
                    <div>
                      <p className="font-medium">{snapshot.datasourceName}</p>
                      <p className="text-sm text-muted-foreground">
                        {snapshot.snapshotType} &middot;{" "}
                        {snapshot.snapshotDate} &middot;{" "}
                        {snapshot.tables.length} tables
                      </p>
                    </div>
                  </div>
                  <Badge
                    variant={
                      snapshot.status === "COMPLETED"
                        ? "default"
                        : snapshot.status === "FAILED"
                          ? "destructive"
                          : "secondary"
                    }
                  >
                    {snapshot.status}
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Datasource Status */}
      <Card>
        <CardHeader>
          <CardTitle>Datasources</CardTitle>
          <CardDescription>Connection status overview</CardDescription>
        </CardHeader>
        <CardContent>
          {dsLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : !datasources?.length ? (
            <p className="py-8 text-center text-muted-foreground">
              No datasources registered yet.
            </p>
          ) : (
            <div className="space-y-2">
              {datasources.map((ds) => (
                <div
                  key={ds.id}
                  className="flex cursor-pointer items-center justify-between rounded-lg border p-3 hover:bg-accent"
                  onClick={() =>
                    navigate({ to: "/datasources/$id", params: { id: ds.id } })
                  }
                >
                  <div className="flex items-center gap-3">
                    <Database className="h-4 w-4 text-muted-foreground" />
                    <div>
                      <p className="font-medium">{ds.name}</p>
                      <p className="text-sm text-muted-foreground">
                        {ds.type}
                        {ds.cronExpression && ` \u00b7 ${ds.cronExpression}`}
                      </p>
                    </div>
                  </div>
                  <Badge variant={ds.enabled ? "default" : "secondary"}>
                    {ds.enabled ? "Active" : "Disabled"}
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function SummaryCard({
  title,
  value,
  description,
  icon,
  onClick,
}: {
  title: string
  value: string | null
  description: string
  icon: React.ReactNode
  onClick?: () => void
}) {
  return (
    <Card
      className={onClick ? "cursor-pointer hover:bg-accent/50 transition-colors" : undefined}
      onClick={onClick}
    >
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
        {icon}
      </CardHeader>
      <CardContent>
        {value === null ? (
          <Skeleton className="h-8 w-16" />
        ) : (
          <div className="text-2xl font-bold">{value}</div>
        )}
        <p className="text-xs text-muted-foreground">{description}</p>
      </CardContent>
    </Card>
  )
}

function StatusIcon({ status }: { status: string }) {
  if (status === "COMPLETED") {
    return <CheckCircle className="h-4 w-4 text-green-600" />
  }
  if (status === "FAILED") {
    return <AlertCircle className="h-4 w-4 text-destructive" />
  }
  return <Clock className="h-4 w-4 text-muted-foreground" />
}
