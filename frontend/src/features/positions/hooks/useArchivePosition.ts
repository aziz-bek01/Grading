import { useMutation, useQueryClient } from '@tanstack/react-query';
import { archivePosition, positionKeys } from '../api/positionApi';

export function useArchivePosition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: archivePosition,
    onSuccess: () => qc.invalidateQueries({ queryKey: positionKeys.all }),
  });
}
