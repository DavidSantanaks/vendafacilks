export default [
  {
    files: ['src/main/resources/static/js/**/*.js'],
    languageOptions: {
      ecmaVersion: 2025,
      sourceType: 'module',
      globals: {
        document: 'readonly',
        window: 'readonly',
        fetch: 'readonly',
        console: 'readonly',
        Headers: 'readonly',
      },
    },
    rules: {
      'no-unused-vars': 'error',
      'no-undef': 'error',
      'no-var': 'error',
      'prefer-const': 'error',
      eqeqeq: ['error', 'always'],
    },
  },
];
