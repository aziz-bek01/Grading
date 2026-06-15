import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Compass } from 'lucide-react';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { Button } from '@/shared/components/ui/Button';
import { routes } from '@/shared/config/routes';

/**
 * 404 / unknown-route screen (replaces the previous silent redirect to the
 * dashboard). Reuses {@link EmptyState} so it matches the rest of the app's
 * empty/no-access surfaces.
 *
 * The copy is deliberately security-safe and non-enumerating: it never reveals
 * whether the path could have existed for another tenant/project — a mistyped,
 * stale or cross-tenant deep link all land on the same neutral message.
 */
export function NotFoundPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-6">
      <EmptyState
        icon={<Compass size={32} aria-hidden />}
        title={t('states.not_found_title')}
        body={t('states.not_found_body')}
        action={
          <div className="flex flex-wrap items-center justify-center gap-2">
            <Button variant="secondary" onClick={() => navigate(-1)}>
              {t('states.not_found_back')}
            </Button>
            <Button onClick={() => navigate(routes.dashboard)}>
              {t('states.not_found_dashboard')}
            </Button>
          </div>
        }
      />
    </div>
  );
}
