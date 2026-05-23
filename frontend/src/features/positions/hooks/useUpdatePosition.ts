import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updatePosition, positionKeys } from '../api/positionApi';
import type { PositionUpdatePayload } from '../types/positionTypes';

export function useUpdatePosition(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: PositionUpdatePayload) => updatePosition(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: positionKeys.all });
      qc.invalidateQueries({ queryKey: positionKeys.detail(id) });
    },
  });
}
