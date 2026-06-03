import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { DataTable } from '@/shared/components/data-table/DataTable';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { useAuthStore } from '@/features/auth/authStore';
import { GradeStructureStatusBadge } from '../components/GradeStructureStatusBadge';
import { GradeStructureTypeBadge } from '../components/GradeStructureTypeBadge';
import { GradeStructureTemplatePicker } from '../components/GradeStructureTemplatePicker';
import {
  useCreateFromTemplate,
  useGradeStructures,
} from '../hooks/useGradeStructure';
import type { Locale, LocalizedString } from '@/shared/types/common';
import type { GradeStructure, GradeStructureTemplateCode } from '../types';
import { routes } from '@/shared/config/routes';

function nameInLocale(value: LocalizedString | undefined, locale: Locale) {
  if (!value) return '—';
  return value[locale] ?? value['ru-RU'] ?? value['en-US'] ?? '—';
}

export function GradeStructureListPage() {
  const { t, i18n } = useTranslation();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const currentUserLocale = (useAuthStore((s) => s.user?.locale) ??
    (i18n.language as Locale)) as Locale;

  const query = useGradeStructures({ projectId });
  const items = query.data?.items ?? [];

  const [pickerOpen, setPickerOpen] = useState(false);
  const createMut = useCreateFromTemplate();

  const handleTemplateSelect = async (template: GradeStructureTemplateCode) => {
    setPickerOpen(false);
    const code = `GS-${Date.now().toString(36).toUpperCase()}`;
    const created = await createMut.mutateAsync({
      template_code: template,
      project_id: projectId,
      code,
      name: {
        'ru-RU':
          template === 'GRADE_14'
            ? t('gradeStructure.template_picker.default_name_14')
            : template === 'GRADE_16'
              ? t('gradeStructure.template_picker.default_name_16')
              : t('gradeStructure.template_picker.default_name_custom'),
      },
    });
    navigate(routes.projectGradeStructureEdit(projectId, created.id));
  };

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: t('nav.grades') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">
            {t('gradeStructure.list_page_title')}
          </h1>
          <p className="text-sm text-text-secondary mt-1">
            {t('gradeStructure.list_page_subtitle')}
          </p>
        </div>
        <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
          <Button
            onClick={() => setPickerOpen(true)}
            leadingIcon={<Plus size={16} />}
            data-testid="grade-structure-list-new"
          >
            {t('gradeStructure.new_structure')}
          </Button>
        </PermissionGate>
      </header>

      <DataTable<GradeStructure>
        rows={items}
        rowKey={(s) => s.id}
        loading={query.isLoading}
        searchPredicate={(s, q) =>
          (s.code ?? '').toLowerCase().includes(q) ||
          Object.values(s.name ?? {}).some((v) =>
            (v as string).toLowerCase().includes(q),
          )
        }
        emptyTitle={t('gradeStructure.empty_title')}
        emptyBody={t('gradeStructure.empty_body')}
        columns={[
          {
            key: 'code',
            header: t('common.code'),
            render: (s) => (
              <span className="font-mono text-xs text-text-secondary">{s.code}</span>
            ),
            width: '12%',
            sortable: true,
            sortAccessor: (s) => s.code,
          },
          {
            key: 'name',
            header: t('common.name'),
            render: (s) => (
              <span className="text-text-primary font-medium">
                {nameInLocale(s.name, currentUserLocale)}
              </span>
            ),
            sortable: true,
            sortAccessor: (s) => nameInLocale(s.name, currentUserLocale),
          },
          {
            key: 'type',
            header: t('common.type'),
            render: (s) => <GradeStructureTypeBadge type={s.structure_type} />,
            width: '14%',
          },
          {
            key: 'status',
            header: t('common.status'),
            render: (s) => <GradeStructureStatusBadge status={s.status} />,
            width: '12%',
          },
          {
            key: 'grades_count',
            header: t('gradeStructure.column.grades_count'),
            render: (s) => (
              <span className="text-text-secondary tabular-nums text-xs">
                {s.grades?.length ?? 0}
              </span>
            ),
            width: '10%',
          },
          {
            key: 'version',
            header: t('gradeStructure.column.version'),
            render: (s) => (
              <span className="text-text-secondary tabular-nums text-xs">
                {t('gradeStructure.version_label', { number: s.version_number })}
              </span>
            ),
            width: '12%',
          },
          {
            key: 'updated_at',
            header: t('gradeStructure.column.updated_at'),
            render: (s) => (
              <span className="text-xs text-text-secondary tabular-nums">
                {s.updated_at}
              </span>
            ),
            width: '14%',
            sortable: true,
            sortAccessor: (s) => s.updated_at,
          },
          {
            key: 'actions',
            header: '',
            render: (s) => (
              <Button
                variant="ghost"
                size="sm"
                data-testid={`grade-structure-${s.code}-open`}
                onClick={() => navigate(routes.projectGradeStructureEdit(projectId, s.id))}
              >
                {t('gradeStructure.open')}
              </Button>
            ),
            width: '12%',
            className: 'text-right',
          },
        ]}
      />

      <GradeStructureTemplatePicker
        open={pickerOpen}
        onCancel={() => setPickerOpen(false)}
        onSelect={handleTemplateSelect}
      />
    </div>
  );
}
