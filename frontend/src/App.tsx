import { useCallback, useEffect, useLayoutEffect, useState, type FormEvent, type ReactNode } from 'react'

import {
  Navigate,
  NavLink,
  Link,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router'

import {
  readSession,
  saveSession,
  clearSession,
  SESSION_KEY,
  type StoredSession,
} from './auth/session'

import {
  login,
  setUnauthorizedHandler,
} from './api/client'

import DashboardPanel from './components/DashboardPanel'
import SellersPanel from './components/SellersPanel'
import PublicationsPanel from './components/PublicationsPanel'
import ProtectedRoute from './components/ProtectedRoute'
import PublicationDetailsPage from './components/PublicationDetailsPage'
import ExtensionLoginPage from './components/ExtensionLoginPage'
import RegisterPage from './components/RegisterPage'

import './App.css'

// Compatibilidad con los enlaces que ya genera la extensión:
// http://localhost:5173/?publication=54
function getPublicationPath(search: string): string | null {
  const rawId = new URLSearchParams(search).get('publication')

  if (!rawId || !/^[1-9]\d*$/.test(rawId)) return null

  const publicationId = Number(rawId)
  if (!Number.isSafeInteger(publicationId)) return null

  return `/publications/${publicationId}`
}

// Solo permitimos destinos internos conocidos para evitar redirecciones abiertas.
function getSafeReturnPath(search: string): string | null {
  const destination = new URLSearchParams(search).get('redirect')

  if (!destination) return null
  if (destination === '/dashboard' || destination === '/sellers' || destination === '/publications') {
    return destination
  }

  const match = /^\/publications\/([1-9]\d*)$/.exec(destination)
  if (!match || !Number.isSafeInteger(Number(match[1]))) return null

  return destination
}

function loginUrlFor(destination: string): string {
  return `/login?redirect=${encodeURIComponent(destination)}`
}

// La preferencia visual no se vincula con la sesión ni con el JWT.
type Theme = 'light' | 'dark'
const THEME_KEY = 'marketlens.frontend.theme'

function readTheme(): Theme {
  try {
    return window.localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

// Permite animar el contenido de las rutas sin desmontar inmediatamente la
// pagina saliente. El encabezado, la navegacion y el fondo quedan fuera.
const PAGE_EXIT_MS = 170

function isContentRoute(pathname: string): boolean {
  return pathname === '/dashboard'
    || pathname === '/sellers'
    || pathname === '/publications'
    || /^\/publications\/[1-9]\d*$/.test(pathname)
}

function AnimatedRoutes({
  children,
  enabled,
}: {
  children: ReactNode
  enabled: boolean
}) {
  const location = useLocation()
  const [visibleLocation, setVisibleLocation] = useState(location)
  const [leaving, setLeaving] = useState(false)

  useLayoutEffect(() => {
    // Cambios en parametros de consulta: actualizar sin remount ni animacion.
    if (visibleLocation.pathname === location.pathname) {
      if (visibleLocation !== location) setVisibleLocation(location)
      setLeaving(false)
      return
    }

    // No retrasar rutas de redireccion ni cambios de autenticacion.
    // Sin animaciones cuando el usuario solicita movimiento reducido.
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const canAnimate = enabled
      && !reducedMotion
      && isContentRoute(visibleLocation.pathname)
      && isContentRoute(location.pathname)

    if (!canAnimate) {
      setVisibleLocation(location)
      setLeaving(false)
      return
    }

    setLeaving(true)
    const timer = window.setTimeout(() => {
      setVisibleLocation(location)
      setLeaving(false)
    }, PAGE_EXIT_MS)

    // Si el usuario cambia de seccion otra vez, gana la ultima navegacion.
    return () => window.clearTimeout(timer)
  }, [location, visibleLocation, enabled])

  return (
    <div
      key={visibleLocation.pathname}
      className={`page-transition${leaving ? ' page-transition--leaving' : ''}`}
    >
      <Routes location={visibleLocation}>{children}</Routes>
    </div>
  )
}

function App() {
  const navigate = useNavigate()
  const location = useLocation()
  const [theme, setTheme] = useState<Theme>(readTheme)

  // Aplicar el tema al elemento raíz ANTES de pintar la interfaz.
  // Así se actualizan todas las superficies mediante variables CSS.
  useLayoutEffect(() => {
    document.documentElement.dataset.theme = theme
  }, [theme])

  // Reflejar los cambios hechos desde otra pestaña de MarketLens.
  useEffect(() => {
    function handleThemeStorage(event: StorageEvent) {
      if (event.storageArea !== window.localStorage || event.key !== THEME_KEY) return
      setTheme(event.newValue === 'dark' ? 'dark' : 'light')
    }
    window.addEventListener('storage', handleThemeStorage)
    return () => window.removeEventListener('storage', handleThemeStorage)
  }, [])

  function toggleTheme() {
    const next: Theme = theme === 'light' ? 'dark' : 'light'
    setTheme(next)
    try {
      window.localStorage.setItem(THEME_KEY, next)
    } catch {
      // El cambio sigue funcionando durante esta sesión si el almacenamiento falla.
    }
  }

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  const [session, setSession] = useState<StoredSession | null>(readSession)
  const token = session?.token ?? null
  const expiresAt = session?.expiresAt ?? null

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [sessionMessage, setSessionMessage] = useState('')

  const afterLoginPath = getSafeReturnPath(location.search) ?? '/dashboard'
  const publicationPath = getPublicationPath(location.search)

  // Parallax ligero de la grilla: se mueve a ~1/3 de la velocidad del contenido.
  // El gradiente no participa del movimiento: permanece ajustado al viewport.
  useEffect(() => {
    const root = document.documentElement
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
    let frame = 0

    const renderGridOffset = () => {
      frame = 0
      root.style.setProperty(
        '--grid-parallax-offset',
        reducedMotion.matches ? '0px' : `${-Math.round(window.scrollY * 0.32)}px`
      )
    }

    const scheduleUpdate = () => {
      if (!frame) frame = window.requestAnimationFrame(renderGridOffset)
    }

    const handleMotionPreference = () => scheduleUpdate()

    renderGridOffset()
    window.addEventListener('scroll', scheduleUpdate, { passive: true })
    reducedMotion.addEventListener('change', handleMotionPreference)

    return () => {
      window.removeEventListener('scroll', scheduleUpdate)
      reducedMotion.removeEventListener('change', handleMotionPreference)
      if (frame) window.cancelAnimationFrame(frame)
      root.style.removeProperty('--grid-parallax-offset')
    }
  }, [])

  const acceptExtensionSession = useCallback((newSession: StoredSession) => {
    saveSession(newSession)
    setSession(newSession)
    setSessionMessage('')
  }, [])

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    setLoading(true)
    setError('')

    try {
      const auth = await login(username, password)
      const expirationTime = Date.now() + auth.expiresIn * 1000

      const newSession: StoredSession = {
        token: auth.token,
        expiresAt: expirationTime,
      }

      saveSession(newSession)
      setSession(newSession)
      setPassword('')
      setSessionMessage('')

      // Antes: siempre navegaba a /dashboard.
      // Ahora: si el login fue provocado por un enlace a una publicación,
      // vuelve a esa publicación.
      navigate(afterLoginPath, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error inesperado')
    } finally {
      setLoading(false)
    }
  }

  function handleLogout() {
    clearSession()
    setSession(null)
    setPassword('')
    setError('')
    setSessionMessage('')

    navigate('/login', { replace: true })
  }

  // Mantener sincronizada la sesión entre pestañas del frontend.
  useEffect(() => {
    function handleSessionStorageChange(event: StorageEvent) {
      if (event.storageArea !== window.localStorage) return
      if (event.key !== SESSION_KEY && event.key !== null) return

      const updatedSession = readSession()
      setSession(updatedSession)
      setSessionMessage(
        updatedSession ? '' : 'La sesión se cerró en otra pestaña.'
      )
    }

    window.addEventListener('storage', handleSessionStorageChange)
    return () => window.removeEventListener('storage', handleSessionStorageChange)
  }, [])

  // Cerrar sesión automáticamente cuando vence el JWT.
  useEffect(() => {
    if (!token || expiresAt === null) return

    const remainingTime = expiresAt - Date.now()
    const timeout = window.setTimeout(() => {
      // Otra pestaña podría haber renovado la sesión.
      const current = readSession()
      if (current && (current.token !== token || current.expiresAt > expiresAt)) {
        setSession(current)
        return
      }

      clearSession()
      setSession(null)
      setSessionMessage('Tu sesión expiró. Iniciá sesión nuevamente.')
    }, Math.max(0, remainingTime))

    return () => window.clearTimeout(timeout)
  }, [token, expiresAt])

  // Cerrar sesión si el backend rechaza el JWT.
  useEffect(() => {
    if (!token) return

    setUnauthorizedHandler((rejectedToken) => {
      if (rejectedToken !== token) return

      clearSession()
      setSession(null)
      setSessionMessage('Tu sesión ya no es válida. Iniciá sesión nuevamente.')
    })

    return () => setUnauthorizedHandler(null)
  }, [token])

  return (
    <div className="app-container">
      <header className="app-header">
        <div className="brand-lockup">
          <span className="brand-mark" aria-hidden="true">M</span>
          <div className="brand-copy">
            <h1>MarketLens</h1>
            <p>Plataforma colaborativa de evaluación de riesgo</p>
          </div>
        </div>

        {token && (
          <div className="header-actions">
            <button
              type="button"
              className="secondary-button theme-toggle"
              onClick={toggleTheme}
              title={theme === 'light' ? 'Cambiar a modo oscuro' : 'Cambiar a modo claro'}
              aria-label={theme === 'light' ? 'Activar modo oscuro' : 'Activar modo claro'}
            >
              <span className="theme-toggle-icon" aria-hidden="true">
                {theme === 'light' ? '☾' : '☀'}
              </span>
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={handleLogout}
            >
              Cerrar sesión
            </button>
          </div>
        )}
      </header>

      {token && (
        <nav className="main-navigation">
          <NavLink
            to="/dashboard"
            className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}
          >
            Dashboard
          </NavLink>

          <NavLink
            to="/sellers"
            className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}
          >
            Vendedores
          </NavLink>

          <NavLink
            to="/publications"
            className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}
          >
            Publicaciones
          </NavLink>
        </nav>
      )}

      <main>
        <AnimatedRoutes enabled={Boolean(token)}>
          <Route
            path="/auth/extension"
            element={<ExtensionLoginPage onSession={acceptExtensionSession} />}
          />

          <Route
            path="/"
            element={
              <Navigate
                to={
                  publicationPath
                    ? token
                      ? publicationPath
                      : loginUrlFor(publicationPath)
                    : token
                      ? '/dashboard'
                      : '/login'
                }
                replace
              />
            }
          />

          <Route
            path="/login"
            element={
              token ? (
                <Navigate to={afterLoginPath} replace />
              ) : (
                <section className="login-container">
                  <h2>Iniciar sesión</h2>

                  {new URLSearchParams(location.search).get('registered') === '1' && (
                    <p className="session-message" role="status">
                      Cuenta creada correctamente. Ya podés iniciar sesión.
                    </p>
                  )}

                  {sessionMessage && (
                    <p className="session-message" role="status">
                      {sessionMessage}
                    </p>
                  )}

                  {error && (
                    <p className="error" role="alert">
                      {error}
                    </p>
                  )}

                  <form className="panel-form" onSubmit={handleLogin}>
                    <label htmlFor="username">Usuario</label>
                    <input
                      id="username"
                      type="text"
                      autoComplete="username"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      required
                    />

                    <label htmlFor="password">Contraseña</label>
                    <input
                      id="password"
                      type="password"
                      autoComplete="current-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                    />

                    <button type="submit" disabled={loading}>
                      {loading ? 'Ingresando...' : 'Ingresar'}
                    </button>
                  </form>
                  <p className="auth-switch">
                    ¿Todavía no tenés cuenta?{' '}
                    <Link to={`/register?redirect=${encodeURIComponent(afterLoginPath)}`}>
                      Crear cuenta
                    </Link>
                  </p>
                </section>
              )
            }
          />

          <Route
            path="/register"
            element={
              token ? (
                <Navigate to={afterLoginPath} replace />
              ) : (
                <RegisterPage returnTo={afterLoginPath} />
              )
            }
          />

          <Route
            path="/dashboard"
            element={
              <ProtectedRoute token={token}>
                <DashboardPanel token={token!} />
              </ProtectedRoute>
            }
          />

          <Route
            path="/sellers"
            element={
              <ProtectedRoute token={token}>
                <SellersPanel token={token!} />
              </ProtectedRoute>
            }
          />

          <Route
            path="/publications"
            element={
              <ProtectedRoute token={token}>
                <PublicationsPanel token={token!} />
              </ProtectedRoute>
            }
          />

          <Route
            path="/publications/:id"
            element={
              token ? (
                <PublicationDetailsPage token={token} />
              ) : (
                <Navigate
                  to={loginUrlFor(location.pathname)}
                  replace
                />
              )
            }
          />

          <Route
            path="*"
            element={
              <Navigate to={token ? '/dashboard' : '/login'} replace />
            }
          />
        </AnimatedRoutes>
      </main>
    </div>
  )
}

export default App
