import type { Publication } from '../api/client'

interface PublicationHighlightsProps {
  publication: Publication
}

// Los importes de las publicaciones registradas se expresan en pesos
// argentinos en el MVP de MarketLens.
export default function PublicationHighlights({ publication }: PublicationHighlightsProps) {
  const seller = publication.sellerName ||
    (publication.sellerId == null ? 'No identificado' : `#${publication.sellerId}`)

  return (
    <div className="publication-main-data">
      <div className="publication-data-price">
        <small>Precio · ARS</small>
        <strong>
          $ {publication.price.toLocaleString('es-AR', {
            minimumFractionDigits: 0,
            maximumFractionDigits: 2,
          })}
        </strong>
      </div>
      <div className="publication-data-seller">
        <small>Vendedor</small>
        <strong>{seller}</strong>
      </div>
    </div>
  )
}
