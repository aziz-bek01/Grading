import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signOut } from '@/test/testUtils';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS, type PermissionCode } from '@/shared/types/permissions';
import type { CurrentUser } from '@/shared/auth/authTypes';
import { QuestionnairePage } from './QuestionnairePage';
import type { JobAnalysisQuestionnaire } from '../types';

/**
 * Negative permission test for D-306/D-307: a user with `JOB_PROFILE_EDIT`
 * but NOT `JOB_ANALYSIS_EDIT` must NOT see the questionnaire Archive button.
 *
 * Before the fix, QuestionnairePage gated the archive button on
 * `JOB_PROFILE_EDIT` — a BOLA-class permission drift. After the fix the
 * gate is `JOB_ANALYSIS_EDIT`.
 */

const baseQuestionnaire: JobAnalysisQuestionnaire = {
  id: 'ques-1',
  position_id: 'pos-1',
  project_id: 'proj-1',
  template_code: 'STANDARD_V1',
  name: { 'en-US': 'Standard Analysis', 'ru-RU': 'Стандарт' },
  status: 'DRAFT',
  questions: [],
  answers: [],
  created_at: '2026-05-01T00:00:00Z',
  updated_at: '2026-05-01T00:00:00Z',
};

vi.mock('../hooks/useQuestionnaire', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useQuestionnaire')>(
    '../hooks/useQuestionnaire',
  );
  return {
    ...actual,
    useQuestionnaire: () => ({
      data: (globalThis as { __ques?: JobAnalysisQuestionnaire }).__ques,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    }),
    useUpdateAnswer: () => ({ mutateAsync: vi.fn() }),
    useSubmitQuestionnaire: () => ({ mutateAsync: vi.fn() }),
    useArchiveQuestionnaire: () => ({ mutateAsync: vi.fn() }),
  };
});

function signInWith(permissions: PermissionCode[]): void {
  const user: CurrentUser = {
    id: '00000000-0000-0000-0000-000000000099',
    email: 'test@hrlab.local',
    name: 'Test User',
    locale: 'ru-RU',
    roles: ['CLIENT_HR_SPECIALIST'],
    permissions,
    salary_data_permission: false,
    tenants: [
      { id: 'tenant-acme', slug: 'acme', brand_name: 'ACME', fingerprint_hue: 215 },
    ],
  };
  useAuthStore
    .getState()
    .setSession(user, { value: 'test', expiresAt: Date.now() + 60_000 });
}

function renderPage() {
  return render(
    renderWithProviders(
      <Routes>
        <Route
          path="/app/projects/:projectId/positions/:positionId/questionnaire/:questionnaireId"
          element={<QuestionnairePage />}
        />
      </Routes>,
      ['/app/projects/proj-1/positions/pos-1/questionnaire/ques-1'],
    ),
  );
}

describe('QuestionnairePage permission gate (D-306/D-307)', () => {
  beforeEach(() => {
    (globalThis as { __ques?: JobAnalysisQuestionnaire }).__ques = {
      ...baseQuestionnaire,
    };
  });
  afterEach(() => {
    signOut();
    delete (globalThis as { __ques?: JobAnalysisQuestionnaire }).__ques;
  });

  it('hides Archive button for user with JOB_PROFILE_EDIT but no JOB_ANALYSIS_EDIT', async () => {
    signInWith([PERMISSIONS.JOB_PROFILE_READ, PERMISSIONS.JOB_PROFILE_EDIT, PERMISSIONS.JOB_ANALYSIS_READ]);
    renderPage();
    // Submit button (no permission gate) appears — the page rendered.
    await waitFor(() => expect(screen.getByTestId('questionnaire-submit')).toBeInTheDocument());
    // Archive button must NOT be visible.
    expect(screen.queryByTestId('questionnaire-archive')).not.toBeInTheDocument();
  });

  it('shows Archive button for user with JOB_ANALYSIS_EDIT', async () => {
    signInWith([PERMISSIONS.JOB_ANALYSIS_READ, PERMISSIONS.JOB_ANALYSIS_EDIT]);
    renderPage();
    await waitFor(() => expect(screen.getByTestId('questionnaire-archive')).toBeInTheDocument());
  });

  it('hides Archive button for viewer (read-only)', async () => {
    signInWith([PERMISSIONS.JOB_ANALYSIS_READ]);
    renderPage();
    await waitFor(() => expect(screen.getByTestId('questionnaire-submit')).toBeInTheDocument());
    expect(screen.queryByTestId('questionnaire-archive')).not.toBeInTheDocument();
  });
});
