import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
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

  it('marks METHODOLOGY_FACTORS_V1 as not yet supported and blocks selection (no committer on BE)', () => {
    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/imports/new" element={<ImportWizardPage />} />
        </Routes>,
        ['/app/projects/proj-acme-2026/imports/new'],
      ),
    );
    // Visible (so users know it's coming) but flagged + non-selectable.
    expect(
      screen.getByTestId('wizard-template-METHODOLOGY_FACTORS_V1-unsupported'),
    ).toBeInTheDocument();
    const select = screen.getByTestId(
      'wizard-template-METHODOLOGY_FACTORS_V1-select',
    ) as HTMLButtonElement;
    expect(select.disabled).toBe(true);

    // Committable templates stay selectable.
    const orgSelect = screen.getByTestId(
      'wizard-template-ORG_STRUCTURE_V1-select',
    ) as HTMLButtonElement;
    expect(orgSelect.disabled).toBe(false);
  });
});

