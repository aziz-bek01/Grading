import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { JobProfileActionsBar } from './JobProfileActionsBar';
import type { JobProfile } from '../types';

function baseProfile(overrides: Partial<JobProfile>): JobProfile {
  return {
    id: 'jp-1',
    position_id: 'pos-1',
    project_id: 'proj-1',
    status: 'DRAFT',
    revision_number: 1,
    purpose_i18n: {},
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
    ...overrides,
  };
}

describe('JobProfileActionsBar', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => signOut());

  it('DRAFT shows Save + Submit + Archive actions', () => {
    render(renderWithProviders(<JobProfileActionsBar profile={baseProfile({ status: 'DRAFT' })} />));
    expect(screen.getByTestId('action-save')).toBeInTheDocument();
    expect(screen.getByTestId('action-submit')).toBeInTheDocument();
    expect(screen.getByTestId('action-archive')).toBeInTheDocument();
    expect(screen.queryByTestId('action-approve')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-create-revision')).not.toBeInTheDocument();
  });

  it('UNDER_REVIEW shows Approve + Request changes when user has approve permission', () => {
    render(renderWithProviders(<JobProfileActionsBar profile={baseProfile({ status: 'UNDER_REVIEW' })} />));
    expect(screen.getByTestId('action-approve')).toBeInTheDocument();
    expect(screen.getByTestId('action-request-changes')).toBeInTheDocument();
  });

  it('APPROVED shows Create new revision + locked notice', () => {
    render(renderWithProviders(<JobProfileActionsBar profile={baseProfile({ status: 'APPROVED' })} />));
    expect(screen.getByTestId('approved-locked-notice')).toBeInTheDocument();
    expect(screen.getByTestId('action-create-revision')).toBeInTheDocument();
    expect(screen.queryByTestId('action-save')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-submit')).not.toBeInTheDocument();
  });

  it('ARCHIVED shows banner and no edit actions', () => {
    render(renderWithProviders(<JobProfileActionsBar profile={baseProfile({ status: 'ARCHIVED' })} />));
    expect(screen.getByTestId('archived-banner')).toBeInTheDocument();
    expect(screen.queryByTestId('action-save')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-approve')).not.toBeInTheDocument();
  });

  it('UNDER_REVIEW hides Approve button when user lacks JOB_PROFILE_APPROVE', () => {
    signOut();
    signIn('viewer');
    render(renderWithProviders(<JobProfileActionsBar profile={baseProfile({ status: 'UNDER_REVIEW' })} />));
    expect(screen.queryByTestId('action-approve')).not.toBeInTheDocument();
  });
});
