import { requestJson } from '../shared/http.js';
import { credentials } from './commerce.js';

const root = document.querySelector('#pos');
if (root) {
  const $ = (selector) => root.querySelector(selector);
  const cart = new Map();
  const payments = new Map();
  const operationId = window.crypto.randomUUID();
  const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  const methodNames = { CASH: 'Dinheiro', PIX: 'Pix', DEBIT: 'Débito', CREDIT: 'Crédito' };
  const searchInput = $('#pos-search');
  const amountInput = $('#payment-amount');
  const quantityDialog = $('#quantity-dialog');
  const discountDialog = $('#discount-dialog');
  const approvalDialog = $('#approval-dialog');
  let globalDiscount = { type: 'PERCENT', value: '0' };
  let selectedMethod = 'CASH';
  let amountAutomatic = true;
  let quote = null;
  let quoteVersion = 0;
  let quoteTimer;
  let searchVersion = 0;
  let searchTimer;
  let weightProduct = null;
  let discountProduct = null;
  let approvalPayload = null;
  let busy = false;
  let pendingScans = 0;
  let scanQueue = Promise.resolve();
  let quoteError = '';
  let searchError = '';

  const node = (tag, text, className) => {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
  };
  const icon = (name) => {
    const element = node('i', undefined, 'bi bi-' + name);
    element.setAttribute('aria-hidden', 'true');
    return element;
  };
  const feedback = (selector, message = '') => {
    const box = $(selector);
    box.textContent = message;
    box.hidden = !message;
  };
  const cents = (text) => {
    const value = String(text).trim();
    if (!/^\d{1,9}(?:[.,]\d{0,2})?$/.test(value)) return null;
    const [whole, decimal = ''] = value.replace(',', '.').split('.');
    return Number(whole) * 100 + Number(decimal.padEnd(2, '0'));
  };
  const decimal = (value) => (value / 100).toFixed(2).replace('.', ',');
  const valueCents = (value) => Math.round(Number(value) * 100);
  const totalCents = () => (quote ? valueCents(quote.totals.total) : 0);
  const quantityValue = (text, unit) => {
    const pattern = unit === 'UN' ? /^\d{1,6}$/ : /^\d{1,6}(?:[.,]\d{0,3})?$/;
    if (!pattern.test(text.trim())) return null;
    const value = Number(text.replace(',', '.'));
    return value > 0 && value <= 999999.999 ? value : null;
  };
  const payload = (withPayments = true) => {
    const combined = new Map(payments);
    const pending = cents(amountInput.value);
    if (withPayments && pending > 0)
      combined.set(selectedMethod, (combined.get(selectedMethod) || 0) + pending);
    return {
      operationId,
      sessionId: root.dataset.sessionId,
      customer: $('#customer').value,
      items: [...cart.values()].map((item) => ({
        productId: item.id,
        quantity: item.quantity,
        unitPrice: String(item.price),
        productVersion: item.version,
        discount: { type: item.discountType, value: item.discount || '0' },
      })),
      discount: globalDiscount,
      payments: withPayments
        ? [...combined].map(([method, amount]) => ({ method, amount: (amount / 100).toFixed(2) }))
        : [],
    };
  };
  const paymentValues = () => {
    const values = new Map(payments);
    const pending = cents(amountInput.value);
    if (pending > 0) values.set(selectedMethod, (values.get(selectedMethod) || 0) + pending);
    const cash = values.get('CASH') || 0;
    const digital = [...values]
      .filter(([method]) => method !== 'CASH')
      .reduce((sum, [, value]) => sum + value, 0);
    return { cash, digital, paid: cash + digital, pending };
  };
  function fillRemaining() {
    const committed = [...payments.values()].reduce((sum, value) => sum + value, 0);
    amountInput.value = quote && totalCents() > committed ? decimal(totalCents() - committed) : '';
  }
  function updatePayment() {
    if (amountAutomatic) fillRemaining();
    const values = paymentValues();
    const remaining = Math.max(0, totalCents() - values.paid);
    const change = Math.max(0, values.paid - totalCents());
    let message = quoteError || searchError;
    if (!message && quote && amountInput.value.trim() && values.pending === null)
      message = 'Informe um valor válido, com até duas casas decimais.';
    if (!message && quote && values.digital > totalCents())
      message =
        'Pagamentos digitais ultrapassam o total. Ajuste o valor; troco somente em dinheiro.';
    $('#pos-paid').textContent = money.format(values.paid / 100);
    $('#pos-remaining').textContent = money.format(remaining / 100);
    $('#pos-remaining-row').hidden = !quote || remaining === 0;
    $('#pos-change').textContent = money.format(
      quote && values.digital <= totalCents() ? change / 100 : 0,
    );
    $('#finish-sale').disabled =
      busy || pendingScans > 0 || !quote || !!message || remaining > 0 || values.paid <= 0;
    $('#add-payment').disabled = busy || !quote || !(values.pending > 0) || !!message;
    $('#exact-payment').disabled = busy || !quote;
    $('#clear-sale').disabled = busy || pendingScans > 0 || cart.size === 0;
    feedback('#pos-feedback', message);
  }
  function renderPayments() {
    const list = $('#payment-list');
    list.replaceChildren();
    list.hidden = payments.size === 0;
    for (const [method, amount] of payments) {
      const item = node('li');
      item.append(
        icon(method === 'CASH' ? 'cash' : method === 'PIX' ? 'qr-code' : 'credit-card'),
        node('span', methodNames[method]),
        node('strong', money.format(amount / 100)),
      );
      const remove = node('button');
      remove.type = 'button';
      remove.setAttribute('aria-label', 'Remover pagamento em ' + methodNames[method]);
      remove.append(icon('x-lg'));
      remove.addEventListener('click', () => {
        if (busy) return;
        payments.delete(method);
        amountAutomatic = true;
        renderPayments();
        updatePayment();
      });
      item.append(remove);
      list.append(item);
    }
  }
  function displayQuote(value) {
    quote = value;
    quoteError = '';
    root.querySelectorAll('[data-pos-total]').forEach((element) => {
      element.textContent = money.format(value.totals.total);
    });
    $('#pos-subtotal').textContent = money.format(value.totals.subtotal);
    $('#pos-discount').textContent = money.format(value.totals.discount);
    for (const item of value.items) {
      const row = document.getElementById('item-' + item.productId);
      if (row) row.querySelector('[data-line-total]').textContent = money.format(item.total);
    }
    const messages = [];
    if (value.discountApprovalRequired)
      messages.push('O desconto precisa de aprovação ao finalizar.');
    if (value.negativeStockApprovalRequired)
      messages.push('O estoque negativo precisa de aprovação.');
    $('#approval-notice').textContent = messages.join(' ');
    $('#approval-notice').hidden = !messages.length;
    $('#cart-update-status').textContent = 'Valores atualizados';
    updatePayment();
  }
  function scheduleQuote(delay = 280) {
    window.clearTimeout(quoteTimer);
    const version = ++quoteVersion;
    quote = null;
    quoteError = '';
    $('#approval-notice').hidden = true;
    root.querySelectorAll('[data-pos-total]').forEach((element) => {
      element.textContent = cart.size ? '—' : money.format(0);
    });
    root.querySelectorAll('[data-line-total]').forEach((element) => {
      element.textContent = '—';
    });
    if (!cart.size) {
      $('#pos-subtotal').textContent = money.format(0);
      $('#pos-discount').textContent = money.format(0);
      $('#cart-update-status').textContent = 'Aguardando produtos';
      amountInput.value = '';
      payments.clear();
      renderPayments();
      updatePayment();
      return;
    }
    const invalid = [...cart.values()].filter(
      (item) => quantityValue(item.quantity, item.unit) === null,
    );
    for (const item of cart.values())
      document
        .getElementById('qty-' + item.id)
        ?.setAttribute('aria-invalid', String(invalid.includes(item)));
    if (invalid.length) {
      quoteError = 'Confira a quantidade: UN inteira e peso KG com até três casas.';
      $('#cart-update-status').textContent = 'Confira as quantidades';
      updatePayment();
      return;
    }
    $('#cart-update-status').textContent = 'Atualizando valores…';
    updatePayment();
    quoteTimer = window.setTimeout(async () => {
      try {
        const result = await requestJson('/api/pdv/previsao', {
          method: 'POST',
          body: payload(false),
        });
        if (version === quoteVersion) displayQuote(result);
      } catch (error) {
        if (version !== quoteVersion) return;
        quoteError = error.message;
        $('#cart-update-status').textContent = 'Confira a venda';
        updatePayment();
      }
    }, delay);
  }
  function openDiscount(product = null) {
    if (busy || !cart.size) return;
    discountProduct = product;
    $('#discount-title').textContent = product ? 'Desconto no item' : 'Desconto na venda';
    $('#discount-product').textContent = product
      ? product.name
      : 'Aplicado sobre os itens desta venda.';
    $('#discount-type').value = product ? product.discountType : globalDiscount.type;
    $('#discount-value').value = product ? product.discount : globalDiscount.value;
    feedback('#discount-error');
    discountDialog.showModal();
    $('#discount-value').focus();
    $('#discount-value').select();
  }
  function renderCart() {
    $('#cart-count').textContent = String(cart.size);
    $('#cart-empty').hidden = cart.size > 0;
    $('#cart-table-wrap').hidden = cart.size === 0;
    $('#cart-items').replaceChildren();
    let index = 0;
    for (const item of cart.values()) {
      const row = node('tr');
      row.id = 'item-' + item.id;
      row.append(node('td', String(++index).padStart(2, '0')));
      const product = node('td');
      product.append(
        node('strong', item.name, 'vf-pos-product-name'),
        node('small', item.sku + ' · ' + item.unit, 'vf-pos-product-meta'),
      );
      const itemDiscount = node('button', undefined, 'vf-pos-text-button vf-pos-item-discount');
      itemDiscount.type = 'button';
      itemDiscount.setAttribute('aria-label', 'Desconto de ' + item.name);
      itemDiscount.append(
        icon('tag'),
        node(
          'span',
          Number(item.discount.replace(',', '.')) > 0
            ? (item.discountType === 'PERCENT'
                ? item.discount + '%'
                : money.format(Number(item.discount.replace(',', '.')))) + ' de desconto'
            : 'Desconto',
        ),
      );
      itemDiscount.addEventListener('click', () => openDiscount(item));
      product.append(itemDiscount);
      row.append(product);
      const quantityCell = node('td');
      const quantityBox = node('div', undefined, 'vf-pos-quantity');
      const input = node('input');
      input.id = 'qty-' + item.id;
      input.value = item.quantity;
      input.inputMode = item.unit === 'KG' ? 'decimal' : 'numeric';
      input.setAttribute(
        'aria-label',
        (item.unit === 'KG' ? 'Peso em KG de ' : 'Quantidade de ') + item.name,
      );
      input.addEventListener('input', () => {
        item.quantity = input.value;
        scheduleQuote();
      });
      for (const step of [-1, 1]) {
        const button = node('button', step < 0 ? '−' : '+');
        button.type = 'button';
        button.setAttribute(
          'aria-label',
          (step < 0 ? 'Diminuir quantidade de ' : 'Aumentar quantidade de ') + item.name,
        );
        button.addEventListener('click', () => {
          if (busy) return;
          const current = quantityValue(item.quantity, item.unit) || 0;
          const minimum = item.unit === 'KG' ? 0.001 : 1;
          const value = Math.max(minimum, Math.round((current + step) * 1000) / 1000);
          item.quantity = String(value).replace('.', ',');
          input.value = item.quantity;
          scheduleQuote();
        });
        if (step < 0) quantityBox.append(button, input);
        else quantityBox.append(button);
      }
      quantityCell.append(quantityBox, node('span', item.unit, 'vf-pos-quantity-unit'));
      row.append(quantityCell);
      row.append(node('td', money.format(item.price), 'vf-pos-unit-price'));
      const line = node('td');
      const total = node('strong', '—', 'vf-pos-line-total');
      total.dataset.lineTotal = '';
      line.append(total);
      row.append(line);
      const actions = node('td');
      const remove = node('button', undefined, 'vf-pos-remove');
      remove.type = 'button';
      remove.append(icon('x-lg'));
      remove.setAttribute('aria-label', 'Remover ' + item.name);
      remove.addEventListener('click', () => {
        if (busy) return;
        cart.delete(item.id);
        renderCart();
        scheduleQuote();
      });
      actions.append(remove);
      row.append(actions);
      $('#cart-items').append(row);
    }
  }
  function clearSearch(preserveInput = false) {
    searchError = '';
    searchVersion++;
    window.clearTimeout(searchTimer);
    if (!preserveInput) searchInput.value = '';
    $('#search-results').hidden = true;
    searchInput.setAttribute('aria-expanded', 'false');
  }
  function add(product, quantity = '1', preserveInput = false) {
    if (busy) return;
    const previous = cart.get(product.id);
    if (previous && (previous.version !== product.version || previous.price !== product.price)) {
      quoteError = 'Este produto mudou. Remova o item da lista e adicione novamente.';
      updatePayment();
      return;
    }
    if (previous) {
      const current = quantityValue(previous.quantity, previous.unit) || 0;
      previous.quantity = String(
        Math.round((current + Number(quantity.replace(',', '.'))) * 1000) / 1000,
      ).replace('.', ',');
    } else cart.set(product.id, { ...product, quantity, discountType: 'PERCENT', discount: '0' });
    clearSearch(preserveInput);
    renderCart();
    scheduleQuote(0);
    searchInput.focus();
  }
  function selectProduct(product, preserveInput = false) {
    if (busy) return;
    if (product.unit !== 'KG') {
      add(product, '1', preserveInput);
      return;
    }
    weightProduct = product;
    $('#quantity-product').textContent =
      product.name + ' · ' + money.format(product.price) + ' / KG';
    $('#weight-input').value = '';
    feedback('#weight-error');
    clearSearch(preserveInput);
    quantityDialog.showModal();
    $('#weight-input').focus();
  }
  async function search(query, addExact, version) {
    $('#search-status').textContent = 'Buscando produtos';
    try {
      const products = await requestJson('/api/pdv/produtos?q=' + encodeURIComponent(query));
      if ((!addExact && version !== searchVersion) || busy) return;
      searchError = '';
      updatePayment();
      const exact = products.filter(
        (item) => item.barcode === query || item.sku.toLowerCase() === query.toLowerCase(),
      );
      if (addExact && (exact.length === 1 || products.length === 1)) {
        selectProduct(exact[0] || products[0], true);
        $('#search-status').textContent = 'Produto encontrado';
        return;
      }
      const results = $('#search-results');
      results.replaceChildren();
      results.hidden = false;
      searchInput.setAttribute('aria-expanded', 'true');
      for (const product of products) {
        const button = node('button', undefined, 'vf-pos-search-product');
        button.type = 'button';
        const description = node('span');
        description.append(node('strong', product.name), node('small', product.sku));
        button.append(
          node('span', product.unit),
          description,
          node('span', money.format(product.price), 'vf-pos-search-price'),
          icon('plus-lg'),
        );
        button.addEventListener('click', () => selectProduct(product));
        results.append(button);
      }
      if (!products.length)
        results.append(node('p', 'Nenhum produto com preço ativo nesta filial.'));
      $('#search-status').textContent = products.length + ' produtos encontrados';
    } catch (error) {
      if (addExact || version === searchVersion) {
        searchError = error.message;
        updatePayment();
      }
    }
  }
  searchInput.addEventListener('input', () => {
    searchError = '';
    updatePayment();
    const version = ++searchVersion;
    window.clearTimeout(searchTimer);
    const query = searchInput.value.trim();
    $('#search-results').hidden = true;
    searchInput.setAttribute('aria-expanded', 'false');
    if (query.length < 2) return;
    searchTimer = window.setTimeout(() => search(query, false, version), 220);
  });
  $('#product-search').addEventListener('submit', (event) => {
    event.preventDefault();
    if (busy) return;
    window.clearTimeout(searchTimer);
    const query = searchInput.value.trim();
    if (!query) return;
    const version = ++searchVersion;
    searchInput.value = '';
    $('#search-results').hidden = true;
    searchInput.setAttribute('aria-expanded', 'false');
    pendingScans++;
    updatePayment();
    scanQueue = scanQueue.then(async () => {
      if (quantityDialog.open)
        await new Promise((resolve) =>
          quantityDialog.addEventListener('close', resolve, { once: true }),
        );
      try {
        await search(query, true, version);
      } finally {
        pendingScans--;
        updatePayment();
      }
    });
  });
  searchInput.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowDown' && !$('#search-results').hidden) {
      event.preventDefault();
      $('#search-results button')?.focus();
    }
  });
  $('#search-results').addEventListener('keydown', (event) => {
    const options = [...root.querySelectorAll('.vf-pos-search-product')];
    const index = options.indexOf(document.activeElement);
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      options[
        (index + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length
      ]?.focus();
    }
  });
  document.addEventListener('click', (event) => {
    if (!$('#pos-search-area').contains(event.target)) {
      $('#search-results').hidden = true;
      searchInput.setAttribute('aria-expanded', 'false');
    }
  });
  $('#quantity-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const value = $('#weight-input').value.trim();
    if (quantityValue(value, 'KG') === null) {
      feedback('#weight-error', 'Informe um peso maior que zero, com até três casas decimais.');
      return;
    }
    quantityDialog.close();
    add(weightProduct, value);
    weightProduct = null;
  });
  $('#sale-discount-button').addEventListener('click', () => openDiscount());
  $('#discount-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const value = $('#discount-value').value.trim();
    const type = $('#discount-type').value;
    const amount = cents(value);
    if (amount === null || (type === 'PERCENT' && amount >= 10000)) {
      feedback('#discount-error', 'Informe um desconto válido, menor que 100%.');
      return;
    }
    if (discountProduct) {
      discountProduct.discountType = type;
      discountProduct.discount = value;
    } else globalDiscount = { type, value };
    $('#global-discount-label').textContent =
      Number(globalDiscount.value.replace(',', '.')) > 0
        ? '(venda: ' +
          (globalDiscount.type === 'PERCENT'
            ? globalDiscount.value + '%'
            : money.format(Number(globalDiscount.value.replace(',', '.')))) +
          ')'
        : '';
    discountDialog.close();
    renderCart();
    scheduleQuote(0);
  });
  $('#clear-sale').addEventListener('click', () => {
    if (busy || !window.confirm('Limpar os itens e pagamentos desta venda?')) return;
    cart.clear();
    payments.clear();
    globalDiscount = { type: 'PERCENT', value: '0' };
    $('#global-discount-label').textContent = '';
    $('#customer').value = '';
    amountAutomatic = true;
    renderCart();
    renderPayments();
    scheduleQuote();
    searchInput.focus();
  });
  root.querySelectorAll('[data-method]').forEach((button) =>
    button.addEventListener('click', () => {
      if (busy) return;
      selectedMethod = button.dataset.method;
      amountAutomatic = true;
      root
        .querySelectorAll('[data-method]')
        .forEach((item) => item.setAttribute('aria-pressed', String(item === button)));
      $('#payment-amount-label').textContent =
        selectedMethod === 'CASH' ? 'Dinheiro recebido' : 'Valor em ' + methodNames[selectedMethod];
      $('#payment-hint').textContent =
        selectedMethod === 'CASH'
          ? 'Use + para dividir entre formas de pagamento.'
          : 'Confirme o recebimento antes de finalizar. Use + para dividir.';
      updatePayment();
      amountInput.focus();
      amountInput.select();
    }),
  );
  amountInput.addEventListener('input', () => {
    amountAutomatic = false;
    updatePayment();
  });
  $('#exact-payment').addEventListener('click', () => {
    amountAutomatic = true;
    updatePayment();
    amountInput.focus();
    amountInput.select();
  });
  $('#add-payment').addEventListener('click', () => {
    if (busy || $('#add-payment').disabled) return;
    const value = cents(amountInput.value);
    if (!(value > 0)) return;
    payments.set(selectedMethod, (payments.get(selectedMethod) || 0) + value);
    amountAutomatic = true;
    renderPayments();
    updatePayment();
  });
  $('#mobile-payment-button').addEventListener('click', () => {
    $('#payment-panel').scrollIntoView({ behavior: 'smooth', block: 'start' });
    amountInput.focus({ preventScroll: true });
  });
  root.querySelectorAll('[data-close-dialog]').forEach((button) =>
    button.addEventListener('click', () => {
      if (!busy) button.closest('dialog').close();
    }),
  );
  approvalDialog.addEventListener('close', () => {
    approvalPayload = null;
    approvalDialog.querySelectorAll('input[type="password"]').forEach((input) => {
      input.value = '';
    });
  });
  root.querySelectorAll('dialog').forEach((dialog) => {
    dialog.addEventListener('cancel', (event) => {
      if (busy) event.preventDefault();
    });
  });
  function setBusy(value) {
    busy = value;
    root.querySelectorAll('input,button,select').forEach((element) => {
      element.disabled = value;
    });
    $('#finish-sale span').textContent = value ? 'Processando…' : 'Finalizar venda';
    $('#finish-sale').setAttribute('aria-busy', String(value));
    if (!value) {
      approvalDialog.querySelectorAll('section[hidden] input').forEach((input) => {
        input.disabled = true;
      });
      updatePayment();
    }
  }
  async function sendSale(value) {
    const result = await requestJson('/api/pdv/vendas', { method: 'POST', body: value });
    window.location.assign('/vendas/' + result.id + '/recibo');
  }
  function showApproval(value, finalQuote) {
    approvalPayload = value;
    feedback('#approval-error');
    $('#approval-total').textContent = money.format(finalQuote.totals.total);
    for (const [id, required] of [
      ['discount-approval', finalQuote.discountApprovalRequired],
      ['stock-approval', finalQuote.negativeStockApprovalRequired],
    ]) {
      const section = $('#' + id);
      section.hidden = !required;
      section.querySelectorAll('input').forEach((input) => {
        input.required = required;
        input.disabled = !required;
      });
    }
    approvalDialog.showModal();
  }
  $('#checkout-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy || $('#finish-sale').disabled) return;
    const value = payload();
    let failure = '';
    setBusy(true);
    feedback('#pos-feedback');
    try {
      const finalQuote = await requestJson('/api/pdv/previsao', { method: 'POST', body: value });
      if (finalQuote.discountApprovalRequired || finalQuote.negativeStockApprovalRequired) {
        setBusy(false);
        showApproval(value, finalQuote);
        return;
      }
      await sendSale(value);
    } catch (error) {
      failure = error.message;
    } finally {
      setBusy(false);
      if (failure) feedback('#pos-feedback', failure);
    }
  });
  $('#approval-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy || !approvalPayload) return;
    const value = { ...approvalPayload };
    if (!$('#discount-approval').hidden)
      value.discountApproval = credentials($('#discount-approval'));
    if (!$('#stock-approval').hidden) value.stockApproval = credentials($('#stock-approval'));
    setBusy(true);
    feedback('#approval-error');
    try {
      await sendSale(value);
    } catch (error) {
      feedback('#approval-error', error.message);
    } finally {
      approvalDialog.querySelectorAll('input[type="password"]').forEach((input) => {
        input.value = '';
      });
      setBusy(false);
    }
  });
  document.addEventListener('keydown', (event) => {
    if (busy || event.altKey || event.ctrlKey || event.metaKey) return;
    if (event.key === 'Escape') {
      $('#search-results').hidden = true;
      searchInput.setAttribute('aria-expanded', 'false');
    }
    if (root.querySelector('dialog[open]')) return;
    if (event.key === 'F2') {
      event.preventDefault();
      searchInput.focus();
      searchInput.select();
    }
    if (event.key === 'F4') {
      event.preventDefault();
      openDiscount();
    }
    if (event.key === 'F9') {
      event.preventDefault();
      if (!$('#finish-sale').disabled) $('#checkout-form').requestSubmit();
    }
  });
  updatePayment();
}
