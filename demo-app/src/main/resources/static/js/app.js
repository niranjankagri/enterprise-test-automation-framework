'use strict';

/* Shared code of every page: API calls, session, header/nav, toasts, modals, tables, cart. */
const App = (() => {
  const TOKEN_KEY = 'shop.token';
  const USER_KEY = 'shop.user';
  const CART_KEY = 'shop.cart';
  let pending = 0;

  const PAGES = [
    { id: 'dashboard', label: 'Dashboard', href: '/dashboard.html' },
    { id: 'customers', label: 'Customers', href: '/customers.html' },
    { id: 'products', label: 'Products', href: '/products.html' },
    { id: 'orders', label: 'Orders', href: '/orders.html' },
    { id: 'checkout', label: 'Checkout', href: '/checkout.html' },
  ];

  /* ---------- helpers ---------- */
  const $ = (selector, root = document) => root.querySelector(selector);
  const esc = (value) => String(value ?? '').replace(/[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const money = (value) => '$' + Number(value || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const date = (iso) => new Date(iso).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
  const statusBadge = (status) => `<span class="status status-${esc(status)}" data-testid="status">${esc(status)}</span>`;

  /* ---------- session ---------- */
  const token = () => sessionStorage.getItem(TOKEN_KEY);
  const user = () => { try { return JSON.parse(sessionStorage.getItem(USER_KEY)); } catch { return null; } };
  const isAdmin = () => user()?.role === 'ADMIN';

  function saveSession(login) {
    sessionStorage.setItem(TOKEN_KEY, login.token);
    sessionStorage.setItem(USER_KEY, JSON.stringify({ username: login.username, fullName: login.fullName, role: login.role }));
  }

  function clearSession() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(CART_KEY);
  }

  /* ---------- loader ---------- */
  function loading(delta) {
    pending = Math.max(0, pending + delta);
    const bar = $('[data-testid="loader"]');
    if (bar) bar.hidden = pending === 0;
    document.body.dataset.busy = pending > 0 ? 'true' : 'false';
  }

  /* ---------- API ---------- */
  async function api(method, path, body) {
    loading(+1);
    try {
      const headers = { 'Content-Type': 'application/json' };
      if (token()) headers.Authorization = 'Bearer ' + token();
      const res = await fetch('/api' + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
      if (res.status === 401 && path !== '/auth/login') {
        clearSession();
        location.href = '/login.html?expired=1';
        throw new Error('Session expired');
      }
      const data = res.status === 204 ? null : await res.json();
      if (!res.ok) {
        const error = new Error(data?.message || res.statusText);
        error.status = res.status;
        error.fieldErrors = data?.fieldErrors || {};
        throw error;
      }
      return data;
    } finally {
      loading(-1);
    }
  }

  /* ---------- layout ---------- */
  function requireAuth(activePage) {
    if (!token()) {
      location.href = '/login.html';
      throw new Error('Not signed in');
    }
    const u = user();
    $('#header').innerHTML = `
      <div class="brand" data-testid="brand">ShopEase Admin</div>
      <a class="cart" href="/checkout.html" data-testid="cart-link">Cart <span class="badge" data-testid="cart-count">0</span></a>
      <span class="user" data-testid="current-user">${esc(u.fullName)} (${esc(u.role)})</span>
      <button class="small" data-testid="logout-button" type="button">Log out</button>`;
    $('#nav').innerHTML = PAGES.map((p) =>
      `<a href="${p.href}" data-testid="nav-${p.id}" class="${p.id === activePage ? 'active' : ''}">${p.label}</a>`).join('');
    $('[data-testid="logout-button"]').addEventListener('click', async () => {
      try { await api('POST', '/auth/logout'); } catch { /* signing out locally is enough */ }
      clearSession();
      location.href = '/login.html?loggedOut=1';
    });
    updateCartCount();
    document.body.dataset.page = activePage;
    return u;
  }

  /* ---------- toasts ---------- */
  function toast(message, type = 'success') {
    let box = $('#toasts');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toasts';
      box.className = 'toasts';
      document.body.appendChild(box);
    }
    const item = document.createElement('div');
    item.className = 'toast ' + type;
    item.dataset.testid = 'toast';
    item.dataset.type = type;
    item.setAttribute('role', type === 'error' ? 'alert' : 'status');
    item.innerHTML = `<span data-testid="toast-message">${esc(message)}</span><button class="close" data-testid="toast-close" aria-label="Close">×</button>`;
    item.querySelector('.close').addEventListener('click', () => item.remove());
    box.appendChild(item);
    setTimeout(() => item.remove(), 5000);
  }

  /* ---------- modals ---------- */
  function closeModal() {
    $('[data-testid="modal-backdrop"]')?.remove();
  }

  function modal({ title, bodyHtml, submitLabel, submitClass = 'primary', cancelLabel = 'Cancel', onSubmit }) {
    closeModal();
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';
    backdrop.dataset.testid = 'modal-backdrop';
    backdrop.innerHTML = `
      <div class="modal" role="dialog" aria-modal="true" data-testid="modal">
        <header data-testid="modal-title">${esc(title)}</header>
        <form novalidate>
          <div class="body">
            <div class="alert" data-testid="modal-error"></div>
            ${bodyHtml}
          </div>
          <footer>
            <button type="button" data-testid="modal-cancel">${esc(cancelLabel)}</button>
            ${submitLabel ? `<button type="submit" class="${submitClass}" data-testid="modal-submit">${esc(submitLabel)}</button>` : ''}
          </footer>
        </form>
      </div>`;
    document.body.appendChild(backdrop);
    $('[data-testid="modal-cancel"]', backdrop).addEventListener('click', closeModal);
    $('form', backdrop).addEventListener('submit', async (event) => {
      event.preventDefault();
      const submit = $('[data-testid="modal-submit"]', backdrop);
      backdrop.querySelectorAll('.field-error').forEach((e) => { e.textContent = ''; });
      $('[data-testid="modal-error"]', backdrop).textContent = '';
      submit.disabled = true;
      try {
        await onSubmit(new FormData($('form', backdrop)));
        closeModal();
      } catch (error) {
        const fields = error.fieldErrors || {};
        Object.entries(fields).forEach(([name, message]) => {
          const slot = $(`[data-testid="error-${name}"]`, backdrop);
          if (slot) slot.textContent = message;
        });
        if (Object.keys(fields).length === 0) $('[data-testid="modal-error"]', backdrop).textContent = error.message;
        submit.disabled = false;
      }
    });
    backdrop.querySelector('input, select')?.focus();
    return backdrop;
  }

  /* Form field markup: label "for" points at the input, errors have a test id per field. */
  function field({ name, label, type = 'text', value = '', options }) {
    const id = 'field-' + name;
    const control = options
      ? `<select id="${id}" name="${name}">${options.map((o) =>
          `<option value="${esc(o.value)}" ${String(o.value) === String(value) ? 'selected' : ''}>${esc(o.label)}</option>`).join('')}</select>`
      : `<input id="${id}" name="${name}" type="${type}" value="${esc(value)}">`;
    return `<div class="field"><label for="${id}">${esc(label)}</label>${control}<div class="field-error" data-testid="error-${name}"></div></div>`;
  }

  /* ---------- tables ---------- */
  function rows(tbody, items, render, emptyText) {
    tbody.innerHTML = items.length
      ? items.map(render).join('')
      : `<tr class="empty" data-testid="empty-row"><td colspan="20">${esc(emptyText)}</td></tr>`;
  }

  /* ---------- cart (kept in the browser session) ---------- */
  const cart = () => { try { return JSON.parse(sessionStorage.getItem(CART_KEY)) || []; } catch { return []; } };
  function saveCart(items) {
    sessionStorage.setItem(CART_KEY, JSON.stringify(items));
    updateCartCount();
  }
  function addToCart(product) {
    const items = cart();
    const line = items.find((i) => i.productId === product.id);
    if (line) line.quantity += 1;
    else items.push({ productId: product.id, sku: product.sku, name: product.name, price: product.price, quantity: 1 });
    saveCart(items);
  }
  function updateCartCount() {
    const badge = $('[data-testid="cart-count"]');
    if (badge) badge.textContent = cart().reduce((sum, i) => sum + i.quantity, 0);
  }

  return { $, esc, money, date, statusBadge, api, saveSession, clearSession, token, user, isAdmin, requireAuth,
    toast, modal, closeModal, field, rows, cart, saveCart, addToCart };
})();
