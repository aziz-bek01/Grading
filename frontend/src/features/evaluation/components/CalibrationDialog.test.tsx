import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { CalibrationDialog } from './CalibrationDialog';
import type { Factor } from '@/features/methodology/types';

const factors: Factor[] = [
  {
    id: 'f-1',
    methodology_version_id: 'v-1',
    code: 'F1',
    name_i18n: { 'ru-RU': 'Знания', 'en-US': 'Knowledge' },
    weight: 10,
    max_points: 100,
    sort_order: 0,
    required: true,
    levels: [],
  },
];

describe('CalibrationDialog', () => {
  it('does not render when closed', () => {
    render(
      renderWithProviders(
        <CalibrationDialog
          open={false}
          factors={factors}
          onConfirm={() => {}}
          onCancel={() => {}}
        />,
      ),
    );
    expect(screen.queryByTestId('calibration-dialog-title')).not.toBeInTheDocument();
  });

  it('renders when open with factor select', () => {
    render(
      renderWithProviders(
        <CalibrationDialog
          open
          factors={factors}
          onConfirm={() => {}}
          onCancel={() => {}}
        />,
      ),
    );
    expect(screen.getByTestId('calibration-dialog-title')).toBeInTheDocument();
    expect(screen.getByTestId('calibration-factor-select')).toBeInTheDocument();
  });

  it('disables Confirm until reason >= 20 chars', () => {
    render(
      renderWithProviders(
        <CalibrationDialog
          open
          factors={factors}
          onConfirm={() => {}}
          onCancel={() => {}}
        />,
      ),
    );
    fireEvent.change(screen.getByTestId('calibration-score-input'), {
      target: { value: '85.5' },
    });
    const btn = screen.getByTestId('calibration-confirm') as HTMLButtonElement;
    expect(btn.disabled).toBe(true);

    fireEvent.change(screen.getByTestId('calibration-reason-input'), {
      target: { value: 'short reason' },
    });
    expect(btn.disabled).toBe(true);

    fireEvent.change(screen.getByTestId('calibration-reason-input'), {
      target: { value: 'Committee unanimously agreed to adjust' },
    });
    expect(btn.disabled).toBe(false);
  });

  it('invokes onConfirm with parsed payload when valid', () => {
    const onConfirm = vi.fn();
    render(
      renderWithProviders(
        <CalibrationDialog open factors={factors} onConfirm={onConfirm} onCancel={() => {}} />,
      ),
    );
    fireEvent.change(screen.getByTestId('calibration-score-input'), {
      target: { value: '42.25' },
    });
    fireEvent.change(screen.getByTestId('calibration-reason-input'), {
      target: { value: 'Committee unanimously agreed to adjust' },
    });
    fireEvent.click(screen.getByTestId('calibration-confirm'));
    expect(onConfirm).toHaveBeenCalledWith({
      factor_id: 'f-1',
      new_raw_factor_score: 42.25,
      reason: 'Committee unanimously agreed to adjust',
    });
  });
});
