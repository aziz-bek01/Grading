import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { JobProfileEditorPage } from './JobProfileEditorPage';
import type { JobProfile } from '../types';

const baseProfile: JobProfile = {
  id: 'jp-1',
  position_id: 'pos-1',
  project_id: 'proj-1',
  status: 'DRAFT',
  revision_number: 1,
  purpose_i18n: { 'ru-RU': 'Покрытие финансовых рисков' },
  main_duties_i18n: {},
  responsibility_area_i18n: {},
  authority_i18n: {},
  kpi_expected_results_i18n: {},
  education_requirements_i18n: {},
  experience_requirements_i18n: {},
  knowledge_skills_i18n: {},
  internal_interactions_i18n: {},
  external_interactions_i18n: {},
  working_conditions_i18n: {},
  documents_regulations_i18n: {},
  created_at: '2026-05-01T00:00:00Z',
  updated_at: '2026-05-01T00:00:00Z',
};

vi.mock('../hooks/useJobProfile', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useJobProfile')>('../hooks/useJobProfile');
  return {
    ...actual,
    useJobProfileByPosition: () => ({ data: (globalThis as { __profile?: JobProfile }).__profile ?? null, isLoading: false, error: null, refetch: vi.fn() }),
    useJobProfileRevisions: () => ({ data: { items: [] }, isLoading: false }),
    useCreateJobProfile: () => ({ mutateAsync: vi.fn() }),
    useUpdateJobProfile: () => ({ mutateAsync: vi.fn(), isPending: false }),
    useSubmitJobProfile: () => ({ mutateAsync: vi.fn() }),
    useApproveJobProfile: () => ({ mutateAsync: vi.fn() }),
    useRequestChanges: () => ({ mutateAsync: vi.fn() }),
    useArchiveJobProfile: () => ({ mutateAsync: vi.fn() }),
    useCreateJobProfileRevision: () => ({ mutateAsync: vi.fn() }),
  };
});

function renderPage() {
  return render(
    renderWithProviders(
      <Routes>
        <Route path="/app/projects/:projectId/positions/:positionId/job-profile" element={<JobProfileEditorPage />} />
      </Routes>,
      ['/app/projects/proj-1/positions/pos-1/job-profile'],
    ),
  );
}

describe('JobProfileEditorPage', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => {
    signOut();
    delete (globalThis as { __profile?: JobProfile }).__profile;
  });

  it('DRAFT shows editable fields + Save/Submit buttons', async () => {
    (globalThis as { __profile?: JobProfile }).__profile = { ...baseProfile, status: 'DRAFT' };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('action-save')).toBeInTheDocument());
    expect(screen.getByTestId('action-submit')).toBeInTheDocument();
    // Field is editable (textarea, not readonly div).
    // Field keys carry the `_i18n` suffix (Contract-A), so the testid is
    // `textarea-purpose_i18n-ru-RU` — see SECTIONS in JobProfileEditorPage.tsx.
    expect(screen.getByTestId('textarea-purpose_i18n-ru-RU')).toBeInTheDocument();
  });

  it('APPROVED renders fields read-only with Create new revision button', async () => {
    (globalThis as { __profile?: JobProfile }).__profile = { ...baseProfile, status: 'APPROVED' };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('action-create-revision')).toBeInTheDocument());
    // No save/submit buttons.
    expect(screen.queryByTestId('action-save')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-submit')).not.toBeInTheDocument();
    // Fields are rendered as read-only divs.
    expect(screen.getByTestId('readonly-purpose_i18n-ru-RU')).toBeInTheDocument();
    expect(screen.queryByTestId('textarea-purpose_i18n-ru-RU')).not.toBeInTheDocument();
  });

  it('UNDER_REVIEW shows Approve + Request changes for users with approve permission', async () => {
    (globalThis as { __profile?: JobProfile }).__profile = { ...baseProfile, status: 'UNDER_REVIEW' };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('action-approve')).toBeInTheDocument());
    expect(screen.getByTestId('action-request-changes')).toBeInTheDocument();
  });

  it('ARCHIVED shows archived banner and no edit actions', async () => {
    (globalThis as { __profile?: JobProfile }).__profile = { ...baseProfile, status: 'ARCHIVED' };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('archived-banner')).toBeInTheDocument());
    expect(screen.queryByTestId('action-save')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-approve')).not.toBeInTheDocument();
  });
});
