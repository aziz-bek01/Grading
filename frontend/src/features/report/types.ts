/**
 * Report — MVP 2 Phase 3 domain types (Reports Center).
 *
 * Mirrors backend `uz.hrlab.grading.reporting.api.ReportResponse` and the
 * three enums (`ReportType`, `ReportFormat`, `ReportStatus`) in
 * `uz.hrlab.grading.reporting.domain.*`. See architecture §17 / ADR-009.
 *
 * Tenant identifiers are NEVER carried by these types — backend derives the
 * active tenant from the JWT (security blueprint API-13).
 */

export type ReportStatus =
  | 'REQUESTED'
  | 'QUEUED'
  | 'GENERATING'
  | 'GENERATED'
  | 'FAILED'
  | 'DOWNLOADED'
  | 'EXPIRED'
  | 'CANCELLED';

export type ReportType =
  | 'GRADE_DISTRIBUTION'
  | 'POSITION_CATALOG'
  | 'EVALUATION_SUMMARY'
  | 'METHODOLOGY_SPEC'
  | 'AUDIT_SUMMARY'
  | 'EXECUTIVE_SUMMARY';

export type ReportFormat = 'PDF' | 'DOCX' | 'XLSX';

/**
 * Per-report-type format availability matrix.
 *
 * Mirrors the backend template registry in the `reporting` module — every
 * report type implementation declares the formats it supports. Frontend
 * uses this to disable format options that the backend would reject.
 *
 * Keep this in lock-step with `ReportType` + `ReportFormat` enum coverage
 * (architecture §17 / ADR-009).
 */
export const REPORT_FORMAT_AVAILABILITY: Record<ReportType, ReportFormat[]> = {
  GRADE_DISTRIBUTION: ['PDF', 'DOCX', 'XLSX'],
  POSITION_CATALOG: ['PDF', 'DOCX', 'XLSX'],
  EVALUATION_SUMMARY: ['PDF', 'DOCX', 'XLSX'],
  METHODOLOGY_SPEC: ['PDF', 'DOCX'],
  AUDIT_SUMMARY: ['PDF', 'XLSX'],
  EXECUTIVE_SUMMARY: ['PDF'],
};

/** Status sets used to gate row actions in the table (cancel / download). */
export const REPORT_IN_FLIGHT_STATUSES: ReadonlySet<ReportStatus> = new Set<ReportStatus>([
  'REQUESTED',
  'QUEUED',
  'GENERATING',
]);

export const REPORT_DOWNLOADABLE_STATUSES: ReadonlySet<ReportStatus> = new Set<ReportStatus>([
  'GENERATED',
  'DOWNLOADED',
]);

/**
 * Wire shape returned by `GET /api/v1/reports/{id}` and elements of the
 * `GET /api/v1/reports` paged response (mirrors `ReportResponse` record).
 */
export interface Report {
  id: string;
  projectId: string;
  reportType: ReportType;
  format: ReportFormat;
  status: ReportStatus;
  title: string | null;
  locale: string | null;
  requestedBy: string | null;
  requestedAt: string;
  generatedAt: string | null;
  expiresAt: string | null;
  downloadedAt: string | null;
  fileSize: number | null;
  containsSalaryData: boolean;
  containsPersonalData: boolean;
  attemptCount: number;
  failureReason: string | null;
  traceId: string | null;
}

export interface ReportRequestPayload {
  reportType: ReportType;
  format: ReportFormat;
  projectId: string;
  filterParams?: string | null;
  /**
   * Structured EVALUATION_SUMMARY filter (PRD: reports-evaluation-filters).
   * Only meaningful when `reportType === 'EVALUATION_SUMMARY'`. Serialized by
   * `reportApi.requestReport` into the single existing `filter_params` wire
   * field as a JSON string — NOT sent as separate top-level request fields
   * (keeps the wire contract for `POST /reports/request` unchanged in shape).
   */
  evaluationFilter?: EvaluationReportFilter | null;
}

/**
 * Structured filter for the EVALUATION_SUMMARY report (methodology version,
 * submitted-date range, evaluator). Mirrors the backend `EvaluationReportFilter`
 * record (reporting/application/template). Own keys are snake_case because
 * this object is JSON-stringified verbatim into `filter_params` — the backend
 * parses it directly with the project's snake_case Jackson convention.
 * Empty arrays / empty date strings are omitted before serialization (absent
 * key = no filter, never an explicit empty-array narrowing).
 */
export interface EvaluationReportFilter {
  methodology_version_ids?: string[];
  date_from?: string;
  date_to?: string;
  evaluator_user_ids?: string[];
}

/**
 * Paged envelope — uses the existing frontend convention (items/total_*),
 * matched in the MSW handlers. The real backend `PageResponse<T>` uses
 * `content/totalElements/totalPages`; the httpClient or fetcher layer can
 * remap if/when wired to live API.
 */
export interface ReportPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
