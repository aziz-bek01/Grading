import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Briefcase, ChevronDown, Check } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import type { ProjectSummary } from '@/shared/auth/authTypes';
import { cn } from '@/shared/lib/cn';

interface ProjectSelectorProps {
  /** projects available within active tenant; supplied by ProjectContextProvider in real flows. */
  projects?: ProjectSummary[];
}

export function ProjectSelector({ projects = [] }: ProjectSelectorProps) {
  const { t } = useTranslation();
  const active = useAuthStore((s) => s.activeProject);
  const setActiveProject = useAuthStore((s) => s.setActiveProject);
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState<ProjectSummary | null>(null);

  const onSelect = (p: ProjectSummary) => {
    setOpen(false);
    if (active?.id === p.id) return;
    if (active) {
      setPending(p);
    } else {
      setActiveProject(p);
    }
  };

  return (
    <>
      <div className="relative">
        <button
          type="button"
          aria-label={t('project.selector_aria')}
          aria-haspopup="listbox"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
          className={cn(
            'inline-flex items-center gap-2 h-9 px-3 rounded-md text-sm',
            'border border-border-strong bg-surface text-text-primary hover:bg-divider',
            active && 'border-b-2 border-b-accent-500',
          )}
        >
          <Briefcase size={14} aria-hidden className="text-text-secondary" />
          <span className="font-medium">{active ? active.name : t('app.select_project')}</span>
          <ChevronDown size={14} aria-hidden />
        </button>
        {open ? (
          <ul
            role="listbox"
            className="absolute left-0 mt-2 w-72 bg-surface border border-border rounded-lg shadow-md py-1 z-30 max-h-72 overflow-auto"
          >
            {projects.length === 0 ? (
              <li className="px-3 py-2 text-sm text-text-muted">{t('app.no_project')}</li>
            ) : (
              projects.map((p) => (
                <li key={p.id}>
                  <button
                    role="option"
                    aria-selected={active?.id === p.id}
                    onClick={() => onSelect(p)}
                    className={cn(
                      'w-full flex items-center justify-between px-3 py-2 text-sm hover:bg-divider',
                      p.archived && 'italic text-text-muted',
                      active?.id === p.id ? 'text-primary-600' : 'text-text-primary',
                    )}
                  >
                    <span>{p.name}</span>
                    {active?.id === p.id ? <Check size={14} aria-hidden /> : null}
                  </button>
                </li>
              ))
            )}
          </ul>
        ) : null}
      </div>

      <ConfirmDialog
        open={!!pending}
        title={t('project.switch_title')}
        body={t('project.switch_body', {
          from: active?.name ?? '',
          to: pending?.name ?? '',
        })}
        confirmLabel={t('project.switch_confirm') ?? undefined}
        onConfirm={() => {
          if (pending) setActiveProject(pending);
          setPending(null);
        }}
        onCancel={() => setPending(null)}
      />
    </>
  );
}
