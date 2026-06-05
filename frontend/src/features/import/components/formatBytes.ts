/**
 * Human-readable byte size formatter for the import uploader.
 *
 * Lives in its own module (not in FileDropzone.tsx) so the .tsx file only
 * exports React components (react-refresh/only-export-components).
 */
export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}
