import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { JobProfileEditorPage } from './JobProfileEditorPage';
import type { JobProfile, JobProfileStatus } from '../types';

/**
 * F-306 — Pending 30s auto-save timers MUST NOT fire a PATCH after the
 * profile transitioned out of DRAFT (e.g. user clicked Submit). Backend
 * rejects with 409, but the wasted PATCH causes a flash error and lost
 * keystrokes. The fix is two-layered:
 *   (a) the useEffect cleanup clears the interval when `readOnly` flips,
 *   (b) the saveDraft callback re-checks the live status via a ref.
 */

const baseProfile: JobProfile = {
  id: 'jp-1',
  position_id: 'pos-1',
  project_id: 'proj-1',
  status: 'DRAFT',
  revision_number: 1,
  purpose: { 'ru-RU': 'Initial purpose' },
  main_duties: {},
  responsibility_area: {},
  authority: {},
  kpi_expected_results: {},
  education_requirements: {},
  experience_requirements: {},
  knowledge_skills: {},
  internal_interactions: {},
  external_interactions: {},
  working_conditions: {},
  documents_regulations: {},
  created_at: '2026-05-01T00:00:00Z',
  updated_at: '2026-05-01T00:00:00Z',
};

const updateMutateAsync = vi.fn();

vi.mock('../hooks/useJobProfile', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useJobProfile')>('../hooks/useJobProfile');
  return {
    ...actual,
    useJobProfileByPosition: () => ({
      data: (globalThis as { __profile?: JobProfile }).__profile ?? null,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    }),
    useJobProfileRevisions: () => ({ data: { items: [] }, isLoading: false }),
    useCreateJobProfile: () => ({ mutateAsync: vi.fn() }),
    useUpdateJobProfile: () => ({ mutateAsync: updateMutateAsync, isPending: false }),
    useSubmitJobProfile: () => ({ mutateAsync: vi.fn() }),
    useApproveJobProfile: () => ({ mutateAsync: vi.fn() }),
    useRequestChanges: () => ({ mutateAsync: vi.fn() }),
    useArchiveJobProfile: () => ({ mutateAsync: vi.fn() }),
    useCreateJobProfileRevision: () => ({ mutateAsync: vi.fn() }),
  };
});

function setProfile(p: JobProfile | null) {
  (globalThis as { __profile?: JobProfile | null }).__profile = p ?? undefined;
}

const PageRoute = () => (
  <Routes>
    <Route
      path="/app/projects/:projectId/positions/:positionId/job-profile"
      element={<JobProfileEditorPage />}
    />
  </Routes>
);

function renderPage() {
  return render(
    renderWithProviders(<PageRoute />, ['/app/projects/proj-1/positions/pos-1/job-profile']),
  );
}

describe('JobProfileEditorPage auto-save cancellation (F-306)', () => {
  beforeEach(() => {
    signIn('super-admin');
    updateMutateAsync.mockReset();
    updateMutateAsync.mockResolvedValue({});
  });
  afterEach(() => {
    vi.useRealTimers();
    signOut();
    setProfile(null);
  });

  it('does NOT fire auto-save PATCH after status flips DRAFT → UNDER_REVIEW', async () => {
    setProfile({ ...baseProfile, status: 'DRAFT' });
    const view = renderPage();

    // Render finishes with real timers — i18n + react-query are happy.
    const textarea = await screen.findByTestId('textarea-purpose-ru-RU');

    // Now switch to fake timers for the 30s interval check.
    vi.useFakeTimers({ shouldAdvanceTime: true, toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] });

    fireEvent.change(textarea, { target: { value: 'Updated purpose content' } });

    // Status flips out of DRAFT — cleanup MUST cancel the queued interval tick.
    act(() => {
      setProfile({ ...baseProfile, status: 'UNDER_REVIEW' as JobProfileStatus });
      view.rerender(
        renderWithProviders(<PageRoute />, ['/app/projects/proj-1/positions/pos-1/job-profile']),
      );
    });

    // Advance well past the 30s auto-save interval.
    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    // No PATCH was sent — auto-save was cancelled when readOnly flipped.
    expect(updateMutateAsync).not.toHaveBeenCalled();
  });

  it('does fire auto-save while status remains DRAFT', async () => {
    setProfile({ ...baseProfile, status: 'DRAFT' });
    renderPage();

    const textarea = await screen.findByTestId('textarea-purpose-ru-RU');

    vi.useFakeTimers({ shouldAdvanceTime: true, toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] });
    fireEvent.change(textarea, { target: { value: 'Another update' } });

    await act(async () => {
      vi.advanceTimersByTime(30_500);
    });

    await waitFor(() => expect(updateMutateAsync).toHaveBeenCalled());
  });
});
