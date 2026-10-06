'use strict';

/* Shared code of every page: API calls, session, header/nav, toasts, modals, tables, cart. */
const App = (() => {
  // Keys in sessionStorage (per browser tab, cleared when the tab closes)
  const TOKEN_KEY = 'shop.token';
  const USER_KEY = 'shop.user';
  const CART_KEY = 'shop.cart';
  // Number of API calls in flight; the loading bar is visible while it is > 0
  let pending = 0;

  // Side-menu entries; "id" is also the value of body[data-page] on that page
  const PAGES = [
    { id: 'dashboard', label: 'Dashboard', href: '/dashboard.html' },
    { id: 'customers', label: 'Customers', href: '/customers.html' },
    { id: 'products', label: 'Products', href: '/products.html' },
    { id: 'orders', label: 'Orders', href: '/orders.html' },
    { id: 'checkout', label: 'Checkout', href: '/checkout.html' },
  ];

  /* ---------- helpers ---------- */
  // Short querySelector
  const $ = (selector, root = document) => root.querySelector(selector);
  // HTML-escape any value before putting it into markup (prevents injecting HTML through data)
  const esc = (value) => String(value ?? '').replace(/[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  // 1199 -> "$1,199.00" (the format tests expect)
  const money = (value) => '$' + Number(value || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  // ISO timestamp -> "Oct 6, 2026, 2:00 PM"
  const date = (iso) => new Date(iso).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
  // Coloured status pill (ACTIVE, PLACED, ...)
  const statusBadge = (status) => `<span class="status status-${esc(status)}" data-testid="status">${esc(status)}</span>`;

  /* ---------- session ---------- */
  // Bearer token of the signed-in user, or null
  const token = () => sessionStorage.getItem(TOKEN_KEY);
  // Signed-in user's display data, or null (also when the stored JSON is broken)
  const user = () => { try { return JSON.parse(sessionStorage.getItem(USER_KEY)); } catch { return null; } };
  // Admins get write controls; viewers are read-only
  const isAdmin = () => user()?.role === 'ADMIN';

  // Stores what the login response returned
  function saveSession(login) {
    sessionStorage.setItem(TOKEN_KEY, login.token);
    sessionStorage.setItem(USER_KEY, JSON.stringify({ username: login.username, fullName: login.fullName, role: login.role }));
  }

  // Forgets token, user and cart (logout, expired session)
  function clearSession() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(CART_KEY);
  }

  /* ---------- loader ---------- */
  // +1 when a call starts, -1 when it ends; shows the loading bar while anything is pending
  function loading(delta) {
    pending = Math.max(0, pending + delta);
    const bar = $('[data-testid="loader"]');
    if (bar) bar.hidden = pending === 0;
    // Also exposed on <body> for tests that want to know whether the page is busy
    document.body.dataset.busy = pending > 0 ? 'true' : 'false';
  }

  /* ---------- API ---------- */
  // Calls /api + path with JSON and the bearer token; throws an Error carrying status and fieldErrors
  async function api(method, path, body) {
    loading(+1);
    try {
      const headers = { 'Content-Type': 'application/json' };
      if (token()) headers.Authorization = 'Bearer ' + token();
      const res = await fetch('/api' + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
      // Token expired or revoked: back to the login page with a message
      if (res.status === 401 && path !== '/auth/login') {
        clearSession();
        location.href = '/login.html?expired=1';
        throw new Error('Session expired');
      }
      // 204 has no body
      const data = res.status === 204 ? null : await res.json();
      if (!res.ok) {
        // Carry the API's message and per-field errors to the form that made the call
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
  // Every signed-in page calls this first: redirects to login without a token, renders header and menu
  function requireAuth(activePage) {
    if (!token()) {
      location.href = '/login.html';
      throw new Error('Not signed in');
    }
    const u = user();
    // Header: brand, cart badge, current user, logout button
    $('#header').innerHTML = `
      <div class="brand" data-testid="brand">ShopEase Admin</div>
      <a class="cart" href="/checkout.html" data-testid="cart-link">Cart <span class="badge" data-testid="cart-count">0</span></a>
      <span class="user" data-testid="current-user">${esc(u.fullName)} (${esc(u.role)})</span>
      <button class="small" data-testid="logout-button" type="button">Log out</button>`;
    // Side menu with the current page highlighted
    $('#nav').innerHTML = PAGES.map((p) =>
      `<a href="${p.href}" data-testid="nav-${p.id}" class="${p.id === activePage ? 'active' : ''}">${p.label}</a>`).join('');
    // Logout: revoke the token on the server (best effort), then forget it locally
    $('[data-testid="logout-button"]').addEventListener('click', async () => {
      try { await api('POST', '/auth/logout'); } catch { /* signing out locally is enough */ }
      clearSession();
      location.href = '/login.html?loggedOut=1';
    });
    updateCartCount();
    // Tells tests which page is shown (body[data-page])
    document.body.dataset.page = activePage;
    return u;
  }

  /* ---------- toasts ---------- */
  // Short notification at the top right; disappears after 5 s or when closed
  function toast(message, type = 'success') {
    // The container is created on first use
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
    // Screen readers announce errors immediately (alert) and successes politely (status)
    item.setAttribute('role', type === 'error' ? 'alert' : 'status');
    item.innerHTML = `<span data-testid="toast-message">${esc(message)}</span><button class="close" data-testid="toast-close" aria-label="Close">×</button>`;
    item.querySelector('.close').addEventListener('click', () => item.remove());
    box.appendChild(item);
    setTimeout(() => item.remove(), 5000);
  }

  /* ---------- modals ---------- */
  // Removes the open dialog, if any
  function closeModal() {
    $('[data-testid="modal-backdrop"]')?.remove();
  }

  // Opens a dialog with a form; onSubmit(FormData) is awaited, API errors are shown in the dialog
  function modal({ title, bodyHtml, submitLabel, submitClass = 'primary', cancelLabel = 'Cancel', onSubmit }) {
    // Only one dialog at a time
    closeModal();
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';
    backdrop.dataset.testid = 'modal-backdrop';
    // Title, a general error slot, the caller's fields, Cancel and (optionally) a submit button
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
      // No page reload: the form is submitted through the API
      event.preventDefault();
      const submit = $('[data-testid="modal-submit"]', backdrop);
      // Clear the messages of a previous attempt
      backdrop.querySelectorAll('.field-error').forEach((e) => { e.textContent = ''; });
      $('[data-testid="modal-error"]', backdrop).textContent = '';
      // Prevent double submission while the call runs
      submit.disabled = true;
      try {
        await onSubmit(new FormData($('form', backdrop)));
        closeModal();
      } catch (error) {
        // Validation errors go under their fields (error-<field>) ...
        const fields = error.fieldErrors || {};
        Object.entries(fields).forEach(([name, message]) => {
          const slot = $(`[data-testid="error-${name}"]`, backdrop);
          if (slot) slot.textContent = message;
        });
        // ... any other error goes to the general slot at the top
        if (Object.keys(fields).length === 0) $('[data-testid="modal-error"]', backdrop).textContent = error.message;
        submit.disabled = false;
      }
    });
    // Focus the first field, as a user would expect
    backdrop.querySelector('input, select')?.focus();
    return backdrop;
  }

  /* Form field markup: label "for" points at the input, errors have a test id per field. */
  function field({ name, label, type = 'text', value = '', options }) {
    const id = 'field-' + name;
    // A select when options are given, a text input otherwise
    const control = options
      ? `<select id="${id}" name="${name}">${options.map((o) =>
          `<option value="${esc(o.value)}" ${String(o.value) === String(value) ? 'selected' : ''}>${esc(o.label)}</option>`).join('')}</select>`
      : `<input id="${id}" name="${name}" type="${type}" value="${esc(value)}">`;
    return `<div class="field"><label for="${id}">${esc(label)}</label>${control}<div class="field-error" data-testid="error-${name}"></div></div>`;
  }

  /* ---------- tables ---------- */
  // Renders rows, or one "empty" row with a message (tests treat it as "no rows")
  function rows(tbody, items, render, emptyText) {
    tbody.innerHTML = items.length
      ? items.map(render).join('')
      : `<tr class="empty" data-testid="empty-row"><td colspan="20">${esc(emptyText)}</td></tr>`;
  }

  /* ---------- cart (kept in the browser session) ---------- */
  // Cart lines: [{productId, sku, name, price, quantity}]
  const cart = () => { try { return JSON.parse(sessionStorage.getItem(CART_KEY)) || []; } catch { return []; } };
  function saveCart(items) {
    sessionStorage.setItem(CART_KEY, JSON.stringify(items));
    updateCartCount();
  }
  // Adding a product already in the cart increases its quantity
  function addToCart(product) {
    const items = cart();
    const line = items.find((i) => i.productId === product.id);
    if (line) line.quantity += 1;
    else items.push({ productId: product.id, sku: product.sku, name: product.name, price: product.price, quantity: 1 });
    saveCart(items);
  }
  // Header badge = total number of units in the cart
  function updateCartCount() {
    const badge = $('[data-testid="cart-count"]');
    if (badge) badge.textContent = cart().reduce((sum, i) => sum + i.quantity, 0);
  }

  // Public API used by the page scripts
  return { $, esc, money, date, statusBadge, api, saveSession, clearSession, token, user, isAdmin, requireAuth,
    toast, modal, closeModal, field, rows, cart, saveCart, addToCart };
})();
