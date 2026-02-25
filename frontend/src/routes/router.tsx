import {
  createRouter,
  createRoute,
  createRootRoute,
  redirect,
  isRedirect,
  Outlet,
} from "@tanstack/react-router"
import { isAuthenticated } from "@/lib/auth"
import { api } from "@/lib/api"

import { AppLayout } from "@/components/layout/AppLayout"

// Lazy page imports
import { SetupWizard } from "@/pages/SetupWizard"
import { LoginPage } from "@/pages/LoginPage"
import { DashboardPage } from "@/pages/DashboardPage"
import { DatasourcesPage } from "@/pages/DatasourcesPage"
import { DatasourceDetailPage } from "@/pages/DatasourceDetailPage"
import { SnapshotsPage } from "@/pages/SnapshotsPage"
import { SnapshotDetailPage } from "@/pages/SnapshotDetailPage"
import { SnapshotTablePage } from "@/pages/SnapshotTablePage"
import { QueryPage } from "@/pages/QueryPage"
import { ComparePage } from "@/pages/ComparePage"
import { StorageSettingsPage } from "@/pages/settings/StorageSettingsPage"
import { AccountSettingsPage } from "@/pages/settings/AccountSettingsPage"

interface SetupStatus {
  initialized: boolean
}

const rootRoute = createRootRoute({
  component: Outlet,
})

// Setup route - accessible only when not initialized
const setupRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/setup",
  beforeLoad: async () => {
    try {
      const status = await api.get<SetupStatus>("/api/setup/status")
      if (status.initialized) {
        throw redirect({ to: "/login" })
      }
    } catch (e) {
      if (isRedirect(e)) throw e
      // Network error - allow setup page to load
    }
  },
  component: SetupWizard,
})

// Login route
const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  beforeLoad: async () => {
    try {
      const status = await api.get<SetupStatus>("/api/setup/status")
      if (!status.initialized) {
        throw redirect({ to: "/setup" })
      }
      if (isAuthenticated()) {
        throw redirect({ to: "/" })
      }
    } catch (e) {
      if (isRedirect(e)) throw e
      // Network error - allow login page to load
    }
  },
  component: LoginPage,
})

// Authenticated layout route
const authenticatedRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "authenticated",
  beforeLoad: async () => {
    try {
      const status = await api.get<SetupStatus>("/api/setup/status")
      if (!status.initialized) {
        throw redirect({ to: "/setup" })
      }
      if (!isAuthenticated()) {
        throw redirect({ to: "/login" })
      }
    } catch (e) {
      if (isRedirect(e)) throw e
      // Network error - redirect to login as safe default
      throw redirect({ to: "/login" })
    }
  },
  component: AppLayout,
})

// Dashboard (index route)
const dashboardRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/",
  component: DashboardPage,
})

// Datasources
const datasourcesRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/datasources",
  component: DatasourcesPage,
})

const datasourceDetailRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/datasources/$id",
  component: DatasourceDetailPage,
})

// Snapshots
const snapshotsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/snapshots",
  component: SnapshotsPage,
})

const snapshotDetailRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/snapshots/$snapshotId",
  component: SnapshotDetailPage,
})

const snapshotTableRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/snapshots/$snapshotId/$schema/$table",
  component: SnapshotTablePage,
})

// Query
const queryRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/query",
  component: QueryPage,
  validateSearch: (search: Record<string, unknown>) => ({
    snapshotId: (search.snapshotId as string) || undefined,
  }),
})

// Compare
const compareRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/compare",
  component: ComparePage,
})

// Settings
const storageSettingsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/settings/storage",
  component: StorageSettingsPage,
})

const accountSettingsRoute = createRoute({
  getParentRoute: () => authenticatedRoute,
  path: "/settings/account",
  component: AccountSettingsPage,
})

const routeTree = rootRoute.addChildren([
  setupRoute,
  loginRoute,
  authenticatedRoute.addChildren([
    dashboardRoute,
    datasourcesRoute,
    datasourceDetailRoute,
    snapshotsRoute,
    snapshotDetailRoute,
    snapshotTableRoute,
    queryRoute,
    compareRoute,
    storageSettingsRoute,
    accountSettingsRoute,
  ]),
])

export const router = createRouter({ routeTree })

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router
  }
}
