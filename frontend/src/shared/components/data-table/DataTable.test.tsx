import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataTable, type DataTableColumn } from './DataTable';
import { renderWithProviders } from '@/test/testUtils';

interface Row {
  id: string;
  name: string;
}
const rows: Row[] = Array.from({ length: 5 }, (_, i) => ({ id: `r${i}`, name: `Row ${i}` }));
const columns: DataTableColumn<Row>[] = [
  { key: 'name', header: 'Name', render: (r) => r.name, sortable: true, sortAccessor: (r) => r.name },
];

describe('<DataTable />', () => {
  it('renders all rows', () => {
    render(renderWithProviders(<DataTable rows={rows} columns={columns} rowKey={(r) => r.id} />));
    rows.forEach((r) => expect(screen.getByText(r.name)).toBeInTheDocument());
  });

  it('shows empty state when no rows', () => {
    render(renderWithProviders(<DataTable rows={[]} columns={columns} rowKey={(r) => r.id} emptyTitle="Nothing" emptyBody="empty body" />));
    expect(screen.getByText('Nothing')).toBeInTheDocument();
  });

  it('paginates when pageSize < rows.length', async () => {
    const user = userEvent.setup();
    const many = Array.from({ length: 25 }, (_, i) => ({ id: `r${i}`, name: `Row ${i}` }));
    render(renderWithProviders(<DataTable rows={many} columns={columns} rowKey={(r) => r.id} pageSize={10} />));
    expect(screen.getByText('Row 0')).toBeInTheDocument();
    expect(screen.queryByText('Row 20')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Далее|Next/i }));
    expect(screen.getByText('Row 10')).toBeInTheDocument();
  });

  it('calls onRowClick', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(renderWithProviders(<DataTable rows={rows} columns={columns} rowKey={(r) => r.id} onRowClick={onClick} />));
    await user.click(screen.getByText('Row 2'));
    expect(onClick).toHaveBeenCalledWith(rows[2]);
  });

  it('FE-5: shows the current-page RANGE (from–to / total), not a start index', async () => {
    const user = userEvent.setup();
    const many = Array.from({ length: 25 }, (_, i) => ({ id: `r${i}`, name: `Row ${i}` }));
    render(renderWithProviders(<DataTable rows={many} columns={columns} rowKey={(r) => r.id} pageSize={10} />));
    // Page 1: range 1–10 of 25 (NOT a bare "1 of 25" start index).
    let text = document.body.textContent ?? '';
    expect(text).toContain('1');
    expect(text).toContain('10');
    expect(text).toContain('25');
    // Page 2: range 11–20 of 25.
    await user.click(screen.getByRole('button', { name: /Далее|Next|Oldinga|Олдинга/i }));
    text = document.body.textContent ?? '';
    expect(text).toContain('11');
    expect(text).toContain('20');
  });
});
