import { useAuthStore } from './authStore';

export function useCurrentUser() {
  return useAuthStore((s) => s.user);
}
