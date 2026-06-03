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
      name: Record<string, string>;
      min_score: number;
      max_score: number;
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
    expect(payload.name['ru-RU']).toBe('Грейд 7');
    expect(payload.min_score).toBe(600);
    expect(payload.max_score).toBe(700);
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
});
