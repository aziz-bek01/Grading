/**
 * Filter bar for the audit list (D-1 FE).
 *
 * Renders date range (from / to), action, entity type, and actor inputs.
 * Builds on the shared <FilterBar /> for action / entity dropdowns and
 * adds raw date pickers + actor id input. Designed as a controlled
 * component so the parent owns the filter state and can serialise it
 * into the URL search params later.
 */
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { DateRangeFields } from '@/shared/components/form/DateRangeFields';
import type { AuditQuery } from '../types/auditTypes';

interface AuditFilterBarProps {
  value: AuditQuery;
  onChange: (next: AuditQuery) => void;
  onReset: () => void;
  /** Optional list of recently-used action codes for the action dropdown. */
  actionOptions?: string[];
  /** Optional list of entity types for the entity dropdown. */
  entityTypeOptions?: string[];
  /** Optional tenant selector — visible only for cross-tenant readers. */
  tenantOptions?: { value: string; label: string }[];
}

const ENTITY_TYPES = [
  'PROJECT',
  'TENANT',
  'USER',
  'USER_MEMBERSHIP',
  'DEPARTMENT',
  'POSITION',
  'JOB_PROFILE',
  'METHODOLOGY',
  'METHODOLOGY_VERSION',
  'EVALUATION',
  'EVALUATION_SCORE',
  'GRADE_STRUCTURE',
  'GRADE',
  'APPROVAL_REQUEST',
  'IMPORT',
  'EXPORT',
  'REPORT',
];

// A curated subset of the 71 AuditAction codes — covers the most common
// forensic searches. The free-form input below lets you type any code.
const CURATED_ACTIONS = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'TENANT_CONTEXT_SWITCH',
  'PROJECT_CREATED',
  'PROJECT_UPDATED',
  'POSITION_CREATED',
  'POSITION_UPDATED',
  'JOB_PROFILE_APPROVED',
  'METHODOLOGY_VERSION_APPROVED',
  'METHODOLOGY_VERSION_LOCKED',
  'EVALUATION_APPROVED',
  'EVALUATION_LOCKED',
  'EVALUATION_SCORE_CALIBRATED',
  'GRADE_ASSIGNED',
  'USER_INVITED',
  'USER_SALARY_PERMISSION_GRANTED',
  'USER_SALARY_PERMISSION_REVOKED',
  'IMPORT_COMMITTED',
  'EXPORT_DOWNLOADED',
  'REPORT_GENERATED',
  'CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT',
  'TENANT_MEMBERSHIP_MISMATCH',
];

export function AuditFilterBar({
  value,
  onChange,
  onReset,
  actionOptions,
  entityTypeOptions,
  tenantOptions,
}: AuditFilterBarProps) {
  const { t } = useTranslation();

  const merged = (patch: Partial<AuditQuery>) => onChange({ ...value, ...patch, page: 0 });
  const actions = actionOptions ?? CURATED_ACTIONS;
  const entityTypes = entityTypeOptions ?? ENTITY_TYPES;
  const hasActive = Boolean(
    value.from || value.to || value.action || value.entityType || value.entityId || value.actorUserId || value.tenantId,
  );

  return (
    <div className="flex items-center gap-3 flex-wrap">
      <DateRangeFields
        value={{ from: value.from, to: value.to }}
        onChange={(next) => merged({ from: next.from, to: next.to })}
        testIdPrefix="audit-filter"
      />

      <label className="inline-flex items-center gap-1 text-xs text-text-secondary">
        <span>{t('audit.filter.action')}:</span>
        <select
          value={value.action ?? ''}
          onChange={(e) => merged({ action: e.target.value || undefined })}
          aria-label={t('audit.filter.action')}
          data-testid="audit-filter-action"
          className="h-8 px-2 border border-border-strong rounded-md bg-surface text-text-primary text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">{t('common.all')}</option>
          {actions.map((code) => (
            <option key={code} value={code}>
              {t(`audit.action.${code}`, { defaultValue: code })}
            </option>
          ))}
        </select>
      </label>

      <label className="inline-flex items-center gap-1 text-xs text-text-secondary">
        <span>{t('audit.filter.entity_type')}:</span>
        <select
          value={value.entityType ?? ''}
          onChange={(e) => merged({ entityType: e.target.value || undefined })}
          aria-label={t('audit.filter.entity_type')}
          data-testid="audit-filter-entity-type"
          className="h-8 px-2 border border-border-strong rounded-md bg-surface text-text-primary text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">{t('common.all')}</option>
          {entityTypes.map((et) => (
            <option key={et} value={et}>
              {t(`audit.entity_type.${et}`, { defaultValue: et })}
            </option>
          ))}
        </select>
      </label>

      <label className="inline-flex items-center gap-1 text-xs text-text-secondary">
        <span>{t('audit.filter.actor')}:</span>
        <input
          type="text"
          value={value.actorUserId ?? ''}
          onChange={(e) => merged({ actorUserId: e.target.value || undefined })}
          placeholder={t('audit.filter.actor_placeholder')}
          aria-label={t('audit.filter.actor')}
          data-testid="audit-filter-actor"
          className="h-8 px-2 border border-border-strong rounded-md bg-surface text-text-primary text-sm w-44 focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
      </label>

      {tenantOptions && tenantOptions.length > 0 ? (
        <label className="inline-flex items-center gap-1 text-xs text-text-secondary">
          <span>{t('audit.filter.tenant')}:</span>
          <select
            value={value.tenantId ?? ''}
            onChange={(e) => merged({ tenantId: e.target.value || undefined })}
            aria-label={t('audit.filter.tenant')}
            data-testid="audit-filter-tenant"
            className="h-8 px-2 border border-border-strong rounded-md bg-surface text-text-primary text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            <option value="">{t('common.all')}</option>
            {tenantOptions.map((tn) => (
              <option key={tn.value} value={tn.value}>
                {tn.label}
              </option>
            ))}
          </select>
        </label>
      ) : null}

      {hasActive ? (
        <Button
          variant="ghost"
          size="sm"
          leadingIcon={<X size={12} />}
          onClick={onReset}
          data-testid="audit-filter-reset"
        >
          {t('common.reset_filters')}
        </Button>
      ) : null}
    </div>
  );
}
