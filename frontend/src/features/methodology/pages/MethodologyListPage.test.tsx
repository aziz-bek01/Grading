import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { MethodologyListPage } from './MethodologyListPage';
import type { Methodology } from '../types';

const sample: Methodology[] = [
  {
    id: 'm-1',
    project_id: 'proj-1',
    code: 'M1',
    name_i18n: { 'ru-RU': 'CFO Финансы', 'en-US': 'CFO Finance' },
    methodology_type: 'CLASSIC_8_FACTOR',
    status: 'ACTIVE',
    latest_version_id: 'v-1',
    active_version_id: 'v-1',
    active_version_number: 1,
    active_version_status: 'APPROVED',
    created_at: '2026-02-01T10:00:00Z',
    updated_at: '2026-05-01T10:00:00Z',
  },
];

const saveAsTemplateSpy = vi.fn().mockResolvedValue({ id: 'tpl-new', code: 'TPL-X' });

vi.mock('../hooks/useMethodology', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useMethodology')>('../hooks/useMethodology');
  return {
    ...actual,
    useMethodologies: () => ({ data: { items: sample }, isLoading: false, error: null }),
    useCreateMethodologyFromTemplate: () => ({ mutateAsync: vi.fn().mockResolvedValue({ id: 'new', latest_version_id: 'nv-1' }) }),
    useSaveMethodologyAsTemplate: () => ({ mutateAsync: saveAsTemplateSpy }),
  };
});

function renderPage() {
  return render(
    renderWithProviders(
      <Routes>
        <Route path="/app/projects/:projectId/methodology" element={<MethodologyListPage />} />
      </Routes>,
      ['/app/projects/proj-1/methodology'],
    ),
  );
}

describe('MethodologyListPage', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => signOut());

  it('renders methodology rows with name + active version badge', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('CFO Финансы')).toBeInTheDocument());
    // Methodology type badge present.
    expect(screen.getByTestId('methodology-type-CLASSIC_8_FACTOR')).toBeInTheDocument();
  });

  it('"+ New methodology" gated by METHODOLOGY_CREATE — visible for super-admin', () => {
    renderPage();
    expect(screen.getByTestId('methodology-list-new')).toBeInTheDocument();
  });

  it('"+ New methodology" not rendered for viewer (no METHODOLOGY_CREATE)', () => {
    signOut();
    signIn('viewer');
    renderPage();
    expect(screen.queryByTestId('methodology-list-new')).toBeNull();
  });

  it('clicking "+ New methodology" opens the template picker', async () => {
    renderPage();
    fireEvent.click(screen.getByTestId('methodology-list-new'));
    await waitFor(() => expect(screen.getByTestId('template-option-CLASSIC_8_FACTOR')).toBeInTheDocument());
    expect(screen.getByTestId('template-option-EXTENDED_11_CRITERIA')).toBeInTheDocument();
    expect(screen.getByTestId('template-option-CUSTOM')).toBeInTheDocument();
  });

  it('Epic E — "Save as template" row action gated by METHODOLOGY_CREATE (super-admin sees it)', () => {
    renderPage();
    expect(screen.getByTestId('methodology-M1-save-template')).toBeInTheDocument();
  });

  it('Epic E — consultant (METHODOLOGY_EDIT, no CREATE) has no save-as-template action', () => {
    signOut();
    signIn('consultant');
    renderPage();
    // Edit (METHODOLOGY_EDIT) is visible, but save-as-template (CREATE) is not.
    expect(screen.getByTestId('methodology-M1-edit')).toBeInTheDocument();
    expect(screen.queryByTestId('methodology-M1-save-template')).toBeNull();
  });

  it('Epic E — save-as-template flow posts the code + name payload', async () => {
    renderPage();
    fireEvent.click(screen.getByTestId('methodology-M1-save-template'));
    await waitFor(() => expect(screen.getByTestId('save-template-code')).toBeInTheDocument());
    fireEvent.change(screen.getByTestId('save-template-code'), { target: { value: 'tpl-x' } });
    fireEvent.click(screen.getByText(/Сохранить шаблон|Save template/));
    await waitFor(() => expect(saveAsTemplateSpy).toHaveBeenCalledTimes(1));
    expect(saveAsTemplateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'TPL-X', name_i18n: expect.objectContaining({ 'ru-RU': 'CFO Финансы' }) }),
    );
  });
});
