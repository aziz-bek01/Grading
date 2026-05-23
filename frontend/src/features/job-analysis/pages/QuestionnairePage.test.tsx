import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { QuestionnairePage } from './QuestionnairePage';
import type { JobAnalysisQuestionnaire } from '../types';

const mockMutateAnswer = vi.fn();
const mockSubmit = vi.fn();

const baseQuestionnaire: JobAnalysisQuestionnaire = {
  id: 'ques-1',
  position_id: 'pos-1',
  project_id: 'proj-1',
  template_code: 'STANDARD_V1',
  name: { 'en-US': 'Standard Analysis', 'ru-RU': 'Стандарт' },
  status: 'DRAFT',
  questions: [
    {
      id: 'q1',
      code: 'Q1',
      question_type: 'TEXT',
      prompt: { 'en-US': 'Required Q1' },
      required: true,
      sort_order: 1,
    },
    {
      id: 'q2',
      code: 'Q2',
      question_type: 'TEXT',
      prompt: { 'en-US': 'Required Q2' },
      required: true,
      sort_order: 2,
    },
    {
      id: 'q3',
      code: 'Q3',
      question_type: 'TEXT',
      prompt: { 'en-US': 'Optional Q3' },
      required: false,
      sort_order: 3,
    },
  ],
  answers: [],
  created_at: '2026-05-01T00:00:00Z',
  updated_at: '2026-05-01T00:00:00Z',
};

vi.mock('../hooks/useQuestionnaire', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useQuestionnaire')>('../hooks/useQuestionnaire');
  return {
    ...actual,
    useQuestionnaire: () => ({
      data: (globalThis as { __ques?: JobAnalysisQuestionnaire }).__ques,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    }),
    useUpdateAnswer: () => ({ mutateAsync: mockMutateAnswer }),
    useSubmitQuestionnaire: () => ({ mutateAsync: mockSubmit }),
    useArchiveQuestionnaire: () => ({ mutateAsync: vi.fn() }),
  };
});

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

describe('QuestionnairePage', () => {
  beforeEach(() => {
    signIn('super-admin');
    mockMutateAnswer.mockClear();
    mockSubmit.mockClear();
  });
  afterEach(() => {
    signOut();
    delete (globalThis as { __ques?: JobAnalysisQuestionnaire }).__ques;
  });

  it('shows progress bar reflecting answered required count', async () => {
    (globalThis as { __ques?: JobAnalysisQuestionnaire }).__ques = {
      ...baseQuestionnaire,
      status: 'IN_PROGRESS',
      answers: [{ question_id: 'q1', value: 'A' }],
    };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('questionnaire-progress')).toBeInTheDocument());
    // 1 of 2 required (q1 answered; q2 missing)
    expect(screen.getByTestId('progress-percent').textContent).toBe('50%');
  });

  it('Submit button is disabled until all required are answered', async () => {
    (globalThis as { __ques?: JobAnalysisQuestionnaire }).__ques = {
      ...baseQuestionnaire,
      status: 'DRAFT',
      answers: [],
    };
    renderPage();
    const submitBtn = await screen.findByTestId('questionnaire-submit');
    expect(submitBtn).toBeDisabled();
  });

  it('Submit button enables once all required are answered', async () => {
    (globalThis as { __ques?: JobAnalysisQuestionnaire }).__ques = {
      ...baseQuestionnaire,
      status: 'IN_PROGRESS',
      answers: [
        { question_id: 'q1', value: 'A' },
        { question_id: 'q2', value: 'B' },
      ],
    };
    renderPage();
    const submitBtn = await screen.findByTestId('questionnaire-submit');
    expect(submitBtn).not.toBeDisabled();
  });

  it('debounced auto-save fires updateAnswer on input change', async () => {
    vi.useFakeTimers();
    (globalThis as { __ques?: JobAnalysisQuestionnaire }).__ques = { ...baseQuestionnaire };
    renderPage();
    const input = screen.getByTestId('q-input-q1');
    fireEvent.change(input, { target: { value: 'Answer A' } });
    // Advance debounce.
    vi.advanceTimersByTime(600);
    expect(mockMutateAnswer).toHaveBeenCalledWith({ question_id: 'q1', value: 'Answer A' });
    vi.useRealTimers();
  });
});
