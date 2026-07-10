import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { Routes, Route } from 'react-router-dom';
import { ImportWizardPage } from './ImportWizardPage';

describe('ImportWizardPage', () => {
  beforeEach(() => {
    signIn('super-admin');
  });
  afterEach(() => {
    signOut();
  });

  it('renders the 4-step stepper and step 1 (choose template)', () => {
    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/imports/new" element={<ImportWizardPage />} />
        </Routes>,
        ['/app/projects/proj-acme-2026/imports/new'],
      ),
    );
    expect(screen.getByTestId('wizard-stepper')).toBeInTheDocument();
    expect(screen.getByTestId('wizard-step-template')).toBeInTheDocument();
  });

  it('lists all 5 templates (super-admin has all imports permissions)', () => {
    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/imports/new" element={<ImportWizardPage />} />
        </Routes>,
        ['/app/projects/proj-acme-2026/imports/new'],
      ),
    );
    expect(screen.getByTestId('wizard-template-ORG_STRUCTURE_V1')).toBeInTheDocument();
    expect(screen.getByTestId('wizard-template-POSITION_CATALOG_V1')).toBeInTheDocument();
    expect(screen.getByTestId('wizard-template-JOB_PROFILE_V1')).toBeInTheDocument();
    expect(screen.getByTestId('wizard-template-METHODOLOGY_FACTORS_V1')).toBeInTheDocument();
    expect(screen.getByTestId('wizard-template-GRADE_BANDS_V1')).toBeInTheDocument();
  });

  it('disables Continue until a template is selected', () => {
    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/imports/new" element={<ImportWizardPage />} />
        </Routes>,
        ['/app/projects/proj-acme-2026/imports/new'],
      ),
    );
    const next = screen.getByTestId('wizard-next-1') as HTMLButtonElement;
    expect(next.disabled).toBe(true);
  });

  it('marks METHODOLOGY_FACTORS_V1 as selectable (BE now upserts methodology + factors + levels) and advances the wizard past template choice', async () => {
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/imports/new" element={<ImportWizardPage />} />
        </Routes>,
        ['/app/projects/proj-acme-2026/imports/new'],
      ),
    );
    // No "not yet supported" badge — the committer exists on the backend now.
    expect(
      screen.queryByTestId('wizard-template-METHODOLOGY_FACTORS_V1-unsupported'),
    ).not.toBeInTheDocument();

    const select = screen.getByTestId(
      'wizard-template-METHODOLOGY_FACTORS_V1-select',
    ) as HTMLButtonElement;
    expect(select.disabled).toBe(false);

    await user.click(select);
    const next = screen.getByTestId('wizard-next-1') as HTMLButtonElement;
    expect(next.disabled).toBe(false);

    await user.click(next);
    // Wizard advances to step 2 (upload) exactly like any other committable template.
    expect(screen.getByTestId('wizard-step-upload')).toBeInTheDocument();
  });

  it('no template is currently marked unsupported in the wizard', () => {
    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/imports/new" element={<ImportWizardPage />} />
        </Routes>,
        ['/app/projects/proj-acme-2026/imports/new'],
      ),
    );
    for (const code of [
      'ORG_STRUCTURE_V1',
      'POSITION_CATALOG_V1',
      'JOB_PROFILE_V1',
      'METHODOLOGY_FACTORS_V1',
      'GRADE_BANDS_V1',
    ]) {
      expect(screen.queryByTestId(`wizard-template-${code}-unsupported`)).not.toBeInTheDocument();
      const select = screen.getByTestId(`wizard-template-${code}-select`) as HTMLButtonElement;
      expect(select.disabled).toBe(false);
    }
  });
});

