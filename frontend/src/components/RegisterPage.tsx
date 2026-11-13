import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { register } from '../api/client'

interface Props {
  returnTo: string
}

function registrationError(error: unknown): string {
  const message = error instanceof Error ? error.message : ''
  if (message.includes('Username is already in use')) {
    return 'Ese nombre de usuario ya está registrado.'
  }
  if (message.includes('Email is already in use')) {
    return 'Ese correo electrónico ya está registrado.'
  }
  if (message.includes('Validation failed')) {
    return 'Revisá los datos: usuario de 3 a 100 caracteres, correo válido y contraseña de 8 a 100 caracteres.'
  }
  return message || 'No se pudo crear la cuenta. Intentá nuevamente.'
}

export default function RegisterPage({ returnTo }: Props) {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const suffix = returnTo === '/dashboard'
    ? ''
    : `&redirect=${encodeURIComponent(returnTo)}`
  const loginPath = returnTo === '/dashboard'
    ? '/login'
    : `/login?redirect=${encodeURIComponent(returnTo)}`

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (loading) return
    setError('')

    const cleanUsername = username.trim()
    const cleanEmail = email.trim()
    if (cleanUsername.length < 3 || cleanUsername.length > 100) {
      setError('El usuario debe tener entre 3 y 100 caracteres.')
      return
    }
    if (!cleanEmail || cleanEmail.length > 255) {
      setError('Ingresá un correo electrónico válido de hasta 255 caracteres.')
      return
    }
    if (password.length < 8 || password.length > 100) {
      setError('La contraseña debe tener entre 8 y 100 caracteres.')
      return
    }
    if (password !== confirmPassword) {
      setError('Las contraseñas no coinciden.')
      return
    }

    setLoading(true)
    try {
      await register({ username: cleanUsername, email: cleanEmail, password })
      setPassword('')
      setConfirmPassword('')
      navigate(`/login?registered=1${suffix}`, { replace: true })
    } catch (err) {
      setError(registrationError(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="login-container" aria-labelledby="register-title">
      <h2 id="register-title">Crear cuenta</h2>
      <p className="auth-description">
        Registrate para analizar publicaciones y enviar reportes desde MarketLens.
      </p>
      {error && <p className="error" role="alert">{error}</p>}
      <form className="panel-form" onSubmit={handleSubmit}>
        <label htmlFor="register-username">Nombre de usuario</label>
        <input
          id="register-username"
          type="text"
          autoComplete="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          minLength={3}
          maxLength={100}
          required
        />
        <label htmlFor="register-email">Correo electrónico</label>
        <input
          id="register-email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          maxLength={255}
          required
        />
        <label htmlFor="register-password">Contraseña</label>
        <input
          id="register-password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          minLength={8}
          maxLength={100}
          required
        />
        <p className="auth-hint">Entre 8 y 100 caracteres.</p>
        <label htmlFor="register-confirm-password">Confirmar contraseña</label>
        <input
          id="register-confirm-password"
          type="password"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          minLength={8}
          maxLength={100}
          required
        />
        <button type="submit" disabled={loading}>
          {loading ? 'Creando cuenta...' : 'Crear cuenta'}
        </button>
      </form>
      <p className="auth-switch">
        ¿Ya tenés cuenta? <Link to={loginPath}>Iniciar sesión</Link>
      </p>
    </section>
  )
}
