/**
 * Verifies the new "Download empty template" and "Download sample" buttons
 * on Import wizard step 1 trigger the correct backend endpoints.
 *
 * Per PO spec AC1/AC2:
 *  - Click "Download empty" -> GET /imports/templates/{code}/empty.xlsx
 *  - Click "Download sample" -> GET /imports/templates/{code}/sample.xlsx
 *
 * We spy on the httpClient.get call rather than relying on the browser's
 * download mechanism (jsdom does not implement <a download>).
 */
import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { Routes, Route } from 'react-router-dom';
import { ImportWizardPage } from './ImportWizardPage';
import { httpClient } from '@/shared/api/httpClient';

describe('ImportWizardPage — template download buttons', () => {
  beforeEach(() => {
    signIn('super-admin');
  });
  afterEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  it('clicking "Download empty" calls /imports/templates/ORG_STRUCTURE_V1/empty.xlsx', async () => {
    const spy = vi
      .spyOn(httpClient, 'get')
      // Resolve with an empty blob; component then attempts a download anchor click which is a no-op in jsdom.
      .mockResolvedValue({
        data: new Blob(['x'], { type: 'text/csv' }),
        headers: {},
      } as unknown as Awaited<ReturnType<typeof httpClient.get>>);

    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/imports/new" element={<ImportWizardPage />} />
        </Routes>,
        ['/app/projects/proj-acme-2026/imports/new'],
      ),
    );

    fireEvent.click(screen.getByTestId('wizard-template-ORG_STRUCTURE_V1-empty'));

    // Wait a tick for the async download helper to run.
    await new Promise((r) => setTimeout(r, 0));

    expect(spy).toHaveBeenCalled();
    const url = (spy.mock.calls[0]?.[0] as string) ?? '';
    expect(url).toContain('/imports/templates/ORG_STRUCTURE_V1/empty.xlsx');
  });

  it('clicking "Download sample" calls /imports/templates/POSITION_CATALOG_V1/sample.xlsx', async () => {
    const spy = vi
      .spyOn(httpClient, 'get')
      .mockResolvedValue({
        data: new Blob(['x'], { type: 'text/csv' }),
        headers: {},
      } as unknown as Awaited<ReturnType<typeof httpClient.get>>);

    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/imports/new" element={<ImportWizardPage />} />
        </Routes>,
        ['/app/projects/proj-acme-2026/imports/new'],
      ),
    );

    fireEvent.click(screen.getByTestId('wizard-template-POSITION_CATALOG_V1-sample'));

    await new Promise((r) => setTimeout(r, 0));

    expect(spy).toHaveBeenCalled();
    const url = (spy.mock.calls[0]?.[0] as string) ?? '';
    expect(url).toContain('/imports/templates/POSITION_CATALOG_V1/sample.xlsx');
  });
});
