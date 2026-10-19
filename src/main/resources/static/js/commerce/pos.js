import { requestJson } from '../shared/http.js';
import { credentials, showError } from './commerce.js';

const root = document.querySelector('#pos');
if (root) {
  const cart = new Map();
  const operationId = window.crypto.randomUUID();
  const checkout = document.querySelector('#checkout-form');
  const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  let busy = false;
  let searchVersion = 0;
  const element = (tag, text, className) => {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
  };
  const invalidate = () => {
    ['subtotal', 'discount', 'total', 'change'].forEach((name) => {
      document.querySelector(`#pos-${name}`).textContent = '—';
    });
    document.querySelector('#approval-notice').hidden = true;
  };
  const discount = (type, value) => ({ type, value: value.trim() || '0' });
  const body = () => ({
    operationId,
    sessionId: root.dataset.sessionId,
    customer: document.querySelector('#customer').value,
    items: [...cart.values()].map((item) => ({
      productId: item.id,
      quantity: item.quantity,
      unitPrice: String(item.price),
      productVersion: item.version,
      discount: discount(item.discountType, item.discount),
    })),
    discount: discount(
      document.querySelector('#sale-discount-type').value,
      document.querySelector('#sale-discount').value,
    ),
    payments: [...document.querySelectorAll('[data-payment]')]
      .filter((input) => input.value.trim() && Number(input.value.replace(',', '.')) !== 0)
      .map((input) => ({ method: input.dataset.payment, amount: input.value })),
    discountApproval: credentials(document.querySelector('#discount-approval')),
    stockApproval: credentials(document.querySelector('#stock-approval')),
  });
  const display = (quote) => {
    ['subtotal', 'discount', 'total'].forEach((name) => {
      document.querySelector(`#pos-${name}`).textContent = money.format(quote.totals[name]);
    });
    document.querySelector('#pos-change').textContent = money.format(quote.change);
    const notice = document.querySelector('#approval-notice');
    const messages = [];
    if (quote.discountApprovalRequired) messages.push('O desconto acumulado exige aprovação.');
    if (quote.negativeStockApprovalRequired)
      messages.push('O estoque ficará negativo e exige aprovação.');
    notice.textContent = messages.join(' ');
    notice.hidden = messages.length === 0;
    document.querySelector('#discount-approval details').open = quote.discountApprovalRequired;
    document.querySelector('#stock-approval details').open = quote.negativeStockApprovalRequired;
  };
  function field(container, label, id, value, callback) {
    const caption = element('label', label);
    caption.htmlFor = id;
    const input = element('input', undefined, 'form-control');
    input.id = id;
    input.value = value;
    input.inputMode = 'decimal';
    input.required = true;
    input.addEventListener('input', () => {
      callback(input.value);
      invalidate();
    });
    container.append(caption, input);
    return input;
  }
  function render() {
    const list = document.querySelector('#cart-items');
    list.replaceChildren();
    document.querySelector('#cart-empty').hidden = cart.size > 0;
    for (const item of cart.values()) {
      const card = element('article', undefined, 'vf-cart-item');
      card.append(
        element('h3', item.name),
        element('p', `${item.sku} · ${money.format(item.price)} / ${item.unit}`, 'vf-muted'),
      );
      const quantity = field(
        card,
        item.unit === 'KG' ? 'Peso manual (KG, até 3 casas)' : 'Quantidade (UN, inteira)',
        `qty-${item.id}`,
        item.quantity,
        (value) => {
          item.quantity = value;
        },
      );
      if (item.unit === 'KG') quantity.placeholder = 'Ex.: 0,350';
      const discountLabel = element('label', 'Desconto do item');
      discountLabel.htmlFor = `discount-${item.id}`;
      const row = element('div', undefined, 'vf-discount-row');
      const select = element('select', undefined, 'form-select');
      select.setAttribute('aria-label', `Tipo de desconto de ${item.name}`);
      for (const [value, label] of [
        ['PERCENT', '%'],
        ['AMOUNT', 'R$'],
      ]) {
        const option = element('option', label);
        option.value = value;
        select.append(option);
      }
      select.value = item.discountType;
      select.addEventListener('change', () => {
        item.discountType = select.value;
        invalidate();
      });
      const input = element('input', undefined, 'form-control');
      input.id = `discount-${item.id}`;
      input.value = item.discount;
      input.inputMode = 'decimal';
      input.addEventListener('input', () => {
        item.discount = input.value;
        invalidate();
      });
      row.append(select, input);
      card.append(discountLabel, row);
      const remove = element('button', 'Remover item', 'btn btn-sm btn-outline-secondary');
      remove.type = 'button';
      remove.setAttribute('aria-label', `Remover ${item.name}`);
      remove.addEventListener('click', () => {
        if (busy) return;
        cart.delete(item.id);
        render();
        invalidate();
      });
      card.append(remove);
      list.append(card);
    }
  }
  function add(product) {
    if (busy) return;
    const previous = cart.get(product.id);
    if (previous && product.unit === 'UN')
      previous.quantity = String(Number(previous.quantity.replace(',', '.')) + 1);
    else if (!previous)
      cart.set(product.id, {
        ...product,
        quantity: product.unit === 'KG' ? '' : '1',
        discountType: 'PERCENT',
        discount: '0',
      });
    render();
    invalidate();
    if (product.unit === 'KG') document.querySelector(`#qty-${product.id}`).focus();
    else {
      document.querySelector('#pos-search').value = '';
      document.querySelector('#pos-search').focus();
    }
  }
  document.querySelector('#product-search').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy) return;
    const version = ++searchVersion;
    const query = document.querySelector('#pos-search').value.trim();
    try {
      const products = await requestJson(`/api/pdv/produtos?q=${encodeURIComponent(query)}`);
      if (version !== searchVersion) return;
      const results = document.querySelector('#search-results');
      results.replaceChildren();
      const exact = query ? products.filter((p) => p.barcode === query) : [];
      if (exact.length === 1) add(exact[0]);
      else
        for (const product of products) {
          const button = element(
            'button',
            `${product.name} · ${money.format(product.price)} / ${product.unit}`,
            'vf-search-product',
          );
          button.type = 'button';
          button.addEventListener('click', () => add(product));
          results.append(button);
        }
      if (!products.length)
        results.append(element('p', 'Nenhum produto com preço ativo nesta filial.'));
    } catch (error) {
      showError(error);
    }
  });
  checkout.addEventListener('input', invalidate);
  checkout.addEventListener('change', invalidate);
  document.querySelector('#quote-button').addEventListener('click', async () => {
    if (busy) return;
    try {
      document.querySelector('#commerce-error').hidden = true;
      display(await requestJson('/api/pdv/previsao', { method: 'POST', body: body() }));
    } catch (error) {
      showError(error);
    }
  });
  checkout.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy) return;
    busy = true;
    const payload = body();
    const controls = [...root.querySelectorAll('input,button,select')];
    controls.forEach((control) => {
      control.disabled = true;
    });
    try {
      document.querySelector('#commerce-error').hidden = true;
      const quote = await requestJson('/api/pdv/previsao', { method: 'POST', body: payload });
      display(quote);
      if (
        !window.confirm(
          `Confirmar venda de ${money.format(quote.totals.total)}? Troco em dinheiro: ${money.format(quote.change)}.`,
        )
      )
        return;
      const result = await requestJson('/api/pdv/vendas', { method: 'POST', body: payload });
      window.location.assign(`/vendas/${result.id}/recibo`);
    } catch (error) {
      showError(error);
    } finally {
      checkout.querySelectorAll('input[type="password"]').forEach((input) => {
        input.value = '';
      });
      controls.forEach((control) => {
        control.disabled = false;
      });
      busy = false;
    }
  });
}
