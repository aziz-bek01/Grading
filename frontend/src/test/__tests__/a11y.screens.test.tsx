import { describe, it, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { expectNoA11yViolations } from '@/test/a11y';
import { httpClient } from '@/shared/api/httpClient';
import { LoginPage } from '@/pages/LoginPage';
import { AppShell } from '@/shared/components/layout/AppShell';
import { ExportCenterPage } from '@/features/export/pages/ExportCenterPage';
import { EvaluationMatrix } from '@/features/evaluation/components/EvaluationMatrix';
import type { Factor, MethodologyVersion } from '@/features/methodology/types';
import type { EvaluationScore } from '@/features/evaluation/types';

const PROJECT_ID = '11111111-1111-1111-1111-111111111111';

function makeFactor(code: string): Factor {
  return {
    id: `f-${code}`,
    methodology_version_id: 'v-1',
    code,
    name_i18n: { 'ru-RU': code, 'en-US': code },
    weight: 10,
    max_points: 100,
    sort_order: 0,
    required: true,
    levels: [
      { id: `f-${code}-l1`, factor_id: `f-${code}`, code: 'A', level_order: 0, points: 25, scale_value: 0.25, label_i18n: { 'ru-RU': 'A', 'en-US': 'A' } },
      { id: `f-${code}-l2`, factor_id: `f-${code}`, code: 'B', level_order: 1, points: 75, scale_value: 0.75, label_i18n: { 'ru-RU': 'B', 'en-US': 'B' } },
    ],
  };
}

const version: MethodologyVersion = {
  id: 'v-1',
  methodology_id: 'm-1',
  project_id: 'p-1',
  version_number: 1,
  status: 'APPROVED',
  scoring_mode: 'WEIGHTED_POINTS',
  target_total_points: 100,
  factors: [makeFactor('F1'), makeFactor('F2')],
  created_at: '2026-05-01T00:00:00Z',
  updated_at: '2026-05-01T00:00:00Z',
};

describe('a11y: key screens', () => {
  beforeEach(() => {
    signIn('super-admin');
  });
  afterEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  it('LoginPage is axe-clean (dev-auth variant) including landmarks', async () => {
    signOut();
    const { container } = render(renderWithProviders(<LoginPage />, ['/login']));
    await expectNoA11yViolations(container);
  });

  it('AppShell exposes banner / nav / main landmarks and is axe-clean', async () => {
    const { container } = render(
      renderWithProviders(
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<h1>Dashboard</h1>} />
          </Route>
        </Routes>,
        ['/'],
      ),
    );
    // banner (TopBar <header>), nav (Sidebar <aside aria-label>), main (#main-content).
    expect(screen.getByRole('navigation')).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    await expectNoA11yViolations(container, { region: true });
  });

  it('EvaluationMatrix (editable, comment open) is axe-clean', async () => {
    const scores: EvaluationScore[] = [
      {
        id: 's1',
        evaluation_id: 'e1',
        factor_id: 'f-F1',
        factor_level_id: 'f-F1-l2',
        comment_text: 'existing comment',
      } as EvaluationScore,
    ];
    const { container } = render(
      renderWithProviders(
        <main>
          <EvaluationMatrix
            version={version}
            scores={scores}
            status="DRAFT"
            onSelectLevel={() => {}}
            onCommentChange={() => {}}
          />
        </main>,
      ),
    );
    // The comment editor only mounts for a selected factor — it's present here.
    await expectNoA11yViolations(container);
  });

  it('ExportCenterPage (table of jobs) is axe-clean incl. landmarks', async () => {
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: {
        items: [
          {
            id: 'exp-1',
            project_id: PROJECT_ID,
            export_type: 'GRADE_SHEET',
            format: 'XLSX',
            status: 'GENERATED',
            requested_at: '2026-05-23T08:15:00Z',
            expires_at: '2026-06-06T08:15:12Z',
            file_size: 20480,
          },
        ],
        page: 0,
        size: 1,
        total_elements: 1,
        total_pages: 1,
      },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    const { container } = render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/exports" element={<ExportCenterPage />} />
        </Routes>,
        [`/app/projects/${PROJECT_ID}/exports`],
      ),
    );
    await waitFor(() => expect(screen.getByTestId('export-center-page')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText(/GRADE_SHEET|Grade|grade/i)).toBeInTheDocument());
    await expectNoA11yViolations(container, { region: true });
  });
});
