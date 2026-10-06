'use strict';

// ---------- Utilidades ----------

const $ = (sel) => document.querySelector(sel);

/** Crea elementos sin innerHTML: los datos del usuario nunca se interpretan como HTML. */
function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key === 'class') node.className = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value);
  }
  node.append(...children.flat().filter((c) => c != null));
  return node;
}

class ApiError extends Error {
  constructor(status, detail, errors) {
    super(detail);
    this.status = status;
    this.errors = errors || {};
  }
}

async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    throw new ApiError(res.status, body?.detail || `Error ${res.status}`, body?.errors);
  }
  return body;
}

const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });

function timeIn(iso, timeZone) {
  return new Intl.DateTimeFormat('es-AR', { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .format(new Date(iso));
}

function longDate(isoDate, timeZone) {
  // Mediodía UTC para que el día no cambie por la zona horaria.
  return new Intl.DateTimeFormat('es-AR', { timeZone, weekday: 'long', day: 'numeric', month: 'long' })
    .format(new Date(`${isoDate}T15:00:00Z`));
}

/** Fecha YYYY-MM-DD de hoy (+ offset días) en la zona horaria del negocio. */
function dateIn(timeZone, offsetDays = 0) {
  const d = new Date(Date.now() + offsetDays * 86_400_000);
  return new Intl.DateTimeFormat('en-CA', { timeZone }).format(d);
}

let toastTimer;
function toast(message) {
  const node = $('#toast');
  node.textContent = message;
  node.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => node.classList.remove('show'), 3500);
}

const STATUS_LABEL = {
  PENDING_PAYMENT: 'Esperando seña',
  CONFIRMED: 'Confirmado',
  EXPIRED: 'Seña vencida',
  CANCELLED: 'Cancelado',
  REFUND_REQUIRED: 'Reembolsar',
};

// ---------- Estado ----------

const state = {
  business: null,
  services: [],
  service: null,
  date: null,
  slot: null,
  countdown: null,
};

// ---------- Reservar: servicios ----------

function renderServices() {
  const list = $('#services');
  list.replaceChildren(...state.services.map((s) => el('button', {
    class: `service${state.service?.id === s.id ? ' selected' : ''}`,
    type: 'button',
    onclick: () => selectService(s),
  },
  el('strong', {}, s.name),
  el('div', { class: 'meta' },
    el('span', {}, `${s.durationMinutes} min · ${money.format(s.price)}`),
    el('span', {}, `Seña ${money.format(s.depositAmount)}`)))));
}

function selectService(service) {
  state.service = service;
  state.slot = null;
  renderServices();
  renderSummary();
  loadSlots();
}

// ---------- Reservar: horarios ----------

async function loadSlots() {
  const container = $('#slots');
  if (!state.service) return;
  container.replaceChildren(el('p', { class: 'muted' }, 'Buscando horarios…'));
  try {
    const params = new URLSearchParams({ serviceId: state.service.id, date: state.date });
    const slots = await api(`/api/businesses/${state.business.id}/availability?${params}`);
    if (slots.length === 0) {
      container.replaceChildren(el('p', { class: 'muted' }, 'No quedan horarios para este día. Probá con otro.'));
      return;
    }
    container.replaceChildren(...slots.map((slot) => el('button', {
      class: `slot${state.slot?.startAt === slot.startAt ? ' selected' : ''}`,
      type: 'button',
      onclick: () => selectSlot(slot),
    }, timeIn(slot.startAt, state.business.timezone))));
  } catch (e) {
    container.replaceChildren(el('p', { class: 'muted' }, e.message));
  }
}

function selectSlot(slot) {
  state.slot = slot;
  document.querySelectorAll('.slot').forEach((b) => {
    b.classList.toggle('selected', b.textContent === timeIn(slot.startAt, state.business.timezone));
  });
  renderSummary();
}

function renderSummary() {
  const summary = $('#summary');
  const ready = state.service && state.slot;
  $('#submit').disabled = !ready;
  if (!ready) {
    summary.className = 'summary muted';
    summary.textContent = 'Elegí un servicio y un horario para continuar.';
    return;
  }
  const tz = state.business.timezone;
  summary.className = 'summary';
  summary.replaceChildren(
    el('div', { class: 'row' }, el('span', {}, 'Servicio'), el('b', {}, state.service.name)),
    el('div', { class: 'row' }, el('span', {}, 'Cuándo'),
      el('b', {}, `${longDate(state.date, tz)}, ${timeIn(state.slot.startAt, tz)} h`)),
    el('div', { class: 'row' }, el('span', {}, 'Seña a pagar'), el('b', {}, money.format(state.service.depositAmount))),
  );
}

// ---------- Reservar: envío ----------

async function submitBooking(event) {
  event.preventDefault();
  const form = event.target;
  form.querySelectorAll('.error').forEach((e) => { e.textContent = ''; });
  const data = Object.fromEntries(new FormData(form));
  const submit = $('#submit');
  submit.disabled = true;
  submit.textContent = 'Reservando…';
  try {
    const booking = await api(`/api/businesses/${state.business.id}/bookings`, {
      method: 'POST',
      body: JSON.stringify({ ...data, serviceId: state.service.id, startAt: state.slot.startAt }),
    });
    showPayment(booking);
  } catch (e) {
    if (e.status === 409) {
      toast('¡Uy! Alguien acaba de tomar ese horario. Elegí otro.');
      state.slot = null;
      renderSummary();
      loadSlots();
    } else if (e.status === 400) {
      for (const [field, message] of Object.entries(e.errors)) {
        const target = form.querySelector(`.error[data-for="${field}"]`);
        if (target) target.textContent = message;
      }
    } else {
      toast(e.message);
    }
  } finally {
    submit.textContent = 'Reservar y pagar seña';
    renderSummary();
  }
}

// ---------- Pago (simulado) ----------

function showPayment(booking) {
  const dialog = $('#payment-dialog');
  const tz = state.business.timezone;
  const countdown = el('span', { class: 'countdown' });
  const payButton = el('button', { class: 'btn primary', onclick: () => pay(booking, payButton) },
    'Pagar seña (simulado)');

  $('#payment-content').replaceChildren(
    el('div', { class: 'big' }, '⏳'),
    el('h3', {}, 'Te guardamos el turno'),
    el('p', { class: 'muted' }, `${booking.customerName}, ${longDate(state.date, tz)} a las ${timeIn(booking.startAt, tz)} h`),
    el('div', { class: 'amount' }, money.format(booking.depositAmount)),
    el('p', {}, 'Pagá la seña en ', countdown, ' o el horario se libera.'),
    payButton,
    el('button', { class: 'btn', onclick: () => closePayment() }, 'Cerrar'),
    el('p', { class: 'note' }, 'MVP: el pago es simulado. En producción este botón abre Mercado Pago.'),
  );

  stopCountdown();
  const tick = () => {
    const left = Math.max(0, new Date(booking.holdExpiresAt) - Date.now());
    const m = Math.floor(left / 60000);
    const s = Math.floor((left % 60000) / 1000);
    countdown.textContent = `${m}:${String(s).padStart(2, '0')}`;
    if (left === 0) {
      stopCountdown();
      showExpired();
    }
  };
  tick();
  state.countdown = setInterval(tick, 1000);
  dialog.showModal();
}

async function pay(booking, button) {
  button.disabled = true;
  button.textContent = 'Procesando pago…';
  try {
    await api(`/dev/payments/${booking.id}/approve`, { method: 'POST' });
    const updated = await api(`/api/bookings/${booking.id}`);
    stopCountdown();
    if (updated.status === 'CONFIRMED') showConfirmed(updated);
    else showExpired(STATUS_LABEL[updated.status]);
  } catch (e) {
    toast(e.message);
    button.disabled = false;
    button.textContent = 'Pagar seña (simulado)';
  }
}

function showConfirmed(booking) {
  const tz = state.business.timezone;
  $('#payment-content').replaceChildren(
    el('div', { class: 'big' }, '✅'),
    el('h3', {}, '¡Turno confirmado!'),
    el('p', {}, `${state.service.name} · ${longDate(state.date, tz)} a las ${timeIn(booking.startAt, tz)} h`),
    el('p', { class: 'muted' }, `Seña pagada: ${money.format(booking.depositAmount)}`),
    el('button', { class: 'btn primary', onclick: () => closePayment() }, 'Listo'),
  );
}

function showExpired(reason = 'La seña venció') {
  $('#payment-content').replaceChildren(
    el('div', { class: 'big' }, '⌛'),
    el('h3', {}, reason),
    el('p', { class: 'muted' }, 'El horario quedó libre. Podés reservar de nuevo.'),
    el('button', { class: 'btn primary', onclick: () => closePayment() }, 'Volver'),
  );
}

function stopCountdown() {
  clearInterval(state.countdown);
  state.countdown = null;
}

function closePayment() {
  stopCountdown();
  $('#payment-dialog').close();
  $('#booking-form').reset();
  state.slot = null;
  renderSummary();
  loadSlots();
}

// ---------- Agenda del negocio ----------

async function loadAgenda() {
  const body = $('#agenda-body');
  const date = $('#agenda-date').value;
  const tz = state.business.timezone;
  try {
    const entries = await api(`/api/businesses/${state.business.id}/bookings?date=${date}`);
    const count = (status) => entries.filter((e) => e.status === status).length;
    const collected = entries.filter((e) => e.status === 'CONFIRMED')
      .reduce((sum, e) => sum + Number(e.depositAmount), 0);
    $('#agenda-stats').replaceChildren(
      el('div', { class: 'stat' }, el('b', {}, String(count('CONFIRMED'))), 'Confirmados'),
      el('div', { class: 'stat' }, el('b', {}, String(count('PENDING_PAYMENT'))), 'Esperando seña'),
      el('div', { class: 'stat' }, el('b', {}, money.format(collected)), 'Señas cobradas'),
    );

    if (entries.length === 0) {
      body.replaceChildren(el('tr', {}, el('td', { class: 'empty', colspan: '6' }, 'No hay turnos para este día.')));
      return;
    }
    body.replaceChildren(...entries.map((e) => el('tr', {},
      el('td', { class: 'time' }, `${timeIn(e.startAt, tz)} – ${timeIn(e.endAt, tz)}`),
      el('td', {}, e.serviceName),
      el('td', {}, e.customerName),
      el('td', {}, e.customerPhone),
      el('td', {}, money.format(e.depositAmount)),
      el('td', {}, el('span', { class: `badge ${e.status}` }, STATUS_LABEL[e.status] || e.status)))));
  } catch (e) {
    toast(e.message);
  }
}

// ---------- Navegación e inicio ----------

let agendaTimer;
function showView(view) {
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.view === view));
  $('#view-book').hidden = view !== 'book';
  $('#view-agenda').hidden = view !== 'agenda';
  clearInterval(agendaTimer);
  if (view === 'agenda') {
    loadAgenda();
    agendaTimer = setInterval(loadAgenda, 10_000); // refresco automático
  } else {
    loadSlots();
  }
}

async function init() {
  try {
    const businesses = await api('/api/businesses');
    if (businesses.length === 0) {
      $('#business-name').textContent = 'No hay negocios cargados';
      return;
    }
    state.business = businesses[0];
    $('#business-name').textContent = state.business.name;

    const tz = state.business.timezone;
    state.date = dateIn(tz, 1); // mañana por defecto
    const dateInput = $('#date');
    dateInput.min = dateIn(tz);
    dateInput.max = dateIn(tz, 59);
    dateInput.value = state.date;
    dateInput.addEventListener('change', () => {
      state.date = dateInput.value;
      state.slot = null;
      renderSummary();
      loadSlots();
    });

    $('#agenda-date').value = state.date;
    $('#agenda-date').addEventListener('change', loadAgenda);
    $('#agenda-refresh').addEventListener('click', loadAgenda);

    state.services = await api(`/api/businesses/${state.business.id}/services`);
    renderServices();
    renderSummary();
  } catch (e) {
    $('#business-name').textContent = 'Error conectando con la API';
    toast(e.message);
  }
}

document.querySelectorAll('.tab').forEach((t) => t.addEventListener('click', () => showView(t.dataset.view)));
$('#booking-form').addEventListener('submit', submitBooking);
$('#payment-dialog').addEventListener('close', stopCountdown);
init();
