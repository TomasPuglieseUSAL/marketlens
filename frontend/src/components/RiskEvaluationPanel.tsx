import { useState } from 'react'

import {
  evaluatePublication,
  getPublicationEvaluations,
  type RiskEvaluation,
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
}

export default function RiskEvaluationPanel({
  token,
  publicationId,
}: RiskEvaluationPanelProps) {
  const [evaluations, setEvaluations] = useState<RiskEvaluation[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  async function handleEvaluate() {
    setLoading(true)
    setError('')
    setMessage('')

    try {
      await evaluatePublication(token, publicationId)

      const history = await getPublicationEvaluations(
        token,
        publicationId
      )

      setEvaluations(history)
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
    setLoading(true)
    setError('')
    setMessage('')

    try {
      const history = await getPublicationEvaluations(
        token,
        publicationId
      )

      setEvaluations(history)
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
          Consultar historial
        </button>
      </div>

      {message && <p className="success-message">{message}</p>}
      {error && <p className="error">{error}</p>}

      {evaluations.length > 0 && (
        <div className="evaluation-history">
          {evaluations.map((evaluation, index) => (
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

              <h4>Factores detectados</h4>

              {evaluation.factors.length === 0 ? (
                <p>No se detectaron factores de riesgo.</p>
              ) : (
                <ul className="risk-factors">
                  {evaluation.factors.map((factor) => (
                    <li key={factor.id}>
                      <div>
                        <strong>{factor.description}</strong>
                        <small>{factor.code}</small>
                      </div>

                      <span>+{factor.scoreContribution}</span>
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