import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'

import {
  getPublication,
  type Publication,
} from '../api/client'

import RiskEvaluationPanel from './RiskEvaluationPanel'
import ReportsPanel from './ReportsPanel'

interface PublicationDetailsPageProps {
  token: string
}

const marketplaceLabels: Record<string, string> = {
  MERCADO_LIBRE: 'Mercado Libre',
  FACEBOOK_MARKETPLACE: 'Facebook Marketplace',
  TEMU: 'Temu',
  OTHER: 'Otros',
}

export default function PublicationDetailsPage({
  token,
}: PublicationDetailsPageProps) {
  const { id } = useParams<{ id: string }>()

  const [publication, setPublication] =
    useState<Publication | null>(null)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const publicationId = Number(id)

  const validId =
    id !== undefined &&
    /^\d+$/.test(id) &&
    Number.isSafeInteger(publicationId) &&
    publicationId > 0

  useEffect(() => {
    let cancelled = false

    if (!validId) {
      setPublication(null)
      setError('El ID de la publicación no es válido.')
      setLoading(false)
      return
    }

    async function loadPublication() {
      setLoading(true)
      setError('')
      setPublication(null)

      try {
        const result = await getPublication(
          token,
          publicationId
        )

        if (!cancelled) {
          setPublication(result)
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : 'No se pudo cargar la publicación.'
          )
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadPublication()

    return () => {
      cancelled = true
    }
  }, [token, publicationId, validId])

  const originalUrl = publication?.originalUrl

  const safeOriginalUrl =
    originalUrl && /^https?:\/\//i.test(originalUrl)
      ? originalUrl
      : null

  return (
    <section className="publication-details-page">
      <Link to="/publications" className="back-link">
        ← Volver a publicaciones
      </Link>

      {loading && (
        <p>Cargando publicación...</p>
      )}

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {!loading && publication && (
        <>
          <article className="detail-card publication-info">
            <div className="publication-details-header">
              <div>
                <span className="publication-id">
                  Publicación #{publication.id}
                </span>

                <h2>{publication.title}</h2>
              </div>

              <span className="marketplace-badge">
                {marketplaceLabels[publication.marketplace]
                  ?? publication.marketplace}
              </span>
            </div>

            <div className="publication-main-data">
              <div>
                <small>Precio</small>

                <strong>
                  {publication.price.toLocaleString('es-AR')}
                </strong>
              </div>

              <div>
                <small>Vendedor</small>

                <strong>
                  {publication.sellerId == null ? 'No identificado' : `#${publication.sellerId}`}
                </strong>
              </div>
            </div>

            <div className="publication-description">
              <h3>Descripción</h3>
              <p>{publication.description}</p>
            </div>

            {publication.externalId && (
              <p>
                <strong>ID externo:</strong>{' '}
                {publication.externalId}
              </p>
            )}

            {safeOriginalUrl && (
              <a
                href={safeOriginalUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="external-link"
              >
                Abrir publicación original ↗
              </a>
            )}
          </article>

          <RiskEvaluationPanel
            key={`evaluation-${publication.id}`}
            token={token}
            publicationId={publication.id}
          />

          <ReportsPanel
            key={`reports-${publication.id}`}
            token={token}
            publicationId={publication.id}
          />
        </>
      )}
    </section>
  )
}