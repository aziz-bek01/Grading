/**
 * Authenticated blob download helper.
 *
 * The export/report `GET .../download` endpoints are JWT-authenticated and
 * STREAM the file bytes (Content-Type + Content-Disposition set
 * server-side). There is NO token in the URL, so a plain `<a href>`
 * navigation omits the Authorization (and X-Active-Tenant-Id) header and the
 * browser gets a 401/403. We MUST pull the bytes through the shared
 * `httpClient` (so the request interceptor attaches auth + active-tenant
 * headers), then trigger a client-side object-URL download.
 *
 * This is the single, reused implementation behind every authenticated file
 * download (Export Center + Reports Center). The import template helper
 * (`features/import/api/templateDownload.ts`) follows the same shape; if its
 * endpoints ever move behind this signature, route it here too.
 *
 * SECURITY:
 *  - Goes through `httpClient` → Authorization + X-Active-Tenant-Id attached.
 *  - Filename comes from the server-authoritative Content-Disposition header
 *    (we never trust a client-built filename if the server provides one).
 *  - Never logs the blob, the URL, or any token.
 */
import { httpClient } from './httpClient';

/** Extension fallback per Content-Type when no Content-Disposition is sent. */
const EXT_BY_MIME: Array<[fragment: string, ext: string]> = [
  ['spreadsheetml', 'xlsx'],
  ['ms-excel', 'xlsx'],
  ['csv', 'csv'],
  ['pdf', 'pdf'],
  ['wordprocessingml', 'docx'],
  ['msword', 'docx'],
];

/** Best-effort extension from a MIME type (defaults to `bin`). */
function extFromMime(mime: string | undefined): string {
  if (!mime) return 'bin';
  const lower = mime.toLowerCase();
  for (const [fragment, ext] of EXT_BY_MIME) {
    if (lower.includes(fragment)) return ext;
  }
  return 'bin';
}

/**
 * Parses a filename from a Content-Disposition header. Handles both the
 * `filename="..."` and RFC 5987 `filename*=UTF-8''...` forms; returns null
 * when neither is present.
 */
export function parseContentDispositionFilename(
  header: string | undefined | null,
): string | null {
  if (!header) return null;
  // RFC 5987 extended form takes precedence (carries the real UTF-8 name).
  const star = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(header);
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''));
    } catch {
      /* fall through to the plain form */
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain?.[1]?.trim() ?? null;
}

/** Reads a (case-insensitive) header from an axios-style headers bag. */
function readHeader(
  headers: Record<string, unknown> | undefined,
  name: string,
): string | undefined {
  if (!headers) return undefined;
  const lower = name.toLowerCase();
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === lower && typeof value === 'string') return value;
  }
  return undefined;
}

export interface DownloadBlobOptions {
  /**
   * Same-origin relative API path to GET, e.g. `/exports/{id}/download`.
   * Passed straight to `httpClient` (baseURL + interceptors applied).
   */
  path: string;
  /**
   * Fallback filename used ONLY when the server omits Content-Disposition.
   * The header (when present) always wins.
   */
  fallbackFilename: string;
}

/**
 * Fetches `path` through the authenticated `httpClient` as a blob and triggers
 * a browser download. Resolves once the click is dispatched; rejects with the
 * typed `ApiError` thrown by `httpClient` on 401/403/404/400 so callers can
 * surface a localized message.
 */
export async function downloadAuthenticatedFile(
  options: DownloadBlobOptions,
): Promise<void> {
  const res = await httpClient.get<Blob>(options.path, { responseType: 'blob' });

  const blob =
    res.data instanceof Blob
      ? res.data
      : new Blob([res.data as unknown as BlobPart]);

  const headers = res.headers as Record<string, unknown> | undefined;
  const fromHeader = parseContentDispositionFilename(
    readHeader(headers, 'content-disposition'),
  );
  const filename =
    fromHeader ??
    ensureExtension(options.fallbackFilename, extFromMime(blob.type));

  const objectUrl = URL.createObjectURL(blob);
  try {
    const a = document.createElement('a');
    a.href = objectUrl;
    a.download = filename;
    a.rel = 'noopener noreferrer';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  } finally {
    // Revoke after the click has been processed; immediate revoke can race the
    // browser's read of the object URL in some engines.
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
  }
}

/** Appends `.<ext>` to `name` if it has no extension already. */
function ensureExtension(name: string, ext: string): string {
  return /\.[a-z0-9]+$/i.test(name) ? name : `${name}.${ext}`;
}
