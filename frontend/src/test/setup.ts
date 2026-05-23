import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import i18n from '@/shared/i18n';

afterEach(() => {
  cleanup();
});

// Make sure all tests start from a known locale
void i18n.changeLanguage('ru-RU');
