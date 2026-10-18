import { cp, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const target = fileURLToPath(new URL('../src/main/resources/static/vendor/', import.meta.url));
await mkdir(`${target}/bootstrap`, { recursive: true });
await mkdir(`${target}/icons`, { recursive: true });
await cp(
  new URL('./node_modules/bootstrap/dist/css/bootstrap.min.css', import.meta.url),
  `${target}/bootstrap/bootstrap.min.css`,
);
await cp(
  new URL('./node_modules/bootstrap/LICENSE', import.meta.url),
  `${target}/bootstrap/LICENSE`,
);
await cp(
  new URL('./node_modules/bootstrap-icons/font/bootstrap-icons.min.css', import.meta.url),
  `${target}/icons/bootstrap-icons.min.css`,
);
await cp(
  new URL('./node_modules/bootstrap-icons/font/fonts', import.meta.url),
  `${target}/icons/fonts`,
  { recursive: true },
);
await cp(
  new URL('./node_modules/bootstrap-icons/LICENSE', import.meta.url),
  `${target}/icons/LICENSE`,
);
console.log('Bootstrap e Bootstrap Icons copiados para os assets locais.');
