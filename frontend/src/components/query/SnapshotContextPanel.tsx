import { useState, useMemo, useCallback } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { ChevronDown, ChevronRight, Plus, X } from "lucide-react"
import {
  type SnapshotResponse,
  type SnapshotContextState,
  type SnapshotContextAdditional,
  formatSnapshotLabel,
  getTableNames,
} from "./snapshot-context-utils"

export type { SnapshotContextState }

interface DatasourceResponse {
  id: string
  name: string
}

interface SnapshotContextPanelProps {
  context: SnapshotContextState
  onContextChange: (context: SnapshotContextState) => void
  defaultDatasourceId: string
  onDefaultDatasourceIdChange: (id: string) => void
}

function nextAlias(existing: SnapshotContextAdditional[]): string {
  const used = new Set(existing.map((a) => a.alias))
  let i = 1
  while (used.has(`s${i}`)) i++
  return `s${i}`
}

export function SnapshotContextPanel({
  context,
  onContextChange,
  defaultDatasourceId,
  onDefaultDatasourceIdChange,
}: SnapshotContextPanelProps) {
  const [isOpen, setIsOpen] = useState(true)

  const { data: datasources } = useQuery({
    queryKey: ["datasources"],
    queryFn: () => api.get<DatasourceResponse[]>("/api/datasources"),
  })

  const { data: defaultSnapshots } = useQuery({
    queryKey: ["snapshots", { datasourceId: defaultDatasourceId }],
    queryFn: () =>
      api.get<SnapshotResponse[]>(
        `/api/snapshots?datasourceId=${defaultDatasourceId}`,
      ),
    enabled: !!defaultDatasourceId,
  })

  const completedDefaultSnapshots = useMemo(
    () =>
      defaultSnapshots
        ?.filter((s) => s.status === "COMPLETED")
        .sort((a, b) => b.startedAt.localeCompare(a.startedAt)) ?? [],
    [defaultSnapshots],
  )

  const handleDefaultDatasourceChange = useCallback(
    (datasourceId: string) => {
      onDefaultDatasourceIdChange(datasourceId)
      onContextChange({
        ...context,
        default: null,
      })
    },
    [context, onContextChange, onDefaultDatasourceIdChange],
  )

  const handleDefaultSnapshotChange = useCallback(
    (snapshotId: string) => {
      const snap = completedDefaultSnapshots.find((s) => s.id === snapshotId)
      if (!snap) return

      onContextChange({
        ...context,
        default: {
          datasourceId: defaultDatasourceId,
          snapshotId: snap.id,
          snapshotLabel: formatSnapshotLabel(snap),
          tables: getTableNames(snap),
        },
      })
    },
    [completedDefaultSnapshots, defaultDatasourceId, context, onContextChange],
  )

  const handleAddAdditional = useCallback(() => {
    onContextChange({
      ...context,
      additional: [
        ...context.additional,
        {
          datasourceId: "",
          snapshotId: "",
          snapshotLabel: "",
          tables: [],
          alias: nextAlias(context.additional),
        },
      ],
    })
  }, [context, onContextChange])

  const handleRemoveAdditional = useCallback(
    (index: number) => {
      onContextChange({
        ...context,
        additional: context.additional.filter((_, i) => i !== index),
      })
    },
    [context, onContextChange],
  )

  const handleAdditionalAliasChange = useCallback(
    (index: number, alias: string) => {
      const updated = [...context.additional]
      updated[index] = { ...updated[index], alias }
      onContextChange({ ...context, additional: updated })
    },
    [context, onContextChange],
  )

  const defaultDatasource = datasources?.find(
    (d) => d.id === (context.default?.datasourceId ?? defaultDatasourceId),
  )

  const summaryText = context.default
    ? `${defaultDatasource?.name ?? "Unknown"} / ${context.default.snapshotLabel}${
        context.additional.length > 0
          ? ` (+${context.additional.length} more)`
          : ""
      }`
    : "No snapshot selected"

  return (
    <Collapsible
      open={isOpen}
      onOpenChange={setIsOpen}
      className="border-b bg-muted/30"
    >
      <CollapsibleTrigger className="flex w-full items-center gap-2 px-4 py-2 text-sm hover:bg-accent/50">
        {isOpen ? (
          <ChevronDown className="h-3.5 w-3.5 shrink-0" />
        ) : (
          <ChevronRight className="h-3.5 w-3.5 shrink-0" />
        )}
        <span className="truncate text-muted-foreground">{summaryText}</span>
      </CollapsibleTrigger>

      <CollapsibleContent className="px-4 pb-4">
        {/* Default Snapshot Section */}
        <div className="space-y-3">
          <Label className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Default Snapshot
          </Label>
          <div className="grid gap-3 sm:grid-cols-2">
            <Select
              value={
                context.default?.datasourceId ?? defaultDatasourceId ?? ""
              }
              onValueChange={handleDefaultDatasourceChange}
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

            <Select
              value={context.default?.snapshotId ?? ""}
              onValueChange={handleDefaultSnapshotChange}
              disabled={!defaultDatasourceId}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select snapshot" />
              </SelectTrigger>
              <SelectContent>
                {completedDefaultSnapshots.map((snap) => (
                  <SelectItem key={snap.id} value={snap.id}>
                    {formatSnapshotLabel(snap)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {context.default && context.default.tables.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {context.default.tables.map((table) => (
                <Badge key={table} variant="secondary" className="text-xs">
                  {table}
                </Badge>
              ))}
            </div>
          )}
        </div>

        {/* Additional Snapshots Section */}
        {context.additional.length > 0 && (
          <>
            <Separator className="my-4" />
            <div className="space-y-4">
              <Label className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Additional Snapshots
              </Label>
              {context.additional.map((entry, index) => (
                <AdditionalSnapshotEntry
                  key={index}
                  entry={entry}
                  index={index}
                  datasources={datasources ?? []}
                  onAliasChange={handleAdditionalAliasChange}
                  onRemove={handleRemoveAdditional}
                  onEntryChange={(idx, updated) => {
                    const newAdditional = [...context.additional]
                    newAdditional[idx] = updated
                    onContextChange({ ...context, additional: newAdditional })
                  }}
                />
              ))}
            </div>
          </>
        )}

        <div className="mt-4">
          <Button variant="outline" size="sm" onClick={handleAddAdditional}>
            <Plus className="mr-1 h-3 w-3" />
            Add Snapshot
          </Button>
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}

interface AdditionalSnapshotEntryProps {
  entry: SnapshotContextAdditional
  index: number
  datasources: { id: string; name: string }[]
  onAliasChange: (index: number, alias: string) => void
  onRemove: (index: number) => void
  onEntryChange: (index: number, updated: SnapshotContextAdditional) => void
}

function AdditionalSnapshotEntry({
  entry,
  index,
  datasources,
  onAliasChange,
  onRemove,
  onEntryChange,
}: AdditionalSnapshotEntryProps) {
  const [datasourceId, setDatasourceId] = useState(entry.datasourceId)

  const { data: snapshots } = useQuery({
    queryKey: ["snapshots", { datasourceId }],
    queryFn: () =>
      api.get<SnapshotResponse[]>(
        `/api/snapshots?datasourceId=${datasourceId}`,
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

  function handleDatasourceChange(newDatasourceId: string) {
    setDatasourceId(newDatasourceId)
    onEntryChange(index, {
      ...entry,
      datasourceId: newDatasourceId,
      snapshotId: "",
      snapshotLabel: "",
      tables: [],
    })
  }

  function handleSnapshotChange(snapshotId: string) {
    const snap = completedSnapshots.find((s) => s.id === snapshotId)
    if (!snap) return

    onEntryChange(index, {
      ...entry,
      datasourceId,
      snapshotId: snap.id,
      snapshotLabel: formatSnapshotLabel(snap),
      tables: getTableNames(snap),
    })
  }

  return (
    <div className="space-y-2 rounded-lg border p-3">
      <div className="flex items-center gap-2">
        <Label className="shrink-0 text-xs">Alias</Label>
        <Input
          value={entry.alias}
          onChange={(e) => onAliasChange(index, e.target.value)}
          className="h-8 w-20 font-mono text-sm"
        />
        <div className="flex-1" />
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7"
          onClick={() => onRemove(index)}
        >
          <X className="h-3.5 w-3.5" />
        </Button>
      </div>
      <div className="grid gap-2 sm:grid-cols-2">
        <Select value={datasourceId} onValueChange={handleDatasourceChange}>
          <SelectTrigger className="h-8 text-sm">
            <SelectValue placeholder="Datasource" />
          </SelectTrigger>
          <SelectContent>
            {datasources.map((ds) => (
              <SelectItem key={ds.id} value={ds.id}>
                {ds.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={entry.snapshotId}
          onValueChange={handleSnapshotChange}
          disabled={!datasourceId}
        >
          <SelectTrigger className="h-8 text-sm">
            <SelectValue placeholder="Snapshot" />
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

      {entry.tables.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {entry.tables.map((table) => (
            <Badge key={table} variant="outline" className="text-xs">
              {table}
            </Badge>
          ))}
        </div>
      )}
    </div>
  )
}
