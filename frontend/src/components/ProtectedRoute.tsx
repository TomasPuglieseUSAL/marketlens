import type { ReactNode } from 'react'
import { Navigate } from 'react-router'

interface ProtectedRouteProps {
  token: string | null
  children: ReactNode
}

export default function ProtectedRoute({
  token,
  children,
}: ProtectedRouteProps) {
  if (!token) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}