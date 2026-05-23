import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from './App';

describe('<App />', () => {
  it('renders without crashing and shows the login page when unauthenticated', () => {
    render(<App />);
    // Login page heading
    expect(screen.getByText(/Добро пожаловать|Welcome/i)).toBeInTheDocument();
  });
});
