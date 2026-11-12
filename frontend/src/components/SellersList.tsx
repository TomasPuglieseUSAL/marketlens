import { useEffect, useState } from 'react'

import {
  getSellers,
  type Seller,
  type PageResponse,
} from '../api/client'

interface SellersListProps {
  token: string
  onSelect: (id: number) => void
  refreshKey: number
}

export default function SellersList({
  token,
  onSelect,
  refreshKey,
}: SellersListProps) {

  const [page, setPage] = useState(0)

  const [data, setData] =
    useState<PageResponse<Seller> | null>(null)

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError('')

      try {
        const result = await getSellers(token, page)

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
    <section className="detail-card sellers-list">
      <h3>Vendedores registrados</h3>

      {loading && <p>Cargando vendedores...</p>}
      {error && <p className="error">{error}</p>}

      {!loading && !error && data?.content.length === 0 && (
        <p>No hay vendedores registrados.</p>
      )}

      {!loading && !error && data && data.content.length > 0 && (
        <>
          <div className="seller-list-items">
            {data.content.map((seller) => (
              <article key={seller.id} className="seller-list-item">
                <div>
                  <strong>{seller.name}</strong>

                  <p>
                    Reputación: {seller.reputation}
                  </p>

                  <small>
                    ID: {seller.id} · Antigüedad:
                    {' '}{seller.accountAgeDays} días
                  </small>
                </div>

                <button onClick={() => onSelect(seller.id)}>
                  Seleccionar
                </button>
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