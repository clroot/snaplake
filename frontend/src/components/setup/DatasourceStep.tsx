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

export interface DatasourceData {
  skip: boolean
  name: string
  type: string
  host: string
  port: string
  database: string
  username: string
  password: string
  schemas: string
}

interface DatasourceStepProps {
  data: DatasourceData
  onChange: (data: DatasourceData) => void
  onNext: () => void
  onBack: () => void
}

export function DatasourceStep({
  data,
  onChange,
  onNext,
  onBack,
}: DatasourceStepProps) {
  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <h2 className="text-2xl font-bold tracking-tight">
          Register a Datasource
        </h2>
        <p className="text-muted-foreground">
          Optionally register your first database connection. You can skip this
          and add one later.
        </p>
      </div>

      {data.skip ? (
        <div className="space-y-4">
          <p className="text-muted-foreground">
            You chose to skip datasource registration. You can add datasources
            from the dashboard.
          </p>
          <Button
            variant="outline"
            onClick={() => onChange({ ...data, skip: false })}
          >
            Add a datasource now
          </Button>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="ds-name">Name</Label>
            <Input
              id="ds-name"
              value={data.name}
              onChange={(e) => onChange({ ...data, name: e.target.value })}
              placeholder="production-db"
            />
          </div>
          <div className="space-y-2">
            <Label>Database Type</Label>
            <Select
              value={data.type}
              onValueChange={(value) => onChange({ ...data, type: value })}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select database type" />
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
                onChange={(e) => onChange({ ...data, host: e.target.value })}
                placeholder="localhost"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="ds-port">Port</Label>
              <Input
                id="ds-port"
                value={data.port}
                onChange={(e) => onChange({ ...data, port: e.target.value })}
                placeholder="5432"
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="ds-database">Database</Label>
            <Input
              id="ds-database"
              value={data.database}
              onChange={(e) => onChange({ ...data, database: e.target.value })}
              placeholder="mydb"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="ds-username">Username</Label>
              <Input
                id="ds-username"
                value={data.username}
                onChange={(e) => onChange({ ...data, username: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="ds-password">Password</Label>
              <Input
                id="ds-password"
                type="password"
                value={data.password}
                onChange={(e) => onChange({ ...data, password: e.target.value })}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="ds-schemas">
              Schemas{" "}
              <span className="text-muted-foreground">
                (comma-separated)
              </span>
            </Label>
            <Input
              id="ds-schemas"
              value={data.schemas}
              onChange={(e) => onChange({ ...data, schemas: e.target.value })}
              placeholder="public"
            />
          </div>
          <Button
            variant="link"
            className="px-0"
            onClick={() => onChange({ ...data, skip: true })}
          >
            Skip this step
          </Button>
        </div>
      )}

      <div className="flex gap-4">
        <Button variant="outline" onClick={onBack} className="flex-1 h-11">
          Back
        </Button>
        <Button onClick={onNext} className="flex-1 h-11">
          {data.skip ? "Next" : "Next"}
        </Button>
      </div>
    </div>
  )
}
