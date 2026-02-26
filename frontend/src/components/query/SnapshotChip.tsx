import { useState, useMemo, useRef, useEffect } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Tag, TextInput, Select, SelectItem } from "@carbon/react"
import { Close } from "@carbon/react/icons"
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
  const popoverRef = useRef<HTMLDivElement>(null)

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

  useEffect(() => {
    if (!open) return
    function handleClickOutside(e: MouseEvent) {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
  }, [open])

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
    <div style={{ position: "relative", display: "inline-block" }}>
      <Tag
        type={entry.snapshotId ? "cool-gray" : "outline"}
        size="sm"
        style={{ cursor: "pointer", maxWidth: "18rem" }}
        onClick={() => setOpen(!open)}
      >
        <span style={{ display: "inline-flex", alignItems: "center", gap: "0.25rem" }}>
          <span style={{ fontFamily: "var(--cds-code-01-font-family, monospace)", fontWeight: 600 }}>
            {entry.alias}
          </span>
          {entry.snapshotId && (
            <span style={{ color: "var(--cds-text-secondary)" }}>
              {" \u00b7 "}
              {entry.datasourceName} / {entry.snapshotLabel}
            </span>
          )}
          {!entry.snapshotId && (
            <span style={{ color: "var(--cds-text-secondary)" }}>
              {" \u00b7 Select snapshot..."}
            </span>
          )}
        </span>
      </Tag>
      <button
        style={{
          position: "absolute",
          top: "-4px",
          right: "-4px",
          width: "16px",
          height: "16px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          borderRadius: "50%",
          border: "none",
          backgroundColor: "var(--cds-layer-02)",
          cursor: "pointer",
          padding: 0,
        }}
        onClick={(e) => {
          e.stopPropagation()
          onRemove()
        }}
      >
        <Close size={12} />
      </button>

      {open && (
        <div
          ref={popoverRef}
          style={{
            position: "absolute",
            top: "calc(100% + 4px)",
            left: 0,
            zIndex: 10,
            width: "18rem",
            padding: "0.75rem",
            backgroundColor: "var(--cds-layer-02)",
            border: "1px solid var(--cds-border-subtle)",
            boxShadow: "0 2px 6px rgba(0,0,0,0.15)",
            display: "flex",
            flexDirection: "column",
            gap: "0.75rem",
          }}
        >
          <TextInput
            id={`alias-${entry.alias}`}
            labelText="Alias"
            size="sm"
            value={entry.alias}
            onChange={(e) => handleAliasChange(e.target.value)}
            style={{ fontFamily: "var(--cds-code-01-font-family, monospace)" }}
            placeholder="e.g. today"
          />
          <Select
            id={`ds-select-${entry.alias}`}
            labelText="Datasource"
            size="sm"
            value={entry.datasourceId}
            onChange={(e) => handleDatasourceChange(e.target.value)}
          >
            <SelectItem value="" text="Select datasource" />
            {datasources.map((ds) => (
              <SelectItem key={ds.id} value={ds.id} text={ds.name} />
            ))}
          </Select>
          <Select
            id={`snap-select-${entry.alias}`}
            labelText="Snapshot"
            size="sm"
            value={entry.snapshotId}
            onChange={(e) => handleSnapshotChange(e.target.value)}
            disabled={!entry.datasourceId}
          >
            <SelectItem value="" text="Select snapshot" />
            {completedSnapshots.map((snap) => (
              <SelectItem
                key={snap.id}
                value={snap.id}
                text={formatSnapshotLabel(snap)}
              />
            ))}
          </Select>
        </div>
      )}
    </div>
  )
}
