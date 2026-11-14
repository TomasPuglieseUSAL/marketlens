const API_BASE_URL = '/api'

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface RegisterResponse {
  id: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  createdAt: string
}

export interface DashboardSummary {
  totalPublications: number
  totalSellers: number
  totalEvaluations: number
  totalReports: number
  averageRiskScore: number
  evaluationsByRiskLevel: Record<string, number>
  publicationsByMarketplace: Record<string, number>
}

type UnauthorizedHandler = (rejectedToken: string) => void

let unauthorizedHandler: UnauthorizedHandler | null = null

export function setUnauthorizedHandler(
  handler: UnauthorizedHandler | null
) {
  unauthorizedHandler = handler
}

async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {},
  token?: string
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })

  if (!response.ok) {
  const body = await response.json().catch(() => null)

  if (response.status === 401 && token) {
    unauthorizedHandler?.(token)

    throw new Error(
      'Tu sesión ya no es válida. Volvé a iniciar sesión.'
    )
  }

  throw new Error(
    body?.message || `Error HTTP ${response.status}`
  )
  }

  return response.json() as Promise<T>
}

export function login(username: string, password: string) {
  return apiRequest<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function register(request: RegisterRequest) {
  return apiRequest<RegisterResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function getDashboardSummary(token: string) {
  return apiRequest<DashboardSummary>(
    '/dashboard/summary',
    {},
    token
  )
}

export interface Seller {
  id: number
  name: string
  reputation: number
  accountAgeDays: number
}

export interface CreateSellerRequest {
  name: string
  reputation: number
  accountAgeDays: number
}

export function createSeller(
  token: string,
  seller: CreateSellerRequest
) {
  return apiRequest<Seller>(
    '/sellers',
    {
      method: 'POST',
      body: JSON.stringify(seller),
    },
    token
  )
}

export function getSeller(
  token: string,
  id: number
) {
  return apiRequest<Seller>(
    `/sellers/${id}`,
    {},
    token
  )
}

export type Marketplace =
  | 'MERCADO_LIBRE'
  | 'FACEBOOK_MARKETPLACE'
  | 'TEMU'
  | 'OTHER'

export interface Publication {
  id: number
  title: string
  description: string
  price: number
  marketplace: Marketplace
  externalId: string | null
  originalUrl: string | null
  sellerId: number | null
}

export interface CreatePublicationRequest {
  title: string
  description: string
  price: number
  marketplace: Marketplace
  externalId: string | null
  originalUrl: string | null
  sellerId: number
}

export function createPublication(
  token: string,
  publication: CreatePublicationRequest
) {
  return apiRequest<Publication>(
    '/publications',
    {
      method: 'POST',
      body: JSON.stringify(publication),
    },
    token
  )
}

export function getPublication(
  token: string,
  id: number
) {
  return apiRequest<Publication>(
    `/publications/${id}`,
    {},
    token
  )
}

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface RiskFactor {
  id: number
  code: string
  description: string
  scoreContribution: number
}

export interface RiskEvaluation {
  id: number
  score: number
  level: RiskLevel
  evaluatedAt: string
  factors: RiskFactor[]
}

export function evaluatePublication(
  token: string,
  publicationId: number
) {
  return apiRequest<RiskEvaluation>(
    `/publications/${publicationId}/evaluations`,
    {
      method: 'POST',
    },
    token
  )
}

export function getPublicationEvaluations(
  token: string,
  publicationId: number
) {
  return apiRequest<RiskEvaluation[]>(
    `/publications/${publicationId}/evaluations`,
    {},
    token
  )
}

export type ReportReason =
  | 'SUSPICIOUS_PRICE'
  | 'SUSPICIOUS_SELLER'
  | 'SUSPICIOUS_DESCRIPTION'
  | 'PAYMENT_OUTSIDE_PLATFORM'
  | 'POSSIBLE_IMPERSONATION'
  | 'OTHER'

export interface Report {
  id: number
  reason: ReportReason
  description: string | null
  createdAt: string
  userId?: number
  publicationId?: number
}

export interface CreateReportRequest {
  reason: ReportReason
  description: string
}

export function createReport(
  token: string,
  publicationId: number,
  report: CreateReportRequest
) {
  return apiRequest<Report>(
    `/publications/${publicationId}/reports`,
    {
      method: 'POST',
      body: JSON.stringify(report),
    },
    token
  )
}

export function getPublicationReports(
  token: string,
  publicationId: number
) {
  return apiRequest<Report[]>(
    `/publications/${publicationId}/reports`,
    {},
    token
  )
}

export interface PublicationListItem {
  id: number
  title: string
  price: number
  marketplace: Marketplace
  sellerId: number | null
  sellerName: string | null
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export function getPublications(
  token: string,
  page = 0,
  size = 10
) {
  return apiRequest<PageResponse<PublicationListItem>>(
    `/publications?page=${page}&size=${size}`,
    {},
    token
  )
}

export function getSellers(
  token: string,
  page = 0,
  size = 10
) {
  return apiRequest<PageResponse<Seller>>(
    `/sellers?page=${page}&size=${size}`,
    {},
    token
  )
}

export interface MarketplaceStatistics {
  marketplace: Marketplace
  totalPublications: number
  evaluatedPublications: number
  averageRiskScore: number
  lowRiskCount: number
  mediumRiskCount: number
  highRiskCount: number
  totalReports: number
}

export function getMarketplaceStatistics(token: string) {
  return apiRequest<MarketplaceStatistics[]>(
    '/dashboard/marketplaces',
    {},
    token
  )
}