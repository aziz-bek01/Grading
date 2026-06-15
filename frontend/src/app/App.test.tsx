import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from './App';

describe('<App />', () => {
  // Routed pages are now lazy-loaded (P1-T1), so the login page resolves on a
  // microtask behind the router <Suspense> fallback — assert with the async
  // `findByText` instead of a synchronous `getByText`.
  it('renders without crashing and shows the login page when unauthenticated', async () => {
    render(<App />);
    // Login page heading
    expect(await screen.findByText(/Добро пожаловать|Welcome/i)).toBeInTheDocument();
  });
});
