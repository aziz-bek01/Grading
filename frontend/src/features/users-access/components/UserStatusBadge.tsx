import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { MembershipStatus, UserStatus } from '../types/userTypes';

function toneFor(status: UserStatus | MembershipStatus): StatusTone {
  switch (status) {
    case 'ACTIVE':
      return 'approved';
    case 'INVITED':
      return 'in-review';
    case 'REVOKED':
      return 'archived';
    case 'SUSPENDED':
      return 'needs-attention';
    default:
      return 'draft';
  }
}

interface Props {
  status: UserStatus | MembershipStatus;
  outline?: boolean;
}

export function UserStatusBadge({ status, outline }: Props) {
  const { t } = useTranslation();
  const labelMap: Record<string, string> = {
    ACTIVE: t('users.status.active'),
    INVITED: t('users.status.invited'),
    REVOKED: t('users.status.revoked'),
    SUSPENDED: t('users.status.suspended'),
  };
  return <StatusBadge tone={toneFor(status)} label={labelMap[status] ?? status} outline={outline} />;
}
