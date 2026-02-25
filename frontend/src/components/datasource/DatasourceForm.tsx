import { useState, useCallback } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { ScheduleInput } from "./ScheduleInput"
import { api } from "@/lib/api"
import { CheckCircle, Loader2, XCircle } from "lucide-react"

export interface DatasourceFormData {
  name: string
  type: string
  host: string
  port: string
  database: string
  username: string
  password: string
  schemas: string
  cronExpression: string | null
  retentionDaily: number
  retentionMonthly: number
}

interface DatasourceFormProps {
  initialData?: DatasourceFormData
  datasourceId?: string
  onSubmit: (data: DatasourceFormData) => Promise<void>
  submitLabel: string
  isSubmitting: boolean
}

const DEFAULT_DATA: DatasourceFormData = {
  name: "",
  type: "POSTGRESQL",
  host: "",
  port: "5432",
  database: "",
  username: "",
  password: "",
  schemas: "public",
  cronExpression: null,
  retentionDaily: 30,
  retentionMonthly: 12,
}

export function DatasourceForm({
  initialData,
  datasourceId,
  onSubmit,
  submitLabel,
  isSubmitting,
}: DatasourceFormProps) {
  const [data, setData] = useState<DatasourceFormData>(
    initialData ?? DEFAULT_DATA,
  )
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [testStatus, setTestStatus] = useState<
    "idle" | "testing" | "success" | "error"
  >("idle")
  const [testMessage, setTestMessage] = useState("")

  const handleScheduleChange = useCallback((cron: string | null) => {
    setData((prev) => ({ ...prev, cronExpression: cron }))
  }, [])

  function validate(): boolean {
    const newErrors: Record<string, string> = {}
    if (!data.name.trim()) newErrors.name = "Name is required"
    if (!data.host.trim()) newErrors.host = "Host is required"
    if (!data.port.trim()) newErrors.port = "Port is required"
    if (!data.database.trim()) newErrors.database = "Database is required"
    if (!data.username.trim()) newErrors.username = "Username is required"
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  async function handleTest() {
    if (!datasourceId) return
    setTestStatus("testing")
    try {
      const result = await api.post<{ success: boolean; message: string }>(
        `/api/datasources/${datasourceId}/test`,
      )
      setTestStatus(result.success ? "success" : "error")
      setTestMessage(result.message)
    } catch (err) {
      setTestStatus("error")
      setTestMessage(err instanceof Error ? err.message : "Test failed")
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!validate()) return
    await onSubmit(data)
  }

  function updatePort(type: string) {
    const portMap: Record<string, string> = {
      POSTGRESQL: "5432",
      MYSQL: "3306",
    }
    setData((prev) => ({
      ...prev,
      type,
      port: portMap[type] ?? prev.port,
    }))
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-8">
      {/* Basic Info */}
      <div className="space-y-4">
        <h3 className="text-lg font-semibold">Connection</h3>
        <div className="space-y-2">
          <Label htmlFor="ds-name">Name</Label>
          <Input
            id="ds-name"
            value={data.name}
            onChange={(e) => setData({ ...data, name: e.target.value })}
            placeholder="production-db"
          />
          {errors.name && (
            <p className="text-sm text-destructive">{errors.name}</p>
          )}
        </div>
        <div className="space-y-2">
          <Label>Database Type</Label>
          <Select value={data.type} onValueChange={updatePort}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="POSTGRESQL">PostgreSQL</SelectItem>
              <SelectItem value="MYSQL">MySQL</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="grid grid-cols-3 gap-4">
          <div className="col-span-2 space-y-2">
            <Label htmlFor="ds-host">Host</Label>
            <Input
              id="ds-host"
              value={data.host}
              onChange={(e) => setData({ ...data, host: e.target.value })}
              placeholder="localhost"
            />
            {errors.host && (
              <p className="text-sm text-destructive">{errors.host}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="ds-port">Port</Label>
            <Input
              id="ds-port"
              value={data.port}
              onChange={(e) => setData({ ...data, port: e.target.value })}
            />
            {errors.port && (
              <p className="text-sm text-destructive">{errors.port}</p>
            )}
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="ds-database">Database</Label>
          <Input
            id="ds-database"
            value={data.database}
            onChange={(e) => setData({ ...data, database: e.target.value })}
            placeholder="mydb"
          />
          {errors.database && (
            <p className="text-sm text-destructive">{errors.database}</p>
          )}
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="ds-username">Username</Label>
            <Input
              id="ds-username"
              value={data.username}
              onChange={(e) => setData({ ...data, username: e.target.value })}
            />
            {errors.username && (
              <p className="text-sm text-destructive">{errors.username}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="ds-password">Password</Label>
            <Input
              id="ds-password"
              type="password"
              value={data.password}
              onChange={(e) => setData({ ...data, password: e.target.value })}
            />
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="ds-schemas">Schemas (comma-separated)</Label>
          <Input
            id="ds-schemas"
            value={data.schemas}
            onChange={(e) => setData({ ...data, schemas: e.target.value })}
            placeholder="public"
          />
        </div>
        {datasourceId && (
          <div className="flex items-center gap-3">
            <Button
              type="button"
              variant="outline"
              onClick={handleTest}
              disabled={testStatus === "testing"}
            >
              {testStatus === "testing" && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              Test Connection
            </Button>
            {testStatus === "success" && (
              <span className="flex items-center gap-1 text-sm text-green-600">
                <CheckCircle className="h-4 w-4" /> {testMessage}
              </span>
            )}
            {testStatus === "error" && (
              <span className="flex items-center gap-1 text-sm text-destructive">
                <XCircle className="h-4 w-4" /> {testMessage}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Schedule */}
      <ScheduleInput
        value={data.cronExpression}
        onChange={handleScheduleChange}
      />

      {/* Retention */}
      <div className="space-y-4">
        <h3 className="text-lg font-semibold">Retention Policy</h3>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="ret-daily">Daily Snapshots to Keep</Label>
            <Input
              id="ret-daily"
              type="number"
              min={0}
              value={data.retentionDaily}
              onChange={(e) =>
                setData({
                  ...data,
                  retentionDaily: parseInt(e.target.value, 10) || 0,
                })
              }
            />
            <p className="text-xs text-muted-foreground">0 = keep all</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="ret-monthly">Monthly Snapshots to Keep</Label>
            <Input
              id="ret-monthly"
              type="number"
              min={0}
              value={data.retentionMonthly}
              onChange={(e) =>
                setData({
                  ...data,
                  retentionMonthly: parseInt(e.target.value, 10) || 0,
                })
              }
            />
            <p className="text-xs text-muted-foreground">0 = keep all</p>
          </div>
        </div>
      </div>

      <Button type="submit" className="h-11 w-full" disabled={isSubmitting}>
        {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
        {submitLabel}
      </Button>
    </form>
  )
}
