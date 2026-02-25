export interface SnapshotResponse {
  id: string
  datasourceId: string
  datasourceName: string
  snapshotType: string
  snapshotDate: string
  startedAt: string
  status: string
  tables: { schema: string; table: string }[]
}

export interface SnapshotContextEntry {
  datasourceId: string
  snapshotId: string
  snapshotLabel: string
  datasourceName: string
  alias: string
}

export interface SnapshotContextState {
  entries: SnapshotContextEntry[]
}

export function formatSnapshotLabel(snap: SnapshotResponse): string {
  const time = new Date(snap.startedAt).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  })
  return `${snap.snapshotType} / ${snap.snapshotDate} ${time}`
}

export function nextAlias(existing: SnapshotContextEntry[]): string {
  const used = new Set(existing.map((e) => e.alias))
  let i = 1
  while (used.has(`s${i}`)) i++
  return `s${i}`
}
