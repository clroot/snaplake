import { useRouterState, Link } from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Fragment } from "react"

const segmentLabels: Record<string, string> = {
  "": "Dashboard",
  datasources: "Datasources",
  snapshots: "Snapshots",
  query: "Query",
  compare: "Compare",
  settings: "Settings",
  storage: "Storage",
  account: "Account",
}

function getLabel(segment: string): string {
  return segmentLabels[segment] ?? segment
}

interface BreadcrumbEntry {
  label: string
  href: string
}

function useDatasourceLabel(id: string | undefined) {
  const { data } = useQuery({
    queryKey: ["datasources", id],
    queryFn: () => api.get<{ name: string }>(`/api/datasources/${id}`),
    enabled: !!id,
  })
  return data?.name
}

function useSnapshotLabel(id: string | undefined) {
  const { data } = useQuery({
    queryKey: ["snapshots", id],
    queryFn: () =>
      api.get<{ datasourceName: string; snapshotDate: string }>(
        `/api/snapshots/${id}`,
      ),
    enabled: !!id,
  })
  return data
    ? `${data.datasourceName} (${data.snapshotDate})`
    : undefined
}

export function AppBreadcrumb() {
  const routerState = useRouterState()
  const pathname = routerState.location.pathname

  const segments =
    pathname === "/" ? [""] : pathname.replace(/\/$/, "").split("/").slice(1)

  // Identify dynamic segment IDs for reactive queries
  const datasourceId =
    segments[0] === "datasources" && segments[1] && !segmentLabels[segments[1]]
      ? segments[1]
      : undefined

  const snapshotId =
    segments[0] === "snapshots" && segments[1]
      ? segments[1]
      : undefined

  const datasourceName = useDatasourceLabel(datasourceId)
  const snapshotLabel = useSnapshotLabel(snapshotId)

  const entries: BreadcrumbEntry[] = []

  for (let i = 0; i < segments.length; i++) {
    const segment = segments[i]
    const href =
      i === 0 && segment === ""
        ? "/"
        : "/" + segments.slice(0, i + 1).join("/")

    // Datasource detail: show datasource name
    if (i > 0 && segments[0] === "datasources" && !segmentLabels[segment]) {
      entries.push({ label: datasourceName ?? segment, href })
      continue
    }

    // Snapshot routes: /snapshots/$snapshotId/$schema/$table
    if (segments[0] === "snapshots" && i === 1) {
      entries.push({ label: snapshotLabel ?? segment, href })
      continue
    }

    if (segments[0] === "snapshots" && i === 2) {
      continue
    }

    if (segments[0] === "snapshots" && i === 3) {
      entries.push({ label: segment, href })
      continue
    }

    entries.push({ label: getLabel(segment), href })
  }

  const pageTitle =
    entries.length > 0
      ? entries[entries.length - 1].label + " - Snaplake"
      : "Snaplake"

  if (entries.length === 1) {
    return (
      <>
        <title>{pageTitle}</title>
        <Breadcrumb>
          <BreadcrumbList>
            <BreadcrumbItem>
              <BreadcrumbPage>{entries[0].label}</BreadcrumbPage>
            </BreadcrumbItem>
          </BreadcrumbList>
        </Breadcrumb>
      </>
    )
  }

  return (
    <>
      <title>{pageTitle}</title>
      <Breadcrumb>
        <BreadcrumbList>
          {entries.map((entry, index) => {
            const isLast = index === entries.length - 1

            return (
              <Fragment key={index}>
                {index > 0 && <BreadcrumbSeparator />}
                <BreadcrumbItem>
                  {isLast ? (
                    <BreadcrumbPage>{entry.label}</BreadcrumbPage>
                  ) : (
                    <BreadcrumbLink asChild>
                      <Link to={entry.href}>{entry.label}</Link>
                    </BreadcrumbLink>
                  )}
                </BreadcrumbItem>
              </Fragment>
            )
          })}
        </BreadcrumbList>
      </Breadcrumb>
    </>
  )
}
