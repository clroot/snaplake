import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { useNavigate, useParams } from "@tanstack/react-router"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  DatasourceForm,
  type DatasourceFormData,
} from "@/components/datasource/DatasourceForm"
import { useState } from "react"
import {
  ArrowLeft,
  Camera,
  Loader2,
  Pencil,
  Trash2,
  CheckCircle,
  AlertCircle,
  Clock,
} from "lucide-react"

interface DatasourceResponse {
  id: string
  name: string
  type: string
  host: string
  port: number
  database: string
  username: string
  schemas: string[]
  cronExpression: string | null
  retentionDaily: number
  retentionMonthly: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

interface SnapshotResponse {
  id: string
  datasourceId: string
  datasourceName: string
  snapshotType: string
  snapshotDate: string
  status: string
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
  tables: { schema: string; table: string; rowCount: number; sizeBytes: number }[]
}

export function DatasourceDetailPage() {
  const { id } = useParams({ from: "/authenticated/datasources/$id" })
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [showEdit, setShowEdit] = useState(false)

  const { data: ds, isLoading } = useQuery({
    queryKey: ["datasources", id],
    queryFn: () => api.get<DatasourceResponse>(`/api/datasources/${id}`),
  })

  const { data: snapshots } = useQuery({
    queryKey: ["snapshots", { datasourceId: id }],
    queryFn: () =>
      api.get<SnapshotResponse[]>(`/api/snapshots?datasourceId=${id}`),
  })

  const updateMutation = useMutation({
    mutationFn: (data: DatasourceFormData) =>
      api.put(`/api/datasources/${id}`, {
        name: data.name,
        type: data.type,
        host: data.host,
        port: parseInt(data.port, 10),
        database: data.database,
        username: data.username,
        password: data.password || undefined,
        schemas: data.schemas
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean),
        cronExpression: data.cronExpression,
        retentionDaily: data.retentionDaily,
        retentionMonthly: data.retentionMonthly,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["datasources"] })
      setShowEdit(false)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/api/datasources/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["datasources"] })
      navigate({ to: "/datasources" })
    },
  })

  const snapshotMutation = useMutation({
    mutationFn: () =>
      api.post<SnapshotResponse>(`/api/datasources/${id}/snapshot`),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["snapshots", { datasourceId: id }],
      })
    },
  })

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64" />
      </div>
    )
  }

  if (!ds) {
    return <p className="text-muted-foreground">Datasource not found.</p>
  }

  const recentSnapshots = snapshots
    ?.sort(
      (a, b) =>
        new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime(),
    )
    .slice(0, 10)

  return (
    <div className="space-y-8">
      <div className="flex items-center gap-4">
        <Button
          variant="ghost"
          size="icon"
          onClick={() => navigate({ to: "/datasources" })}
        >
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold tracking-tight">{ds.name}</h1>
          <p className="text-muted-foreground">
            {ds.type} &middot; {ds.host}:{ds.port}/{ds.database}
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={() =>
              snapshotMutation.mutate()
            }
            disabled={snapshotMutation.isPending}
          >
            {snapshotMutation.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Camera className="mr-2 h-4 w-4" />
            )}
            Take Snapshot
          </Button>
          <Button variant="outline" onClick={() => setShowEdit(true)}>
            <Pencil className="mr-2 h-4 w-4" />
            Edit
          </Button>
          <Button
            variant="destructive"
            onClick={() => {
              if (confirm("Delete this datasource? This cannot be undone.")) {
                deleteMutation.mutate()
              }
            }}
            disabled={deleteMutation.isPending}
          >
            <Trash2 className="mr-2 h-4 w-4" />
            Delete
          </Button>
        </div>
      </div>

      {/* Info cards */}
      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Status</CardTitle>
          </CardHeader>
          <CardContent>
            <Badge variant={ds.enabled ? "default" : "secondary"}>
              {ds.enabled ? "Active" : "Disabled"}
            </Badge>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Schedule</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm">
              {ds.cronExpression ?? "Manual only"}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">Schemas</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm">{ds.schemas.join(", ")}</p>
          </CardContent>
        </Card>
      </div>

      {/* Mutation error display */}
      {(deleteMutation.isError || snapshotMutation.isError) && (
        <div className="flex items-center gap-2 rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {(deleteMutation.error ?? snapshotMutation.error) instanceof Error
            ? (deleteMutation.error ?? snapshotMutation.error)?.message
            : "An error occurred"}
        </div>
      )}

      {/* Snapshots */}
      <Card>
        <CardHeader>
          <CardTitle>Snapshots</CardTitle>
          <CardDescription>Recent snapshot operations</CardDescription>
        </CardHeader>
        <CardContent>
          {!recentSnapshots?.length ? (
            <p className="py-8 text-center text-muted-foreground">
              No snapshots yet.
            </p>
          ) : (
            <div className="space-y-2">
              {recentSnapshots.map((snapshot) => (
                <div
                  key={snapshot.id}
                  className="flex items-center justify-between rounded-lg border p-3"
                >
                  <div className="flex items-center gap-3">
                    {snapshot.status === "COMPLETED" ? (
                      <CheckCircle className="h-4 w-4 text-green-600" />
                    ) : snapshot.status === "FAILED" ? (
                      <AlertCircle className="h-4 w-4 text-destructive" />
                    ) : (
                      <Clock className="h-4 w-4 text-muted-foreground" />
                    )}
                    <div>
                      <p className="font-medium">
                        {snapshot.snapshotType} &middot;{" "}
                        {snapshot.snapshotDate}
                      </p>
                      <p className="text-sm text-muted-foreground">
                        {snapshot.tables.length} tables &middot;{" "}
                        {new Date(snapshot.startedAt).toLocaleString()}
                      </p>
                      {snapshot.status === "FAILED" && snapshot.errorMessage && (
                        <p className="text-xs text-destructive">{snapshot.errorMessage}</p>
                      )}
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

      <Dialog open={showEdit} onOpenChange={setShowEdit}>
        <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Edit Datasource</DialogTitle>
          </DialogHeader>
          {updateMutation.isError && (
            <div className="flex items-center gap-2 rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {updateMutation.error instanceof Error
                ? updateMutation.error.message
                : "Failed to update datasource"}
            </div>
          )}
          <DatasourceForm
            datasourceId={id}
            initialData={{
              name: ds.name,
              type: ds.type,
              host: ds.host,
              port: String(ds.port),
              database: ds.database,
              username: ds.username,
              password: "",
              schemas: ds.schemas.join(", "),
              cronExpression: ds.cronExpression,
              retentionDaily: ds.retentionDaily,
              retentionMonthly: ds.retentionMonthly,
            }}
            onSubmit={async (data) => {
              await updateMutation.mutateAsync(data)
            }}
            submitLabel="Save Changes"
            isSubmitting={updateMutation.isPending}
          />
        </DialogContent>
      </Dialog>
    </div>
  )
}
