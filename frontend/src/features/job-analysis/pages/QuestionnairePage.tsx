import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Send, Archive } from 'lucide-react';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { PERMISSIONS } from '@/shared/types/permissions';
import { pickLocalized } from '@/shared/lib/localized';
import {
  useArchiveQuestionnaire,
  useQuestionnaire,
  useSubmitQuestionnaire,
  useUpdateAnswer,
} from '../hooks/useQuestionnaire';
import { QuestionRenderer } from '../components/QuestionRenderer';
import { QuestionnaireProgressBar } from '../components/QuestionnaireProgressBar';
import { QuestionnaireSubmitDialog } from '../components/QuestionnaireSubmitDialog';
import { isAnswerComplete } from '../schemas/answerSchemas';
import type {
  JobAnalysisAnswerValue,
  QuestionnaireStatus,
} from '../types';

const STATUS_TONE: Record<QuestionnaireStatus, StatusTone> = {
  DRAFT: 'draft',
  IN_PROGRESS: 'in-review',
  COMPLETED: 'approved',
  ARCHIVED: 'archived',
};

const STATUS_LABEL: Record<QuestionnaireStatus, string> = {
  DRAFT: 'status.draft',
  IN_PROGRESS: 'status.in_progress',
  COMPLETED: 'status.complete',
  ARCHIVED: 'status.archived',
};

const AUTO_SAVE_DEBOUNCE_MS = 500;

export function QuestionnairePage() {
  const { t, i18n } = useTranslation();
  const { questionnaireId = '', positionId = '' } = useParams<{
    questionnaireId: string;
    positionId: string;
  }>();

  const query = useQuestionnaire(questionnaireId);
  const updateAnswerMut = useUpdateAnswer(questionnaireId);
  const submitMut = useSubmitQuestionnaire(questionnaireId, positionId);
  const archiveMut = useArchiveQuestionnaire(questionnaireId, positionId);

  const [pending, setPending] = useState<Record<string, JobAnalysisAnswerValue>>({});
  const debounceRef = useRef<Record<string, number>>({});

  const data = query.data;
  const readOnly = data?.status === 'COMPLETED' || data?.status === 'ARCHIVED';

  const answersByQuestion = useMemo(() => {
    const map: Record<string, JobAnalysisAnswerValue> = {};
    data?.answers.forEach((a) => {
      map[a.question_id] = a.value;
    });
    Object.entries(pending).forEach(([id, v]) => {
      map[id] = v;
    });
    return map;
  }, [data?.answers, pending]);

  const requiredQuestions = useMemo(
    () => data?.questions.filter((q) => q.required) ?? [],
    [data?.questions],
  );
  const answeredRequiredCount = useMemo(() => {
    return requiredQuestions.filter((q) => isAnswerComplete(q.question_type, answersByQuestion[q.id]))
      .length;
  }, [requiredQuestions, answersByQuestion]);

  const allRequiredAnswered = answeredRequiredCount === requiredQuestions.length;

  const [submitOpen, setSubmitOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);

  const handleChange = useCallback(
    (questionId: string, value: JobAnalysisAnswerValue) => {
      if (readOnly) return;
      setPending((p) => ({ ...p, [questionId]: value }));
      // Debounced auto-save per question.
      const existing = debounceRef.current[questionId];
      if (existing) window.clearTimeout(existing);
      debounceRef.current[questionId] = window.setTimeout(() => {
        void updateAnswerMut.mutateAsync({ question_id: questionId, value });
      }, AUTO_SAVE_DEBOUNCE_MS);
    },
    [readOnly, updateAnswerMut],
  );

  useEffect(() => {
    return () => {
      Object.values(debounceRef.current).forEach((id) => window.clearTimeout(id));
    };
  }, []);

  if (query.isLoading) return <LoadingState />;
  if (query.error || !data) return <ErrorState onRetry={() => query.refetch()} />;

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('positions.tab_job_profile') },
          { label: pickLocalized(data.name, i18n.language) },
        ]}
      />

      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h1 className="text-2xl text-text-primary">{pickLocalized(data.name, i18n.language)}</h1>
            <StatusBadge tone={STATUS_TONE[data.status]} label={t(STATUS_LABEL[data.status])} />
          </div>
          <p className="text-sm text-text-secondary">{t('jobAnalysis.page_subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <PermissionGate permission={PERMISSIONS.JOB_ANALYSIS_EDIT}>
            <Button
              variant="ghost"
              onClick={() => setArchiveOpen(true)}
              leadingIcon={<Archive size={16} />}
              disabled={data.status === 'ARCHIVED'}
              data-testid="questionnaire-archive"
            >
              {t('common.archive')}
            </Button>
          </PermissionGate>
          <Button
            variant="primary"
            disabled={!allRequiredAnswered || readOnly}
            onClick={() => setSubmitOpen(true)}
            leadingIcon={<Send size={16} />}
            data-testid="questionnaire-submit"
          >
            {t('common.submit')}
          </Button>
        </div>
      </header>

      <Card compact>
        <QuestionnaireProgressBar
          answered={answeredRequiredCount}
          required={requiredQuestions.length}
        />
      </Card>

      <Card>
        <ol className="space-y-6" data-testid="question-list">
          {data.questions
            .slice()
            .sort((a, b) => a.sort_order - b.sort_order)
            .map((q, idx) => (
              <li key={q.id} className="space-y-2">
                <div className="text-xs text-text-muted">
                  {t('jobAnalysis.question_number', { number: idx + 1 })}
                </div>
                <QuestionRenderer
                  question={q}
                  value={answersByQuestion[q.id] ?? null}
                  onChange={(v) => handleChange(q.id, v)}
                  readOnly={readOnly}
                />
              </li>
            ))}
        </ol>
      </Card>

      <QuestionnaireSubmitDialog
        open={submitOpen}
        onCancel={() => setSubmitOpen(false)}
        onConfirm={async () => {
          setSubmitOpen(false);
          await submitMut.mutateAsync();
        }}
      />

      <ReasonRequiredDialog
        open={archiveOpen}
        title={t('jobAnalysis.archive_title')}
        body={t('jobAnalysis.archive_body')}
        onCancel={() => setArchiveOpen(false)}
        onConfirm={async (reason) => {
          setArchiveOpen(false);
          await archiveMut.mutateAsync(reason);
        }}
      />
    </div>
  );
}
