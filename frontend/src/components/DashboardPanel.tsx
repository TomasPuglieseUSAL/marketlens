import { useCallback, useEffect, useState } from 'react'

import {
  getDashboardSummary,
  getMarketplaceStatistics,
  type DashboardSummary,
  type MarketplaceStatistics,
  type Marketplace,
} from '../api/client'

interface DashboardPanelProps {
  token: string
}

const marketplaceLabels: Record<Marketplace, string> = {
  MERCADO_LIBRE: 'Mercado Libre',
  FACEBOOK_MARKETPLACE: 'Facebook Marketplace',
  TEMU: 'Temu',
  OTHER: 'Otros',
}

const riskLabels: Record<string, string> = {
  LOW: 'Bajo',
  MEDIUM: 'Medio',
  HIGH: 'Alto',
}

export default function DashboardPanel({
  token,
}: DashboardPanelProps) {
  const [summary, setSummary] =
    useState<DashboardSummary | null>(null)

  const [marketplaces, setMarketplaces] =
    useState<MarketplaceStatistics[]>([])

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)

  const loadDashboard = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const [summaryResult, marketplaceResult] = await Promise.all([
        getDashboardSummary(token),
        getMarketplaceStatistics(token),
      ])

      setSummary(summaryResult)
      setMarketplaces(marketplaceResult)
      setLastUpdated(new Date())
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Error inesperado'
      )
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => {
    void loadDashboard()
  }, [loadDashboard])

  return (
    <section className="dashboard-panel">
      <div className="dashboard-section-header">
        <div>
          <h2>Resumen general</h2>
          <p>Estadísticas de publicaciones y evaluaciones de riesgo.</p>
        </div>

        <button
          onClick={() => void loadDashboard()}
          disabled={loading}
        >
          {loading ? 'Actualizando...' : 'Actualizar estadísticas'}
        </button>
      </div>

      {lastUpdated && (
        <p className="last-updated">
          Última actualización: {lastUpdated.toLocaleTimeString()}
        </p>
      )}

      {error && <p className="error" role="alert">{error}</p>}

      {summary && (
        <>
          <div className="stats-grid">
            <article className="stat-card">
              <h3>Publicaciones</h3>
              <strong>{summary.totalPublications}</strong>
            </article>

            <article className="stat-card">
              <h3>Vendedores</h3>
              <strong>{summary.totalSellers}</strong>
            </article>

            <article className="stat-card">
              <h3>Evaluaciones</h3>
              <strong>{summary.totalEvaluations}</strong>
            </article>

            <article className="stat-card">
              <h3>Reportes</h3>
              <strong>{summary.totalReports}</strong>
            </article>

            <article className="stat-card">
              <h3>Riesgo promedio</h3>
              <strong>{summary.averageRiskScore.toFixed(2)} / 100</strong>
            </article>
          </div>

          <div className="details-grid">
            <article className="detail-card">
              <h3>Distribución de riesgo</h3>

              {(['LOW', 'MEDIUM', 'HIGH'] as const).map((level) => {
                const count = summary.evaluationsByRiskLevel[level] ?? 0

                const total = Object.values(
                  summary.evaluationsByRiskLevel
                ).reduce((sum, value) => sum + value, 0)

                const percentage =
                  total > 0 ? (count / total) * 100 : 0

                return (
                  <div key={level} className="chart-row">
                    <div className="chart-label">
                      <span>{riskLabels[level]}</span>
                      <strong>{count}</strong>
                    </div>

                    <div className="chart-track">
                      <div
                        className={`chart-fill risk-fill-${level.toLowerCase()}`}
                        style={{ width: `${percentage}%` }}
                      />
                    </div>
                  </div>
                )
              })}
            </article>

            <article className="detail-card">
              <h3>Publicaciones por marketplace</h3>

              {Object.entries(
                summary.publicationsByMarketplace
              ).map(([marketplace, count]) => {
                const percentage =
                  summary.totalPublications > 0
                    ? (count / summary.totalPublications) * 100
                    : 0

                return (
                  <div key={marketplace} className="chart-row">
                    <div className="chart-label">
                      <span>
                        {marketplaceLabels[marketplace as Marketplace]
                          ?? marketplace}
                      </span>
                      <strong>{count}</strong>
                    </div>

                    <div className="chart-track">
                      <div
                        className="chart-fill marketplace-fill"
                        style={{ width: `${percentage}%` }}
                      />
                    </div>
                  </div>
                )
              })}
            </article>
          </div>
        </>
      )}

      {marketplaces.length > 0 && (
        <section className="marketplace-comparison">
          <h2>Comparación por marketplace</h2>

          <p>
            Las estadísticas de riesgo consideran la última evaluación
            de cada publicación analizada.
          </p>

          <div className="marketplace-grid">
            {marketplaces.map((item) => (
              <article
                key={item.marketplace}
                className="detail-card marketplace-card"
              >
                <h3>{marketplaceLabels[item.marketplace]}</h3>

                <div className="marketplace-score">
                  <strong>{item.averageRiskScore.toFixed(1)}</strong>
                  <span>/ 100</span>
                </div>

                <div className="chart-track">
                  <div
                    className="chart-fill marketplace-fill"
                    style={{
                      width: `${Math.min(
                        100,
                        Math.max(0, item.averageRiskScore)
                      )}%`,
                    }}
                  />
                </div>

                <div className="marketplace-details">
                  <p>
                    Publicaciones:
                    <strong>{item.totalPublications}</strong>
                  </p>

                  <p>
                    Publicaciones evaluadas:
                    <strong>{item.evaluatedPublications}</strong>
                  </p>

                  <p>
                    Riesgo bajo:
                    <strong>{item.lowRiskCount}</strong>
                  </p>

                  <p>
                    Riesgo medio:
                    <strong>{item.mediumRiskCount}</strong>
                  </p>

                  <p>
                    Riesgo alto:
                    <strong>{item.highRiskCount}</strong>
                  </p>

                  <p>
                    Reportes:
                    <strong>{item.totalReports}</strong>
                  </p>
                </div>
              </article>
            ))}
          </div>
        </section>
      )}
    </section>
  )
}