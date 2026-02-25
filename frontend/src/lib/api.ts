import { getToken, clearAuth } from "./auth"

const API_BASE = ""

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown
}

class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.code = code
  }
}

async function request<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { body, headers: customHeaders, ...rest } = options
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...((customHeaders as Record<string, string>) ?? {}),
  }

  const token = getToken()
  if (token) {
    headers["Authorization"] = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  if (response.status === 401 || response.status === 403) {
    const hadToken = !!getToken()
    clearAuth()
    if (hadToken) {
      window.location.href = "/login"
      throw new ApiError(response.status, "UNAUTHORIZED", "Session expired")
    }
  }

  const text = await response.text()

  if (!response.ok) {
    const data = text ? JSON.parse(text) : null
    throw new ApiError(
      response.status,
      data?.error?.code ?? "UNKNOWN",
      data?.error?.message ?? "An error occurred",
    )
  }

  if (!text) {
    return undefined as T
  }

  return JSON.parse(text) as T
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "GET" }),

  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "POST", body }),

  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "PUT", body }),

  delete: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "DELETE" }),
}

export { ApiError }
