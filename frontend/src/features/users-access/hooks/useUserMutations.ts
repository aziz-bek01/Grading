/**
 * Mutation hooks for Users & Access.
 *
 * Each mutation invalidates `userKeys.all` so both the list view and the
 * details view stay consistent without bespoke cache surgery.
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  addMembership,
  addRole,
  inviteUser,
  removeRole,
  revokeMembership,
  updateSalaryPermission,
  updateUser,
  userKeys,
} from '../api/userApi';
import type {
  AddMembershipPayload,
  AddRolePayload,
  InviteUserPayload,
  UpdateSalaryPermissionPayload,
  UpdateUserPayload,
} from '../types/userTypes';

function useInvalidate() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: userKeys.all });
}

export function useInviteUser() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (payload: InviteUserPayload) => inviteUser(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateUser(id: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (payload: UpdateUserPayload) => updateUser(id, payload),
    onSuccess: invalidate,
  });
}

export function useAddMembership(userId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (payload: AddMembershipPayload) => addMembership(userId, payload),
    onSuccess: invalidate,
  });
}

export function useRevokeMembership(userId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (tenantId: string) => revokeMembership(userId, tenantId),
    onSuccess: invalidate,
  });
}

export function useAddRole(userId: string, tenantId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (payload: AddRolePayload) => addRole(userId, tenantId, payload),
    onSuccess: invalidate,
  });
}

export function useRemoveRole(userId: string, tenantId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (roleId: string) => removeRole(userId, tenantId, roleId),
    onSuccess: invalidate,
  });
}

export function useUpdateSalaryPermission(userId: string, tenantId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (payload: UpdateSalaryPermissionPayload) =>
      updateSalaryPermission(userId, tenantId, payload),
    onSuccess: invalidate,
  });
}
