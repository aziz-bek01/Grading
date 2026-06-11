/**
 * FE-1 — detail-AGGREGATOR regression tests.
 *
 * `hydrateGradeStructure` is the SINGLE place that turns the wire shape into the
 * flat domain `GradeStructure`. It must:
 *  - flatten the `{ structure, grades, bands }` envelope to top level,
 *  - JOIN each grade's band from the separate `bands[]` array by grade_id,
 *  - tolerate the legacy flat shape (MSW back-compat) without crashing.
 */
import { describe, expect, it } from 'vitest';
import { hydrateGradeStructure } from '../api/gradeStructureApi';

describe('hydrateGradeStructure (FE-1 aggregator)', () => {
  it('flattens the envelope and joins bands by grade_id', () => {
    const envelope = {
      structure: {
        id: 's1',
        project_id: 'p1',
        code: 'GS-1',
        name_i18n: { 'ru-RU': 'Структура' },
        description_i18n: null,
        structure_type: 'GRADE_14',
        status: 'DRAFT',
        gap_policy: 'STRICT_NO_GAPS',
        version_number: 1,
        previous_version_id: null,
      },
      grades: [
        { id: 'g1', grade_structure_id: 's1', grade_number: 1, sort_order: 0, name_i18n: { 'ru-RU': 'Грейд 1' } },
        { id: 'g2', grade_structure_id: 's1', grade_number: 2, sort_order: 1, name_i18n: { 'ru-RU': 'Грейд 2' } },
      ],
      bands: [
        { id: 'b2', grade_id: 'g2', min_score: 50, max_score: 100 },
        { id: 'b1', grade_id: 'g1', min_score: 0, max_score: 50 },
      ],
    };

    const result = hydrateGradeStructure(envelope);

    // Flattened to top level.
    expect(result.id).toBe('s1');
    expect(result.name_i18n['ru-RU']).toBe('Структура');
    expect(result.structure_type).toBe('GRADE_14');
    // Bands joined by grade_id (note bands array order differs from grades).
    expect(result.grades).toHaveLength(2);
    expect(result.grades[0].band?.id).toBe('b1');
    expect(result.grades[0].band?.min_score).toBe(0);
    expect(result.grades[1].band?.id).toBe('b2');
    expect(result.grades[1].band?.max_score).toBe(100);
  });

  it('sets band=null for a grade with no matching band', () => {
    const envelope = {
      structure: { id: 's1', code: 'GS-1', name_i18n: {}, structure_type: 'CUSTOM', status: 'DRAFT', gap_policy: 'STRICT_NO_GAPS', version_number: 1 },
      grades: [{ id: 'g1', grade_structure_id: 's1', grade_number: 1, sort_order: 0, name_i18n: {} }],
      bands: [],
    };
    const result = hydrateGradeStructure(envelope);
    expect(result.grades[0].band).toBeNull();
  });

  it('tolerates a legacy flat shape (MSW back-compat)', () => {
    const flat = {
      id: 's1',
      code: 'GS-1',
      name_i18n: { 'ru-RU': 'Старый формат' },
      structure_type: 'CUSTOM',
      status: 'DRAFT',
      gap_policy: 'STRICT_NO_GAPS',
      version_number: 1,
      grades: [
        {
          id: 'g1',
          grade_structure_id: 's1',
          grade_number: 1,
          sort_order: 0,
          name_i18n: {},
          band: { id: 'b1', grade_id: 'g1', min_score: 0, max_score: 50 },
        },
      ],
    };
    const result = hydrateGradeStructure(flat);
    expect(result.id).toBe('s1');
    expect(result.grades[0].band?.min_score).toBe(0);
  });

  it('never crashes when grades/bands are missing', () => {
    const result = hydrateGradeStructure({
      structure: { id: 's1', code: 'X', name_i18n: {}, structure_type: 'CUSTOM', status: 'DRAFT', gap_policy: 'STRICT_NO_GAPS', version_number: 1 },
    });
    expect(result.grades).toEqual([]);
  });
});
