import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import type { StoredSession } from '../auth/session'

type Props = {
  onSession: (session: StoredSession) => void
}

type Handoff = {
  code: string
  destination: string
}

function readHandoff(): Handoff | null {
  const hash = window.location.hash.replace(/^#/, '')
  const params = new URLSearchParams(hash)
  const code = params.get('code')
  const rawId = params.get('publication')

  if (!code || !/^[A-Za-z0-9_-]{43}$/.test(code)) return null

  let destination = '/dashboard'
  if (rawId && /^[1-9]\d*$/.test(rawId)) {
    const id = Number(rawId)
    if (Number.isSafeInteger(id)) destination = `/publications/${id}`
  }
  return { code, destination }
}

async function redeemCode(code: string): Promise<StoredSession> {
  const response = await fetch('http://localhost:8080/api/auth/extension-exchange', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
    credentials: 'omit',
    cache: 'no-store',
  })

  if (!response.ok) {
    if (response.status === 410) {
      throw new Error('El enlace expiró o ya fue utilizado. Abrilo nuevamente desde la extensión.')
    }
    throw new Error(`No se pudo iniciar sesión desde la extensión (HTTP ${response.status}).`)
  }

  const result: unknown = await response.json()
  if (
    !result || typeof result !== 'object' ||
    !('token' in result) || typeof result.token !== 'string' ||
    !('expiresIn' in result) || typeof result.expiresIn !== 'number' ||
    !Number.isFinite(result.expiresIn) || result.expiresIn <= 0
  ) {
    throw new Error('El servidor devolvió una sesión inválida.')
  }

  return {
    token: result.token,
    expiresAt: Date.now() + result.expiresIn * 1000,
  }
}

// En desarrollo React StrictMode puede ejecutar efectos dos veces.
// El ref permite que ambas ejecuciones esperen la misma solicitud.
export default function ExtensionLoginPage({ onSession }: Props) {
  const navigate = useNavigate()
  const [handoff] = useState<Handoff | null>(readHandoff)
  const [error, setError] = useState('')
  const requestRef = useRef<Promise<StoredSession> | null>(null)
  const destination = handoff?.destination ?? '/dashboard'

  useEffect(() => {
    // El código va en el fragmento (no se envía como parte de la petición HTTP).
    // Limpiarlo de la barra de direcciones lo antes posible.
    window.history.replaceState(
      window.history.state,
      '',
      window.location.pathname + window.location.search,
    )

    if (!handoff) {
      setError('El enlace de la extensión no es válido.')
      return
    }

    if (!requestRef.current) requestRef.current = redeemCode(handoff.code)
    let active = true

    requestRef.current
      .then((session) => {
        if (!active) return
        onSession(session)
        navigate(destination, { replace: true })
      })
      .catch((err: unknown) => {
        if (!active) return
        setError(err instanceof Error ? err.message : 'No se pudo iniciar sesión.')
      })

    return () => { active = false }
  }, [handoff, destination, onSession, navigate])

  return (
    <section className="login-container">
      <h2>Acceso desde la extensión</h2>
      {error ? (
        <>
          <p className="error" role="alert">{error}</p>
          <a href={`/login?redirect=${encodeURIComponent(destination)}`}>
            Iniciar sesión manualmente
          </a>
        </>
      ) : (
        <p role="status">Conectando con tu sesión de MarketLens…</p>
      )}
    </section>
  )
}
