import { useEffect, useState } from "react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"

interface Shortcut {
  keys: string[]
  description: string
}

const shortcuts: Shortcut[] = [
  { keys: ["?"], description: "Show keyboard shortcuts" },
  { keys: ["Ctrl", "Enter"], description: "Execute query" },
  { keys: ["Esc"], description: "Close dialog" },
]

function isMac(): boolean {
  return navigator.platform.toUpperCase().includes("MAC")
}

function formatKey(key: string): string {
  if (key === "Ctrl") return isMac() ? "\u2318" : "Ctrl"
  if (key === "Alt") return isMac() ? "\u2325" : "Alt"
  if (key === "Shift") return "\u21E7"
  if (key === "Enter") return "\u23CE"
  if (key === "Esc") return "Esc"
  return key
}

export function ShortcutHelp() {
  const [open, setOpen] = useState(false)

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement
      const isInput =
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.isContentEditable

      if (e.key === "?" && !isInput) {
        e.preventDefault()
        setOpen(true)
      }
    }

    document.addEventListener("keydown", handleKeyDown)
    return () => document.removeEventListener("keydown", handleKeyDown)
  }, [])

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Keyboard Shortcuts</DialogTitle>
        </DialogHeader>
        <div className="space-y-2 pt-2">
          {shortcuts.map((shortcut) => (
            <div
              key={shortcut.description}
              className="flex items-center justify-between py-2"
            >
              <span className="text-sm text-muted-foreground">
                {shortcut.description}
              </span>
              <div className="flex items-center gap-1">
                {shortcut.keys.map((key) => (
                  <kbd
                    key={key}
                    className="inline-flex h-6 min-w-6 items-center justify-center rounded border bg-muted px-1.5 text-xs font-medium text-muted-foreground"
                  >
                    {formatKey(key)}
                  </kbd>
                ))}
              </div>
            </div>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}
