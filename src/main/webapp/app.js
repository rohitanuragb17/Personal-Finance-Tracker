const $ = selector => document.querySelector(selector);
const money = value => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2
}).format(value);
const categoryNames = {
  Housing: 'Housing & Rent',
  Food: 'Provisions & Dining',
  Transport: 'Transit & Utilities',
  Entertainment: 'Sundry & Leisure'
};
const chartColors = ['#c2652a', '#ad826c', '#967269', '#d09a70', '#ab9d83', '#857667', '#d7b8a4'];
const pageSize = 5;
const moneyAnimations = {};
let selectedMonth = localMonthString();
let dashboardData = null;
let currentPage = 1;
let dashboardRequestId = 0;
let budgetEditing = false;
let savedBudget = 0;
let apiPending = false;

function localMonthString() {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;
}

function dateForMonth(month) {
  const today = new Date();
  return month === localMonthString()
    ? `${month}-${String(today.getDate()).padStart(2, '0')}`
    : `${month}-01`;
}

function monthLabel(month) {
  const [year, number] = month.split('-').map(Number);
  return new Date(year, number - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
}

function shiftMonth(month, offset) {
  const [year, number] = month.split('-').map(Number);
  const absolute = year * 12 + number - 1 + offset;
  return `${Math.floor(absolute / 12)}-${String(absolute % 12 + 1).padStart(2, '0')}`;
}

function readableDate(value) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString('en-IN', {
    day: 'numeric', month: 'long', year: 'numeric'
  });
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
  })[character]);
}

function showMessage(selector, message, kind = 'error') {
  const element = $(selector);
  element.textContent = message;
  element.className = `form-message ${kind}`;
}

function animateMoney(id, nextValue) {
  const element = $(`#${id}`);
  if (!element) return;
  if (moneyAnimations[id]) cancelAnimationFrame(moneyAnimations[id]);
  const start = Number(element.dataset.currentValue ?? nextValue);
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    element.textContent = money(nextValue);
    element.dataset.currentValue = nextValue;
    return;
  }
  const started = performance.now();
  function frame(now) {
    const progress = Math.min((now - started) / 550, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    const value = start + (nextValue - start) * eased;
    element.textContent = money(value);
    element.dataset.currentValue = value;
    moneyAnimations[id] = progress < 1 ? requestAnimationFrame(frame) : null;
  }
  moneyAnimations[id] = requestAnimationFrame(frame);
}

function setBudgetEditing(editing, discard = false) {
  budgetEditing = editing;
  $('#budget-input').disabled = !editing;
  $('#budget-save').hidden = !editing;
  if (discard) $('#budget-input').value = savedBudget;
  if (editing) $('#budget-input').focus();
}

function renderDashboard(data) {
  dashboardData = data;
  const month = data.month || selectedMonth;
  const budget = Number(data.profile.monthlyBudget);
  const spent = Number(data.expenses);
  const hasBudget = budget > 0;
  const percentage = hasBudget ? Math.round(spent / budget * 100) : 0;

  $('#profile-name').textContent = data.profile.name;
  $('#selected-month-label').textContent = monthLabel(month);
  $('#month-headline').textContent = monthLabel(month).split(' ')[0];
  animateMoney('income', data.income);
  animateMoney('expenses', spent);
  animateMoney('balance', data.balance);
  animateMoney('budget-spent', spent);
  animateMoney('budget-limit', budget);
  animateMoney('allocation-cap', budget);
  animateMoney('flow-ceiling', budget);
  $('#budget-remaining').textContent = hasBudget ? money(budget - spent) : '—';
  $('#budget-percent').hidden = !hasBudget;
  $('#budget-percent').textContent = hasBudget ? `${percentage}% USED` : '';
  $('#budget-progress').style.setProperty('--progress-width', `${Math.min(percentage, 100)}%`);
  $('#budget-message').textContent = !hasBudget
    ? 'No ceiling set for this month.'
    : spent > budget
      ? `${money(spent - budget)} above your ceiling.`
      : `${Math.floor((budget - spent) / budget * 100)}% of your ceiling remains available.`;
  savedBudget = budget;
  $('#budget-input').value = budget;
  setBudgetEditing(false);
  showMessage('#budget-feedback', '', 'success');
  $('#date').value = dateForMonth(month);
  renderSpending(data);
  renderTransactions();
}

function renderSpending(data) {
  const categories = Object.entries(data.spendingByCategory).sort((a, b) => b[1] - a[1]);
  const total = categories.reduce((sum, [, amount]) => sum + Number(amount), 0);
  const budget = Number(data.profile.monthlyBudget);
  const remaining = budget > 0 ? Math.max(budget - total, 0) : 0;

  const rows = categories.map(([category, amount], index) => {
    const share = total ? Number(amount) / total * 100 : 0;
    const ceilingShare = budget ? `${(Number(amount) / budget * 100).toFixed(1)}% of ceiling` : 'No ceiling set';
    return `<div class="legend-item">
      <div class="legend-head"><i class="legend-dot" style="background:${chartColors[index % chartColors.length]}"></i><span class="legend-name">${escapeHtml(categoryNames[category] || category)}</span><span class="dotted-leader"></span></div>
      <span class="legend-value">${money(amount)}</span>
      <div class="legend-note"><span>${share.toFixed(1)}% of outlays</span><strong>${ceilingShare}</strong></div>
    </div>`;
  });
  if (budget > 0) rows.push(`<div class="legend-item not-spent-item">
    <div class="legend-head"><i class="legend-dot" style="background:#c8c0b4"></i><span class="legend-name">Not spent</span><span class="dotted-leader"></span></div>
    <span class="legend-value">${money(remaining)}</span>
    <div class="legend-note"><span>Still available</span><strong>${(remaining / budget * 100).toFixed(1)}% of ceiling</strong></div>
  </div>`);
  $('#category-legend').innerHTML = rows.join('') || '<p class="allocation-empty">No outlays recorded this month.</p>';


}

function renderTransactions(printAll = false) {
  if (!dashboardData) return;
  const search = $('#transaction-search').value.trim().toLowerCase();
  const type = $('#transaction-type-filter').value;
  const category = $('#transaction-category-filter').value;
  const matches = dashboardData.transactions.filter(item =>
    (!search || `${item.description} ${item.category} ${categoryNames[item.category] || ''} ${item.date}`.toLowerCase().includes(search)) &&
    (type === 'ALL' || item.type === type) &&
    (category === 'ALL' || item.category === category)
  );
  const pages = Math.max(1, Math.ceil(matches.length / pageSize));
  currentPage = Math.min(currentPage, pages);
  $('#transaction-count').textContent = `${matches.length} ${matches.length === 1 ? 'ENTRY' : 'ENTRIES'} LOGGED`;
  const visible = printAll ? matches : matches.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  $('#transactions').innerHTML = visible.map(item => `
    <div class="transaction">
      <div class="transaction-main">
        <div class="transaction-heading"><span class="transaction-title">${escapeHtml(item.description)}</span><span class="transaction-type">${escapeHtml(categoryNames[item.category] || item.category)} · ${item.type === 'INCOME' ? 'Inflow' : 'Outflow'}</span></div>
        <div class="transaction-meta">${readableDate(item.date)}</div>
      </div>
      <div class="transaction-right">
        <span class="transaction-amount ${item.type === 'INCOME' ? 'income-amount' : ''}">${item.type === 'INCOME' ? '+' : '-'}${money(item.amount)}</span>
        <div class="transaction-actions">
          <button class="transaction-action edit-transaction" data-id="${item.id}" type="button" aria-label="Edit ${escapeHtml(item.description)}">Edit</button>
          <button class="transaction-action delete-transaction" data-id="${item.id}" type="button" aria-label="Delete ${escapeHtml(item.description)}">Delete</button>
        </div>
      </div>
    </div>`).join('') || '<p class="transaction-empty">No matching entries for this month.</p>';
  $('#pagination').innerHTML = pages > 1
    ? `<button type="button" data-page="prev" ${currentPage === 1 ? 'disabled' : ''} aria-label="Previous page">←</button><span>Page ${currentPage} of ${pages}</span><button type="button" data-page="next" ${currentPage === pages ? 'disabled' : ''} aria-label="Next page">→</button>`
    : '';
}

async function readResponse(response) {
  const body = await response.text();
  try { return body ? JSON.parse(body) : {}; }
  catch { return { error: `Unexpected server response (HTTP ${response.status}). Check the server log.` }; }
}

async function requestJson(url, options) {
  const isDashboardRequest = url.startsWith('api');
  if (isDashboardRequest && apiPending) throw new Error('Please wait for the current request to finish.');
  if (isDashboardRequest) {
    apiPending = true;
    $('#dashboard-content').inert = true;
    $('#dashboard-content').setAttribute('aria-busy', 'true');
  }
  try {
  let response;
  try { response = await fetch(url, options); }
  catch { throw new Error('Could not reach the server. Check that the app is running.'); }
  const data = await readResponse(response);
  if (!response.ok || data.error) {
    const error = new Error(data.error || `Request failed (HTTP ${response.status}).`);
    error.status = response.status;
    throw error;
  }
  return data;
  } finally {
    if (isDashboardRequest) {
      apiPending = false;
      $('#dashboard-content').inert = false;
      $('#dashboard-content').removeAttribute('aria-busy');
    }
  }
}

async function loadDashboard(month = selectedMonth) {
  const requestId = ++dashboardRequestId;
  try {
    const data = await requestJson(`api?month=${encodeURIComponent(month)}`);
    if (requestId === dashboardRequestId) renderDashboard(data);
  } catch (error) {
    if (requestId === dashboardRequestId) throw error;
  }
}

function showAppError(error) {
  if (error.status === 401) {
    $('#edit-dialog').close();
    $('#dashboard-content').hidden = true;
    $('#auth-panel').hidden = false;
    showMessage('#auth-message', error.message);
  } else {
    showMessage($('#dashboard-content').hidden ? '#auth-message' : '#form-message', error.message);
  }
}

async function moveMonth(offset) {
  if (apiPending) return;
  const nextMonth = offset === 0 ? localMonthString() : shiftMonth(selectedMonth, offset);
  if (nextMonth < '1000-01' || nextMonth > '9999-12') return;
  selectedMonth = nextMonth;
  currentPage = 1;
  try { await loadDashboard(nextMonth); }
  catch (error) {
    if (selectedMonth === nextMonth) selectedMonth = dashboardData?.month || localMonthString();
    showAppError(error);
  }
}

async function authenticate(action, payload) {
  await requestJson(`auth/${action}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
  });
  await loadDashboard();
  $('#auth-panel').hidden = true;
  $('#dashboard-content').hidden = false;
}

$('#login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.target.querySelector('button[type="submit"]');
  button.disabled = true;
  try { await authenticate('login', { email: $('#login-email').value, password: $('#login-password').value }); }
  catch (error) { showMessage('#auth-message', error.message); }
  finally { button.disabled = false; }
});

$('#register-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.target.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    await authenticate('register', {
      name: $('#register-name').value, email: $('#register-email').value,
      password: $('#register-password').value, monthlyBudget: $('#register-budget').value
    });
  } catch (error) { showMessage('#auth-message', error.message); }
  finally { button.disabled = false; }
});

$('#logout-button').addEventListener('click', async event => {
  const button = event.currentTarget;
  button.disabled = true;
  try { await requestJson('auth/logout', { method: 'POST' }); window.location.reload(); }
  catch (error) { button.disabled = false; showAppError(error); }
});

$('#previous-month').addEventListener('click', () => moveMonth(-1));
$('#next-month').addEventListener('click', () => moveMonth(1));
$('#current-month').addEventListener('click', () => moveMonth(0));
$('#print-button').addEventListener('click', () => window.print());
window.addEventListener('beforeprint', () => renderTransactions(true));
window.addEventListener('afterprint', () => renderTransactions());
['#transaction-search', '#transaction-type-filter', '#transaction-category-filter'].forEach(selector => {
  $(selector).addEventListener(selector === '#transaction-search' ? 'input' : 'change', () => {
    currentPage = 1;
    renderTransactions();
  });
});

$('#transaction-form').addEventListener('submit', async event => {
  event.preventDefault();
  const amount = Number($('#amount').value);
  const description = $('#description').value.trim();
  const date = $('#date').value;
  if (!Number.isFinite(amount) || amount <= 0 || amount > 100000000) return showMessage('#form-message', 'Enter an amount between ₹0.01 and ₹100,000,000.');
  if (!description) return showMessage('#form-message', 'Description is required.');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return showMessage('#form-message', 'Choose a valid date.');
  const button = event.target.querySelector('button[type="submit"]');
  button.disabled = true;
  const month = date.slice(0, 7);
  try {
    const data = await requestJson(`api?month=${encodeURIComponent(month)}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: $('#type').value, category: $('#category').value, amount: $('#amount').value, description, date })
    });
    selectedMonth = month;
    currentPage = 1;
    renderDashboard(data);
    event.target.reset();
    $('#date').value = dateForMonth(month);
    showMessage('#form-message', 'Movement recorded.', 'success');
  } catch (error) { showAppError(error); }
  finally { button.disabled = false; }
});

$('#transactions').addEventListener('click', async event => {
  const editButton = event.target.closest('.edit-transaction');
  if (editButton) {
    const item = dashboardData.transactions.find(transaction => String(transaction.id) === editButton.dataset.id);
    if (!item) return;
    $('#edit-id').value = item.id;
    $('#edit-description').value = item.description;
    $('#edit-amount').value = item.amount;
    $('#edit-type').value = item.type;
    $('#edit-category').value = item.category;
    $('#edit-date').value = item.date;
    showMessage('#edit-message', '', 'success');
    $('#edit-dialog').showModal();
    return;
  }
  const deleteButton = event.target.closest('.delete-transaction');
  if (!deleteButton || !confirm('Delete this transaction? This cannot be undone.')) return;
  deleteButton.disabled = true;
  try {
    const data = await requestJson(`api/transactions/${deleteButton.dataset.id}?month=${selectedMonth}`, { method: 'DELETE' });
    renderDashboard(data);
    showMessage('#form-message', 'Movement deleted.', 'success');
  } catch (error) { deleteButton.disabled = false; showAppError(error); }
});

$('#pagination').addEventListener('click', event => {
  const button = event.target.closest('button[data-page]');
  if (!button || button.disabled) return;
  currentPage += button.dataset.page === 'next' ? 1 : -1;
  renderTransactions();
});

$('#edit-form').addEventListener('submit', async event => {
  event.preventDefault();
  const amount = Number($('#edit-amount').value);
  if (!Number.isFinite(amount) || amount <= 0 || amount > 100000000) return showMessage('#edit-message', 'Enter a valid amount.');
  const button = event.target.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    const data = await requestJson(`api/transactions/${$('#edit-id').value}?month=${selectedMonth}`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: $('#edit-type').value, category: $('#edit-category').value,
        amount: $('#edit-amount').value, description: $('#edit-description').value.trim(), date: $('#edit-date').value
      })
    });
    $('#edit-dialog').close();
    renderDashboard(data);
    showMessage('#form-message', 'Movement updated.', 'success');
  } catch (error) { if (error.status === 401) showAppError(error); else showMessage('#edit-message', error.message); }
  finally { button.disabled = false; }
});
$('#close-edit').addEventListener('click', () => $('#edit-dialog').close());

$('#budget-edit').addEventListener('click', () => {
  if (budgetEditing) {
    setBudgetEditing(false, true);
    showMessage('#budget-feedback', 'Unsaved changes discarded.', 'success');
  } else {
    showMessage('#budget-feedback', '', 'success');
    setBudgetEditing(true);
  }
});

$('#budget-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!budgetEditing) return;
  const rawAmount = $('#budget-input').value;
  const amount = Number(rawAmount);
  if (rawAmount === '' || !Number.isFinite(amount) || amount < 0 || amount > 100000000) {
    return showMessage('#budget-feedback', 'Enter a budget from 0 to ₹100,000,000. Use 0 to clear it.');
  }
  const button = $('#budget-save');
  button.disabled = true;
  try {
    const data = await requestJson(`api/budget?month=${selectedMonth}`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ monthlyBudget: rawAmount })
    });
    renderDashboard(data);
    showMessage('#budget-feedback', 'Monthly ceiling saved.', 'success');
  } catch (error) { if (error.status === 401) showAppError(error); else showMessage('#budget-feedback', error.message); }
  finally { button.disabled = false; }
});

$('#date').value = dateForMonth(selectedMonth);
loadDashboard().then(() => {
  $('#auth-panel').hidden = true;
  $('#dashboard-content').hidden = false;
}).catch(error => {
  if (error.status !== 401) showAppError(error);
});
