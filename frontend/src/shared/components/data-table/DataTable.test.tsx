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

  it('QA-052: the "№" display-index column stays a contiguous 1..N BEFORE and AFTER sorting (regression lock — old rows.indexOf(row) went stale on re-derived arrays)', async () => {
    const user = userEvent.setup();
    // Deliberately NOT alphabetical, so sorting genuinely reorders the rows.
    const idxRows: Row[] = [
      { id: 'r0', name: 'Charlie' },
      { id: 'r1', name: 'Alice' },
      { id: 'r2', name: 'Echo' },
      { id: 'r3', name: 'Bravo' },
      { id: 'r4', name: 'Delta' },
    ];
    const idxColumns: DataTableColumn<Row>[] = [
      // The regression column under test: a stable running "№" driven by the
      // display `index` DataTable passes to `render`, NOT `rows.indexOf(row)`.
      { key: 'index', header: '№', render: (_r, i) => i + 1 },
      { key: 'name', header: 'Name', render: (r) => r.name, sortable: true, sortAccessor: (r) => r.name },
    ];
    render(renderWithProviders(<DataTable rows={idxRows} columns={idxColumns} rowKey={(r) => r.id} />));

    const readColumns = () => {
      // Row 0 is the header row (thead > tr also has role="row"); body rows follow.
      const bodyRows = screen.getAllByRole('row').slice(1);
      const indexes = bodyRows.map((tr) => Number(tr.querySelectorAll('td')[0].textContent));
      const names = bodyRows.map((tr) => tr.querySelectorAll('td')[1].textContent);
      return { indexes, names };
    };

    // BEFORE sort: contiguous 1..N in original insertion order.
    let { indexes, names } = readColumns();
    expect(indexes).toEqual([1, 2, 3, 4, 5]);
    expect(names).toEqual(['Charlie', 'Alice', 'Echo', 'Bravo', 'Delta']);

    // Click the sortable "Name" header — ascending sort re-derives the row array.
    await user.click(screen.getByText('Name'));

    // AFTER sort: STILL contiguous 1..N, now in the NEW (sorted) order. Before
    // the fix, `rows.indexOf(row)` looked each row up in the ORIGINAL `rows`
    // prop array (whose reference/order never changes), so post-sort it would
    // have produced the row's OLD position (e.g. [2,4,1,5,3] here) instead of a
    // fresh running count — this is exactly the staleness this test locks out.
    ({ indexes, names } = readColumns());
    expect(indexes).toEqual([1, 2, 3, 4, 5]);
    expect(names).toEqual(['Alice', 'Bravo', 'Charlie', 'Delta', 'Echo']);
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
