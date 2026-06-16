import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import i18n from '@/shared/i18n';

// jsdom does not implement the object-URL APIs used by the authenticated
// blob-download path (downloadAuthenticatedFile, AuditCsvExportButton,
// templateDownload). Provide no-op stubs so download flows can be exercised
// in component tests without "Not implemented" errors. Individual tests may
// still spy on these to assert create/revoke behaviour.
if (typeof URL.createObjectURL !== 'function') {
  URL.createObjectURL = () => 'blob:mock';
}
if (typeof URL.revokeObjectURL !== 'function') {
  URL.revokeObjectURL = () => undefined;
}

afterEach(() => {
  cleanup();
});

// Make sure EVERY test starts from a known, fully-loaded locale.
//
// Non-default locale bundles are now code-split and resolved through an async
// i18next backend (P1-T1). i18next's `changeLanguage` therefore sets the active
// language only after the backend `read` resolves, so this must be AWAITED in a
// `beforeEach` (a fire-and-forget `void` could leave a test rendering under the
// jsdom-detected `navigator` locale, en-US). ru-RU is eagerly bundled, so this
// resolves on a microtask without any network/dynamic-import round-trip.
beforeEach(async () => {
  await i18n.changeLanguage('ru-RU');
});
