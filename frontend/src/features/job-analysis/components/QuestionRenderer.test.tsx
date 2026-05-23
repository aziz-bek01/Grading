import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { QuestionRenderer } from './QuestionRenderer';
import type { JobAnalysisQuestion } from '../types';

function q(partial: Partial<JobAnalysisQuestion> & { id: string }): JobAnalysisQuestion {
  return {
    code: partial.id,
    question_type: 'TEXT',
    prompt: { 'en-US': 'Prompt' },
    required: false,
    sort_order: 1,
    ...partial,
  } as JobAnalysisQuestion;
}

describe('QuestionRenderer', () => {
  it('TEXT renders an input', () => {
    const onChange = vi.fn();
    render(renderWithProviders(<QuestionRenderer question={q({ id: 't1', question_type: 'TEXT' })} value={null} onChange={onChange} />));
    const input = screen.getByTestId('q-input-t1');
    fireEvent.change(input, { target: { value: 'Hi' } });
    expect(onChange).toHaveBeenCalledWith('Hi');
  });

  it('LONG_TEXT renders a textarea', () => {
    render(renderWithProviders(<QuestionRenderer question={q({ id: 't2', question_type: 'LONG_TEXT' })} value={null} onChange={() => {}} />));
    expect(screen.getByTestId('q-textarea-t2')).toBeInTheDocument();
  });

  it('SINGLE_CHOICE renders radios; selecting one calls onChange', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <QuestionRenderer
          question={q({
            id: 't3',
            question_type: 'SINGLE_CHOICE',
            choices: [
              { code: 'A', label: { 'en-US': 'A' } },
              { code: 'B', label: { 'en-US': 'B' } },
            ],
          })}
          value={null}
          onChange={onChange}
        />,
      ),
    );
    fireEvent.click(screen.getByTestId('q-radio-t3-B'));
    expect(onChange).toHaveBeenCalledWith('B');
  });

  it('MULTI_CHOICE toggles selections', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <QuestionRenderer
          question={q({
            id: 't4',
            question_type: 'MULTI_CHOICE',
            choices: [
              { code: 'X', label: { 'en-US': 'X' } },
              { code: 'Y', label: { 'en-US': 'Y' } },
            ],
          })}
          value={['X']}
          onChange={onChange}
        />,
      ),
    );
    fireEvent.click(screen.getByTestId('q-check-t4-Y'));
    expect(onChange).toHaveBeenCalledWith(['X', 'Y']);
  });

  it('RATING_SCALE renders stars', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <QuestionRenderer
          question={q({ id: 't5', question_type: 'RATING_SCALE', scale_min: 1, scale_max: 5 })}
          value={2}
          onChange={onChange}
        />,
      ),
    );
    expect(screen.getByTestId('q-rating-t5-1')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('q-rating-t5-4'));
    expect(onChange).toHaveBeenCalledWith(4);
  });

  it('NUMBER renders numeric input', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <QuestionRenderer question={q({ id: 't6', question_type: 'NUMBER' })} value={null} onChange={onChange} />,
      ),
    );
    const input = screen.getByTestId('q-number-t6');
    fireEvent.change(input, { target: { value: '42' } });
    expect(onChange).toHaveBeenCalledWith(42);
  });
});
