import { useEffect, useState } from 'react'

import {
  getPublications,
  type PublicationListItem,
  type PageResponse,
} from '../api/client'

import { Link } from 'react-router'

interface PublicationsListProps {
  token: string
  onSelect: (id: number) => void
  refreshKey: number
}

export default function PublicationsList({
  token,
  onSelect,
  refreshKey,
}: PublicationsListProps) {

  const [page, setPage] = useState(0)

  const [data, setData] =
    useState<PageResponse<PublicationListItem> | null>(null)

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError('')

      try {
        const result = await getPublications(token, page)

        if (!cancelled) {
          setData(result)
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : 'Error inesperado'
          )
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [token, page, refreshKey])

  return (
    <section className="detail-card publications-list">
      <h3>Publicaciones registradas</h3>

      {loading && <p>Cargando publicaciones...</p>}

      {error && <p className="error">{error}</p>}

      {!loading && !error && data?.content.length === 0 && (
        <p>No hay publicaciones registradas.</p>
      )}

      {!loading && !error && data && data.content.length > 0 && (
        <>
          <div className="publication-list-items">
            {data.content.map((publication) => (
              <article
                key={publication.id}
                className="publication-list-item"
              >
                <div>
                  <strong>{publication.title}</strong>

                  <p>
                    {publication.marketplace} · {publication.sellerName ?? 'Vendedor no identificado'}
                  </p>

                  <small>
                    ID: {publication.id} · Precio: {publication.price}
                  </small>
                </div>

                <div className="publication-list-actions">
                  <button
                    onClick={() => onSelect(publication.id)}
                        className="secondary-button"
                          >
                                Seleccionar
                  </button>
                  
                  <Link
                      to={`/publications/${publication.id}`}
                          className="action-link"
                            >
                                    Ver detalle
                  </Link>
                </div>
              </article>
            ))}
          </div>

          <div className="pagination-controls">
            <button
              className="secondary-button"
              disabled={loading || data.first}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Anterior
            </button>

            <span>
              Página {data.number + 1} de {data.totalPages}
            </span>

            <button
              className="secondary-button"
              disabled={loading || data.last}
              onClick={() => setPage((p) => p + 1)}
            >
              Siguiente
            </button>
          </div>
        </>
      )}
    </section>
  )
}