import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import { env } from '@/shared/config/env';
import type { MeResponseDto } from './mapMeResponse';

/**
 * Fetch the authenticated user from the backend.
 *
 * Used right after the OIDC callback, BEFORE the auth store session exists, so
 * the access token is passed explicitly as a Bearer header for this single
 * request. (Once the session is set, httpClient attaches the in-memory token
 * automatically for subsequent calls.)
 *
 * In dev-auth mode there is NO real JWT — the stored token is a placeholder.
 * Forcing an `Authorization: Bearer` header makes the backend's resource-server
 * filter try to decode it ("Real JWT decoding is disabled in dev/test") and
 * reject the request with 401, which (e.g. on a tenant switch via refreshUser)
 * logs the user out. So in dev-auth mode we omit the Bearer and let httpClient
 * attach the X-Dev-* headers instead.
 *
 * The token is only ever held in memory — see tokenStorage / oidcClient.
 */
export async function fetchCurrentUser(accessToken: string): Promise<MeResponseDto> {
  const devAuthMode = !env.useMockApi && env.devAuthEnabled;
  const res = await httpClient.get<MeResponseDto>(endpoints.auth.me, {
    headers: devAuthMode ? undefined : { Authorization: `Bearer ${accessToken}` },
  });
  return res.data;
}
