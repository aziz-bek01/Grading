import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { ApprovalStatusBadge } from '../components/ApprovalStatusBadge';

describe('<ApprovalStatusBadge />', () => {
  it.each([
    ['PENDING', 'Ожидает'],
    ['APPROVED', 'Утверждено'],
    ['REJECTED', 'Отклонено'],
    ['CHANGES_REQUESTED', 'Требует доработки'],
    ['CANCELLED', 'Отменено'],
  ] as const)('renders %s with localized label', (status, label) => {
    render(renderWithProviders(<ApprovalStatusBadge status={status} />));
    expect(screen.getByLabelText(label)).toBeInTheDocument();
  });
});
