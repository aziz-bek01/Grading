import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { AddPositionsDialog } from './AddPositionsDialog';
import type { Methodology } from '@/features/methodology/types';
import type { Position } from '@/features/positions/types/positionTypes';

const positions: Position[] = [
  {
    id: 'pos-1',
    project_id: 'proj-1',
    department_id: 'dep-1',
    code: 'P1',
    title_i18n: { 'ru-RU': 'Позиция 1', 'en-US': 'Position 1' },
    status: 'ACTIVE',
  },
];

const methodologies: Methodology[] = [
  {
    id: 'm-8',
    project_id: 'proj-1',
    code: 'M8',
    name_i18n: { 'ru-RU': '8 факторли', 'en-US': '8-factor' },
    methodology_type: 'CLASSIC_8_FACTOR',
    status: 'ACTIVE',
    active_version_id: 'v-8',
    active_version_number: 1,
  },
  {
    id: 'm-12',
    project_id: 'proj-1',
    code: 'M12',
    name_i18n: { 'ru-RU': 'HR Lab', 'en-US': 'HR Lab' },
    methodology_type: 'CUSTOM',
    status: 'ACTIVE',
    active_version_id: 'v-12',
    active_version_number: 1,
  },
];

function renderDialog(defaultVersionId: string | null) {
  const onConfirm = vi.fn().mockResolvedValue({ created: 1, failed: [] });
  render(
    renderWithProviders(
      <AddPositionsDialog
        open
        positions={positions}
        methodologies={methodologies}
        existingKeys={new Set()}
        departmentNameOf={() => 'Подразделение'}
        defaultVersionId={defaultVersionId}
        onConfirm={onConfirm}
        onClose={() => {}}
      />,
    ),
  );
  return { onConfirm };
}

describe('AddPositionsDialog — defaultVersionId follows the K-sheet selection', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => signOut());

  it('defaults the version select to the passed selected version', () => {
    renderDialog('v-12');
    const select = screen.getByTestId(
      'add-positions-version-select',
    ) as HTMLSelectElement;
    expect(select.value).toBe('v-12');
  });

  it('creates evaluations for the selected version', async () => {
    const { onConfirm } = renderDialog('v-12');
    fireEvent.click(screen.getByTestId('add-positions-check-P1'));
    fireEvent.click(screen.getByTestId('add-positions-confirm'));
    await screen.findByTestId('add-positions-result');
    expect(onConfirm).toHaveBeenCalledWith('v-12', ['pos-1']);
  });

  it('falls back to the first active version when no selection is provided', () => {
    renderDialog(null);
    const select = screen.getByTestId(
      'add-positions-version-select',
    ) as HTMLSelectElement;
    expect(select.value).toBe('v-8');
  });
});
