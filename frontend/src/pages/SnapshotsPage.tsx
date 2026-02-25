import { useNavigate } from "@tanstack/react-router"
import { SnapshotLayout } from "@/components/snapshot/SnapshotLayout"
import { Layers } from "lucide-react"

export function SnapshotsPage() {
  const navigate = useNavigate()

  function handleSelectSnapshot(snapshotId: string) {
    navigate({
      to: "/snapshots/$snapshotId",
      params: { snapshotId },
    })
  }

  function handleSelectTable(snapshotId: string, tableName: string) {
    const dotIndex = tableName.indexOf(".")
    const schema = tableName.substring(0, dotIndex)
    const table = tableName.substring(dotIndex + 1)
    navigate({
      to: "/snapshots/$snapshotId/$schema/$table",
      params: { snapshotId, schema, table },
    })
  }

  return (
    <SnapshotLayout onSelectTable={handleSelectTable} onSelectSnapshot={handleSelectSnapshot}>
      <div className="flex h-full flex-col items-center justify-center text-muted-foreground">
        <Layers className="mb-4 h-12 w-12" />
        <p className="text-lg font-medium">Select a table</p>
        <p className="text-sm">
          Browse the tree on the left or press Cmd+K to search
        </p>
      </div>
    </SnapshotLayout>
  )
}
