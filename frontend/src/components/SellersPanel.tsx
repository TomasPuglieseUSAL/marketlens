import { useState, type FormEvent } from 'react'
import {
  createSeller,
  getSeller,
  type Seller,
} from '../api/client'
import SellersList from './SellersList'

interface SellersPanelProps {
  token: string
}

export default function SellersPanel({
  token,
}: SellersPanelProps) {
  const [name, setName] = useState('')
  const [reputation, setReputation] = useState('0.5')
  const [accountAgeDays, setAccountAgeDays] = useState('0')

  const [searchId, setSearchId] = useState('')
  const [seller, setSeller] = useState<Seller | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    setLoading(true)
    setMessage('')
    setError('')

    try {
      const created = await createSeller(token, {
        name: name.trim(),
        reputation: Number(reputation),
        accountAgeDays: Number(accountAgeDays),
      })

      setSeller(created)
      setMessage(`Vendedor creado correctamente. ID: ${created.id}`)

      setName('')
      setReputation('0.5')
      setAccountAgeDays('0')
      setRefreshKey((current) => current + 1)
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Error inesperado'
      )
    } finally {
      setLoading(false)
    }
  }

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await selectSeller(Number(searchId))
  }

  async function selectSeller(id: number) {
  setLoading(true)
  setError('')
  setMessage('')
  setSeller(null)

  try {
    const result = await getSeller(token, id)
    setSeller(result)
  } catch (err) {
    setError(
      err instanceof Error ? err.message : 'Error inesperado'
    )
  } finally {
    setLoading(false)
  }
  }

  return (
    <section className="sellers-panel">
      <h2>Gestión de vendedores</h2>

      <div className="details-grid">
        <article className="detail-card">
          <h3>Registrar vendedor</h3>

          <form className="panel-form" onSubmit={handleCreate}>
            <label htmlFor="seller-name">Nombre</label>
            <input
              id="seller-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={255}
            />

            <label htmlFor="seller-reputation">
              Reputación (0 a 1)
            </label>
            <input
              id="seller-reputation"
              type="number"
              min="0"
              max="1"
              step="0.01"
              value={reputation}
              onChange={(e) => setReputation(e.target.value)}
              required
            />

            <label htmlFor="seller-age">
              Antigüedad de la cuenta (días)
            </label>
            <input
              id="seller-age"
              type="number"
              min="0"
              step="1"
              value={accountAgeDays}
              onChange={(e) => setAccountAgeDays(e.target.value)}
              required
            />

            <button type="submit" disabled={loading}>
              Registrar vendedor
            </button>
          </form>
        </article>

        <article className="detail-card">
          <h3>Consultar vendedor</h3>

          <form className="panel-form" onSubmit={handleSearch}>
            <label htmlFor="seller-id">ID del vendedor</label>
            <input
              id="seller-id"
              type="number"
              min="1"
              step="1"
              value={searchId}
              onChange={(e) => setSearchId(e.target.value)}
              required
            />

            <button type="submit" disabled={loading}>
              Buscar vendedor
            </button>
          </form>
        </article>
      </div>

      {message && <p className="success-message">{message}</p>}
      {error && <p className="error">{error}</p>}

      <SellersList
        token={token}
        refreshKey={refreshKey}
        onSelect={selectSeller}
      />

      {seller && (
        <article className="detail-card seller-result">
          <h3>Datos del vendedor</h3>

          <p><strong>ID:</strong> {seller.id}</p>
          <p><strong>Nombre:</strong> {seller.name}</p>
          <p><strong>Reputación:</strong> {seller.reputation}</p>
          <p>
            <strong>Antigüedad:</strong> {seller.accountAgeDays} días
          </p>
        </article>
      )}
    </section>
  )
}