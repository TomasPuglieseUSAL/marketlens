import { useState, type FormEvent } from 'react'

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

export default function ReportsPanel({
  token,
  publicationId,
}: ReportsPanelProps) {

  const [reason, setReason] =
    useState<ReportReason>('SUSPICIOUS_PRICE')

  const [description, setDescription] = useState('')

  const [reports, setReports] = useState<Report[]>([])
  const [historyLoaded, setHistoryLoaded] = useState(false)

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

      const history = await getPublicationReports(
        token,
        publicationId
      )

      setReports(history)
      setHistoryLoaded(true)
      setDescription('')

      setMessage('Reporte registrado correctamente.')

    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Error inesperado'
      )
    } finally {
      setLoading(false)
    }
  }

  async function handleLoadReports() {
    setLoading(true)
    setMessage('')
    setError('')

    try {
      const history = await getPublicationReports(
        token,
        publicationId
      )

      setReports(history)
      setHistoryLoaded(true)

    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Error inesperado'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="reports-panel">
      <h3>Reportes colaborativos</h3>

      <p>
        Publicación seleccionada: <strong>#{publicationId}</strong>
      </p>

      <div className="details-grid">
        <article className="detail-card">
          <h4>Registrar reporte</h4>

          <form className="panel-form" onSubmit={handleCreate}>

            <label htmlFor="report-reason">
              Motivo del reporte
            </label>

            <select
              id="report-reason"
              value={reason}
              onChange={(e) =>
                setReason(e.target.value as ReportReason)
              }
            >
              {Object.entries(reportReasons).map(
                ([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                )
              )}
            </select>

            <label htmlFor="report-description">
              Descripción
            </label>

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

          <p>
            Consultá los reportes registrados para esta publicación.
          </p>

          <button
            onClick={handleLoadReports}
            disabled={loading}
            className="secondary-button"
          >
            Consultar reportes
          </button>
        </article>
      </div>

      {message && (
        <p className="success-message" role="status">
          {message}
        </p>
      )}

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {historyLoaded && (
        <div className="reports-history">
          <h4>Reportes registrados ({reports.length})</h4>

          {reports.length === 0 ? (
            <p>No hay reportes para esta publicación.</p>
          ) : (
            reports.map((report) => (
              <article
                key={report.id}
                className="detail-card report-card"
              >
                <div className="report-header">
                  <strong>
                    {reportReasons[report.reason] ?? report.reason}
                  </strong>

                  <span>#{report.id}</span>
                </div>

                <p>
                  {report.description || 'Sin descripción'}
                </p>

                <small>
                  {new Date(report.createdAt).toLocaleString()}
                </small>
              </article>
            ))
          )}
        </div>
      )}
    </section>
  )
}