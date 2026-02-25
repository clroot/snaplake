import { Outlet } from "@tanstack/react-router"
import { AppSidebar } from "./AppSidebar"
import { AppBreadcrumb } from "./AppBreadcrumb"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"
import { ShortcutHelp } from "@/components/common/ShortcutHelp"

export function AppLayout() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
          <AppBreadcrumb />
        </header>
        <main className="min-w-0 flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </SidebarInset>
      <ShortcutHelp />
    </SidebarProvider>
  )
}
