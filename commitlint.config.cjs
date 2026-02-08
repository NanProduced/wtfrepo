module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'subject-case': [0],
    'scope-enum': [
      2,
      'always',
      [
        'frontend',
        'backend',
        'docs',
        'infra',
        'config',
        'build',
        'ci',
        'deps'
      ]
    ]
  }
};
