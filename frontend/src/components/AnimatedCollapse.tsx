import { useLayoutEffect, useRef, type ReactNode } from 'react'

interface AnimatedCollapseProps {
  open: boolean
  children: ReactNode
  id?: string
  className?: string
  previewLines?: number
}

// Web Animations anima alturas medidas en píxeles (también al cerrar).
// Evita los saltos de grid-template-rows y max-height cuando el contenido
// se carga dinámicamente o el usuario invierte la animación a mitad.
export default function AnimatedCollapse({
  open,
  children,
  id,
  className = '',
  previewLines = 0,
}: AnimatedCollapseProps) {
  const outerRef = useRef<HTMLDivElement>(null)
  const innerRef = useRef<HTMLDivElement>(null)
  const previousOpenRef = useRef<boolean | null>(null)
  const animationRef = useRef<Animation | null>(null)

  useLayoutEffect(() => {
    const outer = outerRef.current
    const inner = innerRef.current
    if (!outer || !inner) return

    function collapsedHeight() {
      if (previewLines <= 0) return 0
      const text = inner?.querySelector('p') ?? inner
      const lineHeight = Number.parseFloat(window.getComputedStyle(text).lineHeight)
      // CSS line-height 1.65 para la descripción; fallback solo si es "normal".
      const linesHeight = (Number.isFinite(lineHeight) ? lineHeight : 26) * previewLines
      return Math.min(inner?.scrollHeight ?? 0, Math.ceil(linesHeight))
    }

    const closed = collapsedHeight()
    const targetHeight = open ? inner.scrollHeight : closed
    const targetOpacity = open || previewLines > 0 ? 1 : 0
    const firstRender = previousOpenRef.current === null
    const unchanged = previousOpenRef.current === open

    if (firstRender) {
      outer.style.height = open ? 'auto' : `${targetHeight}px`
      outer.style.opacity = String(targetOpacity)
      previousOpenRef.current = open
      return
    }

    // El contenido podría actualizarse sin cambiar el estado del panel.
    if (unchanged) return
    previousOpenRef.current = open

    // Leer la posición actual ANTES de cancelar permite revertir a mitad.
    const startHeight = outer.getBoundingClientRect().height
    const startOpacity = Number.parseFloat(window.getComputedStyle(outer).opacity)
    animationRef.current?.cancel()
    animationRef.current = null

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduceMotion || typeof outer.animate !== 'function') {
      outer.style.height = open ? 'auto' : `${targetHeight}px`
      outer.style.opacity = String(targetOpacity)
      return
    }

    outer.style.height = `${startHeight}px`
    outer.style.opacity = String(Number.isFinite(startOpacity) ? startOpacity : targetOpacity)

    const animation = outer.animate(
      [
        { height: `${startHeight}px`, opacity: Number.isFinite(startOpacity) ? startOpacity : targetOpacity },
        { height: `${targetHeight}px`, opacity: targetOpacity },
      ],
      {
        duration: open ? 480 : 390,
        easing: 'cubic-bezier(.2, .75, .2, 1)',
        fill: 'forwards',
      }
    )
    animationRef.current = animation
    animation.onfinish = () => {
      if (animationRef.current !== animation) return
      outer.style.height = open ? 'auto' : `${targetHeight}px`
      outer.style.opacity = String(targetOpacity)
      animation.cancel()
      animationRef.current = null
    }
  }, [open, previewLines])

  // También cancelar al desmontar para no dejar callbacks sobre nodos viejos.
  useLayoutEffect(() => () => animationRef.current?.cancel(), [])

  const inaccessible = !open && previewLines === 0

  return (
    <div
      ref={outerRef}
      id={id}
      className={`animated-collapse${className ? ` ${className}` : ''}`}
      style={{ height: '0px', opacity: inaccessible ? 0 : 1 }}
      aria-hidden={inaccessible}
      inert={inaccessible}
    >
      <div ref={innerRef} className="animated-collapse-inner">
        {children}
      </div>
    </div>
  )
}
