import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { GradeRowEditor } from '../components/GradeRowEditor';

describe('GradeRowEditor', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('renders 4 locale tabs and accepts a primary-locale name', async () => {
    interface GradeRowInput {
      grade_number: number;
      name_i18n: Record<string, string>;
      min_score: number;
      max_score: number;
      clear_band: boolean;
    }
    const onSubmit = vi.fn(async (_input: GradeRowInput) => {});
    render(
      renderWithProviders(
        <GradeRowEditor
          open
          grade={null}
          readOnly={false}
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );
    expect(screen.getByTestId('grade-row-editor-tab-ru-RU')).toBeInTheDocument();
    expect(screen.getByTestId('grade-row-editor-tab-uz-Cyrl-UZ')).toBeInTheDocument();
    expect(screen.getByTestId('grade-row-editor-tab-uz-Latn-UZ')).toBeInTheDocument();
    expect(screen.getByTestId('grade-row-editor-tab-en-US')).toBeInTheDocument();

    fireEvent.change(screen.getByTestId('grade-row-editor-name-ru-RU'), {
      target: { value: 'Грейд 7' },
    });
    fireEvent.change(screen.getByTestId('grade-row-editor-min-score'), {
      target: { value: '600' },
    });
    fireEvent.change(screen.getByTestId('grade-row-editor-max-score'), {
      target: { value: '700' },
    });
    fireEvent.submit(screen.getByTestId('grade-row-editor').closest('form')!);
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    const payload = onSubmit.mock.calls[0]?.[0] as GradeRowInput;
    expect(payload.name_i18n['ru-RU']).toBe('Грейд 7');
    expect(payload.min_score).toBe(600);
    expect(payload.max_score).toBe(700);
    expect(payload.clear_band).toBe(false);
  });

  it('shows min>max error and does not submit', async () => {
    const onSubmit = vi.fn(async () => {});
    render(
      renderWithProviders(
        <GradeRowEditor
          open
          grade={null}
          readOnly={false}
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );
    fireEvent.change(screen.getByTestId('grade-row-editor-name-ru-RU'), {
      target: { value: 'Грейд 7' },
    });
    fireEvent.change(screen.getByTestId('grade-row-editor-min-score'), {
      target: { value: '900' },
    });
    fireEvent.change(screen.getByTestId('grade-row-editor-max-score'), {
      target: { value: '800' },
    });
    expect(
      screen.getByTestId('grade-row-editor-error-min-gt-max'),
    ).toBeInTheDocument();
    fireEvent.submit(screen.getByTestId('grade-row-editor').closest('form')!);
    // submit handler set error state — onSubmit is NOT called when invalid
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('emits clear_band=true when both score fields are cleared (FE-8)', async () => {
    interface GradeRowInput {
      clear_band: boolean;
      name_i18n: Record<string, string>;
    }
    const onSubmit = vi.fn(async (_input: GradeRowInput) => {});
    render(
      renderWithProviders(
        <GradeRowEditor
          open
          grade={{
            id: 'g1',
            grade_structure_id: 's',
            grade_number: 1,
            name_i18n: { 'ru-RU': 'Грейд 1' },
            sort_order: 0,
            band: { id: 'b1', grade_id: 'g1', min_score: 0, max_score: 50 },
          }}
          readOnly={false}
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );
    // Click the explicit "clear band" control — both fields empty out.
    fireEvent.click(screen.getByTestId('grade-row-editor-clear-band'));
    expect(screen.getByTestId('grade-row-editor-band-cleared')).toBeInTheDocument();
    fireEvent.submit(screen.getByTestId('grade-row-editor').closest('form')!);
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    const payload = onSubmit.mock.calls[0]?.[0] as GradeRowInput;
    expect(payload.clear_band).toBe(true);
    expect(payload.name_i18n['ru-RU']).toBe('Грейд 1');
  });
});
