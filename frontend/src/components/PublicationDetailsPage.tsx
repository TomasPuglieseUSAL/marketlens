import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'

import {
  getPublication,
  type Publication,
} from '../api/client'

import RiskEvaluationPanel from './RiskEvaluationPanel'
import ReportsPanel from './ReportsPanel'
import PublicationDescription from './PublicationDescription'
import PublicationHighlights from './PublicationHighlights'

interface PublicationDetailsPageProps {
  token: string
}

const marketplaceLabels: Record<string, string> = {
  MERCADO_LIBRE: 'Mercado Libre',
  FACEBOOK_MARKETPLACE: 'Facebook Marketplace',
  TEMU: 'Temu',
  OTHER: 'Otros',
}

// La validación de la ruta se resuelve al renderizar, sin efectos.
export default function PublicationDetailsPage({ token }: PublicationDetailsPageProps) {
  const { id } = useParams<{ id: string }>()
  const publicationId = Number(id)
  const validId = id !== undefined
    && /^\d+$/.test(id)
    && Number.isSafeInteger(publicationId)
    && publicationId > 0

  if (!validId) {
    return (
      <section className="publication-details-page">
        <Link to="/publications" className="back-link">
          ← Volver a publicaciones
        </Link>
        <p className="error" role="alert">El ID de la publicación no es válido.</p>
      </section>
    )
  }

  // Un cambio de ID crea un estado nuevo: nunca muestra la publicación
  // anterior durante la carga de la siguiente.
  return <ValidPublicationDetails key={publicationId} token={token} publicationId={publicationId} />
}

function ValidPublicationDetails({
  token,
  publicationId,
}: {
  token: string
  publicationId: number
}) {
  const [publication, setPublication] = useState<Publication | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    // La solicitud HTTP es asíncrona. Evitamos inicializaciones síncronas
    // de estado dentro del efecto y respuestas de rutas ya desmontadas.
    void getPublication(token, publicationId)
      .then((result) => {
        if (!cancelled) setPublication(result)
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error
            ? err.message
            : 'No se pudo cargar la publicación.')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [token, publicationId])

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

            <PublicationHighlights publication={publication} />

            {publication.marketplace === 'TEMU' && publication.temuSellerReviewCount != null && (
              <p className="session-message" role="note">
                <strong>Valoraciones de tienda:</strong> {publication.temuSellerReviewCount.toLocaleString('es-AR')}
                {' '}· Recuento observado, no cantidad de ventas.
              </p>
            )}
            {publication.marketplace === 'TEMU' && publication.temuStoreId && (
              <p><strong>ID de tienda Temu:</strong> {publication.temuStoreId}</p>
            )}
            {publication.marketplace === 'TEMU' && publication.temuSellerObservedAt && (
              <p><small>Datos de tienda observados: {new Date(publication.temuSellerObservedAt).toLocaleString('es-AR')}</small></p>
            )}
            {publication.marketplace === 'TEMU' && publication.temuSellerRating != null && (
              <p className="session-message" role="note">
                <strong>Calificación de tienda:</strong>{' '}
                {publication.temuSellerRating.toLocaleString('es-AR')} / 5
                {' '}· Valor observado en Temu; distinto de la calificación del producto.
              </p>
            )}

            {publication.marketplace === 'TEMU' && publication.temuProductRating != null
              && publication.temuProductReviewCount != null && (
              <p className="session-message" role="note">
                <strong>Opiniones del producto:</strong>{' '}
                {publication.temuProductRating.toLocaleString('es-AR')} / 5
                {' '}· {publication.temuProductReviewCount.toLocaleString('es-AR')} reseñas.
                {' '}Estas opiniones no equivalen a ventas verificadas ni garantizan autenticidad.
                {publication.temuProductObservedAt &&
                  <> Observadas: {new Date(publication.temuProductObservedAt).toLocaleString('es-AR')}.</>}
              </p>
            )}

            {publication.marketplace === 'TEMU' && publication.temuStoreTenureText && (
              <p className="session-message" role="note">
                <strong>Antigüedad aproximada de la tienda:</strong>{' '}
                {publication.temuStoreTenureText}.
                {' '}Es una estimación de Temu, no la fecha exacta de apertura.
                {publication.temuStoreTenureObservedAt &&
                  <> Observada: {new Date(publication.temuStoreTenureObservedAt).toLocaleString('es-AR')}.</>}
              </p>
            )}
            {publication.marketplace === 'TEMU' && publication.temuStoreSoldText && (
              <p className="session-message" role="note">
                <strong>Actividad comercial de la tienda:</strong>{' '}
                {publication.temuStoreSoldText}.
                {' '}Cifra observada y posiblemente aproximada, no verificada por MarketLens.
                {publication.temuStoreSalesObservedAt &&
                  <> Observada: {new Date(publication.temuStoreSalesObservedAt).toLocaleString('es-AR')}.</>}
              </p>
            )}
            {publication.marketplace === 'TEMU' && publication.temuProductSoldText && (
              <p className="session-message" role="note">
                <strong>Actividad observada del producto:</strong>{' '}
                {publication.temuProductSoldText}.
                {' '}No equivale a ventas totales verificadas del vendedor.
                {publication.temuProductSalesObservedAt &&
                  <> Observada: {new Date(publication.temuProductSalesObservedAt).toLocaleString('es-AR')}.</>}
              </p>
            )}

            {publication.marketplace === 'TEMU' && publication.sellerBadge === 'TEMU_STAR_SELLER' && (
              <p className="session-message" role="note">
                <strong>Star Seller</strong> · Distintivo observado en Temu; no equivale
                a reputación verificada ni garantiza que la compra sea segura.
              </p>
            )}

            <PublicationDescription
              key={publication.id}
              description={publication.description}
            />

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
                className="action-link original-publication-button"
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