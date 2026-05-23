import { BrowserRouter } from 'react-router-dom';
import { QueryProvider } from './providers/QueryProvider';
import { I18nProvider } from './providers/I18nProvider';
import { AuthProvider } from './providers/AuthProvider';
import { ThemeProvider } from './providers/ThemeProvider';
import { AppRouter } from './router';

export function App() {
  return (
    <BrowserRouter>
      <I18nProvider>
        <QueryProvider>
          <AuthProvider>
            <ThemeProvider>
              <AppRouter />
            </ThemeProvider>
          </AuthProvider>
        </QueryProvider>
      </I18nProvider>
    </BrowserRouter>
  );
}
