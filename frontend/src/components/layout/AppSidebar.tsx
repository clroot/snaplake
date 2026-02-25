import { useState } from "react"
import { useNavigate, useRouterState } from "@tanstack/react-router"
import {
  ChevronDown,
  Database,
  LayoutDashboard,
  Layers,
  LogOut,
  Monitor,
  Moon,
  Search,
  Settings,
  Split,
  Sun,
} from "lucide-react"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  useSidebar,
} from "@/components/ui/sidebar"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { clearAuth, getUsername } from "@/lib/auth"
import { type Theme, getStoredTheme, setStoredTheme } from "@/lib/theme"
import { SnaplakeLogo } from "@/components/common/SnaplakeLogo"

const mainNavItems = [
  { title: "Dashboard", icon: LayoutDashboard, path: "/" },
  { title: "Datasources", icon: Database, path: "/datasources" },
  { title: "Snapshots", icon: Layers, path: "/snapshots" },
  { title: "Query", icon: Search, path: "/query" },
  { title: "Compare", icon: Split, path: "/compare" },
]

const settingsNavItems = [
  { title: "Storage", path: "/settings/storage" },
  { title: "Account", path: "/settings/account" },
]

function ThemeIcon({ theme }: { theme: Theme }) {
  if (theme === "dark") return <Moon className="h-4 w-4" />
  if (theme === "light") return <Sun className="h-4 w-4" />
  return <Monitor className="h-4 w-4" />
}

export function AppSidebar() {
  const navigate = useNavigate()
  const routerState = useRouterState()
  const currentPath = routerState.location.pathname
  const { toggleSidebar } = useSidebar()
  const username = getUsername()
  const [theme, setTheme] = useState<Theme>(getStoredTheme)

  function handleLogout() {
    clearAuth()
    navigate({ to: "/login" })
  }

  function handleThemeChange(newTheme: Theme) {
    setTheme(newTheme)
    setStoredTheme(newTheme)
  }

  const isSettingsActive = currentPath.startsWith("/settings")

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              onClick={toggleSidebar}
              tooltip="Toggle sidebar"
            >
              <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                <SnaplakeLogo className="size-4" />
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-bold">Snaplake</span>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Navigation</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {mainNavItems.map((item) => (
                <SidebarMenuItem key={item.path}>
                  <SidebarMenuButton
                    tooltip={item.title}
                    isActive={
                      item.path === "/"
                        ? currentPath === "/"
                        : currentPath.startsWith(item.path)
                    }
                    onClick={() => navigate({ to: item.path })}
                  >
                    <item.icon className="h-4 w-4" />
                    <span>{item.title}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}

              <Collapsible
                defaultOpen={isSettingsActive}
                className="group/collapsible"
              >
                <SidebarMenuItem>
                  <CollapsibleTrigger asChild>
                    <SidebarMenuButton
                      tooltip="Settings"
                      isActive={isSettingsActive}
                    >
                      <Settings className="h-4 w-4" />
                      <span>Settings</span>
                      <ChevronDown className="ml-auto h-4 w-4 transition-transform group-data-[state=open]/collapsible:rotate-180" />
                    </SidebarMenuButton>
                  </CollapsibleTrigger>
                  <CollapsibleContent>
                    <SidebarMenuSub>
                      {settingsNavItems.map((item) => (
                        <SidebarMenuSubItem key={item.path}>
                          <SidebarMenuSubButton
                            isActive={currentPath.startsWith(item.path)}
                            onClick={() => navigate({ to: item.path })}
                          >
                            <span>{item.title}</span>
                          </SidebarMenuSubButton>
                        </SidebarMenuSubItem>
                      ))}
                    </SidebarMenuSub>
                  </CollapsibleContent>
                </SidebarMenuItem>
              </Collapsible>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <SidebarMenuButton size="lg" tooltip={username}>
                  <Avatar className="h-8 w-8 rounded-lg">
                    <AvatarFallback className="rounded-lg">
                      {username[0].toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                  <div className="grid flex-1 text-left text-sm leading-tight">
                    <span className="truncate font-semibold">{username}</span>
                  </div>
                </SidebarMenuButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent
                className="w-[--radix-dropdown-menu-trigger-width] min-w-56 rounded-lg"
                side="top"
                align="start"
                sideOffset={4}
              >
                <DropdownMenuSub>
                  <DropdownMenuSubTrigger>
                    <ThemeIcon theme={theme} />
                    Theme
                  </DropdownMenuSubTrigger>
                  <DropdownMenuSubContent>
                    <DropdownMenuItem
                      onClick={() => handleThemeChange("light")}
                    >
                      <Sun className="h-4 w-4" />
                      Light
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onClick={() => handleThemeChange("dark")}
                    >
                      <Moon className="h-4 w-4" />
                      Dark
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onClick={() => handleThemeChange("system")}
                    >
                      <Monitor className="h-4 w-4" />
                      System
                    </DropdownMenuItem>
                  </DropdownMenuSubContent>
                </DropdownMenuSub>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={handleLogout}>
                  <LogOut className="h-4 w-4" />
                  Logout
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  )
}
