import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type { MeResponseDto } from './mapMeResponse';

/**
 * Fetch the authenticated user from the backend.
 *
 * Used right after the OIDC callback, BEFORE the auth store session exists, so
 * the access token is passed explicitly as a Bearer header for this single
 * request. (Once the session is set, httpClient attaches the in-memory token
 * automatically for subsequent calls.)
 *
 * The token is only ever held in memory — see tokenStorage / oidcClient.
 */
export async function fetchCurrentUser(accessToken: string): Promise<MeResponseDto> {
  const res = await httpClient.get<MeResponseDto>(endpoints.auth.me, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  return res.data;
}
