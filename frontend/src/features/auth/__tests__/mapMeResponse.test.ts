import { describe, expect, it } from 'vitest';
import {
  mapMeResponse,
  pickActiveTenant,
  type MeResponseDto,
} from '@/features/auth/mapMeResponse';

const BASE: MeResponseDto = {
  user: {
    id: 'u-1',
    email: 'jane@acme.example',
    full_name: 'Jane Doe',
    locale: 'en-US',
    status: 'ACTIVE',
  },
  tenants: [
    {
      id: 't-1',
      slug: 'acme',
      brand_name: 'ACME Holdings',
      fingerprint_hue: 215,
      membership_status: 'ACTIVE',
      salary_data_permission: false,
    },
    {
      id: 't-2',
      slug: 'beta',
      brand_name: 'Beta University',
      fingerprint_hue: 145,
    },
  ],
  active_tenant_id: 't-2',
  roles: ['HRLAB_CONSULTANT'],
  permissions: ['PROJECT_READ', 'POSITION_READ'],
  salary_data_permission: true,
};

describe('mapMeResponse', () => {
  it('maps full_name -> name and core identity fields', () => {
    const u = mapMeResponse(BASE);
    expect(u.id).toBe('u-1');
    expect(u.email).toBe('jane@acme.example');
    expect(u.name).toBe('Jane Doe');
    expect(u.locale).toBe('en-US');
  });

  it('passes roles and permissions through', () => {
    const u = mapMeResponse(BASE);
    expect(u.roles).toEqual(['HRLAB_CONSULTANT']);
    expect(u.permissions).toEqual(['PROJECT_READ', 'POSITION_READ']);
  });

  it('maps the explicit top-level salary_data_permission flag', () => {
    expect(mapMeResponse(BASE).salary_data_permission).toBe(true);
    expect(
      mapMeResponse({ ...BASE, salary_data_permission: false }).salary_data_permission,
    ).toBe(false);
  });

  it('maps tenants to TenantSummary (drops membership/salary fields)', () => {
    const u = mapMeResponse(BASE);
    expect(u.tenants).toEqual([
      { id: 't-1', slug: 'acme', brand_name: 'ACME Holdings', fingerprint_hue: 215 },
      { id: 't-2', slug: 'beta', brand_name: 'Beta University', fingerprint_hue: 145 },
    ]);
  });

  it('falls back to ru-RU for an unsupported locale', () => {
    const u = mapMeResponse({ ...BASE, user: { ...BASE.user, locale: 'fr-FR' } });
    expect(u.locale).toBe('ru-RU');
  });

  it('tolerates missing roles/permissions/tenants arrays', () => {
    const u = mapMeResponse({
      user: BASE.user,
      tenants: undefined as unknown as MeResponseDto['tenants'],
      roles: undefined as unknown as string[],
      permissions: undefined as unknown as string[],
      salary_data_permission: false,
    });
    expect(u.roles).toEqual([]);
    expect(u.permissions).toEqual([]);
    expect(u.tenants).toEqual([]);
  });
});

describe('pickActiveTenant', () => {
  it('returns the tenant matching active_tenant_id', () => {
    expect(pickActiveTenant(BASE)?.id).toBe('t-2');
  });

  it('returns null when active_tenant_id is absent', () => {
    expect(pickActiveTenant({ ...BASE, active_tenant_id: undefined })).toBeNull();
  });

  it('returns null when active_tenant_id is not in the tenant list', () => {
    expect(pickActiveTenant({ ...BASE, active_tenant_id: 't-999' })).toBeNull();
  });
});
