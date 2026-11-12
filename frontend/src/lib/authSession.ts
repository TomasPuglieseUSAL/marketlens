export type StoredSession = {
  token: string;
  expiresAt: number;
};

const SESSION_KEY = "marketlens_session";

export function loadSession(): StoredSession | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);

    if (!raw) return null;

    const session = JSON.parse(raw) as StoredSession;

    if (
      typeof session.token !== "string" ||
      !session.token ||
      typeof session.expiresAt !== "number" ||
      session.expiresAt <= Date.now()
    ) {
      clearSession();
      return null;
    }

    return session;
  } catch {
    clearSession();
    return null;
  }
}

export function saveSession(session: StoredSession): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_KEY);
}