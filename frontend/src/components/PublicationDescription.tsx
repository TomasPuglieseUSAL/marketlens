import { useState } from 'react'
import AnimatedCollapse from './AnimatedCollapse'
import DisclosureChevron from './DisclosureChevron'

interface PublicationDescriptionProps {
  description: string | null
}

// Los primeros cuatro renglones quedan visibles; el resto se abre con
// una transición medida en píxeles, incluso para descripciones muy largas.
export default function PublicationDescription({ description }: PublicationDescriptionProps) {
  const [expanded, setExpanded] = useState(false)
  const content = description?.trim() || ''
  const collapsible = content.length > 240 || content.split(/\r?\n/).length > 4

  return (
    <div className="publication-description">
      <h3>Descripción</h3>
      {content ? (
        <>
          <AnimatedCollapse open={!collapsible || expanded} previewLines={collapsible ? 4 : 0}>
            <p>{content}</p>
          </AnimatedCollapse>
          {collapsible && (
            <button
              type="button"
              className="secondary-button description-toggle"
              aria-expanded={expanded}
              onClick={() => setExpanded((current) => !current)}
            >
              {expanded ? 'Ver menos' : 'Leer descripción completa'}
              <DisclosureChevron />
            </button>
          )}
        </>
      ) : (
        <p>No hay descripción disponible.</p>
      )}
    </div>
  )
}
