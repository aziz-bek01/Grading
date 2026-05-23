import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';

interface PlaceholderPageProps {
  titleKey: string;
  subtitleKey?: string;
}

export function PlaceholderPage({ titleKey, subtitleKey }: PlaceholderPageProps) {
  const { t } = useTranslation();
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl text-text-primary">{t(titleKey)}</h1>
        {subtitleKey ? (
          <p className="text-sm text-text-secondary mt-1">{t(subtitleKey)}</p>
        ) : null}
      </header>
      <Card>
        <p className="text-sm text-text-secondary">{t('pages.placeholder_body')}</p>
      </Card>
    </div>
  );
}
