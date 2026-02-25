import { useState } from "react"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { CompareSelector } from "@/components/compare/CompareSelector"
import { CompareStats } from "@/components/compare/CompareStats"
import { CompareDiffView } from "@/components/compare/CompareDiffView"
import { Split } from "lucide-react"

export function ComparePage() {
  const [datasourceId, setDatasourceId] = useState<string | null>(null)
  const [leftSnapshotId, setLeftSnapshotId] = useState<string | null>(null)
  const [rightSnapshotId, setRightSnapshotId] = useState<string | null>(null)
  const [tableName, setTableName] = useState<string | null>(null)

  const isReady = leftSnapshotId && rightSnapshotId && tableName

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">
          Compare Snapshots
        </h1>
        <p className="text-muted-foreground">
          Select two snapshots of the same table to compare differences
        </p>
      </div>

      <CompareSelector
        datasourceId={datasourceId}
        onDatasourceChange={(id) => {
          setDatasourceId(id)
          setLeftSnapshotId(null)
          setRightSnapshotId(null)
          setTableName(null)
        }}
        leftSnapshotId={leftSnapshotId}
        onLeftChange={(id) => {
          setLeftSnapshotId(id)
          setTableName(null)
        }}
        rightSnapshotId={rightSnapshotId}
        onRightChange={(id) => {
          setRightSnapshotId(id)
          setTableName(null)
        }}
        tableName={tableName}
        onTableChange={setTableName}
      />

      {isReady ? (
        <Tabs defaultValue="stats" className="space-y-4">
          <TabsList>
            <TabsTrigger value="stats">Stats</TabsTrigger>
            <TabsTrigger value="diff">Diff</TabsTrigger>
          </TabsList>

          <TabsContent value="stats">
            <CompareStats
              leftSnapshotId={leftSnapshotId}
              rightSnapshotId={rightSnapshotId}
              tableName={tableName}
            />
          </TabsContent>

          <TabsContent value="diff">
            <CompareDiffView
              leftSnapshotId={leftSnapshotId}
              rightSnapshotId={rightSnapshotId}
              tableName={tableName}
            />
          </TabsContent>
        </Tabs>
      ) : (
        <div className="flex flex-col items-center py-16 text-muted-foreground">
          <Split className="mb-4 h-12 w-12" />
          <p className="text-lg font-medium">Select snapshots to compare</p>
          <p className="text-sm">
            Choose a datasource, two snapshots, and a table above
          </p>
        </div>
      )}
    </div>
  )
}
