import { useState, type FormEvent } from 'react'

import {
  createPublication,
  getPublication,
  type Publication,
  type Marketplace,
} from '../api/client'

import RiskEvaluationPanel from './RiskEvaluationPanel'
import ReportsPanel from './ReportsPanel'
import PublicationsList from './PublicationsList'
import SellerSelect from './SellerSelect'
import { Link } from 'react-router'

interface PublicationsPanelProps {
  token: string
}

export default function PublicationsPanel({
  token,
}: PublicationsPanelProps) {

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [price, setPrice] = useState('')
  const [marketplace, setMarketplace] =
    useState<Marketplace>('MERCADO_LIBRE')

  const [sellerId, setSellerId] = useState('')
  const [externalId, setExternalId] = useState('')
  const [originalUrl, setOriginalUrl] = useState('')

  const [searchId, setSearchId] = useState('')
  const [publication, setPublication] =
    useState<Publication | null>(null)

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
      const created = await createPublication(token, {
        title: title.trim(),
        description: description.trim(),
        price: Number(price),
        marketplace,
        sellerId: Number(sellerId),
        externalId: externalId.trim() || null,
        originalUrl: originalUrl.trim() || null,
      })

      setPublication(created)
      setMessage(
        `Publicación creada correctamente. ID: ${created.id}`
      )

      setTitle('')
      setDescription('')
      setPrice('')
      setExternalId('')
      setOriginalUrl('')
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
  await selectPublication(Number(searchId))
  }

  async function selectPublication(id: number) {
  setLoading(true)
  setMessage('')
  setError('')
  setPublication(null)

  try {
    const result = await getPublication(token, id)
    setPublication(result)
  } catch (err) {
    setError(
      err instanceof Error ? err.message : 'Error inesperado'
    )
  } finally {
    setLoading(false)
  }
  }

  return (
    <section className="publications-panel">
      <h2>Gestión de publicaciones</h2>

      <div className="details-grid">

        <article className="detail-card">
          <h3>Registrar publicación</h3>

          <form className="panel-form" onSubmit={handleCreate}>

            <label htmlFor="publication-title">
              Título
            </label>

            <input
              id="publication-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />

            <label htmlFor="publication-description">
              Descripción
            </label>

            <textarea
              id="publication-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={4}
              required
            />

            <label htmlFor="publication-price">
              Precio
            </label>

            <input
              id="publication-price"
              type="number"
              min="0.01"
              step="0.01"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              required
            />

            <label htmlFor="publication-marketplace">
              Marketplace
            </label>

            <select
              id="publication-marketplace"
              value={marketplace}
              onChange={(e) =>
                setMarketplace(e.target.value as Marketplace)
              }
            >
              <option value="MERCADO_LIBRE">
                Mercado Libre
              </option>
              <option value="FACEBOOK_MARKETPLACE">
                Facebook Marketplace
              </option>
              <option value="TEMU">
                Temu
              </option>
              <option value="OTHER">
                Otro
              </option>
            </select>

            <SellerSelect
              token={token}
              value={sellerId}
              onChange={setSellerId}
            />

            <label htmlFor="publication-external-id">
              ID externo (opcional)
            </label>

            <input
              id="publication-external-id"
              value={externalId}
              onChange={(e) => setExternalId(e.target.value)}
            />

            <label htmlFor="publication-url">
              URL original (opcional)
            </label>

            <input
              id="publication-url"
              type="url"
              value={originalUrl}
              onChange={(e) => setOriginalUrl(e.target.value)}
            />

            <button type="submit" disabled={loading}>
              Registrar publicación
            </button>

          </form>
        </article>

        <article className="detail-card">
          <h3>Consultar publicación</h3>

          <form className="panel-form" onSubmit={handleSearch}>
            <label htmlFor="publication-search-id">
              ID de publicación
            </label>

            <input
              id="publication-search-id"
              type="number"
              min="1"
              step="1"
              value={searchId}
              onChange={(e) => setSearchId(e.target.value)}
              required
            />

            <button type="submit" disabled={loading}>
              Buscar publicación
            </button>
          </form>
        </article>

      </div>

      {message && (
        <p className="success-message">{message}</p>
      )}

      {error && (
        <p className="error">{error}</p>
      )}

      <PublicationsList
        token={token}
        refreshKey={refreshKey}
        onSelect={selectPublication}
      />

      {publication && (
  <>
    <article className="detail-card publication-result">
      <h3>Datos de la publicación</h3>

      <p><strong>ID:</strong> {publication.id}</p>
      <p><strong>Título:</strong> {publication.title}</p>
      <p><strong>Descripción:</strong> {publication.description}</p>
      <p><strong>Precio:</strong> {publication.price}</p>
      <p><strong>Marketplace:</strong> {publication.marketplace}</p>
      <p><strong>Vendedor:</strong> {publication.sellerId}</p>

      {publication.externalId && (
        <p>
          <strong>ID externo:</strong> {publication.externalId}
        </p>
      )}

      {publication.originalUrl && (
        <p>
          <strong>URL original:</strong>{' '}
          <a
            href={publication.originalUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            Abrir publicación
          </a>
        </p>
      )}

      <Link
        to={`/publications/${publication.id}`}
          className="action-link"
          >
              Abrir página de la publicación
      </Link>
    </article>
    

    <RiskEvaluationPanel
      key={publication.id}
      token={token}
      publicationId={publication.id}
    />
    <ReportsPanel
      key={publication.id}
      token={token}
      publicationId={publication.id}
    />
    </>
)}
    </section>
  )
}