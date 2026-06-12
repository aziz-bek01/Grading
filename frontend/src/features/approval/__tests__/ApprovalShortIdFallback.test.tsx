import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ApprovalRequestCard } from '../components/ApprovalRequestCard';
import { ApprovalStepCard } from '../components/ApprovalStepCard';
import { ApprovalDecisionsList } from '../components/ApprovalDecisionsList';
import type {
  ApprovalRequestSummary,
  ApprovalStep,
  ApprovalDecision,
} from '../types';

const FULL_ENTITY_ID = '40000000-0000-0000-0000-0000000000a2';
const FULL_USER_ID = '50000000-0000-0000-0000-0000000000b3';

const SUMMARY_NO_LABEL: ApprovalRequestSummary = {
  id: 'appr-x',
  projectId: 'proj-1',
  entityType: 'EVALUATION',
  entityId: FULL_ENTITY_ID,
  entityLabel: undefined,
  status: 'PENDING',
  initiatedByUserId: FULL_USER_ID,
  initiatedByName: null,
  initiatedAt: '2026-05-15T08:30:00Z',
  currentStepOrder: 1,
  totalSteps: 1,
  notesI18n: undefined,
};

describe('Approval short-id fallback (FE-2 / FE-3)', () => {
  it('card shows a SHORT id (not a full UUID) when label + initiator name are absent', () => {
    render(renderWithProviders(<ApprovalRequestCard request={SUMMARY_NO_LABEL} />));
    // Title link degrades to the first UUID segment.
    expect(screen.getByText('40000000')).toBeInTheDocument();
    // The full 36-char UUID must never appear as visible text.
    expect(screen.queryByText(FULL_ENTITY_ID)).toBeNull();
    expect(screen.queryByText(FULL_USER_ID)).toBeNull();
    // The initiator short id must be rendered somewhere in the card.
    expect(screen.getByText(/50000000/)).toBeInTheDocument();
  });

  it('step card shows a SHORT id for an unresolved decided-by user', () => {
    const step: ApprovalStep = {
      id: 'step-1',
      approvalRequestId: 'appr-x',
      stepOrder: 1,
      approverUserId: null,
      approverName: null,
      requiredPermission: 'EVALUATION_APPROVE',
      status: 'APPROVED',
      decidedAt: '2026-05-16T09:00:00Z',
      decidedByUserId: FULL_USER_ID,
      decidedByName: null,
      notes: null,
      reason: null,
    };
    render(renderWithProviders(<ApprovalStepCard step={step} />));
    expect(screen.getByText(/50000000/)).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(FULL_USER_ID))).toBeNull();
  });

  it('decisions list shows a SHORT id for an unresolved decider', () => {
    const decisions: ApprovalDecision[] = [
      {
        id: 'd-1',
        approvalRequestId: 'appr-x',
        approvalStepId: 'step-1',
        decision: 'APPROVED',
        decidedByUserId: FULL_USER_ID,
        decidedByName: null,
        decidedAt: '2026-05-16T09:00:00Z',
        notes: null,
        reason: null,
      },
    ];
    render(renderWithProviders(<ApprovalDecisionsList decisions={decisions} />));
    expect(screen.getByText('50000000')).toBeInTheDocument();
    expect(screen.queryByText(FULL_USER_ID)).toBeNull();
  });
});
