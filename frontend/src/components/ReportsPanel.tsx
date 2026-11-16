import { useEffect, useId, useState, type FormEvent } from 'react'
import AnimatedCollapse from './AnimatedCollapse'
import DisclosureChevron from './DisclosureChevron'

import {
  createReport,
  getPublicationReports,
  getCommunityReportSummary,
  type CommunityReportSummary,
  type Report,
  type ReportReason,
} from '../api/client'

interface ReportsPanelProps {
  token: string
  publicationId: number
}

const reportReasons: Record<ReportReason, string> = {
  SUSPICIOUS_PRICE: 'Precio sospechoso',
  SUSPICIOUS_SELLER: 'Vendedor sospechoso',
  SUSPICIOUS_DESCRIPTION: 'Descripción sospechosa',
  PAYMENT_OUTSIDE_PLATFORM: 'Pago fuera de la plataforma',
  POSSIBLE_IMPERSONATION: 'Posible suplantación de identidad',
  OTHER: 'Otro motivo',
}

export default function ReportsPanel({ token, publicationId }: ReportsPanelProps) {
  const [reason, setReason] = useState<ReportReason>('SUSPICIOUS_PRICE')
  const [description, setDescription] = useState('')
  const [reports, setReports] = useState<Report[]>([])
  const [communitySummary, setCommunitySummary] = useState<CommunityReportSummary | null>(null)
  const [historyLoaded, setHistoryLoaded] = useState(false)
  const [expanded, setExpanded] = useState(false)

  const contentId = useId()
  const reportsHistoryId = useId()

  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    void getCommunityReportSummary(token, publicationId)
      .then((result) => { if (active) setCommunitySummary(result) })
      .catch(() => { if (active) setCommunitySummary(null) })
    return () => { active = false }
  }, [token, publicationId])

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLoading(true)
    setMessage('')
    setError('')

    try {
      await createReport(token, publicationId, {
        reason,
        description: description.trim(),
      })

      // El envío está confirmado: cerrar progresivamente el formulario,
      // pero mantener la confirmación visible fuera del panel colapsado.
      setDescription('')
      setExpanded(false)
      setHistoryLoaded(false)
      setMessage('Reporte registrado correctamente. El puntaje de las evaluaciones anteriores no cambia hasta realizar otro análisis.')

      // Las consultas auxiliares no deben producir un falso error de envío.
      const [history, summary] = await Promise.allSettled([
        getPublicationReports(token, publicationId),
        getCommunityReportSummary(token, publicationId),
      ])
      if (history.status === 'fulfilled') setReports(history.value)
      if (summary.status === 'fulfilled') setCommunitySummary(summary.value)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error inesperado')
    } finally {
      setLoading(false)
    }
  }

  async function handleLoadReports() {
    if (historyLoaded) {
      setHistoryLoaded(false)
      return
    }
    setLoading(true)
    setMessage('')
    setError('')

    try {
      const history = await getPublicationReports(token, publicationId)
      setReports(history)
      setHistoryLoaded(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error inesperado')
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="reports-panel">
      <h3>Reportes colaborativos</h3>

      {communitySummary && communitySummary.uniqueReporterCount > 0 && (
        <aside className="community-summary" aria-label="Resumen de reportes comunitarios">
          <div className="community-summary-heading">
            <strong>Reportes recientes de la comunidad</strong>
            <span>{communitySummary.uniqueReporterCount} {communitySummary.uniqueReporterCount === 1 ? 'usuario' : 'usuarios'}</span>
          </div>
          <p>
            {communitySummary.recentReportCount} {communitySummary.recentReportCount === 1 ? 'reporte' : 'reportes'}
            {' '}en los últimos {communitySummary.windowDays} días.
            {communitySummary.riskPoints > 0
              ? ` Factor comunitario: +${communitySummary.riskPoints} puntos en un nuevo análisis.`
              : ' Sin penalización automática por ahora.'}
          </p>
          <div className="community-summary-reasons">
            {communitySummary.reasons.slice(0, 3).map((entry) => (
              <span key={entry.reason}>
                {reportReasons[entry.reason]} ({entry.count})
              </span>
            ))}
          </div>
          <small>Reportes no verificados; los puntajes anteriores no cambian.</small>
        </aside>
      )}

      {message && (
        <div className="report-submit-confirmation" role="status">
          <span className="report-submit-check" aria-hidden="true">✓</span>
          <span>{message}</span>
        </div>
      )}

      <div className="reports-disclosure">
        <button
          type="button"
          className="reports-toggle"
          onClick={() => setExpanded((current) => !current)}
          aria-expanded={expanded}
          aria-controls={contentId}
        >
          Registrar o consultar reportes
          <DisclosureChevron />
        </button>

        <AnimatedCollapse open={expanded} id={contentId} className="reports-reveal">
          <div className="reports-panel-content">
            <p>
              Publicación seleccionada: <strong>#{publicationId}</strong>
            </p>

            <div className="details-grid">
              <article className="detail-card">
                <h4>Registrar reporte</h4>

                <form className="panel-form" onSubmit={handleCreate}>
                  <label htmlFor="report-reason">Motivo del reporte</label>
                  <select
                    id="report-reason"
                    value={reason}
                    onChange={(e) => setReason(e.target.value as ReportReason)}
                  >
                    {Object.entries(reportReasons).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>

                  <label htmlFor="report-description">Descripción</label>
                  <textarea
                    id="report-description"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    rows={4}
                    maxLength={2000}
                    placeholder="Describí qué observaste en esta publicación"
                    required
                  />

                  <button type="submit" disabled={loading}>
                    {loading ? 'Procesando...' : 'Enviar reporte'}
                  </button>
                </form>
              </article>

              <article className="detail-card">
                <h4>Historial de reportes</h4>
                <p>Consultá los reportes registrados para esta publicación.</p>

                <button
                  type="button"
                  onClick={handleLoadReports}
                  disabled={loading}
                  className="secondary-button history-toggle"
                  aria-expanded={historyLoaded}
                  aria-controls={reportsHistoryId}
                >
                  {historyLoaded ? 'Ocultar reportes' : 'Consultar reportes'}
                  <DisclosureChevron />
                </button>
              </article>
            </div>

            {error && <p className="error" role="alert">{error}</p>}

            <AnimatedCollapse open={historyLoaded} id={reportsHistoryId} className="reports-history-reveal">
              <div className="reports-history">
                <h4>Reportes registrados ({reports.length})</h4>
                {reports.length === 0 ? (
                  <p>No hay reportes para esta publicación.</p>
                ) : (
                  <div
                    className="reports-history-list"
                    role="region"
                    aria-label="Historial de reportes"
                    tabIndex={historyLoaded ? 0 : -1}
                  >
                    {reports.map((report) => (
                      <article key={report.id} className="detail-card report-card">
                        <div className="report-header">
                          <strong>{reportReasons[report.reason] ?? report.reason}</strong>
                          <span>#{report.id}</span>
                        </div>
                        <p>{report.description || 'Sin descripción'}</p>
                        <small>{new Date(report.createdAt).toLocaleString('es-AR')}</small>
                      </article>
                    ))}
                  </div>
                )}
              </div>
            </AnimatedCollapse>
          </div>
        </AnimatedCollapse>
      </div>
    </section>
  )
}
