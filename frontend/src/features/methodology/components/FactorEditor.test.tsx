import { describe, expect, it, vi, beforeAll, afterAll } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { FactorEditor } from './FactorEditor';
import type { Factor } from '../types';

const factor: Factor = {
  id: 'f-1',
  methodology_version_id: 'v1',
  code: 'KNOWLEDGE',
  name_i18n: { 'ru-RU': 'Знания', 'en-US': 'Knowledge' },
  weight: 15,
  max_points: 100,
  sort_order: 0,
  required: true,
  levels: [],
};

describe('FactorEditor', () => {
  beforeAll(() => signIn('super-admin'));
  afterAll(() => signOut());

  it('requires primary locale name and weight numeric', () => {
    const onSubmit = vi.fn();
    render(
      renderWithProviders(
        <FactorEditor
          open
          factor={null}
          scoringMode="WEIGHTED_POINTS"
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );

    // Fill in code only — name/ru-RU still empty → submit should NOT call onSubmit.
    fireEvent.change(screen.getByTestId('factor-code'), { target: { value: 'X' } });
    fireEvent.click(screen.getByText(/save|сохранить|saqlash|сақлаш/i));
    expect(onSubmit).not.toHaveBeenCalled();
    // Visible error.
    expect(screen.getByTestId('factor-editor-error')).toBeInTheDocument();
  });

  it('accepts numeric weight + maxPoints and bubbles patch', () => {
    const onSubmit = vi.fn();
    render(
      renderWithProviders(
        <FactorEditor
          open
          factor={factor}
          scoringMode="WEIGHTED_POINTS"
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );

    fireEvent.change(screen.getByTestId('factor-weight'), { target: { value: '20' } });
    fireEvent.change(screen.getByTestId('factor-max-points'), { target: { value: '120' } });
    fireEvent.click(screen.getByText(/save|сохранить|saqlash|сақлаш/i));

    expect(onSubmit).toHaveBeenCalled();
    const patch = onSubmit.mock.calls[0][0];
    expect(patch.weight).toBe(20);
    expect(patch.max_points).toBe(120);
  });

  it('renders nested FactorLevelEditor for existing factor', () => {
    render(
      renderWithProviders(
        <FactorEditor
          open
          factor={{ ...factor, levels: [{ id: 'l-1', factor_id: factor.id, code: 'A', level_order: 0, points: 10, scale_value: 0.5, label_i18n: { 'ru-RU': 'А' } }] }}
          scoringMode="WEIGHTED_POINTS"
          onClose={() => {}}
          onSubmit={() => {}}
        />,
      ),
    );
    expect(screen.getByTestId('factor-level-editor')).toBeInTheDocument();
    expect(screen.getByTestId('level-row-A')).toBeInTheDocument();
  });

  it('new-level draft bubbles description_i18n through onAdd', () => {
    const onAddLevel = vi.fn();
    render(
      renderWithProviders(
        <FactorEditor
          open
          factor={{ ...factor, levels: [] }}
          scoringMode="WEIGHTED_POINTS"
          onClose={() => {}}
          onSubmit={() => {}}
          onAddLevel={onAddLevel}
        />,
      ),
    );

    const draftForm = screen.getByTestId('level-new-form');
    // The draft form must render BOTH a label editor and a description editor.
    const draftLocaleInputs = within(draftForm).getAllByTestId('locale-input-ru-RU');
    expect(draftLocaleInputs.length).toBe(2); // label + description

    // code (text input is the first/only text input in the draft grid)
    fireEvent.change(within(draftForm).getByPlaceholderText('A'), { target: { value: 'B' } });
    // label ru-RU (first locale input) — required to enable the add button
    fireEvent.change(draftLocaleInputs[0], { target: { value: 'Label B' } });
    // description ru-RU (second locale input)
    fireEvent.change(draftLocaleInputs[1], { target: { value: 'Rubric B' } });

    fireEvent.click(within(draftForm).getByTestId('level-add'));

    expect(onAddLevel).toHaveBeenCalledTimes(1);
    const added = onAddLevel.mock.calls[0][0];
    expect(added.label_i18n['ru-RU']).toBe('Label B');
    expect(added.description_i18n['ru-RU']).toBe('Rubric B');
  });

  it('existing-level row bubbles description_i18n through onUpdate', () => {
    const onUpdateLevel = vi.fn();
    render(
      renderWithProviders(
        <FactorEditor
          open
          factor={{
            ...factor,
            levels: [
              {
                id: 'l-1',
                factor_id: factor.id,
                code: 'A',
                level_order: 0,
                points: 10,
                scale_value: 0.5,
                label_i18n: { 'ru-RU': 'А' },
              },
            ],
          }}
          scoringMode="WEIGHTED_POINTS"
          onClose={() => {}}
          onSubmit={() => {}}
          onUpdateLevel={onUpdateLevel}
        />,
      ),
    );

    const row = screen.getByTestId('level-row-A');
    // Existing row must now expose label + description editors (2 LocalizedNameTabs).
    const rowLocaleInputs = within(row).getAllByTestId('locale-input-ru-RU');
    expect(rowLocaleInputs.length).toBe(2); // label + description

    // Edit the description (second locale input within the row). The row
    // buffers locally and commits to onUpdate ON BLUR (focusout bubbles to the
    // wrapping element) — never per keystroke — so the controlled input bound
    // to a non-optimistic mutation never drops characters or spams PATCH/audit.
    fireEvent.change(rowLocaleInputs[1], { target: { value: 'Updated rubric' } });
    fireEvent.blur(rowLocaleInputs[1]);

    expect(onUpdateLevel).toHaveBeenCalled();
    const last = onUpdateLevel.mock.calls.at(-1)![0];
    expect(last.description_i18n['ru-RU']).toBe('Updated rubric');
  });
});
