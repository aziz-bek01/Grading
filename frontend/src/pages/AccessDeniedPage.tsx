import { NoAccessState } from '@/shared/components/feedback/NoAccessState';

export function AccessDeniedPage() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-6">
      <NoAccessState />
    </div>
  );
}
