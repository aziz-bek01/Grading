import { SalaryValue } from '@/shared/components/salary/SalaryValue';

/**
 * E2E-ONLY harness page (route registered solely in dev/mock builds — see
 * router.tsx). MVP 1 has no reachable production screen that renders salary
 * figures (the Compensation surfaces are still "upcoming"), so this page mounts
 * the REAL {@link SalaryValue} component to let an E2E assert its security
 * invariant end-to-end: masked for a non-permitted session, visible for a
 * salary-permitted one. The session is seeded by the spec via `window.__E2E__`.
 *
 * Never shipped in production: its route is only added when
 * `import.meta.env.DEV || __ENABLE_MSW__`, both false in a real prod build.
 */
export function E2ESalaryHarnessPage() {
  return (
    <main data-testid="e2e-salary-harness" style={{ padding: 24 }}>
      <h1>Salary harness</h1>
      <p>
        Value:{' '}
        <span data-testid="e2e-salary-value">
          <SalaryValue value={5_000_000} currency="UZS" />
        </span>
      </p>
    </main>
  );
}
