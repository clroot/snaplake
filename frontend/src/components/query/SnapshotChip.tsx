import { useState, useMemo } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { X } from "lucide-react"
import {
  type SnapshotContextEntry,
  type SnapshotResponse,
  formatSnapshotLabel,
} from "./snapshot-context-utils"

interface SnapshotChipProps {
  entry: SnapshotContextEntry
  datasources: { id: string; name: string }[]
  onEntryChange: (updated: SnapshotContextEntry) => void
  onRemove: () => void
}

export function SnapshotChip({
  entry,
  datasources,
  onEntryChange,
  onRemove,
}: SnapshotChipProps) {
  const [open, setOpen] = useState(!entry.snapshotId)

  const { data: snapshots } = useQuery({
    queryKey: ["snapshots", { datasourceId: entry.datasourceId }],
    queryFn: () =>
      api.get<SnapshotResponse[]>(
        `/api/snapshots?datasourceId=${entry.datasourceId}`,
      ),
    enabled: !!entry.datasourceId,
  })

  const completedSnapshots = useMemo(
    () =>
      snapshots
        ?.filter((s) => s.status === "COMPLETED")
        .sort((a, b) => b.startedAt.localeCompare(a.startedAt)) ?? [],
    [snapshots],
  )

  function handleDatasourceChange(datasourceId: string) {
    const ds = datasources.find((d) => d.id === datasourceId)
    onEntryChange({
      ...entry,
      datasourceId,
      datasourceName: ds?.name ?? "",
      snapshotId: "",
      snapshotLabel: "",
    })
  }

  function handleSnapshotChange(snapshotId: string) {
    const snap = completedSnapshots.find((s) => s.id === snapshotId)
    if (!snap) return
    onEntryChange({
      ...entry,
      snapshotId: snap.id,
      snapshotLabel: formatSnapshotLabel(snap),
      datasourceName: snap.datasourceName,
    })
  }

  function handleAliasChange(alias: string) {
    onEntryChange({ ...entry, alias })
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Badge
          variant={entry.snapshotId ? "secondary" : "outline"}
          className="cursor-pointer gap-1 py-1 pl-2 pr-1 font-normal"
        >
          <span className="max-w-64 truncate text-xs">
            <span className="font-mono font-semibold">{entry.alias}</span>
            {entry.snapshotId && (
              <span className="text-muted-foreground">
                {" · "}
                {entry.datasourceName} / {entry.snapshotLabel}
              </span>
            )}
            {!entry.snapshotId && (
              <span className="text-muted-foreground">
                {" · Select snapshot..."}
              </span>
            )}
          </span>
          <button
            className="ml-1 rounded-sm p-0.5 hover:bg-accent"
            onClick={(e) => {
              e.stopPropagation()
              onRemove()
            }}
          >
            <X className="h-3 w-3" />
          </button>
        </Badge>
      </PopoverTrigger>
      <PopoverContent className="w-72 space-y-3" align="start">
        <div className="space-y-1.5">
          <Label className="text-xs">Alias</Label>
          <Input
            value={entry.alias}
            onChange={(e) => handleAliasChange(e.target.value)}
            className="h-8 font-mono text-sm"
            placeholder="e.g. today"
          />
        </div>
        <div className="space-y-1.5">
          <Label className="text-xs">Datasource</Label>
          <Select
            value={entry.datasourceId}
            onValueChange={handleDatasourceChange}
          >
            <SelectTrigger className="h-8 text-sm">
              <SelectValue placeholder="Select datasource" />
            </SelectTrigger>
            <SelectContent>
              {datasources.map((ds) => (
                <SelectItem key={ds.id} value={ds.id}>
                  {ds.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label className="text-xs">Snapshot</Label>
          <Select
            value={entry.snapshotId}
            onValueChange={handleSnapshotChange}
            disabled={!entry.datasourceId}
          >
            <SelectTrigger className="h-8 text-sm">
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
      </PopoverContent>
    </Popover>
  )
}
