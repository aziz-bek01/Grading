import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { departmentTypeIcon } from './departmentTypeIcon';
import type { DepartmentType } from '../types/organizationTypes';

const ALL_TYPES: DepartmentType[] = ['BRANCH', 'DEPARTMENT', 'DIVISION', 'UNIT'];

describe('departmentTypeIcon', () => {
  it('has an icon entry for every department type', () => {
    for (const type of ALL_TYPES) {
      expect(departmentTypeIcon[type]).toBeTruthy();
    }
  });

  it('renders each icon without throwing', () => {
    for (const type of ALL_TYPES) {
      const { container, unmount } = render(<>{departmentTypeIcon[type]}</>);
      expect(container.querySelector('svg')).toBeInTheDocument();
      unmount();
    }
  });
});
