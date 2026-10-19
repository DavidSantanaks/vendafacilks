import { requestJson } from '../shared/http.js';

export function showError(error) {
  const box = document.querySelector('#commerce-error');
  box.textContent = error.message;
  box.hidden = false;
  box.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

export function credentials(root) {
  return {
    email: root.querySelector('[name="approvalEmail"]').value,
    password: root.querySelector('[name="approvalPassword"]').value,
    reason: root.querySelector('[name="approvalReason"]').value,
  };
}

document.querySelectorAll('[data-print]').forEach((button) => {
  button.addEventListener('click', () => window.print());
});

const operations = new WeakMap();
document.querySelectorAll('[data-cash-action]').forEach((form) => {
  operations.set(form, window.crypto.randomUUID());
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const action = form.dataset.cashAction;
    if (
      action === 'count' &&
      !window.confirm(
        'Confirmar esta contagem? Ela ficará registrada e esta sessão deixará de aceitar vendas e movimentos.',
      )
    )
      return;
    const button = form.querySelector('button');
    if (button.disabled) return;
    button.disabled = true;
    document.querySelector('#commerce-error').hidden = true;
    const data = Object.fromEntries(new window.FormData(form));
    const body = { ...data, operationId: operations.get(form) };
    delete body.approvalEmail;
    delete body.approvalPassword;
    delete body.approvalReason;
    delete body._csrf;
    if (form.querySelector('[name="approvalPassword"]')) body.approval = credentials(form);
    const id = document.querySelector('[data-session-id]')?.dataset.sessionId;
    let url;
    switch (action) {
      case 'open':
        url = '/api/caixa/sessoes';
        break;
      case 'register':
        url = '/api/caixa/registros';
        break;
      case 'move':
        url = `/api/caixa/${id}/movimentos`;
        break;
      case 'count':
        url = `/api/caixa/${id}/contagem`;
        break;
      case 'close':
        url = `/api/caixa/${id}/fechamento`;
        break;
      case 'policy':
        url = '/api/caixa/politicas';
        body.blind = form.elements.blind.checked;
        body.withdrawalApproval = form.elements.withdrawalApproval.checked;
        break;
      default:
        button.disabled = false;
        return;
    }
    try {
      const result = await requestJson(url, { method: 'POST', body });
      if (action === 'open') window.location.assign(`/caixa/${result.id}`);
      else window.location.reload();
    } catch (error) {
      showError(error);
      button.disabled = false;
    } finally {
      form.querySelectorAll('input[type="password"]').forEach((input) => {
        input.value = '';
      });
    }
  });
});
