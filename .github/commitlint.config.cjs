// Conventional Commits, scoped to grading.hrlab.uz subagent areas.
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'chore', 'docs', 'refactor', 'test', 'ci', 'build', 'perf', 'revert', 'security'],
    ],
    'scope-enum': [
      1,
      'always',
      [
        'backend',
        'frontend',
        'infra',
        'ci',
        'db',
        'audit',
        'tenancy',
        'access',
        'security',
        'docs',
        'release',
      ],
    ],
    'header-max-length': [2, 'always', 100],
  },
};
