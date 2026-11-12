export interface StoredSession {
  token: string
  expiresAt: number
}

export const SESSION_KEY = 'marketlens.session'

export function readSession(): StoredSession | null {
  try {
    const stored = localStorage.getItem(SESSION_KEY)

    if (!stored) {
      return null
    }

    const session = JSON.parse(stored) as Partial<StoredSession>

    if (
      typeof session.token !== 'string' ||
      !session.token ||
      typeof session.expiresAt !== 'number' ||
      !Number.isFinite(session.expiresAt) ||
      session.expiresAt <= Date.now()
    ) {
      clearSession()
      return null
    }

    return {
      token: session.token,
      expiresAt: session.expiresAt,
    }
  } catch {
    clearSession()
    return null
  }
}

export function saveSession(session: StoredSession): void {
  try {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  } catch {
    // Si el almacenamiento está bloqueado, la sesión
    // seguirá funcionando en el estado de React.
  }
}

export function clearSession(): void {
  try {
    localStorage.removeItem(SESSION_KEY)
    // Limpieza de la versión anterior basada en sessionStorage.
    sessionStorage.removeItem(SESSION_KEY)
  } catch {
    // El almacenamiento podría no estar disponible.
  }
}
