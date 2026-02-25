import { useState, useMemo, useEffect, useCallback } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Input } from "@/components/ui/input"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import {
  Dialog,
  DialogContent,
} from "@/components/ui/dialog"
import {
  ChevronDown,
  ChevronRight,
  Database,
  Folder,
  Table,
  Search,
} from "lucide-react"
import { cn } from "@/lib/utils"

interface SnapshotResponse {
  id: string
  datasourceId: string
  datasourceName: string
  snapshotType: string
  snapshotDate: string
  startedAt: string
  status: string
  tables: { schema: string; table: string; rowCount: number; sizeBytes: number }[]
}

interface SidePanelProps {
  onSelectTable: (snapshotId: string, tableName: string) => void
  onSelectSnapshot?: (snapshotId: string) => void
  selectedSnapshotId?: string
  selectedTable?: string
}

interface TreeNode {
  type: "datasource" | "snapshotType" | "date" | "table"
  label: string
  children?: TreeNode[]
  snapshotId?: string
  tableName?: string
}

export function SidePanel({ onSelectTable, onSelectSnapshot, selectedSnapshotId, selectedTable }: SidePanelProps) {
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set())
  const [searchOpen, setSearchOpen] = useState(false)
  const [filter, setFilter] = useState("")

  const { data: snapshots } = useQuery({
    queryKey: ["snapshots"],
    queryFn: () => api.get<SnapshotResponse[]>("/api/snapshots"),
  })

  // Build tree from snapshots
  const tree = useMemo(() => {
    if (!snapshots) return []

    const completed = snapshots.filter((s) => s.status === "COMPLETED")
    const byDatasource = new Map<string, SnapshotResponse[]>()

    for (const snap of completed) {
      const list = byDatasource.get(snap.datasourceName) ?? []
      list.push(snap)
      byDatasource.set(snap.datasourceName, list)
    }

    const nodes: TreeNode[] = []

    for (const [dsName, snaps] of byDatasource) {
      const byType = new Map<string, SnapshotResponse[]>()
      for (const snap of snaps) {
        const list = byType.get(snap.snapshotType) ?? []
        list.push(snap)
        byType.set(snap.snapshotType, list)
      }

      const typeNodes: TreeNode[] = []
      for (const [type, typeSnaps] of byType) {
        const sorted = typeSnaps.sort(
          (a, b) => b.startedAt.localeCompare(a.startedAt),
        )

        const dateNodes: TreeNode[] = sorted.map((snap) => {
          const time = new Date(snap.startedAt).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
          })
          return {
            type: "date" as const,
            label: `${snap.snapshotDate} ${time}`,
            snapshotId: snap.id,
            children: snap.tables.map((t) => ({
              type: "table" as const,
              label: `${t.schema}.${t.table}`,
              snapshotId: snap.id,
              tableName: `${t.schema}.${t.table}`,
            })),
          }
        })

        typeNodes.push({
          type: "snapshotType",
          label: type.toLowerCase(),
          children: dateNodes,
        })
      }

      nodes.push({
        type: "datasource",
        label: dsName,
        children: typeNodes,
      })
    }

    return nodes
  }, [snapshots])

  // Auto-expand tree to selected item on mount / URL change
  useEffect(() => {
    if (!selectedSnapshotId || tree.length === 0) return

    const paths: string[] = []
    for (let di = 0; di < tree.length; di++) {
      const ds = tree[di]
      if (!ds.children) continue
      for (let ti = 0; ti < ds.children.length; ti++) {
        const typeNode = ds.children[ti]
        if (!typeNode.children) continue
        for (let si = 0; si < typeNode.children.length; si++) {
          const dateNode = typeNode.children[si]
          if (dateNode.snapshotId !== selectedSnapshotId) continue

          if (!selectedTable) {
            paths.push(String(di), `${di}/${ti}`, `${di}/${ti}/${si}`)
          } else {
            const hasTable = dateNode.children?.some(
              (t) => t.tableName === selectedTable,
            )
            if (hasTable) {
              paths.push(String(di), `${di}/${ti}`, `${di}/${ti}/${si}`)
            }
          }
        }
      }
    }

    if (paths.length > 0) {
      setExpandedNodes((prev) => {
        const next = new Set(prev)
        for (const p of paths) next.add(p)
        return next
      })
    }
  }, [tree, selectedSnapshotId, selectedTable])

  // Keyboard shortcut for search
  const handleKeydown = useCallback((e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key === "k") {
      e.preventDefault()
      setSearchOpen(true)
    }
  }, [])

  useEffect(() => {
    document.addEventListener("keydown", handleKeydown)
    return () => document.removeEventListener("keydown", handleKeydown)
  }, [handleKeydown])

  function toggleNode(path: string) {
    setExpandedNodes((prev) => {
      const next = new Set(prev)
      if (next.has(path)) {
        next.delete(path)
      } else {
        next.add(path)
      }
      return next
    })
  }

  // Flat list of all tables for search
  const allTables = useMemo(() => {
    const result: { snapshotId: string; tableName: string; label: string }[] = []
    if (!snapshots) return result

    for (const snap of snapshots) {
      if (snap.status !== "COMPLETED") continue
      for (const t of snap.tables) {
        const time = new Date(snap.startedAt).toLocaleTimeString([], {
          hour: "2-digit",
          minute: "2-digit",
        })
        result.push({
          snapshotId: snap.id,
          tableName: `${t.schema}.${t.table}`,
          label: `${snap.datasourceName} / ${snap.snapshotType} / ${snap.snapshotDate} ${time} / ${t.schema}.${t.table}`,
        })
      }
    }
    return result
  }, [snapshots])

  function renderNode(node: TreeNode, path: string) {
    const isExpanded = expandedNodes.has(path)
    const hasChildren = node.children && node.children.length > 0

    const filterLower = filter.toLowerCase()
    const matchesFilter =
      !filter || node.label.toLowerCase().includes(filterLower)
    const childrenMatchFilter =
      !filter ||
      node.children?.some(
        (child) =>
          child.label.toLowerCase().includes(filterLower) ||
          child.children?.some((gc) =>
            gc.label.toLowerCase().includes(filterLower),
          ),
      )

    if (!matchesFilter && !childrenMatchFilter) return null

    const icon =
      node.type === "datasource" ? (
        <Database className="h-4 w-4 shrink-0 text-muted-foreground" />
      ) : node.type === "snapshotType" || node.type === "date" ? (
        <Folder className="h-4 w-4 shrink-0 text-muted-foreground" />
      ) : (
        <Table className="h-4 w-4 shrink-0 text-muted-foreground" />
      )

    return (
      <div key={path}>
        <button
          className={cn(
            "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-accent",
            node.type === "table" && "cursor-pointer",
            node.type === "table" &&
              node.snapshotId === selectedSnapshotId &&
              node.tableName === selectedTable &&
              "bg-accent font-medium",
          )}
          onClick={() => {
            if (node.type === "table" && node.snapshotId && node.tableName) {
              onSelectTable(node.snapshotId, node.tableName)
            } else if (node.type === "date" && node.snapshotId && onSelectSnapshot) {
              onSelectSnapshot(node.snapshotId)
              toggleNode(path)
            } else if (hasChildren) {
              toggleNode(path)
            }
          }}
        >
          {hasChildren ? (
            isExpanded ? (
              <ChevronDown className="h-3 w-3 shrink-0" />
            ) : (
              <ChevronRight className="h-3 w-3 shrink-0" />
            )
          ) : (
            <span className="w-3" />
          )}
          {icon}
          <span className="truncate">{node.label}</span>
        </button>
        {isExpanded && node.children && (
          <div className="ml-4">
            {node.children.map((child, i) =>
              renderNode(child, `${path}/${i}`),
            )}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col border-r">
      <div className="flex items-center gap-2 border-b p-3">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Filter... (Cmd+K)"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="pl-8"
            onFocus={() => {
              // Don't open the dialog, just use the inline filter
            }}
          />
        </div>
      </div>
      <ScrollArea className="flex-1 p-2">
        {tree.length === 0 ? (
          <p className="p-4 text-center text-sm text-muted-foreground">
            No snapshots available
          </p>
        ) : (
          tree.map((node, i) => renderNode(node, String(i)))
        )}
      </ScrollArea>

      {/* Cmd+K Search Dialog */}
      <Dialog open={searchOpen} onOpenChange={setSearchOpen}>
        <DialogContent className="max-w-lg p-0">
          <Command>
            <CommandInput placeholder="Search tables..." />
            <CommandList>
              <CommandEmpty>No results found.</CommandEmpty>
              <CommandGroup heading="Tables">
                {allTables.map((item, i) => (
                  <CommandItem
                    key={i}
                    onSelect={() => {
                      onSelectTable(item.snapshotId, item.tableName)
                      setSearchOpen(false)
                    }}
                  >
                    <Table className="mr-2 h-4 w-4" />
                    {item.label}
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </DialogContent>
      </Dialog>
    </div>
  )
}
