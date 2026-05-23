import type { ErrorEnvelope } from '@/shared/types/common';

/** Typed wrapper around backend ErrorResponse. */
export class ApiError extends Error {
  public readonly status: number;
  public readonly code: string;
  public readonly correlationId?: string;
  public readonly traceId?: string;
  public readonly fieldErrors?: Record<string, string>;

  constructor(status: number, envelope: Partial<ErrorEnvelope> & { message?: string }) {
    super(envelope.message ?? 'API error');
    this.name = 'ApiError';
    this.status = status;
    this.code = envelope.code ?? 'UNKNOWN_ERROR';
    this.correlationId = envelope.correlation_id;
    this.traceId = envelope.traceId;
    this.fieldErrors = envelope.fieldErrors;
  }

  isAuthError(): boolean {
    return this.status === 401;
  }
  isForbidden(): boolean {
    return this.status === 403;
  }
  isNotFound(): boolean {
    return this.status === 404;
  }
  isValidation(): boolean {
    return this.status === 400 || this.status === 422;
  }
}
