import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { WeightSumVisualizer, WEIGHT_SUM_TOLERANCE } from './WeightSumVisualizer';
import type { Factor } from '../types';

function factor(weight: number, id = `f-${weight}`): Factor {
  return {
    id,
    methodology_version_id: 'v1',
    code: id,
    name_i18n: { 'ru-RU': id },
    weight,
    max_points: 100,
    sort_order: 0,
    required: true,
    levels: [],
  };
}

describe('WeightSumVisualizer', () => {
  it('renders nothing in DIRECT_POINTS mode', () => {
    const { container } = render(
      renderWithProviders(
        <WeightSumVisualizer factors={[factor(50)]} scoringMode="DIRECT_POINTS" target={100} />,
      ),
    );
    expect(container.querySelector('[data-testid="weight-sum-visualizer"]')).toBeNull();
  });

  it('marks at-target (sum=100, target=100) with tone=success', () => {
    render(
      renderWithProviders(
        <WeightSumVisualizer
          factors={[factor(50, 'a'), factor(50, 'b')]}
          scoringMode="WEIGHTED_POINTS"
          target={100}
        />,
      ),
    );
    const el = screen.getByTestId('weight-sum-visualizer');
    expect(el.getAttribute('data-tone')).toBe('success');
  });

  it('shows warning when sum is close but not equal to target', () => {
    render(
      renderWithProviders(
        <WeightSumVisualizer
          factors={[factor(45, 'a'), factor(50, 'b')]}
          scoringMode="WEIGHTED_POINTS"
          target={100}
        />,
      ),
    );
    expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('warning');
  });

  it('shows danger when sum drifts > 10% of target', () => {
    render(
      renderWithProviders(
        <WeightSumVisualizer
          factors={[factor(40, 'a'), factor(40, 'b')]}
          scoringMode="WEIGHTED_POINTS"
          target={100}
        />,
      ),
    );
    expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('danger');
  });

  it('uses target_total_points in WEIGHTED_SCALE mode', () => {
    render(
      renderWithProviders(
        <WeightSumVisualizer
          factors={[factor(500, 'a'), factor(500, 'b')]}
          scoringMode="WEIGHTED_SCALE"
          target={1000}
        />,
      ),
    );
    expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('success');
  });

  /**
   * D-401 / PC4-1 boundary cases.
   *
   * Frontend tolerance constant `WEIGHT_SUM_TOLERANCE = 0.0001` is now the
   * single source of truth and matches the backend
   * `MethodologyWeightValidationPolicy` tolerance contract.
   *
   * The visualiser MUST render `tone="success"` for sums in
   * `[100 - 1e-4, 100 + 1e-4]` and warn (or danger) outside that band.
   */
  describe('D-401 — tolerance boundary cases', () => {
    it('exposes WEIGHT_SUM_TOLERANCE = 0.0001', () => {
      expect(WEIGHT_SUM_TOLERANCE).toBe(0.0001);
    });

    it('sum=100.0000 (exact) → success', () => {
      render(
        renderWithProviders(
          <WeightSumVisualizer
            factors={[factor(50, 'a'), factor(50, 'b')]}
            scoringMode="WEIGHTED_POINTS"
            target={100}
          />,
        ),
      );
      expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('success');
    });

    /*
     * NOTE: Strict 99.9999 / 100.0001 boundary values cannot be tested with
     * JS doubles — `99.9999` and `100.0001` have FP-representation drift on
     * the order of ~3e-13 above their decimal value (verified with Node).
     * Backend uses BigDecimal where 0.0001 is exact; frontend operates on
     * doubles for live UX. We test the "well inside" and "well outside"
     * boundary cases. Backend `MethodologyWeightValidationPolicy` remains
     * the authoritative gate (preview-only contract).
     */

    it('sum within tolerance (delta=5e-5) → success', () => {
      render(
        renderWithProviders(
          <WeightSumVisualizer
            factors={[factor(99.99995, 'a')]}
            scoringMode="WEIGHTED_POINTS"
            target={100}
          />,
        ),
      );
      expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('success');
    });

    it('sum within tolerance over target (delta=5e-5) → success', () => {
      render(
        renderWithProviders(
          <WeightSumVisualizer
            factors={[factor(100.00005, 'a')]}
            scoringMode="WEIGHTED_POINTS"
            target={100}
          />,
        ),
      );
      expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('success');
    });

    it('sum=100.0002 (exceeds tolerance, within 10%) → warning', () => {
      render(
        renderWithProviders(
          <WeightSumVisualizer
            factors={[factor(100.0002, 'a')]}
            scoringMode="WEIGHTED_POINTS"
            target={100}
          />,
        ),
      );
      expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('warning');
    });

    it('sum=99.9998 (exceeds tolerance, within 10%) → warning', () => {
      render(
        renderWithProviders(
          <WeightSumVisualizer
            factors={[factor(99.9998, 'a')]}
            scoringMode="WEIGHTED_POINTS"
            target={100}
          />,
        ),
      );
      expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('warning');
    });

    it('sum=91 (within 10% deviation, exceeds tolerance) → warning', () => {
      // |91 - 100| / 100 = 9% → warning (not danger).
      render(
        renderWithProviders(
          <WeightSumVisualizer
            factors={[factor(91, 'a')]}
            scoringMode="WEIGHTED_POINTS"
            target={100}
          />,
        ),
      );
      expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('warning');
    });

    it('sum=85.0 (>10% deviation) → danger', () => {
      render(
        renderWithProviders(
          <WeightSumVisualizer
            factors={[factor(35, 'a'), factor(50, 'b')]}
            scoringMode="WEIGHTED_POINTS"
            target={100}
          />,
        ),
      );
      expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('danger');
    });

    it('WEIGHTED_SCALE: sum=targetTotalPoints+1e-4 → success', () => {
      render(
        renderWithProviders(
          <WeightSumVisualizer
            factors={[factor(500.0001, 'a'), factor(500, 'b')]}
            scoringMode="WEIGHTED_SCALE"
            target={1000}
          />,
        ),
      );
      expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('success');
    });

    it('WEIGHTED_SCALE: sum=targetTotalPoints+0.0002 → warning', () => {
      render(
        renderWithProviders(
          <WeightSumVisualizer
            factors={[factor(500.0002, 'a'), factor(500, 'b')]}
            scoringMode="WEIGHTED_SCALE"
            target={1000}
          />,
        ),
      );
      expect(screen.getByTestId('weight-sum-visualizer').getAttribute('data-tone')).toBe('warning');
    });
  });
});
