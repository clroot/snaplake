const HISTORY_KEY = "snaplake_query_history"
const MAX_HISTORY = 50

export interface QueryHistoryEntry {
  sql: string
  executedAt: string
  rowCount: number
  durationMs: number
}

export function getQueryHistory(): QueryHistoryEntry[] {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    if (!raw) return []
    return JSON.parse(raw) as QueryHistoryEntry[]
  } catch {
    return []
  }
}

export function addQueryHistory(entry: QueryHistoryEntry): void {
  const history = getQueryHistory()
  history.unshift(entry)
  if (history.length > MAX_HISTORY) {
    history.splice(MAX_HISTORY)
  }
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history))
}

export function clearQueryHistory(): void {
  localStorage.removeItem(HISTORY_KEY)
}
