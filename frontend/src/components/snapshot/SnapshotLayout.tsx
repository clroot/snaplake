import { useCallback, useEffect, useRef, useState, type ReactNode } from "react"
import { SidePanel } from "@/components/snapshot/SidePanel"
import { cn } from "@/lib/utils"

const STORAGE_KEY = "snaplake:sidepanel-width"
const MIN_WIDTH = 200
const MAX_WIDTH = 480
const DEFAULT_WIDTH = 288

function getInitialWidth(): number {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored) {
    const parsed = Number(stored)
    if (parsed >= MIN_WIDTH && parsed <= MAX_WIDTH) return parsed
  }
  return DEFAULT_WIDTH
}

interface SnapshotLayoutProps {
  children: ReactNode
  onSelectTable: (snapshotId: string, tableName: string) => void
  onSelectSnapshot?: (snapshotId: string) => void
  selectedSnapshotId?: string
  selectedTable?: string
}

export function SnapshotLayout({
  children,
  onSelectTable,
  onSelectSnapshot,
  selectedSnapshotId,
  selectedTable,
}: SnapshotLayoutProps) {
  const [width, setWidth] = useState(getInitialWidth)
  const [isDragging, setIsDragging] = useState(false)
  const startXRef = useRef(0)
  const startWidthRef = useRef(0)

  const handlePointerDown = useCallback(
    (e: React.PointerEvent) => {
      e.preventDefault()
      setIsDragging(true)
      startXRef.current = e.clientX
      startWidthRef.current = width
      ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
    },
    [width],
  )

  const handlePointerMove = useCallback(
    (e: React.PointerEvent) => {
      if (!isDragging) return
      const delta = e.clientX - startXRef.current
      const newWidth = Math.min(
        MAX_WIDTH,
        Math.max(MIN_WIDTH, startWidthRef.current + delta),
      )
      setWidth(newWidth)
    },
    [isDragging],
  )

  const handlePointerUp = useCallback(() => {
    setIsDragging(false)
  }, [])

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, String(width))
  }, [width])

  return (
    <div className="-m-6 flex h-[calc(100vh-3.5rem)]">
      <div className="relative shrink-0" style={{ width }}>
        <SidePanel
          onSelectTable={onSelectTable}
          onSelectSnapshot={onSelectSnapshot}
          selectedSnapshotId={selectedSnapshotId}
          selectedTable={selectedTable}
        />
        <div
          className={cn(
            "absolute inset-y-0 -right-px z-10 w-1 cursor-col-resize transition-colors hover:bg-primary/20",
            isDragging && "bg-primary/30",
          )}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
        />
      </div>
      <div className="flex-1 overflow-y-auto overflow-x-hidden p-6">
        {children}
      </div>
    </div>
  )
}
