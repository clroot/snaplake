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

export interface SnapshotContextDefault {
  datasourceId: string
  snapshotId: string
  snapshotLabel: string
  tables: string[]
}

export interface SnapshotContextAdditional {
  datasourceId: string
  snapshotId: string
  snapshotLabel: string
  tables: string[]
  alias: string
}

export interface SnapshotContextState {
  default: SnapshotContextDefault | null
  additional: SnapshotContextAdditional[]
}

export function formatSnapshotLabel(snap: SnapshotResponse): string {
  const time = new Date(snap.startedAt).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  })
  return `${snap.snapshotType} / ${snap.snapshotDate} ${time}`
}

export function getTableNames(snap: SnapshotResponse): string[] {
  return snap.tables.map((t) => `${t.schema}.${t.table}`)
}
