import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { JobProfileFieldEditor } from './JobProfileFieldEditor';

describe('JobProfileFieldEditor', () => {
  it('renders 4 locale tabs with primary indicator', () => {
    render(
      renderWithProviders(
        <JobProfileFieldEditor
          fieldKey="purpose"
          label="Purpose"
          value={{}}
          onChange={() => {}}
        />,
      ),
    );
    expect(screen.getByTestId('tab-purpose-ru-RU')).toBeInTheDocument();
    expect(screen.getByTestId('tab-purpose-uz-Cyrl-UZ')).toBeInTheDocument();
    expect(screen.getByTestId('tab-purpose-uz-Latn-UZ')).toBeInTheDocument();
    expect(screen.getByTestId('tab-purpose-en-US')).toBeInTheDocument();
    // primary indicator
    expect(screen.getByTestId('tab-purpose-ru-RU').textContent).toContain('*');
  });

  it('calls onChange when typing into the active locale', () => {
    const onChange = vi.fn();
    render(
      renderWithProviders(
        <JobProfileFieldEditor
          fieldKey="purpose"
          label="Purpose"
          value={{}}
          onChange={onChange}
        />,
      ),
    );
    const textarea = screen.getByTestId('textarea-purpose-ru-RU');
    fireEvent.change(textarea, { target: { value: 'Hello' } });
    expect(onChange).toHaveBeenCalledWith({ 'ru-RU': 'Hello' });
  });

  it('renders read-only divs (not textareas) when readOnly', () => {
    render(
      renderWithProviders(
        <JobProfileFieldEditor
          fieldKey="purpose"
          label="Purpose"
          value={{ 'ru-RU': 'Cthulhu', 'en-US': 'Eldritch' }}
          onChange={() => {}}
          readOnly
        />,
      ),
    );
    expect(screen.getByTestId('readonly-purpose-ru-RU')).toBeInTheDocument();
    expect(screen.queryByTestId('textarea-purpose-ru-RU')).not.toBeInTheDocument();
  });
});
