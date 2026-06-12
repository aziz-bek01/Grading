import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { OpenPanelDialog } from './OpenPanelDialog';
import type { Methodology } from '@/features/methodology/types';
import type { Position } from '@/features/positions/types/positionTypes';

// Mock the people picker's data source so the dialog has >= 3 selectable users
// independent of the shared user fixtures (FE-2 / AC-1 min-3 disable rule).
vi.mock('@/features/users-access/hooks/useUsers', () => ({
  useUsers: () => ({
    data: {
      items: [
        { id: 'u1', full_name: 'Evaluator One', status: 'ACTIVE' },
        { id: 'u2', full_name: 'Evaluator Two', status: 'ACTIVE' },
        { id: 'u3', full_name: 'Evaluator Three', status: 'ACTIVE' },
        { id: 'u4', full_name: 'Evaluator Four', status: 'ACTIVE' },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

const positions: Position[] = [
  {
    id: 'pos-1',
    project_id: 'proj-acme-2026',
    department_id: 'dep-1',
    code: 'P-1',
    title_i18n: { 'ru-RU': 'Должность 1', 'en-US': 'Position 1' },
    status: 'DRAFT',
  } as Position,
];

const methodologies: Methodology[] = [
  {
    id: 'meth-1',
    project_id: 'proj-acme-2026',
    code: 'M1',
    name_i18n: { 'ru-RU': 'Методология 1', 'en-US': 'Methodology 1' },
    methodology_type: 'CLASSIC_8_FACTOR',
    status: 'ACTIVE',
    active_version_id: 'mver-1',
    active_version_number: 1,
  } as Methodology,
];

function setup(onConfirm = vi.fn().mockResolvedValue({ ok: true, failed: [] })) {
  render(
    renderWithProviders(
      <OpenPanelDialog
        open
        positions={positions}
        methodologies={methodologies}
        departmentNameOf={() => 'HR'}
        onConfirm={onConfirm}
        onClose={vi.fn()}
      />,
    ),
  );
  return { onConfirm };
}

describe('<OpenPanelDialog /> — min-3 mandatory-roles rule (FE-2)', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  it('renders the three pre-labelled mandatory role rows', () => {
    setup();
    expect(screen.getByTestId('panel-role-chip-HR_DIRECTOR')).toBeInTheDocument();
    expect(screen.getByTestId('panel-role-chip-DEPARTMENT_DIRECTOR')).toBeInTheDocument();
    expect(screen.getByTestId('panel-role-chip-EXTERNAL_EXPERT')).toBeInTheDocument();
  });

  it('keeps Confirm DISABLED until position + version + 3 mandatory roles are filled', () => {
    setup();
    const confirm = screen.getByTestId('open-panel-confirm') as HTMLButtonElement;
    // Initially disabled (no position, no evaluators).
    expect(confirm.disabled).toBe(true);

    // Pick position + version.
    fireEvent.change(screen.getByTestId('open-panel-position-select'), {
      target: { value: 'pos-1' },
    });
    fireEvent.change(screen.getByTestId('open-panel-version-select'), {
      target: { value: 'mver-1' },
    });
    // Still disabled — no evaluators yet.
    expect(confirm.disabled).toBe(true);

    // Fill only 2 of the 3 mandatory roles → still disabled.
    fireEvent.change(screen.getByTestId('open-panel-picker-0'), {
      target: { value: 'u1' },
    });
    fireEvent.change(screen.getByTestId('open-panel-picker-1'), {
      target: { value: 'u2' },
    });
    expect(confirm.disabled).toBe(true);

    // Fill the third mandatory role → now enabled.
    fireEvent.change(screen.getByTestId('open-panel-picker-2'), {
      target: { value: 'u3' },
    });
    expect(confirm.disabled).toBe(false);
  });

  it('"+ Add evaluator" appends an ADDITIONAL row', () => {
    setup();
    fireEvent.click(screen.getByTestId('open-panel-add-evaluator'));
    // The new row carries an ADDITIONAL role chip.
    expect(screen.getByTestId('open-panel-row-3')).toBeInTheDocument();
    expect(screen.getByTestId('panel-role-chip-ADDITIONAL')).toBeInTheDocument();
  });

  it('confirms with the chosen position, version and roster', async () => {
    const onConfirm = vi.fn().mockResolvedValue({ ok: true, failed: [] });
    setup(onConfirm);
    fireEvent.change(screen.getByTestId('open-panel-position-select'), {
      target: { value: 'pos-1' },
    });
    fireEvent.change(screen.getByTestId('open-panel-version-select'), {
      target: { value: 'mver-1' },
    });
    fireEvent.change(screen.getByTestId('open-panel-picker-0'), { target: { value: 'u1' } });
    fireEvent.change(screen.getByTestId('open-panel-picker-1'), { target: { value: 'u2' } });
    fireEvent.change(screen.getByTestId('open-panel-picker-2'), { target: { value: 'u3' } });
    fireEvent.click(screen.getByTestId('open-panel-confirm'));

    expect(onConfirm).toHaveBeenCalledTimes(1);
    const [positionId, versionId, roster] = onConfirm.mock.calls[0];
    expect(positionId).toBe('pos-1');
    expect(versionId).toBe('mver-1');
    expect(roster).toHaveLength(3);
    expect(roster.map((r: { role: string }) => r.role)).toEqual([
      'HR_DIRECTOR',
      'DEPARTMENT_DIRECTOR',
      'EXTERNAL_EXPERT',
    ]);
  });
});
