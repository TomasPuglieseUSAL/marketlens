import { useEffect, useState } from 'react'

import {
  getSellers,
  type Seller,
} from '../api/client'

interface SellerSelectProps {
  token: string
  value: string
  onChange: (value: string) => void
}

export default function SellerSelect({
  token,
  value,
  onChange,
}: SellerSelectProps) {

  const [sellers, setSellers] = useState<Seller[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    async function loadSellers() {
      setLoading(true)
      setError('')

      try {
        const result = await getSellers(token, 0, 50)

        if (!cancelled) {
          setSellers(result.content)
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : 'Error inesperado'
          )
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadSellers()

    return () => {
      cancelled = true
    }
  }, [token])

  return (
    <>
      <label htmlFor="publication-seller">
        Vendedor
      </label>

      <select
        id="publication-seller"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={loading || Boolean(error)}
        required
      >
        <option value="">
          {loading ? 'Cargando vendedores...' : 'Seleccionar vendedor'}
        </option>

        {sellers.map((seller) => (
          <option key={seller.id} value={seller.id}>
            {seller.name} (ID: {seller.id})
          </option>
        ))}
      </select>

      {error && <p className="error">{error}</p>}
    </>
  )
}