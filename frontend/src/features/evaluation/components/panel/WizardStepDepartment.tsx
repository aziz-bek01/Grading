import { Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  DepartmentSingleSelectTree,
  type DepartmentCoverage,
} from '@/features/organization/components/DepartmentSingleSelectTree';
import type { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';

interface WizardStepDepartmentProps {
  deptSearch: string;
  onDeptSearchChange: (value: string) => void;
  treeQuery: ReturnType<typeof useDepartmentTree>;
  departmentId: string | null;
  onSelect: (id: string) => void;
  coverageOf: (deptId: string) => DepartmentCoverage | undefined;
}

/** Step 1 — DEPARTMENT: searchable single-select dept tree + coverage badges. */
export function WizardStepDepartment({
  deptSearch,
  onDeptSearchChange,
  treeQuery,
  departmentId,
  onSelect,
  coverageOf,
}: WizardStepDepartmentProps) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col flex-1 min-h-0 space-y-2" data-testid="wizard-step-1">
      <p className="shrink-0 text-sm text-text-secondary">
        {t('panel.wizard.step_1_body')}
      </p>
      <label className="relative shrink-0">
        <span className="sr-only">{t('common.search')}</span>
        <Search
          size={14}
          aria-hidden
          className="absolute left-2 top-1/2 -translate-y-1/2 text-text-muted"
        />
        <input
          type="search"
          value={deptSearch}
          onChange={(e) => onDeptSearchChange(e.target.value)}
          placeholder={t('panel.wizard.dept.search_placeholder')}
          data-testid="wizard-dept-search"
          className="w-full h-9 pl-7 pr-3 border border-border-strong rounded-md text-sm bg-surface"
        />
      </label>
      <div
        className="flex-1 min-h-0 overflow-y-auto border border-border rounded-md p-1"
        data-testid="wizard-dept-list"
      >
        {treeQuery.isLoading ? (
          <p className="p-4 text-sm text-text-muted text-center">
            {t('common.loading')}
          </p>
        ) : treeQuery.isError ? (
          <p className="p-4 text-sm text-danger-600 text-center">
            {t('panel.wizard.dept.load_error')}
          </p>
        ) : (
          <DepartmentSingleSelectTree
            items={treeQuery.data ?? []}
            selectedId={departmentId}
            onSelect={onSelect}
            countsOf={coverageOf}
            search={deptSearch}
          />
        )}
      </div>
    </div>
  );
}
