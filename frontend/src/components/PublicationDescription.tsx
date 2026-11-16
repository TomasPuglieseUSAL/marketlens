import { useState } from 'react'

interface PublicationDescriptionProps {
  description: string | null
}

// La extensión puede importar descripciones largas y con muchos saltos
// de línea. La vista comienza compacta y respeta el texto original al abrir.
export default function PublicationDescription({ description }: PublicationDescriptionProps) {
  const [expanded, setExpanded] = useState(false)
  const content = description?.trim() || ''
  const collapsible = content.length > 240 || content.split(/\r?\n/).length > 4

  return (
    <div className="publication-description">
      <h3>Descripción</h3>
      {content ? (
        <>
          <p className={collapsible && !expanded ? 'description-clamped' : undefined}>
            {content}
          </p>
          {collapsible && (
            <button
              type="button"
              className="secondary-button description-toggle"
              aria-expanded={expanded}
              onClick={() => setExpanded((current) => !current)}
            >
              {expanded ? 'Ver menos' : 'Leer descripción completa'}
            </button>
          )}
        </>
      ) : (
        <p>No hay descripción disponible.</p>
      )}
    </div>
  )
}
