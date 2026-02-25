import { useEffect, useState } from "react"
import { Moon, Sun } from "lucide-react"
import { type Theme, getStoredTheme, setStoredTheme } from "@/lib/theme"
import { SidebarMenuButton } from "@/components/ui/sidebar"

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(getStoredTheme)

  useEffect(() => {
    setStoredTheme(theme)
  }, [theme])

  function cycleTheme() {
    setTheme((current) => {
      if (current === "light") return "dark"
      if (current === "dark") return "system"
      return "light"
    })
  }

  const label =
    theme === "light" ? "Light" : theme === "dark" ? "Dark" : "System"
  const Icon = theme === "dark" ? Moon : Sun

  return (
    <SidebarMenuButton onClick={cycleTheme}>
      <Icon className="h-4 w-4" />
      <span>Theme: {label}</span>
    </SidebarMenuButton>
  )
}
