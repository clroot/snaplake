import { useCallback } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Database, Plus } from "lucide-react"
import { SnapshotChip } from "./SnapshotChip"
import {
  type SnapshotContextState,
  type SnapshotContextEntry,
  nextAlias,
} from "./snapshot-context-utils"

interface DatasourceResponse {
  id: string
  name: string
}

interface SnapshotContextBarProps {
  context: SnapshotContextState
  onContextChange: (context: SnapshotContextState) => void
}

export function SnapshotContextBar({
  context,
  onContextChange,
}: SnapshotContextBarProps) {
  const { data: datasources } = useQuery({
    queryKey: ["datasources"],
    queryFn: () => api.get<DatasourceResponse[]>("/api/datasources"),
  })

  const handleAdd = useCallback(() => {
    onContextChange({
      entries: [
        ...context.entries,
        {
          datasourceId: "",
          snapshotId: "",
          snapshotLabel: "",
          datasourceName: "",
          alias: nextAlias(context.entries),
        },
      ],
    })
  }, [context, onContextChange])

  const handleRemove = useCallback(
    (index: number) => {
      onContextChange({
        entries: context.entries.filter((_, i) => i !== index),
      })
    },
    [context, onContextChange],
  )

  const handleEntryChange = useCallback(
    (index: number, updated: SnapshotContextEntry) => {
      const entries = [...context.entries]
      entries[index] = updated
      onContextChange({ entries })
    },
    [context, onContextChange],
  )

  return (
    <div className="flex items-center gap-2 border-b bg-muted/30 px-4 py-2">
      <Database className="h-4 w-4 shrink-0 text-muted-foreground" />
      {context.entries.length === 0 ? (
        <span className="text-sm text-muted-foreground">
          No snapshots selected
        </span>
      ) : (
        <div className="flex flex-wrap items-center gap-1.5">
          {context.entries.map((entry, index) => (
            <SnapshotChip
              key={index}
              entry={entry}
              datasources={datasources ?? []}
              onEntryChange={(updated) => handleEntryChange(index, updated)}
              onRemove={() => handleRemove(index)}
            />
          ))}
        </div>
      )}
      <Button
        variant="ghost"
        size="sm"
        className="ml-auto shrink-0"
        onClick={handleAdd}
      >
        <Plus className="mr-1 h-3 w-3" />
        Add Snapshot
      </Button>
    </div>
  )
}
