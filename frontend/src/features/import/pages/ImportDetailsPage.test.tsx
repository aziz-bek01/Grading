import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { ApiError } from '@/shared/api/apiError';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { mockDb, type MockImportBatch } from '@/shared/api/mocks/fixtures';
import { ImportDetailsPage } from './ImportDetailsPage';
import { ImportListPage } from './ImportListPage';
import type { AxiosAdapter } from 'axios';

const PROJECT_ID = 'proj-acme-2026';

function seedBatch(overrides: Partial<MockImportBatch> & { id: string }): MockImportBatch {
  const batch: MockImportBatch = {
    project_id: PROJECT_ID,
    template_code: 'ORG_STRUCTURE_V1',
    status: 'READY_FOR_REVIEW',
    original_filename: 'acme_org_structure.xlsx',
    file_size: 1024,
    total_row_count: 373,
    error_row_count: 4,
    warning_row_count: 0,
    committed_row_count: 0,
    contains_salary_data: false,
    uploaded_by: 'user-1',
    uploaded_at: '2026-08-10T09:00:00Z',
    ...overrides,
  };
  mockDb.importBatches.unshift(batch);
  return batch;
}

function renderDetailsPage(importId: string) {
  return render(
    renderWithProviders(
      <Routes>
        <Route
          path="/app/projects/:projectId/imports/:importId"
          element={<ImportDetailsPage />}
        />
        <Route path="/app/projects/:projectId/imports" element={<ImportListPage />} />
      </Routes>,
      [`/app/projects/${PROJECT_ID}/imports/${importId}`],
    ),
  );
}

describe('ImportDetailsPage', () => {
  let originalAdapter: AxiosAdapter | undefined;

  beforeEach(() => {
    signIn('super-admin');
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
  });

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter;
    signOut();
    // Drop every batch this file pushed so fixtures stay pristine for other tests.
    mockDb.importBatches = mockDb.importBatches.filter((b) => !b.id.startsWith('imp-test-'));
  });

  // -----------------------------------------------------------------------
  // Status-gated action buttons — MUST mirror the backend
  // ImportBatchStatusTransitionPolicy exactly (canCancelImportStatus /
  // canArchiveImportStatus in ../types.ts).
  // -----------------------------------------------------------------------

  it('hides Cancel and shows Archive for PARTIALLY_COMMITTED (prod bug regression)', async () => {
    const batch = seedBatch({
      id: 'imp-test-partial-1',
      status: 'PARTIALLY_COMMITTED',
      committed_row_count: 369,
      error_row_count: 4,
    });
    renderDetailsPage(batch.id);

    await screen.findByTestId('import-summary-card');
    expect(screen.queryByTestId('import-details-cancel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('import-details-commit')).not.toBeInTheDocument();
    expect(screen.getByTestId('import-details-archive')).toBeInTheDocument();
  });

  it('hides Cancel and shows Archive for COMMITTED', async () => {
    const batch = seedBatch({ id: 'imp-test-committed-1', status: 'COMMITTED', committed_row_count: 10 });
    renderDetailsPage(batch.id);

    await screen.findByTestId('import-summary-card');
    expect(screen.queryByTestId('import-details-cancel')).not.toBeInTheDocument();
    expect(screen.getByTestId('import-details-archive')).toBeInTheDocument();
  });

  it('shows Cancel (and Commit) but not Archive for READY_TO_COMMIT', async () => {
    const batch = seedBatch({ id: 'imp-test-ready-1', status: 'READY_TO_COMMIT' });
    renderDetailsPage(batch.id);

    await screen.findByTestId('import-summary-card');
    expect(screen.getByTestId('import-details-cancel')).toBeInTheDocument();
    expect(screen.getByTestId('import-details-commit')).toBeInTheDocument();
    expect(screen.queryByTestId('import-details-archive')).not.toBeInTheDocument();
  });

  it('shows BOTH Cancel and Archive for VALIDATION_FAILED (backend allows either transition)', async () => {
    const batch = seedBatch({ id: 'imp-test-valfail-1', status: 'VALIDATION_FAILED' });
    renderDetailsPage(batch.id);

    await screen.findByTestId('import-summary-card');
    expect(screen.getByTestId('import-details-cancel')).toBeInTheDocument();
    expect(screen.getByTestId('import-details-archive')).toBeInTheDocument();
  });

  // -----------------------------------------------------------------------
  // Archive: non-destructive terminal action, navigates back to the list.
  // -----------------------------------------------------------------------

  it('archiving navigates back to the imports list and shows a success flash', async () => {
    const batch = seedBatch({ id: 'imp-test-archive-ok-1', status: 'COMMITTED', committed_row_count: 5 });
    renderDetailsPage(batch.id);

    await screen.findByTestId('import-details-archive');
    fireEvent.click(screen.getByTestId('import-details-archive'));

    await waitFor(() => {
      expect(screen.getByTestId('import-list-page')).toBeInTheDocument();
    });
    const flash = await screen.findByTestId('import-list-flash');
    expect(flash.textContent ?? '').not.toHaveLength(0);
  });

  // -----------------------------------------------------------------------
  // Error surfacing — a rejected mutation must be VISIBLE, never a silent
  // no-op (the original prod bug: clicking Cancel on PARTIALLY_COMMITTED did
  // nothing because `cancel.mutate()` had no onError handler).
  // -----------------------------------------------------------------------

  it('surfaces a cancel failure inline, including the backend transition-rejected reason', async () => {
    const batch = seedBatch({ id: 'imp-test-cancel-fail-1', status: 'READY_TO_COMMIT' });
    renderDetailsPage(batch.id);
    await screen.findByTestId('import-details-cancel');

    const postSpy = vi
      .spyOn(httpClient, 'post')
      .mockRejectedValueOnce(
        new ApiError(409, {
          code: 'IMPORT_BATCH_TRANSITION_REJECTED',
          message: 'Illegal status transition: PARTIALLY_COMMITTED -> CANCELLED',
        }),
      );

    fireEvent.click(screen.getByTestId('import-details-cancel'));
    await waitFor(() => expect(postSpy).toHaveBeenCalled());

    const banner = await screen.findByTestId('import-details-error');
    expect(banner.textContent ?? '').toContain('PARTIALLY_COMMITTED');
    postSpy.mockRestore();
  });

  it('surfaces an archive failure inline with the backend error code + reference', async () => {
    const batch = seedBatch({ id: 'imp-test-archive-fail-1', status: 'COMMITTED' });
    renderDetailsPage(batch.id);
    await screen.findByTestId('import-details-archive');

    const postSpy = vi
      .spyOn(httpClient, 'post')
      .mockRejectedValueOnce(new ApiError(500, { code: 'INTERNAL_ERROR', correlation_id: 'corr-archive-1' }));

    fireEvent.click(screen.getByTestId('import-details-archive'));
    await waitFor(() => expect(postSpy).toHaveBeenCalled());

    const banner = await screen.findByTestId('import-details-error');
    expect(banner.textContent).toContain('INTERNAL_ERROR');
    expect(banner.textContent).toContain('corr-archive-1');
    // A failed archive must NOT navigate away — the user needs to see the error.
    expect(screen.queryByTestId('import-list-page')).not.toBeInTheDocument();
    postSpy.mockRestore();
  });

  it('surfaces a commit failure inline (permission denied)', async () => {
    const batch = seedBatch({ id: 'imp-test-commit-fail-1', status: 'READY_TO_COMMIT' });
    renderDetailsPage(batch.id);
    await screen.findByTestId('import-details-commit');

    const postSpy = vi
      .spyOn(httpClient, 'post')
      .mockRejectedValueOnce(new ApiError(403, { code: 'FORBIDDEN' }));

    fireEvent.click(screen.getByTestId('import-details-commit'));
    await waitFor(() => expect(postSpy).toHaveBeenCalled());

    const banner = await screen.findByTestId('import-details-error');
    expect(banner.textContent ?? '').not.toHaveLength(0);
    postSpy.mockRestore();
  });

  // -----------------------------------------------------------------------
  // Success feedback — cancel/commit stay on the page, so success is an
  // inline banner (archive navigates away, covered above).
  // -----------------------------------------------------------------------

  it('shows an inline success banner after a successful cancel', async () => {
    const batch = seedBatch({ id: 'imp-test-cancel-ok-1', status: 'READY_TO_COMMIT' });
    renderDetailsPage(batch.id);
    await screen.findByTestId('import-details-cancel');

    fireEvent.click(screen.getByTestId('import-details-cancel'));

    const banner = await screen.findByTestId('import-details-success');
    expect(banner.textContent ?? '').not.toHaveLength(0);
  });

  it('shows an inline success banner after a successful commit', async () => {
    const batch = seedBatch({ id: 'imp-test-commit-ok-1', status: 'READY_TO_COMMIT', total_row_count: 12 });
    renderDetailsPage(batch.id);
    await screen.findByTestId('import-details-commit');

    fireEvent.click(screen.getByTestId('import-details-commit'));

    const banner = await screen.findByTestId('import-details-success');
    expect(banner.textContent ?? '').not.toHaveLength(0);
  });
});
