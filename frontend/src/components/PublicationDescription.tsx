import { useEffect, useRef, useState } from 'react'

interface PublicationDescriptionProps {
  description: string | null
}

// Medimos la altura real para animar también textos extensos, sin recurrir
// a un max-height arbitrario que recorte descripciones muy largas.
export default function PublicationDescription({ description }: PublicationDescriptionProps) {
  const [expanded, setExpanded] = useState(false)
  const [expandedHeight, setExpandedHeight] = useState(0)
  const textRef = useRef<HTMLParagraphElement>(null)
  const content = description?.trim() || ''
  const collapsible = content.length > 240 || content.split(/\r?\n/).length > 4

  useEffect(() => {
    if (!expanded || !textRef.current) return
    // Si cambia el ancho de la ventana o la tipografía, ajustar la altura
    // sin cortar el final de la descripción.
    const observer = new ResizeObserver(() => {
      const nextHeight = textRef.current?.scrollHeight ?? 0
      setExpandedHeight((previous) => previous === nextHeight ? previous : nextHeight)
    })
    observer.observe(textRef.current)
    return () => observer.disconnect()
  }, [expanded, content])

  function toggleDescription() {
    if (!expanded) setExpandedHeight(textRef.current?.scrollHeight ?? 0)
    setExpanded((current) => !current)
  }

  return (
    <div className="publication-description">
      <h3>Descripción</h3>
      {content ? (
        <>
          <div
            className={`description-viewport${collapsible && expanded ? ' is-expanded' : ''}`}
            style={collapsible && expanded ? { maxHeight: `${expandedHeight}px` } : undefined}
          >
            <p ref={textRef}>{content}</p>
          </div>
          {collapsible && (
            <button
              type="button"
              className="secondary-button description-toggle"
              aria-expanded={expanded}
              onClick={toggleDescription}
            >
              {expanded ? 'Ver menos' : 'Leer descripción completa'}
              <span className="disclosure-chevron" aria-hidden="true">⌄</span>
            </button>
          )}
        </>
      ) : (
        <p>No hay descripción disponible.</p>
      )}
    </div>
  )
}
