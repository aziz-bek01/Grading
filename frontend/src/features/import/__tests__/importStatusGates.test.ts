import { describe, expect, it } from 'vitest';
import { canArchiveImportStatus, canCancelImportStatus, type ImportBatchStatus } from '../types';

/**
 * These sets MUST mirror `ImportBatchStatusTransitionPolicy` on the backend
 * exactly (integration-blueprint §8.1) — this test pins that contract so the
 * FE gate can never silently drift again (the original prod bug: the FE
 * showed a Cancel button for PARTIALLY_COMMITTED long after the backend
 * state machine had stopped allowing that transition).
 */
const CANCELLABLE: ImportBatchStatus[] = [
  'UPLOADED',
  'SCANNING',
  'PARSING',
  'VALIDATING',
  'READY_FOR_REVIEW',
  'READY_TO_COMMIT',
  'VALIDATION_FAILED',
];

const NOT_CANCELLABLE: ImportBatchStatus[] = [
  'SCAN_FAILED',
  'COMMITTING',
  'COMMITTED',
  'PARTIALLY_COMMITTED',
  'FAILED',
  'CANCELLED',
  'ARCHIVED',
];

const ARCHIVABLE: ImportBatchStatus[] = [
  'COMMITTED',
  'PARTIALLY_COMMITTED',
  'CANCELLED',
  'FAILED',
  'SCAN_FAILED',
  'VALIDATION_FAILED',
];

const NOT_ARCHIVABLE: ImportBatchStatus[] = [
  'UPLOADED',
  'SCANNING',
  'PARSING',
  'VALIDATING',
  'READY_FOR_REVIEW',
  'READY_TO_COMMIT',
  'COMMITTING',
  'ARCHIVED',
];

describe('canCancelImportStatus', () => {
  it.each(CANCELLABLE)('allows cancel from %s', (status) => {
    expect(canCancelImportStatus(status)).toBe(true);
  });

  it.each(NOT_CANCELLABLE)('rejects cancel from %s', (status) => {
    expect(canCancelImportStatus(status)).toBe(false);
  });
});

describe('canArchiveImportStatus', () => {
  it.each(ARCHIVABLE)('allows archive from %s', (status) => {
    expect(canArchiveImportStatus(status)).toBe(true);
  });

  it.each(NOT_ARCHIVABLE)('rejects archive from %s', (status) => {
    expect(canArchiveImportStatus(status)).toBe(false);
  });
});

describe('every ImportBatchStatus is covered by both fixtures', () => {
  it('CANCELLABLE + NOT_CANCELLABLE cover exactly the 14 known statuses', () => {
    expect(new Set([...CANCELLABLE, ...NOT_CANCELLABLE]).size).toBe(14);
  });

  it('ARCHIVABLE + NOT_ARCHIVABLE cover exactly the 14 known statuses', () => {
    expect(new Set([...ARCHIVABLE, ...NOT_ARCHIVABLE]).size).toBe(14);
  });

  it('VALIDATION_FAILED is reachable via EITHER cancel or archive (both true)', () => {
    expect(canCancelImportStatus('VALIDATION_FAILED')).toBe(true);
    expect(canArchiveImportStatus('VALIDATION_FAILED')).toBe(true);
  });
});
