import { useMemo } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

interface SnapshotResponse {
  id: string
  datasourceId: string
  datasourceName: string
  snapshotType: string
  snapshotDate: string
  startedAt: string
  status: string
  tables: { schema: string; table: string }[]
}

interface DatasourceResponse {
  id: string
  name: string
}

interface CompareSelectorProps {
  datasourceId: string | null
  onDatasourceChange: (id: string) => void
  leftSnapshotId: string | null
  onLeftChange: (id: string) => void
  rightSnapshotId: string | null
  onRightChange: (id: string) => void
  tableName: string | null
  onTableChange: (name: string) => void
}

function formatSnapshotLabel(snap: SnapshotResponse): string {
  const time = new Date(snap.startedAt).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  })
  return `${snap.snapshotType} / ${snap.snapshotDate} ${time}`
}

export function CompareSelector({
  datasourceId,
  onDatasourceChange,
  leftSnapshotId,
  onLeftChange,
  rightSnapshotId,
  onRightChange,
  tableName,
  onTableChange,
}: CompareSelectorProps) {
  const { data: datasources } = useQuery({
    queryKey: ["datasources"],
    queryFn: () => api.get<DatasourceResponse[]>("/api/datasources"),
  })

  const { data: snapshots } = useQuery({
    queryKey: ["snapshots", { datasourceId }],
    queryFn: () =>
      api.get<SnapshotResponse[]>(
        `/api/snapshots${datasourceId ? `?datasourceId=${datasourceId}` : ""}`,
      ),
    enabled: !!datasourceId,
  })

  const completedSnapshots = useMemo(
    () =>
      snapshots
        ?.filter((s) => s.status === "COMPLETED")
        .sort((a, b) => b.startedAt.localeCompare(a.startedAt)) ?? [],
    [snapshots],
  )

  const leftSnapshot = completedSnapshots.find(
    (s) => s.id === leftSnapshotId,
  )
  const rightSnapshot = completedSnapshots.find(
    (s) => s.id === rightSnapshotId,
  )

  // Available tables = intersection of both snapshot tables
  const availableTables = useMemo(() => {
    if (!leftSnapshot || !rightSnapshot) return []

    const leftTables = new Set(
      leftSnapshot.tables.map((t) => `${t.schema}.${t.table}`),
    )
    return rightSnapshot.tables
      .map((t) => `${t.schema}.${t.table}`)
      .filter((t) => leftTables.has(t))
  }, [leftSnapshot, rightSnapshot])

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div className="space-y-2">
        <Label>Datasource</Label>
        <Select
          value={datasourceId ?? ""}
          onValueChange={onDatasourceChange}
        >
          <SelectTrigger>
            <SelectValue placeholder="Select datasource" />
          </SelectTrigger>
          <SelectContent>
            {datasources?.map((ds) => (
              <SelectItem key={ds.id} value={ds.id}>
                {ds.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label>Left Snapshot</Label>
        <Select
          value={leftSnapshotId ?? ""}
          onValueChange={onLeftChange}
          disabled={!datasourceId}
        >
          <SelectTrigger>
            <SelectValue placeholder="Select snapshot" />
          </SelectTrigger>
          <SelectContent>
            {completedSnapshots.map((snap) => (
              <SelectItem key={snap.id} value={snap.id}>
                {formatSnapshotLabel(snap)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label>Right Snapshot</Label>
        <Select
          value={rightSnapshotId ?? ""}
          onValueChange={onRightChange}
          disabled={!datasourceId}
        >
          <SelectTrigger>
            <SelectValue placeholder="Select snapshot" />
          </SelectTrigger>
          <SelectContent>
            {completedSnapshots.map((snap) => (
              <SelectItem key={snap.id} value={snap.id}>
                {formatSnapshotLabel(snap)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label>Table</Label>
        <Select
          value={tableName ?? ""}
          onValueChange={onTableChange}
          disabled={availableTables.length === 0}
        >
          <SelectTrigger>
            <SelectValue placeholder="Select table" />
          </SelectTrigger>
          <SelectContent>
            {availableTables.map((t) => (
              <SelectItem key={t} value={t}>
                {t}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  )
}
