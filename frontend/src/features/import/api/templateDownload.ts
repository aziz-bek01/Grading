/**
 * Template download helper — drives the two backend endpoints
 *   GET /api/v1/imports/templates/{code}/empty.xlsx
 *   GET /api/v1/imports/templates/{code}/sample.xlsx
 *
 * In real-backend mode this hits Spring + Apache POI. In MSW/demo mode the
 * mock adapter (handlers.ts) returns a synthesised CSV blob with the same
 * ACME Holdings data so the user always sees a real file download.
 */
import { httpClient } from '@/shared/api/httpClient';
import type { ImportTemplateCode } from '../types';

export type TemplateVariant = 'empty' | 'sample';

export function templateDownloadUrl(
  code: ImportTemplateCode,
  variant: TemplateVariant,
): string {
  return `/imports/templates/${code}/${variant}.xlsx`;
}

export async function downloadTemplate(
  code: ImportTemplateCode,
  variant: TemplateVariant,
): Promise<void> {
  const res = await httpClient.get<Blob>(templateDownloadUrl(code, variant), {
    responseType: 'blob',
  });
  const blob = res.data instanceof Blob ? res.data : new Blob([res.data as unknown as ArrayBuffer]);
  // Best-effort filename — backend sends Content-Disposition but axios in
  // some setups does not expose it; we fall back to the convention.
  const cd = (res.headers as Record<string, string>)['content-disposition'];
  const match = cd ? /filename=\"?([^";]+)\"?/i.exec(cd) : null;
  const ext = blob.type.includes('csv') ? 'csv' : 'xlsx';
  const filename = match?.[1] ?? `${code}_${variant}.${ext}`;

  const url = URL.createObjectURL(blob);
  try {
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.rel = 'noopener noreferrer';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  } finally {
    // Free the object URL after the click completes.
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
