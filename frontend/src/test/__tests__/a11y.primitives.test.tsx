import { describe, it, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { expectNoA11yViolations } from '@/test/a11y';
import { Button } from '@/shared/components/ui/Button';
import { Card } from '@/shared/components/ui/Card';
import { StatusBadge } from '@/shared/components/status/StatusBadge';
import { LockedBadge } from '@/shared/components/status/LockedBadge';
import { SalaryValue } from '@/shared/components/salary/SalaryValue';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { Drawer } from '@/shared/components/layout/Drawer';
import { LanguageSwitcher } from '@/shared/components/layout/LanguageSwitcher';
import { DataTable } from '@/shared/components/data-table/DataTable';
import { LockEvaluationDialog } from '@/features/evaluation/components/LockEvaluationDialog';
import { AIRecommendationPanel } from '@/features/job-profiles/components/AIRecommendationPanel';
import { Plus } from 'lucide-react';

/**
 * Axe-clean assertions for the reusable primitives the whole app is built on.
 * Fixing a violation here propagates everywhere (the batch goal).
 */
describe('a11y: core primitives', () => {
  beforeEach(() => {
    signIn('super-admin');
  });

  it('Button (text + icon-only) has accessible names', async () => {
    const { container } = render(
      renderWithProviders(
        <div>
          <Button>Save</Button>
          {/* icon-only must carry an accessible name */}
          <Button aria-label="Add row" leadingIcon={<Plus size={16} aria-hidden />} />
        </div>,
      ),
    );
    await expectNoA11yViolations(container);
  });

  it('Card renders accessible heading structure', async () => {
    const { container } = render(
      renderWithProviders(
        <Card title="Overview" subtitle="Project summary">
          <p>Body</p>
        </Card>,
      ),
    );
    await expectNoA11yViolations(container);
  });

  it('StatusBadge tones are axe-clean', async () => {
    const { container } = render(
      renderWithProviders(
        <div>
          <StatusBadge tone="approved" label="Approved" />
          <StatusBadge tone="locked" label="Locked" />
          <StatusBadge tone="ai-suggestion" label="AI suggestion" />
          <StatusBadge tone="needs-attention" label="Needs attention" />
          <LockedBadge variant="soon" />
        </div>,
      ),
    );
    await expectNoA11yViolations(container);
  });

  it('SalaryValue (masked / permission-required) is axe-clean', async () => {
    signOut();
    signIn('viewer');
    const { container } = render(
      renderWithProviders(
        <div>
          <SalaryValue value={5_000_000} />
          <SalaryValue value={5_000_000} forceMasked />
        </div>,
      ),
    );
    await expectNoA11yViolations(container);
  });

  it('ConfirmDialog (with required reason) is axe-clean', async () => {
    const { container } = render(
      renderWithProviders(
        <ConfirmDialog
          open
          title="Delete draft"
          body="This cannot be undone."
          requireReason
          onConfirm={() => {}}
          onCancel={() => {}}
        />,
      ),
    );
    await expectNoA11yViolations(container);
  });

  it('Drawer (icon-only close) is axe-clean', async () => {
    const { container } = render(
      renderWithProviders(
        <Drawer open title="Edit position" onClose={() => {}}>
          <p>Drawer body</p>
        </Drawer>,
      ),
    );
    await expectNoA11yViolations(container);
  });

  it('LockEvaluationDialog (Batch 5) is axe-clean', async () => {
    const { container } = render(
      renderWithProviders(
        <LockEvaluationDialog
          open
          summary={{ positionName: 'Chief Accountant', scoredFactors: 8, totalFactors: 8, displayedTotal: 612 }}
          onConfirm={() => {}}
          onCancel={() => {}}
        />,
      ),
    );
    await expectNoA11yViolations(container);
  });

  it('LanguageSwitcher (open listbox) is axe-clean', async () => {
    const { container } = render(renderWithProviders(<LanguageSwitcher />));
    await expectNoA11yViolations(container);
  });

  it('AIRecommendationPanel (advisory AI state) is axe-clean', async () => {
    const { container } = render(
      renderWithProviders(
        <main>
          <AIRecommendationPanel />
        </main>,
      ),
    );
    await expectNoA11yViolations(container);
  });

  it('DataTable (search + sortable headers) is axe-clean', async () => {
    type Row = { id: string; name: string; score: number };
    const rows: Row[] = [
      { id: '1', name: 'Alpha', score: 10 },
      { id: '2', name: 'Beta', score: 20 },
    ];
    const { container } = render(
      renderWithProviders(
        <DataTable<Row>
          caption="Positions"
          columns={[
            { key: 'name', header: 'Name', render: (r) => r.name, sortable: true, sortAccessor: (r) => r.name },
            { key: 'score', header: 'Score', render: (r) => r.score },
          ]}
          rows={rows}
          rowKey={(r) => r.id}
          searchPredicate={(r, q) => r.name.toLowerCase().includes(q)}
        />,
      ),
    );
    await expectNoA11yViolations(container);
  });
});
