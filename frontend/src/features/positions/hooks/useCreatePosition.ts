import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createPosition, positionKeys } from '../api/positionApi';

export function useCreatePosition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createPosition,
    onSuccess: () => qc.invalidateQueries({ queryKey: positionKeys.all }),
  });
}
