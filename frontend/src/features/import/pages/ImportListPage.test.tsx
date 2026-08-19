import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { mockDb, type MockImportBatch } from '@/shared/api/mocks/fixtures';
import { ImportListPage } from './ImportListPage';
import type { AxiosAdapter } from 'axios';

const PROJECT_ID = 'proj-acme-2026';

function seedBatch(overrides: Partial<MockImportBatch> & { id: string }): MockImportBatch {
  const batch: MockImportBatch = {
    project_id: PROJECT_ID,
    template_code: 'ORG_STRUCTURE_V1',
    status: 'READY_FOR_REVIEW',
    original_filename: `${overrides.id}.xlsx`,
    file_size: 1024,
    total_row_count: 10,
    error_row_count: 0,
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

function renderListPage(initialPath = `/app/projects/${PROJECT_ID}/imports`) {
  return render(
    renderWithProviders(
      <Routes>
        <Route path="/app/projects/:projectId/imports" element={<ImportListPage />} />
      </Routes>,
      [initialPath],
    ),
  );
}

describe('ImportListPage', () => {
  let originalAdapter: AxiosAdapter | undefined;

  beforeEach(() => {
    signIn('super-admin');
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
  });

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter;
    signOut();
    mockDb.importBatches = mockDb.importBatches.filter((b) => !b.id.startsWith('imp-list-test-'));
  });

  it('hides ARCHIVED batches from the default ("All statuses") view', async () => {
    seedBatch({ id: 'imp-list-test-active-1', status: 'READY_FOR_REVIEW' });
    seedBatch({ id: 'imp-list-test-archived-1', status: 'ARCHIVED', original_filename: 'archived-one.xlsx' });
    renderListPage();

    await screen.findByTestId('import-row-imp-list-test-active-1');
    expect(screen.queryByTestId('import-row-imp-list-test-archived-1')).not.toBeInTheDocument();
  });

  it('reveals ARCHIVED batches once "Show archived" is checked', async () => {
    seedBatch({ id: 'imp-list-test-active-2', status: 'READY_FOR_REVIEW' });
    seedBatch({ id: 'imp-list-test-archived-2', status: 'ARCHIVED', original_filename: 'archived-two.xlsx' });
    renderListPage();

    await screen.findByTestId('import-row-imp-list-test-active-2');
    expect(screen.queryByTestId('import-row-imp-list-test-archived-2')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('import-list-show-archived'));

    await waitFor(() => {
      expect(screen.getByTestId('import-row-imp-list-test-archived-2')).toBeInTheDocument();
    });
    // The other (non-archived) batch is still visible too.
    expect(screen.getByTestId('import-row-imp-list-test-active-2')).toBeInTheDocument();
  });

  it('still lets the existing status filter dropdown select ARCHIVED explicitly', async () => {
    seedBatch({ id: 'imp-list-test-active-3', status: 'READY_FOR_REVIEW' });
    seedBatch({ id: 'imp-list-test-archived-3', status: 'ARCHIVED', original_filename: 'archived-three.xlsx' });
    renderListPage();

    await screen.findByTestId('import-row-imp-list-test-active-3');

    fireEvent.change(screen.getByTestId('import-list-filter-status'), {
      target: { value: 'ARCHIVED' },
    });

    await waitFor(() => {
      expect(screen.getByTestId('import-row-imp-list-test-archived-3')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('import-row-imp-list-test-active-3')).not.toBeInTheDocument();
  });

  it('renders a flash success banner forwarded from ImportDetailsPage after archiving', () => {
    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/imports" element={<ImportListPage />} />
        </Routes>,
        [
          {
            pathname: `/app/projects/${PROJECT_ID}/imports`,
            state: { flashMessageKey: 'import.details.archive_success' },
          },
        ],
      ),
    );
    expect(screen.getByTestId('import-list-flash')).toBeInTheDocument();
  });
});
