import { useState, useEffect } from "react"
import { Label } from "@/components/ui/label"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import cronstrue from "cronstrue"

interface ScheduleInputProps {
  value: string | null
  onChange: (value: string | null) => void
}

type SimpleMode = "none" | "daily" | "weekly" | "monthly"

function cronToSimple(cron: string | null): {
  mode: SimpleMode
  hour: string
  minute: string
  dayOfWeek: string
  dayOfMonth: string
} {
  const defaults = {
    mode: "none" as SimpleMode,
    hour: "2",
    minute: "0",
    dayOfWeek: "1",
    dayOfMonth: "1",
  }

  if (!cron) return defaults

  const parts = cron.split(" ")
  if (parts.length !== 5) return { ...defaults, mode: "none" }

  const [min, hr, dom, , dow] = parts

  if (dom === "*" && dow === "*") {
    return { mode: "daily", hour: hr, minute: min, dayOfWeek: "1", dayOfMonth: "1" }
  }
  if (dom === "*" && dow !== "*") {
    return { mode: "weekly", hour: hr, minute: min, dayOfWeek: dow, dayOfMonth: "1" }
  }
  if (dom !== "*" && dow === "*") {
    return { mode: "monthly", hour: hr, minute: min, dayOfWeek: "1", dayOfMonth: dom }
  }

  return defaults
}

function simpleToCron(
  mode: SimpleMode,
  hour: string,
  minute: string,
  dayOfWeek: string,
  dayOfMonth: string,
): string | null {
  if (mode === "none") return null
  if (mode === "daily") return `${minute} ${hour} * * *`
  if (mode === "weekly") return `${minute} ${hour} * * ${dayOfWeek}`
  if (mode === "monthly") return `${minute} ${hour} ${dayOfMonth} * *`
  return null
}

function getCronDescription(cron: string): string {
  try {
    return cronstrue.toString(cron)
  } catch {
    return "Invalid cron expression"
  }
}

const DAYS_OF_WEEK = [
  { value: "0", label: "Sunday" },
  { value: "1", label: "Monday" },
  { value: "2", label: "Tuesday" },
  { value: "3", label: "Wednesday" },
  { value: "4", label: "Thursday" },
  { value: "5", label: "Friday" },
  { value: "6", label: "Saturday" },
]

export function ScheduleInput({ value, onChange }: ScheduleInputProps) {
  const initial = cronToSimple(value)
  const [tabMode, setTabMode] = useState<"simple" | "advanced">("simple")
  const [simpleMode, setSimpleMode] = useState<SimpleMode>(initial.mode)
  const [hour, setHour] = useState(initial.hour)
  const [minute, setMinute] = useState(initial.minute)
  const [dayOfWeek, setDayOfWeek] = useState(initial.dayOfWeek)
  const [dayOfMonth, setDayOfMonth] = useState(initial.dayOfMonth)
  const [advancedCron, setAdvancedCron] = useState(value ?? "")

  useEffect(() => {
    if (tabMode === "simple") {
      onChange(simpleToCron(simpleMode, hour, minute, dayOfWeek, dayOfMonth))
    }
  }, [simpleMode, hour, minute, dayOfWeek, dayOfMonth, tabMode, onChange])

  return (
    <div className="space-y-4">
      <Label>Schedule</Label>
      <Tabs
        value={tabMode}
        onValueChange={(v) => setTabMode(v as "simple" | "advanced")}
      >
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="simple">Simple</TabsTrigger>
          <TabsTrigger value="advanced">Advanced (Cron)</TabsTrigger>
        </TabsList>

        <TabsContent value="simple" className="space-y-4 pt-4">
          <Select
            value={simpleMode}
            onValueChange={(v) => setSimpleMode(v as SimpleMode)}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="none">No schedule (manual only)</SelectItem>
              <SelectItem value="daily">Daily</SelectItem>
              <SelectItem value="weekly">Weekly</SelectItem>
              <SelectItem value="monthly">Monthly</SelectItem>
            </SelectContent>
          </Select>

          {simpleMode !== "none" && (
            <div className="flex items-center gap-4">
              {simpleMode === "weekly" && (
                <div className="space-y-2">
                  <Label>Day</Label>
                  <Select value={dayOfWeek} onValueChange={setDayOfWeek}>
                    <SelectTrigger className="w-36">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {DAYS_OF_WEEK.map((day) => (
                        <SelectItem key={day.value} value={day.value}>
                          {day.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}
              {simpleMode === "monthly" && (
                <div className="space-y-2">
                  <Label>Day of Month</Label>
                  <Input
                    type="number"
                    min={1}
                    max={28}
                    value={dayOfMonth}
                    onChange={(e) => setDayOfMonth(e.target.value)}
                    className="w-24"
                  />
                </div>
              )}
              <div className="space-y-2">
                <Label>Hour</Label>
                <Input
                  type="number"
                  min={0}
                  max={23}
                  value={hour}
                  onChange={(e) => setHour(e.target.value)}
                  className="w-20"
                />
              </div>
              <div className="space-y-2">
                <Label>Minute</Label>
                <Input
                  type="number"
                  min={0}
                  max={59}
                  value={minute}
                  onChange={(e) => setMinute(e.target.value)}
                  className="w-20"
                />
              </div>
            </div>
          )}
        </TabsContent>

        <TabsContent value="advanced" className="space-y-4 pt-4">
          <div className="space-y-2">
            <Input
              value={advancedCron}
              onChange={(e) => setAdvancedCron(e.target.value)}
              placeholder="0 2 * * *"
              className="font-mono"
            />
            {advancedCron && (
              <p className="text-sm text-muted-foreground">
                {getCronDescription(advancedCron)}
              </p>
            )}
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              className="text-sm text-primary underline-offset-4 hover:underline"
              onClick={() => {
                onChange(advancedCron || null)
              }}
            >
              Apply
            </button>
          </div>
        </TabsContent>
      </Tabs>

      {value && (
        <p className="text-sm text-muted-foreground">
          Current: <code className="rounded bg-muted px-1 py-0.5">{value}</code>{" "}
          &mdash; {getCronDescription(value)}
        </p>
      )}
    </div>
  )
}
