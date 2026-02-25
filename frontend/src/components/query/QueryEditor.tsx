import { useEffect, useRef, useCallback, useMemo } from "react"
import { EditorView, keymap, placeholder } from "@codemirror/view"
import { EditorState, Prec } from "@codemirror/state"
import { sql, SQLDialect } from "@codemirror/lang-sql"
import {
  type Completion,
  type CompletionContext,
  type CompletionResult,
  autocompletion,
} from "@codemirror/autocomplete"
import { basicSetup } from "codemirror"
import { oneDark } from "@codemirror/theme-one-dark"

interface QueryEditorProps {
  value: string
  onChange: (value: string) => void
  onExecute: () => void
  tables?: Record<string, string[]>
}

const KEYWORDS =
  "select from where group by order having limit offset as on join left right inner outer cross full natural using with recursive union intersect except all distinct case when then else end and or not in is null between like ilike exists cast"
const TYPES =
  "integer int bigint smallint tinyint float double real decimal numeric varchar text boolean date timestamp time blob"
const BUILTINS =
  "count sum avg min max coalesce nullif abs round floor ceil upper lower trim length substring replace concat now current_date current_timestamp"

const duckDialect = SQLDialect.define({
  keywords: KEYWORDS,
  types: TYPES,
  builtin: BUILTINS,
})

const FROM_CONTEXT_RE =
  /\b(FROM|JOIN)\b/gi
const NON_FROM_CONTEXT_RE =
  /\b(SELECT|WHERE|ORDER\s+BY|GROUP\s+BY|HAVING|ON|SET|LIMIT|OFFSET|AND|OR|WHEN|THEN|ELSE|VALUES)\b/gi

function isInFromContext(text: string): boolean {
  let lastPos = -1
  let isFrom = false

  for (const match of text.matchAll(FROM_CONTEXT_RE)) {
    if (match.index > lastPos) {
      lastPos = match.index
      isFrom = true
    }
  }
  for (const match of text.matchAll(NON_FROM_CONTEXT_RE)) {
    if (match.index > lastPos) {
      lastPos = match.index
      isFrom = false
    }
  }

  return isFrom
}

function buildCompletionSource(schema?: Record<string, string[]>) {
  const keywordOptions: Completion[] = KEYWORDS.split(" ").map((k) => ({
    label: k.toUpperCase(),
    type: "keyword",
    boost: -2,
  }))
  const typeOptions: Completion[] = TYPES.split(" ").map((t) => ({
    label: t.toUpperCase(),
    type: "type",
    boost: -2,
  }))
  const builtinOptions: Completion[] = BUILTINS.split(" ").map((b) => ({
    label: b.toUpperCase(),
    type: "function",
    boost: -1,
  }))

  const tableOptions: Completion[] = schema
    ? Object.keys(schema).map((t) => ({ label: t, type: "class" }))
    : []

  const columnOptions: Completion[] = schema
    ? [...new Set(Object.values(schema).flat())].map((c) => ({
        label: c,
        type: "property",
      }))
    : []

  return (context: CompletionContext): CompletionResult | null => {
    // table.column 패턴 처리
    if (schema) {
      const dotMatch = context.matchBefore(/\w+\.\w*/)
      if (dotMatch) {
        const dotIdx = dotMatch.text.indexOf(".")
        const tableName = dotMatch.text.slice(0, dotIdx)
        const columns = schema[tableName]
        if (columns) {
          return {
            from: dotMatch.from + dotIdx + 1,
            options: columns.map((c) => ({
              label: c,
              type: "property" as const,
            })),
          }
        }
      }
    }

    const word = context.matchBefore(/\w+/)
    if (!word && !context.explicit) return null
    const from = word?.from ?? context.pos

    const textBefore = context.state.doc.sliceString(0, context.pos)
    const inFrom = isInFromContext(textBefore)

    // FROM/JOIN 절: 테이블명 + 키워드만
    // 그 외: 컬럼명 + 테이블명 + 키워드
    const options = inFrom
      ? [...tableOptions, ...keywordOptions, ...typeOptions, ...builtinOptions]
      : [
          ...columnOptions,
          ...tableOptions,
          ...keywordOptions,
          ...typeOptions,
          ...builtinOptions,
        ]

    return { from, options }
  }
}

export function QueryEditor({
  value,
  onChange,
  onExecute,
  tables,
}: QueryEditorProps) {
  const editorRef = useRef<HTMLDivElement>(null)
  const viewRef = useRef<EditorView | null>(null)
  const onExecuteRef = useRef(onExecute)
  onExecuteRef.current = onExecute

  const completionSource = useMemo(
    () => buildCompletionSource(tables),
    [tables],
  )

  const handleExecute = useCallback(() => {
    onExecuteRef.current()
    return true
  }, [])

  useEffect(() => {
    if (!editorRef.current) return

    const isDark = document.documentElement.classList.contains("dark")

    const state = EditorState.create({
      doc: value,
      extensions: [
        basicSetup,
        sql({ dialect: duckDialect }),
        autocompletion({ override: [completionSource] }),
        Prec.highest(
          keymap.of([
            {
              key: "Ctrl-Enter",
              mac: "Cmd-Enter",
              run: handleExecute,
            },
          ]),
        ),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            onChange(update.state.doc.toString())
          }
        }),
        placeholder(
          `Write your SQL query here... (${/Mac|iPhone|iPad/.test(navigator.userAgent) ? "⌘" : "Ctrl"}+Enter to execute)`,
        ),
        EditorView.theme({
          "&": {
            fontSize: "14px",
            height: "100%",
          },
          ".cm-scroller": {
            overflow: "auto",
          },
          ".cm-content": {
            minHeight: "200px",
          },
        }),
        ...(isDark ? [oneDark] : []),
      ],
    })

    const view = new EditorView({
      state,
      parent: editorRef.current,
    })

    viewRef.current = view

    return () => {
      view.destroy()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [completionSource])

  return (
    <div
      ref={editorRef}
      className="h-full overflow-hidden rounded-xl border"
    />
  )
}
