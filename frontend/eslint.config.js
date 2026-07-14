import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist', 'playwright-report', 'test-results']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      // Honor the project-wide underscore convention for intentionally unused
      // bindings: omitted destructured keys (e.g. `{ levels: _levels, ...rest }`)
      // and unused callback params (e.g. `(_input) => {}`).
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
          ignoreRestSiblings: true,
        },
      ],
    },
  },
  {
    // Architectural import-boundary guardrail: a feature module must not import
    // another feature's route PAGES. Pages are route targets composed ONLY by
    // app/router.tsx; reaching into a sibling feature's `pages/` couples routing
    // and layout across features. Cross-feature reuse must go through `shared/`
    // or the target feature's hooks/components/types/api — never its pages.
    // Zero violations today (verified); this is preventive/enforcing going
    // forward. Scoped to features source only, so app/router.tsx (which composes
    // pages) and same-feature relative imports are unaffected.
    files: ['src/features/**/*.{ts,tsx}'],
    ignores: ['**/*.test.{ts,tsx}', '**/__tests__/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/features/*/pages', '@/features/*/pages/**'],
              message:
                "Do not import another feature's route pages. Pages are composed by app/router.tsx only; share via shared/ or the feature's hooks/components/types/api instead.",
            },
          ],
        },
      ],
    },
  },
  {
    // Playwright E2E specs/fixtures are not React. A fixture's `use()` callback
    // (`async ({ page }, use) => { await use(page) }`) is misread by the
    // react-hooks plugin as the React `use` hook, so disable that rule here;
    // these files also run under node (process.env for the E2E_BASE_URL switch).
    files: ['e2e/**/*.ts'],
    languageOptions: {
      globals: { ...globals.node },
    },
    rules: {
      'react-hooks/rules-of-hooks': 'off',
    },
  },
])
