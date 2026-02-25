import { useEffect, useRef, useCallback } from "react"
import { EditorView, keymap, placeholder } from "@codemirror/view"
import { EditorState } from "@codemirror/state"
import { sql, SQLDialect } from "@codemirror/lang-sql"
import { basicSetup } from "codemirror"
import { oneDark } from "@codemirror/theme-one-dark"

interface QueryEditorProps {
  value: string
  onChange: (value: string) => void
  onExecute: () => void
  tables?: Record<string, string[]>
}

// DuckDB-like dialect
const duckDialect = SQLDialect.define({
  keywords:
    "select from where group by order having limit offset as on join left right inner outer cross full natural using with recursive union intersect except all distinct case when then else end and or not in is null between like ilike exists cast create insert update delete drop alter table index view into values set",
  types:
    "integer int bigint smallint tinyint float double real decimal numeric varchar text boolean date timestamp time blob",
  builtin:
    "count sum avg min max coalesce nullif abs round floor ceil upper lower trim length substring replace concat now current_date current_timestamp",
})

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

  const schema = tables
    ? Object.fromEntries(
        Object.entries(tables).map(([table, columns]) => [table, columns]),
      )
    : undefined

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
        sql({
          dialect: duckDialect,
          schema,
          upperCaseKeywords: true,
        }),
        keymap.of([
          {
            key: "Ctrl-Enter",
            mac: "Cmd-Enter",
            run: handleExecute,
          },
        ]),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            onChange(update.state.doc.toString())
          }
        }),
        placeholder("Write your SQL query here... (Ctrl+Enter to execute)"),
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
  }, [tables])

  return (
    <div
      ref={editorRef}
      className="h-full overflow-hidden rounded-xl border"
    />
  )
}
