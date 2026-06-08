import { useTranslation } from 'react-i18next';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import type { MembershipStatus, UserStatus } from '../types/userTypes';

/**
 * Renders both USER statuses (ACTIVE / INVITED / DISABLED / LOCKED) and
 * MEMBERSHIP statuses (ACTIVE / INVITED / SUSPENDED / REVOKED). The two enums
 * overlap on ACTIVE / INVITED only — every other value is distinct, so a single
 * tone/label map covers both without conflating the enums at the type level.
 */
function toneFor(status: UserStatus | MembershipStatus): StatusTone {
  switch (status) {
    case 'ACTIVE':
      return 'approved';
    case 'INVITED':
      return 'in-review';
    case 'DISABLED':
      return 'archived';
    case 'LOCKED':
      return 'locked';
    case 'SUSPENDED':
      return 'needs-attention';
    case 'REVOKED':
      return 'archived';
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
    DISABLED: t('users.status.disabled'),
    LOCKED: t('users.status.locked'),
    SUSPENDED: t('users.status.suspended'),
    REVOKED: t('users.status.revoked'),
  };
  return <StatusBadge tone={toneFor(status)} label={labelMap[status] ?? status} outline={outline} />;
}
