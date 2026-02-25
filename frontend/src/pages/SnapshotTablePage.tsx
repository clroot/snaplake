import { useNavigate, useParams } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { SnapshotLayout } from "@/components/snapshot/SnapshotLayout"
import { TablePreview } from "@/components/snapshot/TablePreview"

export function SnapshotTablePage() {
  const { snapshotId, schema, table } = useParams({
    from: "/authenticated/snapshots/$snapshotId/$schema/$table",
  })
  const navigate = useNavigate()
  const tableName = `${schema}.${table}`

  // Populate cache for breadcrumb display
  useQuery({
    queryKey: ["snapshots", snapshotId],
    queryFn: () =>
      api.get<{ datasourceName: string; snapshotDate: string }>(
        `/api/snapshots/${snapshotId}`,
      ),
  })

  function handleSelectSnapshot(sid: string) {
    navigate({
      to: "/snapshots/$snapshotId",
      params: { snapshotId: sid },
    })
  }

  function handleSelectTable(sid: string, tName: string) {
    const dotIndex = tName.indexOf(".")
    const s = tName.substring(0, dotIndex)
    const t = tName.substring(dotIndex + 1)
    navigate({
      to: "/snapshots/$snapshotId/$schema/$table",
      params: { snapshotId: sid, schema: s, table: t },
    })
  }

  return (
    <SnapshotLayout
      onSelectTable={handleSelectTable}
      onSelectSnapshot={handleSelectSnapshot}
      selectedSnapshotId={snapshotId}
      selectedTable={tableName}
    >
      <TablePreview
        key={`${snapshotId}-${tableName}`}
        snapshotId={snapshotId}
        tableName={tableName}
      />
    </SnapshotLayout>
  )
}
