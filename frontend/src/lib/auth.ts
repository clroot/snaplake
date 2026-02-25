const TOKEN_KEY = "snaplake_token"
const TOKEN_EXPIRES_KEY = "snaplake_token_expires"
const USERNAME_KEY = "snaplake_username"

export function getToken(): string | null {
  const token = localStorage.getItem(TOKEN_KEY)
  const expires = localStorage.getItem(TOKEN_EXPIRES_KEY)

  if (!token || !expires) {
    return null
  }

  if (new Date(expires) <= new Date()) {
    clearAuth()
    return null
  }

  return token
}

export function setAuth(
  token: string,
  expiresAt: string,
  username: string,
): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(TOKEN_EXPIRES_KEY, expiresAt)
  localStorage.setItem(USERNAME_KEY, username)
}

export function getUsername(): string {
  return localStorage.getItem(USERNAME_KEY) ?? "User"
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TOKEN_EXPIRES_KEY)
  localStorage.removeItem(USERNAME_KEY)
}

export function isAuthenticated(): boolean {
  return getToken() !== null
}
