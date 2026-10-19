const API = {
  accounts: 'http://localhost:8085/api',
  subjects: 'http://localhost:8081/api',
  assessments: 'http://localhost:8082/api',
  activity: 'http://localhost:8083/api',
  planning: 'http://localhost:8084/api'
};

const state = {
  token: localStorage.getItem('studyLeftoversToken'),
  account: null,
  sessionEpoch: 0,
  calendarRequest: 0,
  subjects: [],
  assessments: [],
  week: null,
  plan: null,
  planHistory: [],
  historyPlan: null,
  review: null,
  uploadItems: [],
  reviewQueue: [],
  subjectAi: null,
  planningAi: null,
  serviceFailures: [],
  availability: {},
  availabilityMinutes: {},
  availabilityReady: false,
  manualAssessments: [],
  sessions: [],
  calendarEntries: [],
  currentPlanCalendar: [],
  calendarPlanId: null,
  completedCalendar: [],
  calendarMonth: new Date(new Date().getFullYear(), new Date().getMonth(), 1),
  calendarWeek: null,
  assistantHistory: [],
  profilePictureDraft: null,
  currentView: 'dashboard'
};

const NAVIGATION_VIEWS = ['dashboard', 'subjects', 'assessments', 'plan', 'calendar', 'activity'];

const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const esc = value => String(value ?? '').replace(/[&<>"']/g, character => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);
const num = value => value === '' ? null : Number(value);

function show(view) {
  if (view === 'upload') {
    show('subjects');
    requestAnimationFrame(() => $('#upload').scrollIntoView({ behavior: 'smooth', block: 'start' }));
    return;
  }
  state.currentView = view;
  $$('.view').forEach(element => element.classList.toggle('active', element.id === view));
  const navigationView = view === 'week' ? 'calendar' : view;
  $$('aside [data-view]').forEach(element => element.classList.toggle('active', element.dataset.view === navigationView));
  const pageTitle = {
    subjects: 'Subjects',
    assessments: 'Assessments',
    plan: 'Study plan',
    calendar: 'Schedule',
    week: 'Schedule',
    activity: 'Study activity',
    profile: 'Your profile',
    settings: 'Settings'
  }[view];
  if (pageTitle) $('#page-title').textContent = pageTitle;
  else updateLiveClock();
  if (view === 'activity') $('#activity-date').max = accountToday();
  const loading = view === 'calendar' || view === 'week' ? loadCalendar() : undefined;
  window.scrollTo({ top: 0, behavior: 'smooth' });
  return loading;
}

$$('[data-view]').forEach(button => {
  button.addEventListener('click', () => show(button.dataset.view));
});

function validNavigationOrder(order) {
  return Array.isArray(order) && order.length === NAVIGATION_VIEWS.length
    && new Set(order).size === NAVIGATION_VIEWS.length
    && order.every(view => NAVIGATION_VIEWS.includes(view));
}

function applyNavigationOrder(order) {
  const navigation = $('#main-navigation');
  const safeOrder = validNavigationOrder(order) ? order : NAVIGATION_VIEWS;
  safeOrder.forEach(view => {
    const button = navigation.querySelector(`[data-view="${view}"]`);
    if (button) navigation.append(button);
  });
}

let draggedNavigationButton = null;
let navigationOrderBeforeDrag = null;
$('#main-navigation').addEventListener('dragstart', event => {
  const button = event.target.closest('[data-view][draggable="true"]');
  if (!button) return;
  draggedNavigationButton = button;
  navigationOrderBeforeDrag = $$('#main-navigation [data-view]').map(item => item.dataset.view);
  button.classList.add('dragging');
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', button.dataset.view);
});
$('#main-navigation').addEventListener('dragover', event => {
  if (!draggedNavigationButton) return;
  event.preventDefault();
  const target = event.target.closest('[data-view][draggable="true"]');
  if (!target || target === draggedNavigationButton) return;
  const after = event.clientY > target.getBoundingClientRect().top + target.offsetHeight / 2;
  target.parentElement.insertBefore(draggedNavigationButton, after ? target.nextSibling : target);
});
$('#main-navigation').addEventListener('drop', async event => {
  if (!draggedNavigationButton) return;
  event.preventDefault();
  const order = $$('#main-navigation [data-view]').map(item => item.dataset.view);
  const previousOrder = navigationOrderBeforeDrag;
  try {
    state.account = await request(`${API.accounts}/settings/navigation`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ navigationOrder: order })
    });
    $('#app-announcer').textContent = 'Navigation order saved.';
  } catch (error) {
    if (error.staleSession) return;
    applyNavigationOrder(previousOrder);
    feedback(state.currentView === 'dashboard' ? 'dashboard-feedback' : 'settings-feedback', error.message, 'error');
  }
});
$('#main-navigation').addEventListener('dragend', () => {
  draggedNavigationButton?.classList.remove('dragging');
  draggedNavigationButton = null;
  navigationOrderBeforeDrag = null;
});

function feedback(targetId, message, type = 'info') {
  const target = document.getElementById(targetId);
  if (!target) return;
  target.replaceChildren();
  if (!message) return;
  const box = document.createElement('div');
  box.className = `feedback-message ${type}`;
  const icon = document.createElement('span');
  icon.className = 'feedback-icon';
  icon.setAttribute('aria-hidden', 'true');
  icon.textContent = type === 'error' ? '!' : type === 'success' ? '✓' : 'i';
  const text = document.createElement('span');
  text.textContent = message;
  box.append(icon, text);
  target.append(box);
  $('#app-announcer').textContent = message;
}

function setBusy(button, busy, busyText) {
  if (!button.dataset.idleText) button.dataset.idleText = button.textContent.trim();
  button.disabled = busy;
  button.setAttribute('aria-busy', String(busy));
  if (busy) {
    button.classList.add('button-busy');
    button.innerHTML = `<span class="spinner" aria-hidden="true"></span>${esc(busyText)}`;
  } else {
    button.classList.remove('button-busy');
    button.textContent = button.dataset.idleText;
  }
}

async function request(url, options) {
  const token = state.token;
  const epoch = state.sessionEpoch || 0;
  const ensureCurrent = () => {
    if (state.token !== token || (state.sessionEpoch || 0) !== epoch) {
      const error = new Error('Discarded response from a previous session');
      error.staleSession = true;
      throw error;
    }
  };
  const requestOptions = { ...(options || {}) };
  const headers = new Headers(requestOptions.headers || {});
  if (token) headers.set('Authorization', `Bearer ${token}`);
  headers.set('X-Study-Timezone', accountTimezone());
  requestOptions.headers = headers;
  let response;
  try { response = await fetch(url, requestOptions); }
  catch (error) {
    ensureCurrent();
    throw new Error('Could not reach the service. Check that the application is running and try again.');
  }
  ensureCurrent();
  if (!response.ok) {
    let payload = {};
    try { payload = await response.json(); } catch { payload = { message: response.statusText }; }
    ensureCurrent();
    const validation = payload.validationErrors ? Object.values(payload.validationErrors).filter(Boolean).join('; ') : '';
    if (response.status === 401 && token) signOut(false);
    const detail = [payload.message || 'Request failed', validation].filter(Boolean).join(': ');
    const error = new Error(studentError(detail));
    error.status = response.status;
    throw error;
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  const body = await (contentType.includes('application/json') ? response.json() : response.text());
  ensureCurrent();
  return body;
}

function studentError(message) {
  if (/upstream HTTP 503|provider.*temporarily unavailable|provider.*overloaded/i.test(message))
    return 'The AI service is temporarily busy. Your saved work is unchanged. Please try again shortly.';
  if (/upstream HTTP 429|provider.*quota/i.test(message))
    return 'The AI usage limit has been reached. Your saved work is unchanged. Try again when the limit resets; you can still manage assessments and calendar items.';
  if (/upstream HTTP (400|401|403|404)/i.test(message))
    return 'AI is unavailable with the current configuration. Your saved work is unchanged. Check the model and key in your local setup.';
  return message;
}

function setAuthMode(mode) {
  const signup = mode === 'signup';
  $('#signup-form').hidden = !signup;
  $('#login-form').hidden = signup;
  $('#signup-tab').classList.toggle('active', signup);
  $('#login-tab').classList.toggle('active', !signup);
  $('#signup-tab').setAttribute('aria-selected', String(signup));
  $('#login-tab').setAttribute('aria-selected', String(!signup));
  feedback('auth-feedback', '');
}

function applyTheme(account) {
  if (!account) return;
  const root = document.documentElement;
  root.style.setProperty('--green', account.primaryColor);
  root.style.setProperty('--green-dark', account.primaryColor);
  root.style.setProperty('--focus', account.accentColor);
  root.style.setProperty('--page-bg', account.backgroundColor);
  root.style.setProperty('--surface', account.surfaceColor);
  root.style.setProperty('--text', account.textColor);
  root.style.setProperty('--nav-active', account.navigationColor || '#2a5745');
  root.style.color = account.textColor;
  root.style.background = account.backgroundColor;
}

function accountTimezone() {
  return BrowserTimezone.effective(state.account);
}

function accountToday() { return StudentWorkflow.now(accountTimezone()).slice(0, 10); }

function calendarItems() {
  const entries = [...new Map([...(state.completedCalendar || []), ...state.calendarEntries, ...(state.currentPlanCalendar || [])].map(item => [item.id, item])).values()];
  return StudentWorkflow.calendar(entries, state.assessments, state.sessions);
}

function updateLiveClock() {
  if (!state.account) return;
  const now = new Date();
  const timeZone = accountTimezone();
  const hour = Number(new Intl.DateTimeFormat('en-AU', {
    timeZone, hour: '2-digit', hourCycle: 'h23'
  }).formatToParts(now).find(part => part.type === 'hour')?.value ?? 12);
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  if (state.currentView === 'dashboard') {
    $('#page-title').textContent = `${greeting}, ${state.account.displayName || state.account.username}`;
  }
  $('#live-time').textContent = new Intl.DateTimeFormat('en-AU', {
    timeZone, weekday: 'long', day: 'numeric', month: 'long',
    hour: 'numeric', minute: '2-digit', timeZoneName: 'short'
  }).format(now);
  $('#live-date').textContent = new Intl.DateTimeFormat('en-AU', {
    timeZone, weekday: 'long', day: 'numeric', month: 'long'
  }).format(now).toUpperCase();
}

function populateTimezones() {
  const selected = $('#profile-timezone').value || state.account?.timezone || BrowserTimezone.detected();
  const query = $('#timezone-search').value.trim().toLowerCase();
  const zones = StudentControls.zones(selected).filter(zone => `${zone} ${StudentControls.zoneLabel(zone)}`.toLowerCase().includes(query));
  $('#profile-timezone').innerHTML = zones.map(zone => `<option value="${esc(zone)}">${esc(StudentControls.zoneLabel(zone))}</option>`).join('');
  if (zones.includes(selected)) $('#profile-timezone').value = selected;
  else if (zones.length) $('#profile-timezone').value = zones[0];
  previewTimezone();
}
function previewTimezone() {
  const zone = $('#profile-timezone').value;
  $('#timezone-preview').textContent = zone ? `Selected: ${zone}. Local time: ${new Intl.DateTimeFormat('en-AU', {timeZone:zone,dateStyle:'medium',timeStyle:'short'}).format(new Date())}. Save your profile to apply.` : 'No matching locations. Try a city or region.';
}
$('#timezone-search').addEventListener('input', populateTimezones);
$('#profile-timezone').addEventListener('change', previewTimezone);
$('#detect-timezone').addEventListener('click', () => {
  $('#timezone-search').value = '';populateTimezones();$('#profile-timezone').value = BrowserTimezone.detected();previewTimezone();
});

function populateAccount() {
  const account = state.account;
  if (!account) return;
  $('#account-name').textContent = account.displayName || account.username;
  $('#account-username').textContent = `@${account.username}`;
  const initials = (account.displayName || account.username).slice(0, 2).toUpperCase();
  $('#account-avatar-initials').textContent = initials;
  $('#profile-picture-initials').textContent = initials;
  state.profilePictureDraft = account.profilePicture || null;
  renderProfilePictures(state.profilePictureDraft);
  $('#profile-name').value = account.displayName || '';
  $('#profile-institution').value = account.institution || '';
  $('#profile-course').value = account.course || '';
  $('#profile-goal').value = account.studyGoal || '';
  $('#timezone-search').value = '';
  populateTimezones();
  $('#profile-timezone').value = accountTimezone();
  previewTimezone();
  $('#theme-primary').value = account.primaryColor;
  $('#theme-accent').value = account.accentColor;
  $('#theme-background').value = account.backgroundColor;
  $('#theme-surface').value = account.surfaceColor;
  $('#theme-text').value = account.textColor;
  $('#theme-navigation').value = account.navigationColor || '#2a5745';
  applyNavigationOrder(account.navigationOrder);
  applyTheme(account);
  $('#plan-start').value = accountToday();
  $('#plan-start').min = accountToday();
  $('#activity-date').value = accountToday();
  $('#activity-date').max = accountToday();
  const accountDate = new Date(`${accountToday()}T12:00:00`);
  state.calendarMonth = new Date(accountDate.getFullYear(), accountDate.getMonth(), 1);
  state.calendarWeek = mondayFor(accountDate);
  updateLiveClock();
  renderAvailability();
}

function renderProfilePictures(picture) {
  const pairs = [[$('#account-avatar-image'), $('#account-avatar-initials')],
    [$('#profile-picture-preview'), $('#profile-picture-initials')]];
  pairs.forEach(([image, fallback]) => {
    image.hidden = !picture;
    fallback.hidden = Boolean(picture);
    if (picture) image.src = picture;
    else image.removeAttribute('src');
  });
  $('#remove-profile-picture').hidden = !picture;
}

const dashboardStream = new DashboardStreamClient({
  endpoint: `${API.planning}/planning/dashboard/stream`,
  getToken: () => state.token,
  getTimezone: accountTimezone,
  onSnapshot: snapshot => {
    state.week = snapshot.week;
    state.streamStatus = snapshot.stream;
    state.serviceFailures = state.serviceFailures.filter(name => name !== 'week');
    // Only redraw summary panels. Live messages must not reset an in-progress upload/editor/form.
    renderDashboard();
    renderSystemStatus();
  },
  onStatus: status => { state.streamConnection = status; renderSystemStatus(); },
  onUnauthorized: () => signOut(false)
});

async function establishSession(session) {
  const epoch = state.sessionEpoch = (state.sessionEpoch || 0) + 1;
  state.token = session.token;
  state.account = session.account;
  localStorage.setItem('studyLeftoversToken', state.token);
  if (state.account.timezoneAutomatic !== false) {
    const detected = BrowserTimezone.detected();
    state.account = { ...state.account, timezone: detected, timezoneAutomatic: true };
    try {
      state.account = await request(`${API.accounts}/profile/timezone/detected`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ timezone: detected })
      });
    } catch {
      // The detected browser timezone still applies locally if persistence is temporarily unavailable.
    }
  }
  if (state.sessionEpoch !== epoch) return;
  document.body.classList.remove('auth-required');
  $('#auth-shell').hidden = true;
  populateAccount();
  await load();
  if (state.sessionEpoch === epoch) dashboardStream.start();
}

async function signOut(callServer = true) {
  dashboardStream.stop();
  const token = state.token;
  const logout = callServer && token
    ? request(`${API.accounts}/auth/logout`, { method: 'POST' }).catch(() => {}) : null;
  state.sessionEpoch = (state.sessionEpoch || 0) + 1;
  state.calendarRequest = (state.calendarRequest || 0) + 1;
  state.token = null;
  state.account = null;
  Object.assign(state, { subjects: [], assessments: [], sessions: [], calendarEntries: [], completedCalendar: [], currentPlanCalendar: [], calendarPlanId: null,
    plan: null, planHistory: [], historyPlan: null, planPending: false, week: null, review: null, reviewQueue: [], uploadItems: [],
    availability: {}, availabilityMinutes: {}, availabilityReady: false, availabilityPending: false, assistantHistory: [], manualAssessments: [],
    profilePictureDraft: null, subjectAi: null, planningAi: null, serviceFailures: [] });
  localStorage.removeItem('studyLeftoversToken');
  setAssistantOpen(false);
  document.body.classList.add('auth-required');
  $('#auth-shell').hidden = false;
  setAuthMode('login');
  document.querySelectorAll('form').forEach(form => form.reset());
  document.querySelectorAll('dialog[open]').forEach(dialog => dialog.close());
  document.querySelectorAll('.feedback').forEach(target => target.replaceChildren());
  $('#app-announcer').textContent = '';
  ['study-chat-messages', 'chat-messages', 'review', 'manual-assessments'].forEach(id => document.getElementById(id)?.replaceChildren());
  $('#activity-date').value = accountToday();
  $('#plan-start').value = accountToday();
  renderAvailability();
  ['#availability-chat-form button', '#plan-form button', '#refresh-plan-history'].forEach(selector => setBusy($(selector), false));
  render(); renderCalendar(); renderWeek(); renderUploadQueue(); renderProfilePictures(null);
  await logout;
}

$('#signup-tab').addEventListener('click', () => setAuthMode('signup'));
$('#login-tab').addEventListener('click', () => setAuthMode('login'));
$('#logout-btn').addEventListener('click', () => signOut(true));

$('#signup-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  setBusy(button, true, 'Creating');
  feedback('auth-feedback', 'Creating your private study workspace…', 'info');
  try {
    const session = await request(`${API.accounts}/auth/register`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: $('#signup-username').value.trim(),
        password: $('#signup-password').value, displayName: $('#signup-name').value.trim(),
        timezone: BrowserTimezone.detected() })
    });
    await establishSession(session);
  } catch (error) {
    if (error.staleSession) return; feedback('auth-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

$('#login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  setBusy(button, true, 'Signing in');
  feedback('auth-feedback', 'Opening your remembered workspace…', 'info');
  try {
    const session = await request(`${API.accounts}/auth/login`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: $('#login-username').value.trim(), password: $('#login-password').value })
    });
    await establishSession(session);
  } catch (error) {
    if (error.staleSession) return; feedback('auth-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

function dateCard(dueDate, dueWeek) {
  if (!dueDate) {
    return `<div class="date"><small>WEEK</small><strong>${esc(dueWeek ?? '?')}</strong></div>`;
  }
  const date = new Date(`${dueDate}T00:00:00`);
  return `<div class="date"><small>${date.toLocaleString('en', { month: 'short' }).toUpperCase()}</small><strong>${date.getDate()}</strong></div>`;
}

function subjectName(id) {
  return state.subjects.find(subject => subject.id === id)?.code || 'Subject';
}

function assessmentRow(assessment) {
  return `<div class="row">
    ${dateCard(assessment.dueDate, assessment.dueWeek)}
    <div class="body"><strong>${esc(assessment.title)}</strong><br><small>${esc(subjectName(assessment.subjectId))} · ${assessment.weighting ?? '—'}% · ${assessment.estimatedMinutes ?? '—'} min</small></div>
    <span class="pill">${assessment.status === 'INCOMPLETE' && assessment.dueDate && assessment.dueDate < accountToday() ? 'OVERDUE' : esc(assessment.status)}</span>
    <div class="row-actions">
      <button class="link edit-assessment" data-id="${esc(assessment.id)}">Edit</button>
      ${assessment.status === 'INCOMPLETE' ? `<button class="link complete" data-id="${esc(assessment.id)}">Complete</button>` : ''}
      <button class="link danger remove-saved-assessment" data-id="${esc(assessment.id)}" aria-label="Remove ${esc(assessment.title)}">Remove</button>
    </div>
  </div>`;
}

async function load() {
  const accountToken = state.token;
  const calls = [
    ['subjects', request(`${API.subjects}/subjects`)],
    ['assessments', request(`${API.assessments}/assessments`)],
    ['week', request(`${API.planning}/planning/this-week`)],
    ['plan', request(`${API.planning}/planning/plans/latest`)],
    ['planHistory', request(`${API.planning}/planning/plans`)],
    ['subjectAi', request(`${API.subjects}/ai/status`)],
    ['planningAi', request(`${API.planning}/planning/ai/status`)]
    ,['sessions', request(`${API.activity}/study-sessions`)]
  ];
  const results = await Promise.allSettled(calls.map(([, promise]) => promise));
  if (state.token !== accountToken) return;
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') state[calls[index][0]] = result.value;
  });
  state.serviceFailures = results.map((result, index) => result.status === 'rejected' ? calls[index][0] : null).filter(Boolean);
  const unavailable = state.serviceFailures.length;
  if (unavailable) {
    const planningOffline = state.serviceFailures.some(name => ['week', 'plan', 'planningAi'].includes(name));
    feedback('dashboard-feedback', planningOffline
      ? 'Planning is offline. Rebuild the services, then use Retry; your other data is still available.'
      : 'Some data could not be refreshed. Check the running services and try again.', 'error');
  } else {
    feedback('dashboard-feedback', '');
  }
  render();
  renderCalendar();
  renderWeek();
  await loadCalendar(false);
}

function render() {
  renderSystemStatus();
  renderDashboard();
  $('#subject-list').innerHTML = state.subjects.length
    ? state.subjects.map(subject => `<article><div class="subject-card-head"><p class="eyebrow">${esc(subject.code)}</p><button class="secondary edit-subject" type="button" data-id="${esc(subject.id)}">Edit</button></div><h3>${esc(subject.name)}</h3><p>${subject.creditPoints ?? '—'} credit points</p><small>${subject.weeklyStudyTargetMinutes} min weekly target</small></article>`).join('')
    : '<p>No subjects yet. Upload an outline to begin.</p>';
  $('#activity-subject').innerHTML = '<option value="">Choose subject</option>'
    + state.subjects.map(subject => `<option value="${esc(subject.id)}">${esc(subject.code)} — ${esc(subject.name)}</option>`).join('');
  $('#calendar-subject').innerHTML = '<option value="">No subject</option>'
    + state.subjects.map(subject => `<option value="${esc(subject.id)}">${esc(subject.code)} — ${esc(subject.name)}</option>`).join('');
  $('#assessment-subject').innerHTML = '<option value="">Choose subject</option>'
    + state.subjects.map(subject => `<option value="${esc(subject.id)}">${esc(subject.code)} — ${esc(subject.name)}</option>`).join('');

  $('#assistant-subject-count').textContent = state.subjects.length;
  $('#assistant-assessment-count').textContent = state.assessments.filter(item => item.status === 'INCOMPLETE').length;
  $('#assistant-calendar-count').textContent = state.calendarEntries.filter(item => item.status === 'PLANNED').length;
  renderSessions();
  updateActivityAssessments();
  const upcomingCalendar = calendarItems().filter(item => item.status === 'PLANNED'
    && item.endAt > StudentWorkflow.now(accountTimezone())).sort((a, b) => a.startAt.localeCompare(b.startAt)).slice(0, 4);
  $('#dashboard-calendar').classList.toggle('empty', !upcomingCalendar.length);
  $('#dashboard-calendar').innerHTML = upcomingCalendar.length
    ? upcomingCalendar.map(item => `<button class="calendar-list-item" data-calendar-id="${esc(item.id)}"><span class="calendar-dot ${item.type.toLowerCase()}"></span><span><strong>${esc(item.title)}</strong><small>${item.dateOnly ? `${esc(item.startAt.slice(0, 10))} · Deadline` : formatDateTime(item.startAt)}</small></span></button>`).join('')
    : 'No upcoming calendar items. Add a deadline or build a study plan.';

  renderAssessments();
  renderPlan('#dashboard-plan');
  renderPlan('#plan-list');
  renderPlanHistory();
  bindComplete();
  bindDeleteAssessments();
  bindEditAssessments();
  bindEditSubjects();
  bindCalendarItems();
}

function bindEditSubjects() {
  $$('.edit-subject:not([data-bound])').forEach(button => {
    button.dataset.bound = 'true';
    button.addEventListener('click', () => {
      const subject = state.subjects.find(item => item.id === button.dataset.id);
      if (!subject) return;
      $('#subject-id').value = subject.id;
      $('#subject-code').value = subject.code;
      $('#subject-name').value = subject.name;
      $('#subject-credit-points').value = subject.creditPoints ?? '';
      $('#subject-weekly-target').value = subject.weeklyStudyTargetMinutes;
      feedback('subject-dialog-feedback', '');
      $('#subject-dialog').showModal();
      $('#subject-name').focus();
    });
  });
}

function renderDashboard() {
  const week = state.week;
  $('#due-count').textContent = week?.dueThisWeek?.length ?? 0;
  $('#upcoming-count').textContent = week?.upcoming?.length ?? 0;
  $('#study-count').textContent = week?.studyProgress?.reduce((total, progress) => total + progress.studiedMinutes, 0) ?? 0;
  $('#incomplete-count').textContent = week?.workload?.incompleteAssessments
    ?? state.assessments.filter(assessment => assessment.status === 'INCOMPLETE').length;

  const overdue = week?.overdue || [];
  const status = week?.workload?.status ?? 'LOW';
  const badge = $('#load-badge');
  badge.textContent = overdue.length ? `${overdue.length} OVERDUE · REVIEW DEADLINES` : `${status} WORKLOAD`;
  badge.className = `load ${status.toLowerCase()}`;

  const attention = [...overdue, ...(week?.dueThisWeek || []), ...(week?.upcoming || [])].slice(0, 5);
  $('#dashboard-assessments').innerHTML = attention.length
    ? attention.map(assessmentRow).join('')
    : 'No dated assessments need attention. Check Assessments for work without a deadline.';
  $('#progress-period').textContent = week ? `${week.weekStart} – ${week.weekEnd}` : 'Waiting for progress updates';
  $('#dashboard-progress').innerHTML = week?.studyProgress?.length
    ? week.studyProgress.map(progress => `<div class="row"><div class="body"><strong>${esc(subjectName(progress.subjectId))}</strong><br><small>${progress.studiedMinutes} / ${progress.targetMinutes} minutes</small></div><span class="pill">${esc(progress.state.replaceAll('_', ' '))}</span></div>`).join('')
    : 'Record a session to see progress.';

  bindComplete();
  bindDeleteAssessments();
  bindEditAssessments();
}

function renderSystemStatus() {
  const status = $('#system-status');
  const subjectReady = state.subjectAi?.configured;
  const planningReady = state.planningAi?.configured;
  const subjectStatusUnavailable = state.serviceFailures.includes('subjectAi');
  const planningOffline = state.serviceFailures.some(name => ['week', 'plan', 'planningAi'].includes(name));
  const subjectLabel = subjectReady ? '● AI key configured'
    : subjectStatusUnavailable ? '○ Gemini status unavailable' : '○ Gemini not loaded';
  status.innerHTML = `
    <span class="status-chip ${subjectReady ? 'ready' : subjectStatusUnavailable ? 'offline' : 'warning'}" title="${esc(state.subjectAi?.message || 'Subject AI status unavailable')}">${subjectLabel}</span>
    <span class="status-chip ${planningOffline ? 'offline' : 'ready'}">${planningOffline ? '○ Planner offline' : '● Planner online'}</span>
    <span class="status-chip ${state.streamConnection === 'live' ? 'ready' : 'warning'}" title="${esc(state.streamStatus?.updatedAt ? `Kafka projection updated ${state.streamStatus.updatedAt}` : 'Waiting for Kafka-derived projections')}">${state.streamConnection === 'live' ? (state.streamStatus?.initialized ? '● Live dashboard' : '○ Waiting for events') : state.streamConnection === 'offline' ? '○ Live updates reconnecting' : '○ Connecting live updates'}</span>`;
  const plannerBadge = $('#planner-ai-badge');
  if (plannerBadge) {
    plannerBadge.textContent = planningReady ? `${state.planningAi.provider} key configured` : planningOffline ? 'Planner offline' : 'AI key not loaded';
    plannerBadge.title = planningReady ? `Model: ${state.planningAi.model}. Configuration detected; live requests may still fail.` : '';
    plannerBadge.className = `status-chip ${planningReady ? 'ready' : planningOffline ? 'offline' : 'warning'}`;
  }
}

function renderAssessments() {
  const filter = $('#assessment-filter')?.value || '';
  const assessments = state.assessments.filter(assessment => !filter || assessment.status === filter);
  $('#assessment-list').innerHTML = assessments.length
    ? assessments.map(assessmentRow).join('')
    : 'No assessments match this view.';
  bindComplete();
  bindDeleteAssessments();
  bindEditAssessments();
}

function planMarkup(plan, dashboard = false, snapshot = false) {
  if (!plan) return '<p>No plan yet. Add your assessments and availability to get started.</p>';
  const blocks = !snapshot && plan.id === state.plan?.id ? calendarItems().filter(item => item.planId === plan.id) : [];
  const planned = (!snapshot && state.calendarPlanId === plan.id) || blocks.length ? blocks.map(item => ({...item, calendarId: item.id, date: item.startAt.slice(0, 10),
    allocatedMinutes: StudentWorkflow.minutesBetween(item.startAt, item.endAt)})) : plan.items || [];
  const items = planned.filter(item => !dashboard || (item.date >= accountToday() && item.status !== 'COMPLETED'
    && (!item.endAt || item.endAt > StudentWorkflow.now(accountTimezone()))));
  if (!items.length) return '<p>No upcoming study blocks in this plan. Review your assessments and availability to plan your next seven days.</p>';
  const visible = dashboard ? items.slice(0, 4) : items;
  return `<p class="plan-horizon">${esc(plan.startDate)} – ${esc(plan.endDate)} · ${items.reduce((sum, item) => sum + item.allocatedMinutes, 0)} minutes</p>`
    + (plan.unscheduledMinutes > 0 ? `<p class="feedback-message info">${plan.unscheduledMinutes} minutes of estimated work remain outside this plan. Add more available time or plan another period before your deadlines.</p>` : '')
    + visible.map(item => `<div class="row">${dateCard(item.date, null)}<div class="body"><strong>${esc(item.title)}</strong><br><small>${esc(subjectName(item.subjectId))} · ${item.allocatedMinutes} minutes${item.startAt ? ` · ${formatTime(item.startAt)}` : ''}${item.repetitionStage > 0 ? ' · Review' : ''}</small></div>${item.status === 'COMPLETED' ? '<span class="pill">Completed</span>' : ''}${item.calendarId ? `<button class="link" data-calendar-id="${esc(item.calendarId)}">View / edit</button>` : ''}</div>`).join('');
}

function renderPlan(target) {
  $(target).innerHTML = planMarkup(state.plan, target === '#dashboard-plan');
}

function bindComplete() {
  $$('.complete:not([data-bound])').forEach(button => {
    button.dataset.bound = 'true';
    button.addEventListener('click', async () => {
      setBusy(button, true, 'Saving');
      const target = state.currentView === 'dashboard' ? 'dashboard-feedback' : 'assessment-feedback';
      feedback(target, 'Marking assessment complete…', 'info');
      try {
        await request(`${API.assessments}/assessments/${button.dataset.id}/complete`, { method: 'POST' });
        feedback(target, 'Assessment marked complete. Regenerate your plan to rebalance the week.', 'success');
        await load();
      } catch (error) {
    if (error.staleSession) return;
        feedback(target, error.message, 'error');
        setBusy(button, false);
      }
    });
  });
}

function bindDeleteAssessments() {
  $$('.remove-saved-assessment:not([data-bound])').forEach(button => {
    button.dataset.bound = 'true';
    button.addEventListener('click', async () => {
      if (!window.confirm('Remove this incorrect assessment? This cannot be undone.')) return;
      const target = state.currentView === 'dashboard' ? 'dashboard-feedback' : 'assessment-feedback';
      setBusy(button, true, 'Removing');
      try {
        await request(`${API.assessments}/assessments/${button.dataset.id}`, { method: 'DELETE' });
        feedback(target, 'Assessment removed.', 'success');
        await load();
      } catch (error) {
    if (error.staleSession) return;
        feedback(target, error.message, 'error');
        setBusy(button, false);
      }
    });
  });
}

function bindEditAssessments() {
  $$('.edit-assessment:not([data-bound])').forEach(button => {
    button.dataset.bound = 'true';
    button.addEventListener('click', () => openAssessmentDialog(
      state.assessments.find(assessment => assessment.id === button.dataset.id)));
  });
}

function deadlineHint(input) {
  let hint = input.parentElement.querySelector('.deadline-hint');
  if (!hint) { hint = document.createElement('small');hint.className = 'deadline-hint';input.parentElement.append(hint); }
  hint.textContent = input.value && input.value < accountToday() ? 'Past deadline: check the year. This stays visible as overdue and will not receive new study blocks. Keep it only if it is the real deadline.' : '';
}
document.addEventListener('input', event => {
  if (event.target.matches('.due-date, #assessment-due-date')) deadlineHint(event.target);
});

function openAssessmentDialog(assessment = null) {
  if (!assessment && !state.subjects.length) {
    feedback('assessment-feedback', 'Add a subject before creating an assessment.', 'error');
    return;
  }
  $('#assessment-id').value = assessment?.id || '';
  $('#assessment-subject').value = assessment?.subjectId || state.subjects[0]?.id || '';
  $('#assessment-subject').disabled = Boolean(assessment);
  $('#assessment-title').value = assessment?.title || '';
  $('#assessment-type').value = assessment?.type || '';
  $('#assessment-weight').value = assessment?.weighting ?? '';
  $('#assessment-priority').value = assessment?.priority || 'MEDIUM';
  $('#assessment-due-date').value = assessment?.dueDate || '';
  deadlineHint($('#assessment-due-date'));
  $('#assessment-due-week').value = assessment?.dueWeek ?? '';
  $('#assessment-minutes').value = assessment?.estimatedMinutes ?? '';
  $('#assessment-description').value = assessment?.description || '';
  $('#assessment-dialog-title').textContent = assessment ? 'Edit assessment' : 'Add assessment';
  feedback('assessment-dialog-feedback', '');
  $('#assessment-dialog').showModal();
  $('#assessment-title').focus();
}

$('#new-assessment').addEventListener('click', () => openAssessmentDialog());
$('#close-assessment-dialog').addEventListener('click', () => $('#assessment-dialog').close());
$('#cancel-assessment-dialog').addEventListener('click', () => $('#assessment-dialog').close());
$('#assessment-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  const id = $('#assessment-id').value;
  const payload = {
    title: $('#assessment-title').value.trim(), type: $('#assessment-type').value.trim() || null,
    weighting: num($('#assessment-weight').value), priority: $('#assessment-priority').value,
    dueDate: $('#assessment-due-date').value || null, dueWeek: num($('#assessment-due-week').value),
    estimatedMinutes: num($('#assessment-minutes').value),
    description: $('#assessment-description').value.trim() || null
  };
  if (!id) payload.subjectId = $('#assessment-subject').value;
  setBusy(button, true, 'Saving');
  try {
    await request(`${API.assessments}/assessments${id ? `/${id}` : ''}`, {
      method: id ? 'PATCH' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
    });
    $('#assessment-dialog').close();
    await load();
    const target = state.currentView === 'dashboard' ? 'dashboard-feedback' : 'assessment-feedback';
    feedback(target, id ? 'Assessment updated.' : 'Assessment added.', 'success');
  } catch (error) {
    if (error.staleSession) return; feedback('assessment-dialog-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

$('#assessment-filter').addEventListener('change', renderAssessments);

$('#close-subject-dialog').addEventListener('click', () => $('#subject-dialog').close());
$('#cancel-subject-dialog').addEventListener('click', () => $('#subject-dialog').close());
$('#subject-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  const id = $('#subject-id').value;
  setBusy(button, true, 'Saving');
  try {
    await request(`${API.subjects}/subjects/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code: $('#subject-code').value.trim(),
        name: $('#subject-name').value.trim(),
        creditPoints: num($('#subject-credit-points').value),
        weeklyStudyTargetMinutes: Number($('#subject-weekly-target').value)
      })
    });
    $('#subject-dialog').close();
    await load();
    feedback('subject-feedback', 'Subject updated. Live progress will use the new weekly target.', 'success');
  } catch (error) {
    if (error.staleSession) return;
    feedback('subject-dialog-feedback', error.message, 'error');
  } finally {
    setBusy(button, false);
  }
});

function setSubjectMethod(method) {
  const uploading = method === 'upload';
  $('#upload-panel').hidden = !uploading;
  $('#manual-panel').hidden = uploading;
  $('#upload-tab').classList.toggle('active', uploading);
  $('#manual-tab').classList.toggle('active', !uploading);
  $('#upload-tab').setAttribute('aria-selected', String(uploading));
  $('#manual-tab').setAttribute('aria-selected', String(!uploading));
}

$('#upload-tab').addEventListener('click', () => setSubjectMethod('upload'));
$('#manual-tab').addEventListener('click', () => setSubjectMethod('manual'));

function manualAssessmentEditor(assessment, index) {
  return `<div class="assessment-edit manual-assessment-edit" data-index="${index}">
    <label>Title<input class="title" value="${esc(assessment.title)}" required placeholder="Assessment title"></label>
    <label>Type<input class="type" value="${esc(assessment.type || 'Assessment')}" placeholder="Type"></label>
    <label>Weight %<input class="weight" type="number" min="0" max="100" step="0.1" value="${esc(assessment.weighting)}"></label>
    <label>Due date<input class="due-date" type="date" value="${esc(assessment.dueDate)}"></label>
    <label>Due week<input class="due-week" type="number" min="1" max="52" value="${esc(assessment.dueWeek)}"></label>
    <label>Est. hours<input class="hours" type="number" min="0" step="0.1" value="${esc(assessment.estimatedHours)}"></label>
    <button class="icon-button remove-manual-assessment" type="button" data-index="${index}" aria-label="Remove assessment">×</button>
  </div>`;
}

function syncManualAssessments() {
  state.manualAssessments = $$('.manual-assessment-edit').map(row => ({
    title: row.querySelector('.title').value.trim(),
    type: row.querySelector('.type').value.trim() || null,
    weighting: num(row.querySelector('.weight').value),
    dueDate: row.querySelector('.due-date').value || null,
    dueWeek: num(row.querySelector('.due-week').value),
    estimatedHours: num(row.querySelector('.hours').value),
    description: null,
    confidence: null,
    warning: null
  }));
}

function renderManualAssessments() {
  $('#manual-assessments').innerHTML = state.manualAssessments.map(manualAssessmentEditor).join('');
  $$('.remove-manual-assessment').forEach(button => button.addEventListener('click', () => {
    syncManualAssessments();
    state.manualAssessments.splice(Number(button.dataset.index), 1);
    renderManualAssessments();
  }));
}

$('#manual-add-assessment').addEventListener('click', () => {
  syncManualAssessments();
  state.manualAssessments.push({ title: '', type: 'Assessment' });
  renderManualAssessments();
  $$('.manual-assessment-edit .title').at(-1)?.focus();
});

$('#manual-subject-form').addEventListener('submit', async event => {
  event.preventDefault();
  syncManualAssessments();
  const button = event.submitter;
  setBusy(button, true, 'Saving');
  feedback('manual-feedback', 'Saving the subject and any assessments…', 'info');
  try {
    await request(`${API.subjects}/subjects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code: $('#m-code').value.trim(),
        name: $('#m-name').value.trim(),
        creditPoints: Number($('#m-cp').value),
        weeklyStudyTargetMinutes: Number($('#m-target').value),
        assessments: state.manualAssessments
      })
    });
    event.currentTarget.reset();
    $('#m-cp').value = 6;
    $('#m-target').value = 240;
    state.manualAssessments = [];
    renderManualAssessments();
    await load();
    show('subjects');
    feedback('subject-feedback', 'Subject added successfully.', 'success');
  } catch (error) {
    if (error.staleSession) return;
    feedback('manual-feedback', error.message, 'error');
  } finally {
    setBusy(button, false);
  }
});

const dropZone = $('#outline-drop');
const fileInput = $('#outline-file');
const extractButton = $('#extract-btn');
let dragDepth = 0;

function validateDocument(file) {
  if (!file) return 'Choose or drop a document first.';
  const extension = file.name.toLowerCase().split('.').pop();
  if (!['pdf', 'docx', 'txt', 'jpg', 'jpeg'].includes(extension)) return 'Choose a PDF, DOCX, TXT, JPG or JPEG subject outline.';
  if (file.size > 15_000_000) return 'The document must be 15 MB or smaller.';
  if (file.size === 0) return 'The selected document is empty.';
  return null;
}

function uploadItemId(file) {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

function addFiles(fileList, source) {
  const incoming = [...(fileList || [])];
  if (!incoming.length) return;
  const errors = [];
  const existing = new Set(state.uploadItems.map(item => item.id));
  for (const file of incoming) {
    const error = validateDocument(file);
    if (error) {
      errors.push(`${file.name}: ${error}`);
      continue;
    }
    const id = uploadItemId(file);
    if (existing.has(id)) continue;
    if (state.uploadItems.length >= 10) {
      errors.push('A maximum of 10 files can be queued at once.');
      break;
    }
    state.uploadItems.push({ id, file, source, status: 'ready', error: null, importId: null });
    existing.add(id);
  }
  fileInput.value = '';
  renderUploadQueue();
  const ready = state.uploadItems.filter(item => item.status === 'ready').length;
  if (errors.length) {
    feedback('upload-feedback', errors.join(' '), 'error');
  } else if (ready) {
    feedback('upload-feedback', `${ready} ${ready === 1 ? 'document is' : 'documents are'} ready to analyse.`, 'success');
  }
}

function uploadStatus(item) {
  return {
    ready: 'Ready', analysing: 'Analysing', complete: 'Review ready', confirmed: 'Saved', error: 'Retry'
  }[item.status] || 'Ready';
}

function renderUploadQueue() {
  const target = $('#file-summary');
  target.hidden = !state.uploadItems.length;
  target.innerHTML = state.uploadItems.map(item => `<div class="file-summary">
    <div class="file-symbol" aria-hidden="true">${esc(item.file.name.split('.').pop().toUpperCase())}</div>
    <div class="file-body"><strong>${esc(item.file.name)}</strong><small>${(item.file.size / 1024 / 1024).toFixed(2)} MB · ${esc(item.error || item.source)}</small></div>
    <span class="file-status ${esc(item.status)}">${esc(uploadStatus(item))}</span>
    ${['ready', 'error'].includes(item.status) ? `<button class="icon-button remove-upload-file" type="button" data-file-id="${esc(item.id)}" aria-label="Remove ${esc(item.file.name)}">×</button>` : ''}
  </div>`).join('');
  $$('.remove-upload-file').forEach(button => button.addEventListener('click', () => {
    state.uploadItems = state.uploadItems.filter(item => item.id !== button.dataset.fileId);
    renderUploadQueue();
  }));
  const retryable = state.uploadItems.filter(item => ['ready', 'error'].includes(item.status)).length;
  extractButton.disabled = !retryable;
  extractButton.dataset.idleText = retryable > 1 ? `Analyse ${retryable} documents with Gemini` : 'Analyse with Gemini';
  if (!extractButton.getAttribute('aria-busy') || extractButton.getAttribute('aria-busy') === 'false') {
    extractButton.textContent = extractButton.dataset.idleText;
  }
  $('#clear-file-btn').hidden = !state.uploadItems.length;
  dropZone.classList.toggle('has-file', Boolean(state.uploadItems.length));
}

function clearSelectedFiles() {
  state.uploadItems = [];
  state.reviewQueue = [];
  state.review = null;
  fileInput.value = '';
  $('#review').replaceChildren();
  renderUploadQueue();
  dropZone.classList.remove('has-file', 'dragging');
  feedback('upload-feedback', 'Choose or drop up to 10 PDF, DOCX, TXT, JPG or JPEG files when you are ready.', 'info');
}

fileInput.addEventListener('change', () => addFiles(fileInput.files, 'selected from this device'));
$('#clear-file-btn').addEventListener('click', clearSelectedFiles);
dropZone.addEventListener('keydown', event => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    fileInput.click();
  }
});
dropZone.addEventListener('dragenter', event => {
  event.preventDefault();
  dragDepth += 1;
  dropZone.classList.add('dragging');
  feedback('upload-feedback', 'Release the document to add it.', 'info');
});
dropZone.addEventListener('dragover', event => {
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
});
dropZone.addEventListener('dragleave', event => {
  event.preventDefault();
  dragDepth = Math.max(0, dragDepth - 1);
  if (dragDepth === 0) dropZone.classList.remove('dragging');
});
dropZone.addEventListener('drop', event => {
  event.preventDefault();
  dragDepth = 0;
  dropZone.classList.remove('dragging');
  addFiles(event.dataTransfer?.files, 'dropped into Study Leftovers');
});
document.addEventListener('dragover', event => {
  if (event.dataTransfer?.types?.includes('Files')) event.preventDefault();
});
document.addEventListener('drop', event => {
  if (!dropZone.contains(event.target)) event.preventDefault();
});

extractButton.addEventListener('click', async () => {
  const epoch = state.sessionEpoch;
  const items = state.uploadItems.filter(item => ['ready', 'error'].includes(item.status));
  if (!items.length) return feedback('upload-feedback', 'Add at least one document to analyse.', 'error');
  setBusy(extractButton, true, `Analysing 0 of ${items.length}`);
  $('#upload-progress').hidden = false;
  state.reviewQueue = state.reviewQueue || [];
  let succeeded = 0;
  for (let index = 0; index < items.length; index += 1) {
    const item = items[index];
    item.status = 'analysing';
    item.error = null;
    renderUploadQueue();
    setBusy(extractButton, true, `Analysing ${index + 1} of ${items.length}`);
    $('#upload-progress-detail').textContent = `${item.file.name} · extracting locally before Gemini analyses it.`;
    feedback('upload-feedback', `Analysing ${item.file.name}…`, 'info');
    const data = new FormData();
    data.append('file', item.file);
    try {
      const review = await request(`${API.subjects}/subject-outlines`, { method: 'POST', body: data });
      item.status = 'complete';
      item.importId = review.importId;
      state.reviewQueue.push(review);
      succeeded += 1;
    } catch (requestError) {
      if (requestError.staleSession || state.sessionEpoch !== epoch) { setBusy(extractButton, false); return; }
      item.status = 'error';
      item.error = requestError.message;
    }
    renderUploadQueue();
  }
  $('#upload-progress').hidden = true;
  setBusy(extractButton, false);
  renderUploadQueue();
  if (!state.review && state.reviewQueue.length) {
    state.review = state.reviewQueue.shift();
    renderReview();
    $('#review').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  const failed = items.length - succeeded;
  feedback('upload-feedback', failed
    ? `${succeeded} ${succeeded === 1 ? 'document is' : 'documents are'} ready for review; ${failed} failed. Use Retry after checking each message.`
    : `${succeeded} ${succeeded === 1 ? 'document is' : 'documents are'} ready. Review and confirm each subject below.`,
  failed ? 'error' : 'success');
});

function renderReview() {
  const extraction = state.review?.extraction;
  if (!extraction) return;
  extraction.assessments = extraction.assessments || [];
  const warnings = [...(extraction.warnings || []), ...(extraction.assessments || [])
    .filter(item => item.dueDate && item.dueDate < accountToday())
    .map(item => `${item.title}: the extracted deadline (${item.dueDate}) is in the past. Check the year and date before confirming. It will stay visible as overdue until you update it or mark it complete.`)];
  $('#review').innerHTML = `<div class="review-card">
    <p class="eyebrow">REVIEW REQUIRED</p>
    <h3>Check ${esc(state.review.filename || 'the extracted information')}</h3>
    <p class="review-intro">Nothing is permanent yet. Correct missing or uncertain fields, then confirm.${state.reviewQueue.length ? ` ${state.reviewQueue.length} more ${state.reviewQueue.length === 1 ? 'subject is' : 'subjects are'} waiting.` : ''}</p>
    <div class="review-grid">
      <label>Subject code<input id="r-code" value="${esc(extraction.subjectCode)}" placeholder="e.g. CSCI318"></label>
      <label>Subject name<input id="r-name" value="${esc(extraction.subjectName)}" placeholder="Subject name"></label>
      <label>Credit points<input id="r-cp" type="number" min="1" value="${esc(extraction.creditPoints ?? 6)}"></label>
      <label>Weekly target (minutes)<input id="r-target" type="number" min="0" max="10080" value="${esc(state.review.weeklyStudyTargetMinutes ?? 240)}"></label>
    </div>
    <div class="review-section-head"><h3>Assessments</h3><button id="add-assessment-btn" class="secondary" type="button">＋ Add assessment</button></div>
    <div id="r-assessments">${extraction.assessments.map(assessmentEditor).join('')}</div>
    ${warnings.length ? `<ul class="warning-list">${warnings.map(warning => `<li>${esc(warning)}</li>`).join('')}</ul>` : ''}
    <div id="review-feedback" class="feedback" aria-live="polite"></div>
    <button id="confirm-btn" class="primary">${state.reviewQueue.length ? 'Save subject and review next' : 'Confirm subject and assessments'}</button>
  </div>`;
  $('#confirm-btn').addEventListener('click', confirmReview);
  $('#add-assessment-btn').addEventListener('click', () => {
    syncReviewDraft();
    state.review.extraction.assessments.push({
      title: '', type: 'Assessment', weighting: null, dueDate: null, dueWeek: null,
      description: null, estimatedHours: null, confidence: null, warning: null
    });
    renderReview();
    $$('#r-assessments .title').at(-1)?.focus();
  });
  $$('.remove-assessment').forEach(button => {
    button.addEventListener('click', () => {
      syncReviewDraft();
      state.review.extraction.assessments.splice(Number(button.dataset.index), 1);
      renderReview();
      feedback('review-feedback', 'Assessment removed from this review.', 'info');
    });
  });
}

function assessmentEditor(assessment, index) {
  return `<div class="assessment-edit" data-index="${index}">
    <label>Title<input class="title" value="${esc(assessment.title)}" placeholder="Assessment title"></label>
    <label>Type<input class="type" value="${esc(assessment.type || 'Assessment')}" placeholder="Type"></label>
    <label>Weight %<input class="weight" type="number" min="0" max="100" step="0.1" value="${esc(assessment.weighting)}"></label>
    <label>Due date<input class="due-date" type="date" value="${esc(assessment.dueDate)}"></label>
    <label>Due week<input class="due-week" type="number" min="1" max="52" value="${esc(assessment.dueWeek)}"></label>
    <label>Est. hours<input class="hours" type="number" min="0" step="0.1" value="${esc(assessment.estimatedHours)}"></label>
    <button class="icon-button remove-assessment" type="button" data-index="${index}" aria-label="Remove ${esc(assessment.title || 'assessment')}">×</button>
  </div>`;
}

function syncReviewDraft() {
  if (!state.review?.extraction) return;
  const current = state.review.extraction;
  const rows = $$('#r-assessments .assessment-edit');
  current.subjectCode = $('#r-code')?.value ?? current.subjectCode;
  current.subjectName = $('#r-name')?.value ?? current.subjectName;
  current.creditPoints = num($('#r-cp')?.value ?? '');
  state.review.weeklyStudyTargetMinutes = num($('#r-target')?.value ?? '') ?? 240;
  current.assessments = rows.map(row => {
    const original = current.assessments[Number(row.dataset.index)] || {};
    return {
      ...original,
      title: row.querySelector('.title').value.trim(),
      type: row.querySelector('.type').value.trim() || null,
      weighting: num(row.querySelector('.weight').value),
      dueDate: row.querySelector('.due-date').value || null,
      dueWeek: num(row.querySelector('.due-week').value),
      estimatedHours: num(row.querySelector('.hours').value)
    };
  });
}

async function confirmReview() {
  const epoch = state.sessionEpoch;
  syncReviewDraft();
  const button = $('#confirm-btn');
  const weeklyTarget = state.review.weeklyStudyTargetMinutes;
  const body = { weeklyStudyTargetMinutes: weeklyTarget, extraction: state.review.extraction };
  const confirmedImportId = state.review.importId;
  const confirmedName = state.review.filename || body.extraction.subjectCode;
  setBusy(button, true, 'Confirming');
  feedback('review-feedback', body.extraction.assessments.length
    ? 'Saving the subject, then confirming assessments with Assessment Service…'
    : 'Saving the subject without assessments…', 'info');
  try {
    await request(`${API.subjects}/subject-outlines/${confirmedImportId}/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    const uploadItem = state.uploadItems.find(item => item.importId === confirmedImportId);
    if (uploadItem) uploadItem.status = 'confirmed';
    state.review = null;
    $('#review').replaceChildren();
    await load();
    if (state.sessionEpoch !== epoch) return;
    if (state.reviewQueue.length) {
      state.review = state.reviewQueue.shift();
      renderUploadQueue();
      renderReview();
      feedback('upload-feedback', `${confirmedName} was saved. ${state.reviewQueue.length + 1} ${state.reviewQueue.length ? 'subjects remain' : 'subject remains'} to review.`, 'success');
      $('#review').scrollIntoView({ behavior: 'smooth', block: 'start' });
    } else {
      const savedCount = state.uploadItems.filter(item => item.status === 'confirmed').length;
      state.uploadItems = state.uploadItems.filter(item => item.status === 'error');
      renderUploadQueue();
      show('subjects');
      feedback('subject-feedback', `${savedCount} ${savedCount === 1 ? 'subject was' : 'subjects were'} added successfully.`, 'success');
    }
  } catch (error) {
    if (error.staleSession) return;
    feedback('review-feedback', error.message, 'error');
    setBusy(button, false);
  }
}

function updateActivityAssessments() {
  const selected = $('#activity-assessment').value;
  $('#activity-assessment').innerHTML = '<option value="">General subject study</option>' + state.assessments
    .filter(item => item.subjectId === $('#activity-subject').value)
    .map(item => `<option value="${esc(item.id)}">${esc(item.title)}</option>`).join('');
  if ([...$('#activity-assessment').options].some(option => option.value === selected)) $('#activity-assessment').value = selected;
}
$('#activity-subject').addEventListener('change', updateActivityAssessments);

function renderSessions() {
  const completed = calendarItems().filter(item => !item.source && item.status === 'COMPLETED' && item.subjectId && item.type !== 'TASK')
    .map(item => ({id: item.id, calendar: true, subjectId: item.subjectId, description: item.title,
      studyDate: item.completedAt ? StudentWorkflow.now(accountTimezone(), new Date(item.completedAt)).slice(0, 10) : item.startAt.slice(0, 10), durationMinutes: StudentWorkflow.minutesBetween(item.startAt, item.endAt)}));
  const sessions = [...state.sessions, ...completed].sort((a, b) => b.studyDate.localeCompare(a.studyDate)
    || (b.recordedAt || '').localeCompare(a.recordedAt || ''));
  for (const [selector, limit] of [['#session-history', 10], ['#dashboard-sessions', 5], ['#activity-history', Infinity]]) {
    const target = $(selector);
    target.classList.toggle('empty', !sessions.length);
    target.innerHTML = sessions.length ? sessions.slice(0, limit).map(session =>
      `<div class="row"><div class="body"><strong>${esc(session.description)}</strong><br><small>${esc(subjectName(session.subjectId))} · ${esc(session.studyDate)}${session.calendar ? ' · Completed calendar block' : session.assessmentId ? ' · Assessment study' : ' · General study'}</small></div><span class="pill">${session.durationMinutes} min</span><button class="link" ${session.calendar ? `data-calendar-id="${esc(session.id)}"` : `data-session-id="${esc(session.id)}"`}>${session.calendar ? 'View' : 'View / edit'}</button></div>`).join('') : 'No study activity recorded yet.';
  }
  $$('[data-session-id]').forEach(button => button.addEventListener('click', () => openSessionDialog(button.dataset.sessionId)));
}

function openSessionDialog(id) {
  const session = state.sessions.find(item => item.id === id);
  if (!session) return;
  $('#session-id').value = id;
  $('#session-date').value = session.studyDate;
  $('#session-date').max = accountToday();
  $('#session-minutes').value = session.durationMinutes;
  $('#session-description').value = session.description;
  $('#session-subject-label').textContent = `${subjectName(session.subjectId)} · ${state.assessments.find(a => a.id === session.assessmentId)?.title || 'General subject study'}`;
  feedback('session-feedback', '');
  $('#session-dialog').showModal();
}
$('#close-session-dialog').addEventListener('click', () => $('#session-dialog').close());
$('#session-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  setBusy(button, true, 'Saving');
  try {
    await request(`${API.activity}/study-sessions/${$('#session-id').value}`, {method: 'PATCH', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({studyDate: $('#session-date').value, durationMinutes: Number($('#session-minutes').value), description: $('#session-description').value})});
    $('#session-dialog').close();
    await load();
  } catch (error) { if (!error.staleSession) feedback('session-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});
$('#delete-session').addEventListener('click', async event => {
  if (!confirm('Remove this recorded session and its contribution to your progress?')) return;
  const button = event.currentTarget;
  setBusy(button, true, 'Removing');
  try {
    await request(`${API.activity}/study-sessions/${$('#session-id').value}`, {method: 'DELETE'});
    $('#session-dialog').close();
    await load();
  } catch (error) { if (!error.staleSession) feedback('session-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

$('#activity-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = event.submitter || form.querySelector('button');
  setBusy(button, true, 'Recording');
  feedback('activity-feedback', 'Recording your study session…', 'info');
  try {
    await request(`${API.activity}/study-sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subjectId: $('#activity-subject').value,
        assessmentId: $('#activity-assessment').value || null,
        durationMinutes: Number($('#activity-minutes').value),
        studyDate: $('#activity-date').value,
        description: $('#activity-description').value
      })
    });
    feedback('activity-feedback', 'Session saved in your activity history and calendar. Progress updates automatically.', 'success');
    form.reset();
    $('#activity-date').value = accountToday();
    await load();
  } catch (error) {
    if (error.staleSession) return;
    feedback('activity-feedback', error.message, 'error');
  } finally {
    setBusy(button, false);
  }
});

async function generatePlan(availability, button, availabilitySlots, previousId = null) {
  if (state.planPending) return;
  state.planPending = true;
  const accountToken = state.token;
  const start = $('#plan-start').value;
  setBusy(button, true, previousId ? 'Regenerating' : 'Generating');
  ['#generate-chat-plan', '#regenerate-plan', '#plan-form button'].forEach(selector => { $(selector).disabled = true; });
  feedback('plan-feedback', 'Building a seven-day plan from remaining work, due dates and your availability…', 'info');
  try {
    const generated = await request(`${API.planning}/planning/plans${previousId ? `/${encodeURIComponent(previousId)}/regenerate` : ''}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ startDate: start, dailyAvailabilityMinutes: availability, availabilitySlots })
    });
    if (state.token !== accountToken) return;
    state.planSelectionRequest = (state.planSelectionRequest || 0) + 1;
    state.plan = generated;
    state.planHistory = [state.plan, ...(state.planHistory || []).filter(plan => plan.id !== state.plan.id)];
    await loadCalendar();
    if (state.token !== accountToken) return;
    feedback('plan-feedback', state.plan.items?.length ? `Your plan is ready through ${state.plan.endDate}. Open Schedule to see your study blocks.` : 'There is no remaining study workload. Previous planned blocks have been cleared.', 'success');
    render();
  } catch (error) {
    if (error.staleSession || state.token !== accountToken) return;
    feedback('plan-feedback', error.message, 'error');
  } finally {
    if (state.token === accountToken) {
      state.planPending = false;
      setBusy(button, false);
      $('#plan-form button').disabled = false;
      $('#regenerate-plan').disabled = !state.plan?.id;
      $('#generate-chat-plan').disabled = !state.availabilityReady || !Object.values(state.availabilityMinutes || {}).some(minutes => minutes > 0);
    }
  }
}

$('#plan-form').addEventListener('submit', event => {
  event.preventDefault();
  if (state.planPending || state.availabilityPending) return;
  const start = $('#plan-start').value;
  const minutes = Number($('#plan-minutes').value);
  const availability = {};
  for (let day = 0; day < 7; day++) {
    const date = new Date(`${start}T00:00:00`);
    date.setDate(date.getDate() + day);
    const key = localDate(date);
    const now = StudentWorkflow.now(accountTimezone());
    const left = Math.max(0, 1439 - Number(now.slice(11,13))*60 - Number(now.slice(14,16)));
    availability[key] = key === now.slice(0,10) ? Math.min(minutes, left) : minutes;
  }
  state.availability = null;
  state.availabilityMinutes = availability;
  state.availabilityReady = true;
  renderAvailability();
  feedback('chat-feedback', 'Daily amounts added. Today is limited to the time remaining. Review the dates, then build or update your plan.', 'success');
});

function renderPlanHistory() {
  const history = $('#plan-history');
  const failed = state.serviceFailures?.includes('planHistory');
  history.innerHTML = (state.planHistory || []).map(plan =>
    `<button class="history-card ${plan.id === state.historyPlan?.id ? 'selected' : ''}" data-history-id="${esc(plan.id)}" aria-pressed="${plan.id === state.historyPlan?.id}"><strong>${plan.id === state.plan?.id ? 'Current plan' : 'Previous plan'} · Version ${plan.version}</strong><span>${esc(plan.startDate)} – ${esc(plan.endDate)}</span><small>${plan.items?.length || 0} study blocks · View details</small></button>`).join('') || `<p>${failed ? 'History could not be loaded.' : 'No saved plans yet.'}</p>`;
  $('#plan-history-help').textContent = failed ? 'History could not be refreshed. Use Refresh history to try again.'
    : 'Preview previous plans here. Your current plan and calendar stay unchanged. Failed attempts do not create saved plans.';
  $('#history-preview').innerHTML = state.historyPlan
    ? `<h4>Version ${state.historyPlan.version} · Saved snapshot</h4>${planMarkup(state.historyPlan, false, true)}<button id="reuse-availability" class="secondary" ${state.historyPlan.availability ? '' : 'disabled'}>Use these study times for a new plan</button><p class="hint">${state.historyPlan.availability ? 'Times are matched to the same weekdays in your next seven days. Review them before building.' : 'This older version has no saved availability. Enter fresh study times on the Study plan page.'}</p>` : '';
  $('#reuse-availability')?.addEventListener('click', reuseHistoryAvailability);
  $('#regenerate-plan').disabled = !!state.planPending || !state.plan?.id;
}

function reuseHistoryAvailability() {
  const saved = state.historyPlan?.availability;
  if (!saved) return;
  const start = accountToday();
  $('#plan-start').value = start;
  clearAvailability('Saved study times loaded for your next seven days. Review the dates and times before building.');
  const reused = StudentWorkflow.reuse(saved, start, StudentWorkflow.now(accountTimezone()));
  state.availability = reused.slots;
  state.availabilityMinutes = reused.minutes;
  state.availabilityReady = Object.values(reused.minutes).some(minutes => minutes > 0);
  renderAvailability();
  show('plan');
}

async function selectPlan(id) {
  const accountToken = state.token;
  const selectionId = state.planSelectionRequest = (state.planSelectionRequest || 0) + 1;
  try {
    const selected = await request(`${API.planning}/planning/plans/${encodeURIComponent(id)}`);
    if (state.token !== accountToken || state.planSelectionRequest !== selectionId) return;
    state.historyPlan = selected;
    render();
  } catch (error) {
    if (error.staleSession || state.token !== accountToken || state.planSelectionRequest !== selectionId) return;
    feedback('history-feedback', error.message, 'error');
    renderPlanHistory();
  }
}

async function regenerateSelectedPlan(button) {
  if (!state.plan?.id) return;
  if (state.availabilityPending || !state.availabilityReady) {
    feedback('plan-feedback', 'Confirm your study times above, or choose a daily amount using quick setup. Saved times can be reused from Profile → Plan history.', 'info');
    return;
  }
  await generatePlan(state.availabilityMinutes, button, state.availability, state.plan.id);
}

async function refreshPlanHistory(button) {
  const accountToken = state.token;
  setBusy(button, true, 'Refreshing');
  try {
    const history = await request(`${API.planning}/planning/plans`);
    if (state.token !== accountToken) return;
    // Versions are immutable. Keep a version generated while this read was in flight.
    state.planHistory = [...new Map([...(state.planHistory || []), ...history].map(plan => [plan.id, plan])).values()]
      .sort((a, b) => b.version - a.version);
    state.serviceFailures = (state.serviceFailures || []).filter(name => name !== 'planHistory');
    if (!state.plan && history.length) state.plan = history[0];
    render();
  } catch (error) {
    if (error.staleSession || state.token !== accountToken) return;
    state.serviceFailures = [...new Set([...(state.serviceFailures || []), 'planHistory'])];
    renderPlanHistory();
    feedback('plan-feedback', error.message, 'error');
  } finally {
    if (state.token === accountToken) setBusy(button, false);
  }
}

$('#plan-history').addEventListener('click', event => {
  const card = event.target.closest('[data-history-id]');
  if (card) selectPlan(card.dataset.historyId);
});
$('#refresh-plan-history').addEventListener('click', event => refreshPlanHistory(event.currentTarget));
$('#regenerate-plan').addEventListener('click', event => regenerateSelectedPlan(event.currentTarget));

function appendChatMessage(role, message) {
  const bubble = document.createElement('div');
  bubble.className = `chat-bubble ${role}`;
  bubble.textContent = message;
  $('#chat-messages').append(bubble);
  bubble.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function renderAvailability() {
  const start = $('#plan-start').value || accountToday();
  if ($('#slot-date')) {
    $('#slot-date').min = start;
    $('#slot-date').max = StudentWorkflow.add(start, 6);
    if (!$('#slot-date').value || $('#slot-date').value < start || $('#slot-date').value > $('#slot-date').max) $('#slot-date').value = start;
  }
  const rows = Object.entries(state.availability || {}).filter(([, slots]) => slots?.length);
  $('#availability-summary').classList.toggle('empty', rows.length === 0);
  $('#availability-summary').innerHTML = rows.length ? rows.map(([date, slots]) => {
    const label = new Date(`${date}T00:00:00`).toLocaleDateString('en-AU', { weekday: 'long', day: 'numeric', month: 'short' });
    const times = slots.map((slot, index) => `<span class="availability-slot">${esc(slot.start.slice(0, 5))}–${esc(slot.end.slice(0, 5))}<button type="button" class="link" data-remove-slot="${esc(date)}" data-slot-index="${index}" aria-label="Remove ${esc(date)} ${esc(slot.start.slice(0,5))}">Remove</button></span>`).join('');
    return `<div class="availability-day"><div><strong>${esc(label)}</strong><small>${times}</small></div><span>${state.availabilityMinutes[date] || 0} min</span></div>`;
  }).join('') : state.availability === null && Object.keys(state.availabilityMinutes || {}).length ? Object.entries(state.availabilityMinutes).map(([date, minutes]) => `<div class="availability-day"><strong>${esc(date)}</strong><span>${minutes} min · flexible time</span></div>`).join('') : 'No times added yet.';
  $('#generate-chat-plan').disabled = !!state.planPending || !state.availabilityReady || !Object.values(state.availabilityMinutes || {}).some(minutes => minutes > 0);
}

$('#availability-summary').addEventListener('click', event => {
  const button = event.target.closest('[data-remove-slot]');
  if (!button || state.availabilityPending || state.planPending) return;
  const date = button.dataset.removeSlot;
  state.availability[date].splice(Number(button.dataset.slotIndex), 1);
  state.availabilityMinutes[date] = state.availability[date].reduce((total, slot) => total + StudentWorkflow.slotMinutes(slot), 0);
  state.availabilityReady = Object.values(state.availabilityMinutes).some(value=>value>0);
  renderAvailability();feedback('chat-feedback', 'Study time removed. Review the remaining times.', 'info');
});

function clearAvailability(message = 'Your availability has been cleared. Tell me your new study times.') {
  state.availabilityRequest = (state.availabilityRequest || 0) + 1;
  state.availability = {};
  state.availabilityMinutes = {};
  state.availabilityReady = false;
  state.availabilityPending = false;
  $('#availability-message').value = '';
  setBusy($('#availability-chat-form button'), false);
  renderAvailability();
  feedback('chat-feedback', '');
  appendChatMessage('assistant', message);
}

$('#availability-slot-form').addEventListener('submit', event => {
  event.preventDefault();
  const date = $('#slot-date').value, start = $('#slot-start').value, end = $('#slot-end').value;
  const period = $('#plan-start').value;
  if (date < period || date > StudentWorkflow.add(period, 6) || `${date}T${start}:00` <= StudentWorkflow.now(accountTimezone()) || end <= start) {
    feedback('chat-feedback', 'Choose a future time within these seven days, with the end after the start.', 'error');
    return;
  }
  const existing = state.availability?.[date] || [];
  if (existing.some(slot => start < slot.end.slice(0, 5) && end > slot.start.slice(0, 5))) {
    feedback('chat-feedback', 'This time overlaps another availability slot. Choose a separate time or use Start over.', 'error');
    return;
  }
  state.availabilityRequest = (state.availabilityRequest || 0) + 1;
  state.availabilityPending = false;
  if (state.availability === null) { state.availability = {}; state.availabilityMinutes = {}; }
  state.availability[date] = [...existing, {start, end}].sort((a,b) => a.start.localeCompare(b.start));
  state.availabilityMinutes[date] = state.availability[date].reduce((sum, slot) => sum + StudentWorkflow.slotMinutes(slot), 0);
  state.availabilityReady = true;
  setBusy($('#availability-chat-form button'), false);
  renderAvailability();
  feedback('chat-feedback', 'Study time added. Review your availability, then build your plan.', 'success');
});

async function sendAvailability(message, button) {
  if (['start over', 'clear my availability', 'clear all availability', 'clear my availability and let me start again'].includes(message.trim().toLowerCase().replace(/[.!]+$/, ''))) {
    clearAvailability();
    return;
  }
  const accountToken = state.token;
  const weekStart = $('#plan-start').value;
  const requestId = state.availabilityRequest = (state.availabilityRequest || 0) + 1;
  const current = () => state.token === accountToken && state.availabilityRequest === requestId && $('#plan-start').value === weekStart;
  state.availabilityPending = true;
  state.availabilityReady = false;
  renderAvailability();
  appendChatMessage('user', message);
  setBusy(button, true, 'Thinking');
  feedback('chat-feedback', 'Gemini is interpreting those time slots…', 'info');
  try {
    const result = await request(`${API.planning}/planning/availability/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message,
        weekStart,
        availability: state.availability || {}
      })
    });
    if (!current()) return;
    state.availability = result.availability || {};
    state.availabilityMinutes = result.dailyAvailabilityMinutes || {};
    state.availabilityReady = result.readyToPlan === true;
    appendChatMessage('assistant', result.reply);
    renderAvailability();
    feedback('chat-feedback', result.readyToPlan ? 'Availability understood. You can generate the plan.' : 'Add the detail requested above.', result.readyToPlan ? 'success' : 'info');
  } catch (error) {
    if (error.staleSession || !current()) return;
    $('#availability-message').value = message;
    appendChatMessage('assistant', error.message);
    feedback('chat-feedback', error.message, 'error');
  } finally {
    if (current()) {
      state.availabilityPending = false;
      setBusy(button, false);
    }
  }
}

$('#availability-chat-form').addEventListener('submit', async event => {
  event.preventDefault();
  const input = $('#availability-message');
  const message = input.value.trim();
  if (!message) return;
  input.value = '';
  await sendAvailability(message, event.submitter);
});

$$('[data-prompt]').forEach(button => button.addEventListener('click', () => {
  $('#availability-message').value = button.dataset.prompt;
  $('#availability-message').focus();
}));

$('#clear-availability').addEventListener('click', () => clearAvailability());
$('#plan-start').addEventListener('change', () => clearAvailability('The planning week changed, so I cleared the previous time slots. Tell me your availability for this week.'));

$('#generate-chat-plan').addEventListener('click', async event => {
  if (!state.availabilityReady) return;
  await generatePlan(state.availabilityMinutes, event.currentTarget, state.availability);
});

function mondayFor(date) {
  const result = new Date(date);
  result.setHours(0, 0, 0, 0);
  result.setDate(result.getDate() - ((result.getDay() + 6) % 7));
  return result;
}

function addDays(date, days) {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

function monthRange() {
  const from = mondayFor(new Date(state.calendarMonth.getFullYear(), state.calendarMonth.getMonth(), 1));
  return { from, to: addDays(from, 41) };
}

function formatDateTime(value) {
  return new Date(`${value}Z`).toLocaleString('en-AU', { timeZone: 'UTC', weekday: 'short', day: 'numeric', month: 'short',
    hour: 'numeric', minute: '2-digit' });
}

function formatTime(value) {
  return new Date(`${value}Z`).toLocaleTimeString('en-AU', { timeZone: 'UTC', hour: 'numeric', minute: '2-digit' });
}

async function loadCalendar(showErrors = true) {
  if (!state.token) return;
  const token = state.token;
  const sequence = state.calendarRequest = (state.calendarRequest || 0) + 1;
  const plan = state.plan;
  const month = monthRange();
  const weekStart = state.calendarWeek || mondayFor(new Date());
  const from = new Date(Math.min(month.from.getTime(), weekStart.getTime()));
  const to = new Date(Math.max(month.to.getTime(), addDays(weekStart, 6).getTime()));
  try {
    const [entries, completed, planEntries] = await Promise.all([
      request(`${API.planning}/calendar?from=${localDate(from)}&to=${localDate(to)}`),
      request(`${API.planning}/calendar/completed`),
      plan ? request(`${API.planning}/calendar?from=${plan.startDate}&to=${plan.endDate}`) : Promise.resolve([])
    ]);
    if (state.token !== token || state.calendarRequest !== sequence) return;
    state.calendarEntries = entries;
    state.completedCalendar = completed;
    if (state.plan?.id === plan?.id) {
      state.currentPlanCalendar = planEntries;
      state.calendarPlanId = plan?.id || null;
    }
    feedback(state.currentView === 'week' ? 'week-feedback' : 'calendar-feedback', '');
    renderCalendar();
    renderWeek();
    render();
  } catch (error) {
    if (error.staleSession) return;
    if (state.token !== token || state.calendarRequest !== sequence) return;
    if (showErrors) feedback(state.currentView === 'week' ? 'week-feedback' : 'calendar-feedback', error.message, 'error');
  }
}

function entriesForDate(date) {
  const from = new Date(`${date}T00:00:00`);
  const to = new Date(from); to.setDate(to.getDate() + 1);
  return calendarItems().filter(item => new Date(item.startAt) < to && new Date(item.endAt) > from)
    .sort((a, b) => a.startAt.localeCompare(b.startAt));
}

function calendarEvent(item, compact = false, date = null) {
  const timeLabel = item.dateOnly ? (item.type === 'DEADLINE' ? 'Deadline' : `${item.durationMinutes} min recorded`) : date && item.startAt.slice(0, 10) < date ? `Continues until ${formatTime(item.endAt)}` : formatTime(item.startAt);
  return `<button class="calendar-event ${item.type.toLowerCase()} ${item.status.toLowerCase()}" data-calendar-id="${esc(item.id)}" title="${esc(item.title)}"><span>${timeLabel}</span>${compact ? '' : `<strong>${esc(item.title)}</strong>`}</button>`;
}

function renderCalendar() {
  const range = monthRange();
  $('#month-title').textContent = state.calendarMonth.toLocaleDateString('en-AU', { month: 'long', year: 'numeric' });
  const todayValue = accountToday();
  $('#month-grid').innerHTML = Array.from({ length: 42 }, (_, index) => {
    const date = addDays(range.from, index);
    const dateValue = localDate(date);
    const entries = entriesForDate(dateValue);
    const outside = date.getMonth() !== state.calendarMonth.getMonth();
    return `<div class="month-day ${outside ? 'outside' : ''} ${dateValue === todayValue ? 'today' : ''}" data-calendar-date="${dateValue}">
      <button class="day-number" data-new-calendar-date="${dateValue}" aria-label="Add item on ${date.toLocaleDateString('en-AU')}">${date.getDate()}</button>
      <div class="month-events">${entries.map(item => calendarEvent(item, false, dateValue)).join('')}</div>
    </div>`;
  }).join('');
  bindCalendarItems();
}

function renderWeek() {
  const start = state.calendarWeek || mondayFor(new Date());
  state.calendarWeek = start;
  const end = addDays(start, 6);
  $('#week-title').textContent = `${start.toLocaleDateString('en-AU', { day: 'numeric', month: 'short' })} – ${end.toLocaleDateString('en-AU', { day: 'numeric', month: 'short', year: 'numeric' })}`;
  $('#week-grid').innerHTML = Array.from({ length: 7 }, (_, index) => {
    const date = addDays(start, index);
    const dateValue = localDate(date);
    const entries = entriesForDate(dateValue);
    return `<article class="week-day ${dateValue === accountToday() ? 'today' : ''}"><button class="week-day-head" data-new-calendar-date="${dateValue}"><span>${date.toLocaleDateString('en-AU', { weekday: 'short' })}</span><strong>${date.getDate()}</strong></button><div class="week-events">${entries.length ? entries.map(item => calendarEvent(item, false, dateValue)).join('') : '<small>Free</small>'}</div></article>`;
  }).join('');
  bindCalendarItems();
}

function updateCalendarAssessments(selected = '') {
  $('#calendar-assessment').innerHTML = '<option value="">General subject study</option>' + state.assessments
    .filter(item => item.subjectId === $('#calendar-subject').value)
    .map(item => `<option value="${esc(item.id)}">${esc(item.title)}</option>`).join('');
  $('#calendar-assessment').value = selected;
}
$('#calendar-subject').addEventListener('change', () => updateCalendarAssessments());
$('#calendar-start').addEventListener('change', () => {
  const input = $('#calendar-start');
  if (!input.value) return;
  const previousMinutes = StudentWorkflow.minutesBetween(input.dataset.previousStart, $('#calendar-end').value);
  const minutes = previousMinutes > 0 && previousMinutes <= 1440 ? previousMinutes : 60;
  $('#calendar-end').value = StudentWorkflow.addMinutes(input.value, minutes);
  $('#calendar-end').min = StudentWorkflow.addMinutes(input.value, 1);
  input.dataset.previousStart = input.value;
});

$('#schedule-study-session').addEventListener('click', () => {
  openCalendarDialog();
  $('#calendar-subject').value = $('#activity-subject').value;
  updateCalendarAssessments($('#activity-assessment').value);
  $('#calendar-title').value = ($('#activity-description').value.trim()
    || state.assessments.find(item => item.id === $('#activity-assessment').value)?.title || 'Study session').slice(0, 160);
  $('#calendar-description').value = $('#activity-description').value.trim();
  const minutes = Number($('#activity-minutes').value);
  if (Number.isInteger(minutes) && minutes > 0 && minutes <= 1440)
    $('#calendar-end').value = StudentWorkflow.addMinutes($('#calendar-start').value, minutes);
  $('#calendar-dialog-title').textContent = 'Schedule a study session';
});

function openCalendarDialog(item = null, dateValue = null) {
  const window = StudentWorkflow.nextStudyWindow(StudentWorkflow.now(accountTimezone()), dateValue);
  const start = item ? item.startAt.slice(0, 16) : window.start;
  const end = item ? item.endAt.slice(0, 16) : window.end;
  $('#calendar-id').value = item?.id || '';
  $('#calendar-title').value = item?.title || '';
  $('#calendar-description').value = item?.description || '';
  $('#calendar-type').value = item?.type || 'STUDY_SESSION';
  $('#calendar-subject').value = item?.subjectId || '';
  updateCalendarAssessments(item?.assessmentId || '');
  $('#calendar-start').value = start;
  $('#calendar-start').dataset.previousStart = start;
  $('#calendar-start').min = item ? '' : window.earliest;
  $('#calendar-end').value = end;
  $('#calendar-end').min = StudentWorkflow.addMinutes(start, 1);
  $('#calendar-spaced').checked = item?.spacedRepetition ?? true;
  $('#calendar-dialog-title').textContent = item ? 'Edit calendar item' : 'New study item';
  $('#delete-calendar-entry').hidden = !item;
  $('#complete-calendar-entry').hidden = !item || item.status === 'COMPLETED';
  const completed = item?.status === 'COMPLETED';
  ['#calendar-title', '#calendar-description', '#calendar-type', '#calendar-subject', '#calendar-assessment', '#calendar-start', '#calendar-end', '#calendar-spaced']
    .forEach(selector => { $(selector).disabled = completed; });
  $('#calendar-form button.primary').hidden = completed;
  $('#calendar-dialog-title').textContent = completed ? 'Completed calendar item' : item ? 'Edit calendar item' : 'New study item';
  feedback('calendar-dialog-feedback', '');
  if (completed) feedback('calendar-dialog-feedback', 'This block already counts toward progress. To correct it, remove it first and record the corrected session in Study activity.', 'info');
  $('#calendar-dialog').showModal();
  $('#calendar-title').focus();
}

function bindCalendarItems() {
  $$('[data-calendar-id]:not([data-bound])').forEach(button => {
    button.dataset.bound = 'true';
    button.addEventListener('click', event => {
      event.stopPropagation();
      const item = calendarItems().find(item => item.id === button.dataset.calendarId);
      if (!item) return;
      if (item.source === 'assessment') openAssessmentDialog(state.assessments.find(a => a.id === item.sourceId));
      else if (item.source === 'activity') openSessionDialog(item.sourceId);
      else openCalendarDialog(item);
    });
  });
  $$('[data-new-calendar-date]:not([data-bound])').forEach(button => {
    button.dataset.bound = 'true';
    button.addEventListener('click', () => openCalendarDialog(null, button.dataset.newCalendarDate));
  });
}

$('#month-prev').addEventListener('click', () => { state.calendarMonth.setMonth(state.calendarMonth.getMonth() - 1); loadCalendar(); });
$('#month-next').addEventListener('click', () => { state.calendarMonth.setMonth(state.calendarMonth.getMonth() + 1); loadCalendar(); });
$('#month-today').addEventListener('click', () => { const today = new Date(`${accountToday()}T12:00:00`); state.calendarMonth = new Date(today.getFullYear(), today.getMonth(), 1); loadCalendar(); });
$('#new-calendar-entry').addEventListener('click', () => openCalendarDialog());
$('#week-new-entry').addEventListener('click', () => openCalendarDialog(null, localDate(state.calendarWeek)));
$('#week-prev').addEventListener('click', () => { state.calendarWeek = addDays(state.calendarWeek, -7); loadCalendar(); });
$('#week-next').addEventListener('click', () => { state.calendarWeek = addDays(state.calendarWeek, 7); loadCalendar(); });
$('#week-today').addEventListener('click', () => { state.calendarWeek = mondayFor(new Date(`${accountToday()}T12:00:00`)); loadCalendar(); });
$('#close-calendar-dialog').addEventListener('click', () => $('#calendar-dialog').close());
$('#cancel-calendar-dialog').addEventListener('click', () => $('#calendar-dialog').close());

$('#calendar-form').addEventListener('submit', async event => {
  const epoch = state.sessionEpoch;
  event.preventDefault();
  const button = event.submitter || event.currentTarget.querySelector('button.primary');
  const id = $('#calendar-id').value;
  const startAt = $('#calendar-start').value, endAt = $('#calendar-end').value;
  if (!id && `${startAt}:00` <= StudentWorkflow.now(accountTimezone())) {
    feedback('calendar-dialog-feedback', 'Choose today or a future date and a time that has not passed.', 'error');
    return;
  }
  const duration = StudentWorkflow.minutesBetween(startAt, endAt);
  if (!Number.isFinite(duration) || duration <= 0 || duration > 1440) {
    feedback('calendar-dialog-feedback', 'Choose an end after the start, with a duration of at most 24 hours.', 'error');
    return;
  }
  setBusy(button, true, 'Saving');
  try {
    await request(`${API.planning}/calendar${id ? `/${id}` : ''}`, {
      method: id ? 'PATCH' : 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: $('#calendar-title').value.trim(), description: $('#calendar-description').value.trim(),
        type: $('#calendar-type').value, subjectId: $('#calendar-subject').value || null, assessmentId: $('#calendar-assessment').value || null,
        startAt: $('#calendar-start').value, endAt: $('#calendar-end').value,
        spacedRepetition: $('#calendar-spaced').checked })
    });
    $('#calendar-dialog').close();
    const visibleDate = new Date(`${startAt.slice(0, 10)}T12:00:00`);
    state.calendarMonth = new Date(visibleDate.getFullYear(), visibleDate.getMonth(), 1);
    state.calendarWeek = mondayFor(visibleDate);
    if (state.currentView === 'activity') await show('calendar');
    else await loadCalendar(false);
    if (state.sessionEpoch !== epoch) return;
    feedback(state.currentView === 'week' ? 'week-feedback' : 'calendar-feedback', id ? 'Calendar item updated.' : 'Calendar item scheduled. Study sessions count toward progress when marked complete.', 'success');
  } catch (error) {
    if (error.staleSession) return; feedback('calendar-dialog-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

$('#delete-calendar-entry').addEventListener('click', async event => {
  const epoch = state.sessionEpoch;
  if (!window.confirm('Delete this calendar item?')) return;
  const button = event.currentTarget;
  setBusy(button, true, 'Deleting');
  try {
    await request(`${API.planning}/calendar/${$('#calendar-id').value}`, { method: 'DELETE' });
    $('#calendar-dialog').close();
    await loadCalendar(false);
    if (state.sessionEpoch !== epoch) return;
    feedback(state.currentView === 'week' ? 'week-feedback' : 'calendar-feedback', 'Calendar item deleted.', 'success');
  } catch (error) {
    if (error.staleSession) return; feedback('calendar-dialog-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

$('#complete-calendar-entry').addEventListener('click', async event => {
  const epoch = state.sessionEpoch;
  const button = event.currentTarget;
  setBusy(button, true, 'Completing');
  try {
    const result = await request(`${API.planning}/calendar/${$('#calendar-id').value}/complete`, { method: 'POST' });
    $('#calendar-dialog').close();
    await loadCalendar(false);
    if (state.sessionEpoch !== epoch) return;
    const message = result.nextReview ? `Completed. Your next spaced review is ${formatDateTime(result.nextReview.startAt)}.` : 'Calendar item completed.';
    feedback(state.currentView === 'week' ? 'week-feedback' : 'calendar-feedback', message, 'success');
  } catch (error) {
    if (error.staleSession) return; feedback('calendar-dialog-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

function setAssistantOpen(open) {
  const drawer = $('#assistant-drawer');
  const backdrop = $('#assistant-backdrop');
  drawer.setAttribute('aria-hidden', String(!open));
  $('#assistant-fab').setAttribute('aria-expanded', String(open));
  drawer.classList.toggle('open', open);
  backdrop.hidden = !open;
  document.body.classList.toggle('assistant-open', open);
  if (open) requestAnimationFrame(() => $('#study-chat-message').focus());
}

$('#assistant-fab').addEventListener('click', () => setAssistantOpen(true));
$('#close-assistant').addEventListener('click', () => setAssistantOpen(false));
$('#assistant-backdrop').addEventListener('click', () => setAssistantOpen(false));
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && $('#assistant-drawer').classList.contains('open')) setAssistantOpen(false);
});

function appendStudyChat(role, content) {
  state.assistantHistory.push({ role, content });
  const bubble = document.createElement('div');
  bubble.className = `chat-bubble ${role}`;
  if (role === 'assistant') StudentControls.renderReply(bubble, content);
  else bubble.textContent = content;
  $('#study-chat-messages').append(bubble);
  renderAssistantMath(bubble);
  bubble.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function renderAssistantMath(element) {
  if (typeof window.renderMathInElement !== 'function') return;
  window.renderMathInElement(element, {
    delimiters: [
      { left: '$$', right: '$$', display: true }, { left: '\\[', right: '\\]', display: true },
      { left: '\\(', right: '\\)', display: false }, { left: '$', right: '$', display: false }
    ],
    throwOnError: false,
    strict: false
  });
}

$('#study-chat-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  const input = $('#study-chat-message');
  const message = input.value.trim();
  const history = state.assistantHistory.slice(-12).map(item => ({...item, content: item.content.slice(0, 4000)}));
  input.value = '';
  appendStudyChat('user', message);
  setBusy(button, true, 'Thinking');
  feedback('assistant-feedback', 'Your study assistant is considering your subjects and schedule…', 'info');
  try {
    const answer = await request(`${API.planning}/planning/assistant/chat`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message, history })
    });
    appendStudyChat('assistant', answer.reply);
    feedback('assistant-feedback', `Answered with ${answer.provider}.`, 'success');
  } catch (error) {
    if (error.staleSession) return; appendStudyChat('assistant', error.message); feedback('assistant-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

$$('[data-study-prompt]').forEach(button => button.addEventListener('click', () => {
  $('#study-chat-message').value = button.dataset.studyPrompt;
  $('#study-chat-message').focus();
}));

$('#profile-picture-input').addEventListener('change', event => {
  const file = event.target.files?.[0];
  if (!file) return;
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) {
    event.target.value = '';
    feedback('profile-feedback', 'Choose a PNG, JPEG or WebP image no larger than 10 MB.', 'error');
    return;
  }
  const epoch = state.sessionEpoch;
  const reader = new FileReader();
  reader.addEventListener('load', () => {
    if (state.sessionEpoch !== epoch) return;
    state.profilePictureDraft = String(reader.result);
    renderProfilePictures(state.profilePictureDraft);
    feedback('profile-feedback', 'Picture ready. Save your profile to keep it.', 'info');
  });
  reader.addEventListener('error', () => state.sessionEpoch === epoch && feedback('profile-feedback', 'That image could not be read.', 'error'));
  reader.readAsDataURL(file);
});

$('#remove-profile-picture').addEventListener('click', () => {
  state.profilePictureDraft = '';
  $('#profile-picture-input').value = '';
  renderProfilePictures(null);
  feedback('profile-feedback', 'Picture removed. Save your profile to confirm.', 'info');
});

$('#profile-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  setBusy(button, true, 'Saving');
  try {
    state.account = await request(`${API.accounts}/profile`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ displayName: $('#profile-name').value.trim(), institution: $('#profile-institution').value.trim(),
        course: $('#profile-course').value.trim(), studyGoal: $('#profile-goal').value.trim(),
        timezone: $('#profile-timezone').value.trim(), profilePicture: state.profilePictureDraft ?? null }) });
    populateAccount();
    dashboardStream.start();
    feedback('profile-feedback', 'Profile saved.', 'success');
  } catch (error) {
    if (error.staleSession) return; feedback('profile-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

$('#password-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = event.submitter;
  const next = $('#new-password').value;
  if (next !== $('#confirm-password').value) {
    feedback('password-feedback', 'The new passwords do not match.', 'error');
    return;
  }
  setBusy(button, true, 'Changing');
  try {
    await request(`${API.accounts}/profile/password`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ currentPassword: $('#current-password').value, newPassword: next })
    });
    form.reset();
    feedback('password-feedback', 'Password changed. Other remembered sessions were signed out.', 'success');
  } catch (error) {
    if (error.staleSession) return; feedback('password-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

const themePresets = {
  forest: ['#245d45', '#d69b38', '#2a5745', '#f5f7f5', '#ffffff', '#17201d'],
  ocean: ['#175c70', '#e09c46', '#24758a', '#f1f7f9', '#ffffff', '#13242b'],
  plum: ['#694263', '#d6a34a', '#815777', '#faf5f9', '#ffffff', '#291d27'],
  ember: ['#8a3f2d', '#e0a23c', '#a5523c', '#fbf6f2', '#ffffff', '#2b1d19']
};

function previewTheme() {
  applyTheme({ primaryColor: $('#theme-primary').value, accentColor: $('#theme-accent').value,
    backgroundColor: $('#theme-background').value, surfaceColor: $('#theme-surface').value,
    textColor: $('#theme-text').value, navigationColor: $('#theme-navigation').value });
}

$$('[data-theme-preset]').forEach(button => button.addEventListener('click', () => {
  const [primary, accent, navigation, background, surface, text] = themePresets[button.dataset.themePreset];
  $('#theme-primary').value = primary; $('#theme-accent').value = accent;
  $('#theme-navigation').value = navigation; $('#theme-background').value = background;
  $('#theme-surface').value = surface; $('#theme-text').value = text;
  previewTheme();
}));
$$('#theme-form input[type="color"]').forEach(input => input.addEventListener('input', previewTheme));

$('#theme-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  setBusy(button, true, 'Saving');
  try {
    state.account = await request(`${API.accounts}/settings/theme`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ primaryColor: $('#theme-primary').value, accentColor: $('#theme-accent').value,
        backgroundColor: $('#theme-background').value, surfaceColor: $('#theme-surface').value,
        textColor: $('#theme-text').value, navigationColor: $('#theme-navigation').value }) });
    populateAccount(); feedback('settings-feedback', 'Theme saved to your profile.', 'success');
  } catch (error) {
    if (error.staleSession) return; applyTheme(state.account); feedback('settings-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

function localDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

const today = new Date();
$('#activity-date').value = localDate(today);
const monday = new Date(today);
monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
state.calendarWeek = mondayFor(today);
$('#plan-start').value = accountToday();
$('#plan-start').min = accountToday();

async function bootstrap() {
  const epoch = state.sessionEpoch;
  document.body.classList.add('auth-required');
  if (!state.token) return;
  try {
    state.account = await request(`${API.accounts}/auth/session`);
    document.body.classList.remove('auth-required');
    $('#auth-shell').hidden = true;
    populateAccount();
    await load();
    if (state.sessionEpoch === epoch) dashboardStream.start();
  } catch (error) {
    if (error.staleSession) return;
    signOut(false);
    feedback('auth-feedback', 'Your remembered session expired. Sign in again.', 'info');
  }
}

populateTimezones();
window.setInterval(updateLiveClock, 30_000);
bootstrap();
