import { useCallback } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@carbon/react"
import { Db2Database, Add } from "@carbon/react/icons"
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
    <div style={{
      position: "relative",
      zIndex: 10,
      display: "flex",
      alignItems: "center",
      gap: "0.5rem",
      borderBottom: "1px solid var(--cds-border-subtle)",
      backgroundColor: "var(--cds-layer-01)",
      padding: "0.5rem 1rem",
    }}>
      <Db2Database size={16} style={{ flexShrink: 0, color: "var(--cds-text-secondary)" }} />
      {context.entries.length === 0 ? (
        <span style={{ fontSize: "0.875rem", color: "var(--cds-text-secondary)" }}>
          No snapshots selected
        </span>
      ) : (
        <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "0.375rem" }}>
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
        kind="ghost"
        size="sm"
        renderIcon={Add}
        style={{ marginLeft: "auto", flexShrink: 0 }}
        onClick={handleAdd}
      >
        Add Snapshot
      </Button>
    </div>
  )
}
