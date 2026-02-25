const THEME_KEY = "snaplake-theme"

export type Theme = "light" | "dark" | "system"

export function getStoredTheme(): Theme {
  const stored = localStorage.getItem(THEME_KEY)
  if (stored === "light" || stored === "dark" || stored === "system") {
    return stored
  }
  return "system"
}

export function setStoredTheme(theme: Theme): void {
  localStorage.setItem(THEME_KEY, theme)
  applyTheme(theme)
}

export function applyTheme(theme: Theme): void {
  const root = document.documentElement
  const isDark =
    theme === "dark" ||
    (theme === "system" &&
      window.matchMedia("(prefers-color-scheme: dark)").matches)

  root.classList.toggle("dark", isDark)
}

export function initTheme(): void {
  const theme = getStoredTheme()
  applyTheme(theme)

  // Listen for system theme changes when "system" is selected
  window
    .matchMedia("(prefers-color-scheme: dark)")
    .addEventListener("change", () => {
      if (getStoredTheme() === "system") {
        applyTheme("system")
      }
    })
}
