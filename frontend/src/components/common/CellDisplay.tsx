import { cn } from "@/lib/utils"

interface CellDisplayProps {
  value: unknown
  className?: string
}

export function CellDisplay({ value, className }: CellDisplayProps) {
  if (value === null || value === undefined) {
    return (
      <span className={cn("italic text-muted-foreground", className)}>
        NULL
      </span>
    )
  }

  if (typeof value === "boolean") {
    return (
      <span className={cn("font-mono", className)}>
        {value ? "true" : "false"}
      </span>
    )
  }

  if (typeof value === "number") {
    return (
      <span className={cn("font-mono tabular-nums", className)}>
        {value.toLocaleString()}
      </span>
    )
  }

  const str = String(value)
  const MAX_LENGTH = 200

  if (str.length > MAX_LENGTH) {
    return (
      <span className={className} title={str}>
        {str.slice(0, MAX_LENGTH)}...
      </span>
    )
  }

  return <span className={className}>{str}</span>
}
