import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "@tanstack/react-router"
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
import { AlertCircle, Database, Plus } from "lucide-react"
import cronstrue from "cronstrue"

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

export function DatasourcesPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)

  const { data: datasources, isLoading } = useQuery({
    queryKey: ["datasources"],
    queryFn: () => api.get<DatasourceResponse[]>("/api/datasources"),
  })

  const createMutation = useMutation({
    mutationFn: (data: DatasourceFormData) =>
      api.post("/api/datasources", {
        name: data.name,
        type: data.type,
        host: data.host,
        port: parseInt(data.port, 10),
        database: data.database,
        username: data.username,
        password: data.password,
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
      setShowCreate(false)
    },
  })

  function getCronLabel(cron: string | null) {
    if (!cron) return "Manual only"
    try {
      return cronstrue.toString(cron)
    } catch {
      return cron
    }
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Datasources</h1>
          <p className="text-muted-foreground">
            Manage database connections and snapshot schedules
          </p>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Add Datasource
        </Button>
      </div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-40" />
          ))}
        </div>
      ) : !datasources?.length ? (
        <Card>
          <CardContent className="flex flex-col items-center py-16">
            <Database className="mb-4 h-12 w-12 text-muted-foreground" />
            <p className="text-lg font-medium">No datasources yet</p>
            <p className="mb-6 text-muted-foreground">
              Add your first database connection to start taking snapshots.
            </p>
            <Button onClick={() => setShowCreate(true)}>
              <Plus className="mr-2 h-4 w-4" />
              Add Datasource
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {datasources.map((ds) => (
            <Card
              key={ds.id}
              className="cursor-pointer transition-colors hover:bg-accent/50"
              onClick={() =>
                navigate({ to: "/datasources/$id", params: { id: ds.id } })
              }
            >
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between">
                  <CardTitle className="text-base">{ds.name}</CardTitle>
                  <Badge variant={ds.enabled ? "default" : "secondary"}>
                    {ds.enabled ? "Active" : "Disabled"}
                  </Badge>
                </div>
                <CardDescription>
                  {ds.type} &middot; {ds.host}:{ds.port}/{ds.database}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-2">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <span>Schedule:</span>
                  <span>{getCronLabel(ds.cronExpression)}</span>
                </div>
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <span>Schemas:</span>
                  <span>{ds.schemas.join(", ")}</span>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={showCreate} onOpenChange={setShowCreate}>
        <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Add Datasource</DialogTitle>
          </DialogHeader>
          {createMutation.isError && (
            <div className="flex items-center gap-2 rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {createMutation.error instanceof Error
                ? createMutation.error.message
                : "Failed to create datasource"}
            </div>
          )}
          <DatasourceForm
            onSubmit={async (data) => {
              await createMutation.mutateAsync(data)
            }}
            submitLabel="Create Datasource"
            isSubmitting={createMutation.isPending}
          />
        </DialogContent>
      </Dialog>
    </div>
  )
}
