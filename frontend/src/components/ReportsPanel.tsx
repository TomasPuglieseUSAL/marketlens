import { useId, useState, type FormEvent } from 'react'
import AnimatedCollapse from './AnimatedCollapse'

import {
  createReport,
  getPublicationReports,
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
  const [historyLoaded, setHistoryLoaded] = useState(false)
  const [expanded, setExpanded] = useState(false)

  const contentId = useId()
  const reportsHistoryId = useId()

  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

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
      const history = await getPublicationReports(token, publicationId)
      setReports(history)
      setHistoryLoaded(true)
      setDescription('')
      setMessage('Reporte registrado correctamente.')
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

      <div className="reports-disclosure">
        <button
          type="button"
          className="reports-toggle"
          onClick={() => setExpanded((current) => !current)}
          aria-expanded={expanded}
          aria-controls={contentId}
        >
          Registrar o consultar reportes
          <span className="disclosure-chevron" aria-hidden="true">⌄</span>
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
                  <span className="disclosure-chevron" aria-hidden="true">⌄</span>
                </button>
              </article>
            </div>

            {message && <p className="success-message" role="status">{message}</p>}
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
