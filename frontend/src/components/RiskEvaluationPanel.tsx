import { useState } from 'react'

import {
  evaluatePublication,
  getPublicationEvaluations,
  type RiskEvaluation,
  type RiskFactor,
  type RiskLevel,
} from '../api/client'

interface RiskEvaluationPanelProps {
  token: string
  publicationId: number
}

const riskLabels: Record<RiskLevel, string> = {
  LOW: 'Bajo',
  MEDIUM: 'Medio',
  HIGH: 'Alto',
  INDETERMINATE: 'Indeterminado',
}

// Las evaluaciones históricas conservaron descripciones largas en la base.
// Mostrar sus factores de reseñas con el mismo formato breve que las nuevas,
// sin cambiar los datos ni el puntaje persistidos.
function visibleFactorDescription(factor: RiskFactor): string {
  const code = factor.code
  if (code === 'ML_PRODUCT_REVIEWS_OBSERVED'
      || code === 'ML_LOW_PRODUCT_RATING'
      || code === 'ML_MODERATE_PRODUCT_RATING') {
    const match = factor.description.match(/\b([0-5](?:[.,]\d+)?)\s*\/\s*5\b/)
    if (match) {
      const rating = match[1].replace('.', ',')
      const title = code === 'ML_LOW_PRODUCT_RATING'
        ? 'Calificación baja del producto'
        : code === 'ML_MODERATE_PRODUCT_RATING'
          ? 'Calificación intermedia del producto'
          : 'Calificación del producto'
      return `${title}: ${rating}/5.`
    }
  }
  if (code === 'ML_FEW_PRODUCT_REVIEWS') {
    if (factor.description.startsWith('Cantidad de opiniones reducida:'))
      return factor.description
    const match = factor.description.match(/\b(\d[\d.,]*)\s+opiniones\b/i)
    if (match) return `Cantidad de opiniones reducida: ${match[1]}.`
  }
  return factor.description
}

export default function RiskEvaluationPanel({
  token,
  publicationId,
}: RiskEvaluationPanelProps) {
  const [evaluations, setEvaluations] = useState<RiskEvaluation[]>([])
  const [showHistory, setShowHistory] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  async function handleEvaluate() {
    setLoading(true)
    setError('')
    setMessage('')

    try {
      const created = await evaluatePublication(token, publicationId)

      const history = await getPublicationEvaluations(
        token,
        publicationId
      )

      // El detalle de cobertura corresponde a la evaluación recién creada;
      // el historial anterior no guarda snapshots de cada dimensión.
      setEvaluations(history.map((item) => item.id === created.id
        ? {...item, coverageFactors: created.coverageFactors} : item))
      setShowHistory(false)
      setMessage('Evaluación realizada correctamente.')
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Error inesperado'
      )
    } finally {
      setLoading(false)
    }
  }

  async function handleLoadHistory() {
    if (showHistory) {
      setShowHistory(false)
      return
    }
    setLoading(true)
    setError('')
    setMessage('')

    try {
      const history = await getPublicationEvaluations(
        token,
        publicationId
      )

      setEvaluations(history)
      setShowHistory(true)
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Error inesperado'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="risk-panel">
      <h3>Evaluaciones de riesgo</h3>

      <p>
        Publicación seleccionada: <strong>#{publicationId}</strong>
      </p>

      <div className="risk-actions">
        <button onClick={handleEvaluate} disabled={loading}>
          {loading ? 'Procesando...' : 'Evaluar riesgo'}
        </button>

        <button
          onClick={handleLoadHistory}
          disabled={loading}
          className="secondary-button"
        >
          {showHistory ? 'Ocultar historial' : 'Consultar historial'}
        </button>
      </div>

      {message && <p className="success-message">{message}</p>}
      {error && <p className="error">{error}</p>}

      {evaluations.length > 0 && (
        <div
          className={`evaluation-history${showHistory ? ' evaluation-history--scrollable' : ''}`}
          role={showHistory ? 'region' : undefined}
          aria-label={showHistory ? 'Historial de evaluaciones' : undefined}
          tabIndex={showHistory ? 0 : undefined}
        >
          {(showHistory ? evaluations : evaluations.slice(0, 1)).map((evaluation, index) => (
            <article
              key={evaluation.id}
              className="detail-card evaluation-card"
            >
              <div className="evaluation-header">
                <div>
                  <h4>
                    {index === 0
                      ? 'Evaluación más reciente'
                      : `Evaluación #${evaluation.id}`}
                  </h4>

                  <small>
                    {new Date(evaluation.evaluatedAt).toLocaleString()}
                  </small>
                </div>

                <span
                  className={`risk-badge risk-${evaluation.level.toLowerCase()}`}
                >
                  {riskLabels[evaluation.level]}
                </span>
              </div>

              <div className="risk-score">
                <strong>{evaluation.score}</strong>
                <span>/ 100</span>
              </div>

              {evaluation.totalFactors != null && evaluation.availableFactors != null && (
                <p>
                  <strong>Cobertura: {evaluation.availableFactors}/{evaluation.totalFactors}</strong>
                  {' '}— Datos disponibles, no garantía de seguridad.
                </p>
              )}
              {evaluation.coverageFactors && (
                <ul className="risk-factors">
                  {evaluation.coverageFactors.map((factor) => (
                    <li key={factor.code}>
                      <span>{factor.available ? '✓' : '—'} {factor.name}</span>
                      <small>{factor.available ? 'Disponible' : 'Sin información suficiente'}</small>
                    </li>
                  ))}
                </ul>
              )}

              <h4>Factores del análisis</h4>

              {evaluation.factors.length === 0 ? (
                <p>No se detectaron factores de riesgo.</p>
              ) : (
                <ul className="risk-factors">
                  {evaluation.factors.map((factor) => (
                    <li key={factor.id}>
                      <strong>{visibleFactorDescription(factor)}</strong>
                      {factor.scoreContribution > 0
                        ? <span>+{factor.scoreContribution}</span>
                        : <small>Informativo</small>}
                    </li>
                  ))}
                </ul>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  )
}