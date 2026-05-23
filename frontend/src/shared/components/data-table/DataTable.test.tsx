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
});
