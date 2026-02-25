import type { Column } from "@/components/common/DataTable"

export function exportToCsv(columns: Column[], rows: unknown[][], filename: string) {
  const header = columns.map((c) => c.name).join(",")
  const body = rows
    .map((row) =>
      row
        .map((cell) => {
          if (cell === null || cell === undefined) return ""
          const str = String(cell)
          if (str.includes(",") || str.includes('"') || str.includes("\n")) {
            return `"${str.replace(/"/g, '""')}"`
          }
          return str
        })
        .join(","),
    )
    .join("\n")

  const blob = new Blob([header + "\n" + body], { type: "text/csv" })
  downloadBlob(blob, `${filename}.csv`)
}

export function exportToJson(columns: Column[], rows: unknown[][], filename: string) {
  const data = rows.map((row) => {
    const obj: Record<string, unknown> = {}
    columns.forEach((col, i) => {
      obj[col.name] = row[i]
    })
    return obj
  })

  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: "application/json",
  })
  downloadBlob(blob, `${filename}.json`)
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
