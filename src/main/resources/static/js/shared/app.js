const menuButton = document.querySelector('.vf-menu-toggle');
const menu = document.getElementById('menu-principal');

function closeMenu() {
  menu?.classList.remove('is-open');
  menuButton?.setAttribute('aria-expanded', 'false');
}

menuButton?.addEventListener('click', () => {
  const open = menu.classList.toggle('is-open');
  menuButton.setAttribute('aria-expanded', String(open));
  menuButton.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
});

document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    closeMenu();
    document.querySelectorAll('.vf-help[open]').forEach((help) => {
      help.removeAttribute('open');
      help.querySelector('summary')?.focus();
    });
  }
});

document.addEventListener('click', (event) => {
  if (!menu?.contains(event.target) && !menuButton?.contains(event.target)) closeMenu();
  document.querySelectorAll('.vf-help[open]').forEach((help) => {
    if (!help.contains(event.target)) help.removeAttribute('open');
  });
});

document.querySelectorAll('[data-toggle-password]').forEach((button) => {
  button.addEventListener('click', () => {
    const input = document.getElementById(button.dataset.togglePassword);
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.setAttribute('aria-pressed', String(show));
    button.setAttribute('aria-label', show ? 'Ocultar senha' : 'Mostrar senha');
    button.querySelector('i').className = show ? 'bi bi-eye-slash' : 'bi bi-eye';
  });
});

document.querySelectorAll('[data-search]').forEach((input) => {
  const list = document.getElementById(input.dataset.search);
  const normalize = (text) =>
    text
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
  input.addEventListener('input', () => {
    let visible = 0;
    list.querySelectorAll('[data-search-item]').forEach((item) => {
      item.hidden = !normalize(item.dataset.searchText).includes(normalize(input.value));
      if (!item.hidden) visible++;
    });
    const empty = document.querySelector(`[data-search-empty="${input.dataset.search}"]`);
    if (empty) empty.hidden = visible > 0;
  });
});

document.querySelectorAll('form').forEach((form) => {
  form.addEventListener('submit', (event) => {
    if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) {
      event.preventDefault();
      return;
    }
    const button = form.querySelector('[data-submit]');
    if (button) {
      button.disabled = true;
      button.setAttribute('aria-busy', 'true');
      button.dataset.originalText = button.textContent;
      button.textContent = 'Aguarde…';
    }
  });
});

window.addEventListener('pageshow', () => {
  document.querySelectorAll('[data-original-text]').forEach((button) => {
    button.disabled = false;
    button.removeAttribute('aria-busy');
    button.textContent = button.dataset.originalText;
  });
});

document.querySelectorAll('[data-current-date]').forEach((element) => {
  const now = new Date();
  element.dateTime = now.toISOString();
  element.textContent = new Intl.DateTimeFormat('pt-BR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'America/Sao_Paulo',
  }).format(now);
});
