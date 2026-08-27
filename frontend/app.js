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
  subjects: [],
  assessments: [],
  week: null,
  plan: null,
  review: null,
  uploadItems: [],
  reviewQueue: [],
  subjectAi: null,
  planningAi: null,
  serviceFailures: [],
  availability: {},
  availabilityMinutes: {},
  manualAssessments: [],
  sessions: [],
  calendarEntries: [],
  calendarMonth: new Date(new Date().getFullYear(), new Date().getMonth(), 1),
  calendarWeek: null,
  assistantHistory: [],
  currentView: 'dashboard'
};

const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const esc = value => String(value ?? '').replace(/[&<>"']/g, character => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);
const num = value => value === '' ? null : Number(value);

function show(view) {
  state.currentView = view;
  $$('.view').forEach(element => element.classList.toggle('active', element.id === view));
  $$('nav button').forEach(element => element.classList.toggle('active', element.dataset.view === view));
  $('#page-title').textContent = {
    dashboard: 'Good morning',
    upload: 'Add a subject',
    subjects: 'Subjects',
    assessments: 'Assessments',
    plan: 'Study plan',
    calendar: 'Monthly calendar',
    week: 'Weekly schedule',
    assistant: 'Study assistant',
    activity: 'Study activity',
    profile: 'Your profile',
    settings: 'Settings'
  }[view];
  if (view === 'calendar' || view === 'week') loadCalendar();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

$$('[data-view]').forEach(button => {
  button.addEventListener('click', () => show(button.dataset.view));
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
  const requestOptions = { ...(options || {}) };
  const headers = new Headers(requestOptions.headers || {});
  if (state.token) headers.set('Authorization', `Bearer ${state.token}`);
  requestOptions.headers = headers;
  let response;
  try {
    response = await fetch(url, requestOptions);
  } catch (error) {
    throw new Error('Could not reach the service. Check that the application is running and try again.');
  }
  if (!response.ok) {
    let payload = {};
    try {
      payload = await response.json();
    } catch {
      payload = { message: response.statusText };
    }
    const validation = payload.validationErrors
      ? Object.values(payload.validationErrors).filter(Boolean).join('; ')
      : '';
    if (response.status === 401 && state.token) signOut(false);
    throw new Error([payload.message || 'Request failed', validation].filter(Boolean).join(': '));
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : response.text();
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
  root.style.color = account.textColor;
  root.style.background = account.backgroundColor;
}

function populateAccount() {
  const account = state.account;
  if (!account) return;
  $('#account-name').textContent = account.displayName || account.username;
  $('#account-username').textContent = `@${account.username}`;
  $('#account-avatar').textContent = (account.displayName || account.username).slice(0, 2).toUpperCase();
  $('#profile-name').value = account.displayName || '';
  $('#profile-institution').value = account.institution || '';
  $('#profile-course').value = account.course || '';
  $('#profile-goal').value = account.studyGoal || '';
  $('#profile-timezone').value = account.timezone || 'Australia/Sydney';
  $('#theme-primary').value = account.primaryColor;
  $('#theme-accent').value = account.accentColor;
  $('#theme-background').value = account.backgroundColor;
  $('#theme-surface').value = account.surfaceColor;
  $('#theme-text').value = account.textColor;
  applyTheme(account);
}

async function establishSession(session) {
  state.token = session.token;
  state.account = session.account;
  localStorage.setItem('studyLeftoversToken', state.token);
  document.body.classList.remove('auth-required');
  $('#auth-shell').hidden = true;
  populateAccount();
  await load();
}

async function signOut(callServer = true) {
  const token = state.token;
  if (callServer && token) {
    try { await request(`${API.accounts}/auth/logout`, { method: 'POST' }); } catch { /* local sign-out still succeeds */ }
  }
  state.token = null;
  state.account = null;
  localStorage.removeItem('studyLeftoversToken');
  document.body.classList.add('auth-required');
  $('#auth-shell').hidden = false;
  setAuthMode('login');
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
        password: $('#signup-password').value, displayName: $('#signup-name').value.trim() })
    });
    await establishSession(session);
  } catch (error) { feedback('auth-feedback', error.message, 'error'); }
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
  } catch (error) { feedback('auth-feedback', error.message, 'error'); }
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
    <span class="pill">${esc(assessment.status)}</span>
    <div class="row-actions">
      ${assessment.status === 'INCOMPLETE' ? `<button class="link complete" data-id="${esc(assessment.id)}">Complete</button>` : ''}
      <button class="link danger remove-saved-assessment" data-id="${esc(assessment.id)}" aria-label="Remove ${esc(assessment.title)}">Remove</button>
    </div>
  </div>`;
}

async function load() {
  const calls = [
    ['subjects', request(`${API.subjects}/subjects`)],
    ['assessments', request(`${API.assessments}/assessments`)],
    ['week', request(`${API.planning}/planning/this-week`)],
    ['plan', request(`${API.planning}/planning/plans/latest`)],
    ['subjectAi', request(`${API.subjects}/ai/status`)],
    ['planningAi', request(`${API.planning}/planning/ai/status`)]
    ,['sessions', request(`${API.activity}/study-sessions`)]
  ];
  const results = await Promise.allSettled(calls.map(([, promise]) => promise));
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
  await loadCalendar(false);
}

function render() {
  renderSystemStatus();
  const week = state.week;
  $('#due-count').textContent = week?.dueThisWeek?.length ?? 0;
  $('#upcoming-count').textContent = week?.upcoming?.length ?? 0;
  $('#study-count').textContent = week?.studyProgress?.reduce((total, progress) => total + progress.studiedMinutes, 0) ?? 0;
  $('#incomplete-count').textContent = week?.workload?.incompleteAssessments
    ?? state.assessments.filter(assessment => assessment.status === 'INCOMPLETE').length;

  const status = week?.workload?.status ?? 'LOW';
  const badge = $('#load-badge');
  badge.textContent = `${status} WORKLOAD`;
  badge.className = `load ${status.toLowerCase()}`;

  const attention = [...(week?.dueThisWeek || []), ...(week?.upcoming || [])].slice(0, 5);
  $('#dashboard-assessments').innerHTML = attention.length
    ? attention.map(assessmentRow).join('')
    : 'No upcoming assessments yet.';
  $('#dashboard-progress').innerHTML = week?.studyProgress?.length
    ? week.studyProgress.map(progress => `<div class="row"><div class="body"><strong>${esc(subjectName(progress.subjectId))}</strong><br><small>${progress.studiedMinutes} / ${progress.targetMinutes} minutes</small></div><span class="pill">${esc(progress.state.replaceAll('_', ' '))}</span></div>`).join('')
    : 'Record a session to see progress.';

  $('#subject-list').innerHTML = state.subjects.length
    ? state.subjects.map(subject => `<article><p class="eyebrow">${esc(subject.code)}</p><h3>${esc(subject.name)}</h3><p>${subject.creditPoints ?? '—'} credit points</p><small>${subject.weeklyStudyTargetMinutes} min weekly target</small></article>`).join('')
    : '<p>No subjects yet. Upload an outline to begin.</p>';
  $('#activity-subject').innerHTML = '<option value="">Choose subject</option>'
    + state.subjects.map(subject => `<option value="${esc(subject.id)}">${esc(subject.code)} — ${esc(subject.name)}</option>`).join('');
  $('#calendar-subject').innerHTML = '<option value="">No subject</option>'
    + state.subjects.map(subject => `<option value="${esc(subject.id)}">${esc(subject.code)} — ${esc(subject.name)}</option>`).join('');

  $('#assistant-subject-count').textContent = state.subjects.length;
  $('#assistant-assessment-count').textContent = state.assessments.filter(item => item.status === 'INCOMPLETE').length;
  $('#assistant-calendar-count').textContent = state.calendarEntries.filter(item => item.status === 'PLANNED').length;
  $('#session-history').classList.toggle('empty', !state.sessions.length);
  $('#session-history').innerHTML = state.sessions.length
    ? state.sessions.slice(0, 10).map(session => `<div class="row"><div class="body"><strong>${esc(session.description)}</strong><br><small>${esc(subjectName(session.subjectId))} · ${esc(session.studyDate)}</small></div><span class="pill">${session.durationMinutes} min</span></div>`).join('')
    : 'No study sessions recorded yet.';

  const upcomingCalendar = state.calendarEntries.filter(item => item.status === 'PLANNED'
    && new Date(item.endAt) >= new Date()).sort((a, b) => a.startAt.localeCompare(b.startAt)).slice(0, 4);
  $('#dashboard-calendar').classList.toggle('empty', !upcomingCalendar.length);
  $('#dashboard-calendar').innerHTML = upcomingCalendar.length
    ? upcomingCalendar.map(item => `<button class="calendar-list-item" data-calendar-id="${esc(item.id)}"><span class="calendar-dot ${item.type.toLowerCase()}"></span><span><strong>${esc(item.title)}</strong><small>${formatDateTime(item.startAt)}</small></span></button>`).join('')
    : 'Nothing scheduled yet.';

  renderAssessments();
  renderPlan('#dashboard-plan');
  renderPlan('#plan-list');
  bindComplete();
  bindDeleteAssessments();
  bindCalendarItems();
}

function renderSystemStatus() {
  const status = $('#system-status');
  const subjectReady = state.subjectAi?.configured;
  const planningReady = state.planningAi?.configured;
  const subjectStatusUnavailable = state.serviceFailures.includes('subjectAi');
  const planningOffline = state.serviceFailures.some(name => ['week', 'plan', 'planningAi'].includes(name));
  const subjectLabel = subjectReady ? '● Gemini ready'
    : subjectStatusUnavailable ? '○ Gemini status unavailable' : '○ Gemini not loaded';
  status.innerHTML = `
    <span class="status-chip ${subjectReady ? 'ready' : subjectStatusUnavailable ? 'offline' : 'warning'}" title="${esc(state.subjectAi?.message || 'Subject AI status unavailable')}">${subjectLabel}</span>
    <span class="status-chip ${planningOffline ? 'offline' : 'ready'}">${planningOffline ? '○ Planner offline' : '● Planner online'}</span>`;
  const plannerBadge = $('#planner-ai-badge');
  if (plannerBadge) {
    plannerBadge.textContent = planningReady ? `${state.planningAi.provider} ready` : planningOffline ? 'Planner offline' : 'AI key not loaded';
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
}

function renderPlan(target) {
  const items = state.plan?.items || [];
  $(target).innerHTML = items.length
    ? items.map(item => `<div class="row">${dateCard(item.date, null)}<div class="body"><strong>${esc(item.title)}</strong><br><small>${esc(subjectName(item.subjectId))} · ${item.allocatedMinutes} minutes</small></div></div>`).join('')
    : 'No plan generated yet.';
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
        feedback(target, error.message, 'error');
        setBusy(button, false);
      }
    });
  });
}

$('#assessment-filter').addEventListener('change', renderAssessments);

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
    <label>Est. hours<input class="hours" type="number" min="0.1" step="0.1" value="${esc(assessment.estimatedHours)}"></label>
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
  if (!['pdf', 'docx', 'jpg', 'jpeg'].includes(extension)) return 'Choose a PDF, DOCX, JPG or JPEG subject outline.';
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
  feedback('upload-feedback', 'Choose or drop up to 10 PDF, DOCX, JPG or JPEG files when you are ready.', 'info');
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
  const warnings = extraction.warnings || [];
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
    <label>Est. hours<input class="hours" type="number" min="0.1" step="0.1" value="${esc(assessment.estimatedHours)}"></label>
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
    feedback('review-feedback', error.message, 'error');
    setBusy(button, false);
  }
}

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
        durationMinutes: Number($('#activity-minutes').value),
        studyDate: $('#activity-date').value,
        description: $('#activity-description').value
      })
    });
    feedback('activity-feedback', 'Study session recorded. Your progress has been refreshed.', 'success');
    form.reset();
    $('#activity-date').value = localDate(new Date());
    await load();
  } catch (error) {
    feedback('activity-feedback', error.message, 'error');
  } finally {
    setBusy(button, false);
  }
});

async function generatePlan(availability, button) {
  const start = $('#plan-start').value;
  setBusy(button, true, 'Generating');
  feedback('plan-feedback', 'Gemini is balancing approved assessments against your available time…', 'info');
  try {
    state.plan = await request(`${API.planning}/planning/plans`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ startDate: start, dailyAvailabilityMinutes: availability })
    });
    feedback('plan-feedback', 'Your validated seven-day plan is ready.', 'success');
    render();
  } catch (error) {
    feedback('plan-feedback', error.message, 'error');
  } finally {
    setBusy(button, false);
  }
}

$('#plan-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter || event.currentTarget.querySelector('button');
  const start = $('#plan-start').value;
  const minutes = Number($('#plan-minutes').value);
  const availability = {};
  for (let day = 0; day < 7; day++) {
    const date = new Date(`${start}T00:00:00`);
    date.setDate(date.getDate() + day);
    availability[localDate(date)] = minutes;
  }
  await generatePlan(availability, button);
});

function appendChatMessage(role, message) {
  const bubble = document.createElement('div');
  bubble.className = `chat-bubble ${role}`;
  bubble.textContent = message;
  $('#chat-messages').append(bubble);
  bubble.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function renderAvailability() {
  const rows = Object.entries(state.availability || {}).filter(([, slots]) => slots?.length);
  $('#availability-summary').classList.toggle('empty', rows.length === 0);
  $('#availability-summary').innerHTML = rows.length ? rows.map(([date, slots]) => {
    const label = new Date(`${date}T00:00:00`).toLocaleDateString('en-AU', { weekday: 'long', day: 'numeric', month: 'short' });
    const times = slots.map(slot => `${slot.start.slice(0, 5)}–${slot.end.slice(0, 5)}`).join(', ');
    return `<div class="availability-day"><div><strong>${esc(label)}</strong><small>${esc(times)}</small></div><span>${state.availabilityMinutes[date] || 0} min</span></div>`;
  }).join('') : 'No times added yet.';
  $('#generate-chat-plan').disabled = !Object.values(state.availabilityMinutes || {}).some(minutes => minutes > 0);
}

async function sendAvailability(message, button) {
  appendChatMessage('user', message);
  setBusy(button, true, 'Thinking');
  feedback('chat-feedback', 'Gemini is interpreting those time slots…', 'info');
  try {
    const result = await request(`${API.planning}/planning/availability/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message,
        weekStart: $('#plan-start').value,
        availability: state.availability
      })
    });
    state.availability = result.availability || {};
    state.availabilityMinutes = result.dailyAvailabilityMinutes || {};
    appendChatMessage('assistant', result.reply);
    renderAvailability();
    feedback('chat-feedback', result.readyToPlan ? 'Availability understood. You can generate the plan.' : 'Add the detail requested above.', result.readyToPlan ? 'success' : 'info');
  } catch (error) {
    appendChatMessage('assistant', error.message);
    feedback('chat-feedback', error.message, 'error');
  } finally {
    setBusy(button, false);
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

$('#plan-start').addEventListener('change', () => {
  state.availability = {};
  state.availabilityMinutes = {};
  renderAvailability();
  appendChatMessage('assistant', 'The planning week changed, so I cleared the previous time slots. Tell me your availability for this week.');
});

$('#generate-chat-plan').addEventListener('click', async event => {
  await generatePlan(state.availabilityMinutes, event.currentTarget);
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
  return new Date(value).toLocaleString('en-AU', { weekday: 'short', day: 'numeric', month: 'short',
    hour: 'numeric', minute: '2-digit' });
}

function formatTime(value) {
  return new Date(value).toLocaleTimeString('en-AU', { hour: 'numeric', minute: '2-digit' });
}

async function loadCalendar(showErrors = true) {
  if (!state.token) return;
  const month = monthRange();
  const weekStart = state.calendarWeek || mondayFor(new Date());
  const from = new Date(Math.min(month.from.getTime(), weekStart.getTime()));
  const to = new Date(Math.max(month.to.getTime(), addDays(weekStart, 6).getTime()));
  try {
    state.calendarEntries = await request(`${API.planning}/calendar?from=${localDate(from)}&to=${localDate(to)}`);
    renderCalendar();
    renderWeek();
    render();
  } catch (error) {
    if (showErrors) feedback(state.currentView === 'week' ? 'week-feedback' : 'calendar-feedback', error.message, 'error');
  }
}

function entriesForDate(date) {
  return state.calendarEntries.filter(item => item.startAt.slice(0, 10) === date)
    .sort((a, b) => a.startAt.localeCompare(b.startAt));
}

function calendarEvent(item, compact = false) {
  return `<button class="calendar-event ${item.type.toLowerCase()} ${item.status.toLowerCase()}" data-calendar-id="${esc(item.id)}" title="${esc(item.title)}"><span>${formatTime(item.startAt)}</span>${compact ? '' : `<strong>${esc(item.title)}</strong>`}</button>`;
}

function renderCalendar() {
  const range = monthRange();
  $('#month-title').textContent = state.calendarMonth.toLocaleDateString('en-AU', { month: 'long', year: 'numeric' });
  const todayValue = localDate(new Date());
  $('#month-grid').innerHTML = Array.from({ length: 42 }, (_, index) => {
    const date = addDays(range.from, index);
    const dateValue = localDate(date);
    const entries = entriesForDate(dateValue);
    const outside = date.getMonth() !== state.calendarMonth.getMonth();
    return `<div class="month-day ${outside ? 'outside' : ''} ${dateValue === todayValue ? 'today' : ''}" data-calendar-date="${dateValue}">
      <button class="day-number" data-new-calendar-date="${dateValue}" aria-label="Add item on ${date.toLocaleDateString('en-AU')}">${date.getDate()}</button>
      <div class="month-events">${entries.slice(0, 3).map(item => calendarEvent(item)).join('')}${entries.length > 3 ? `<small>+${entries.length - 3} more</small>` : ''}</div>
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
    return `<article class="week-day ${dateValue === localDate(new Date()) ? 'today' : ''}"><button class="week-day-head" data-new-calendar-date="${dateValue}"><span>${date.toLocaleDateString('en-AU', { weekday: 'short' })}</span><strong>${date.getDate()}</strong></button><div class="week-events">${entries.length ? entries.map(item => calendarEvent(item)).join('') : '<small>Free</small>'}</div></article>`;
  }).join('');
  bindCalendarItems();
}

function dateTimeInput(date) {
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function openCalendarDialog(item = null, dateValue = null) {
  const start = item ? new Date(item.startAt) : new Date(`${dateValue || localDate(new Date())}T18:00:00`);
  const end = item ? new Date(item.endAt) : new Date(start.getTime() + 60 * 60000);
  $('#calendar-id').value = item?.id || '';
  $('#calendar-title').value = item?.title || '';
  $('#calendar-description').value = item?.description || '';
  $('#calendar-type').value = item?.type || 'STUDY_SESSION';
  $('#calendar-subject').value = item?.subjectId || '';
  $('#calendar-start').value = dateTimeInput(start);
  $('#calendar-end').value = dateTimeInput(end);
  $('#calendar-spaced').checked = item?.spacedRepetition ?? true;
  $('#calendar-dialog-title').textContent = item ? 'Edit calendar item' : 'New study item';
  $('#delete-calendar-entry').hidden = !item;
  $('#complete-calendar-entry').hidden = !item || item.status === 'COMPLETED';
  feedback('calendar-dialog-feedback', '');
  $('#calendar-dialog').showModal();
  $('#calendar-title').focus();
}

function bindCalendarItems() {
  $$('[data-calendar-id]:not([data-bound])').forEach(button => {
    button.dataset.bound = 'true';
    button.addEventListener('click', event => {
      event.stopPropagation();
      openCalendarDialog(state.calendarEntries.find(item => item.id === button.dataset.calendarId));
    });
  });
  $$('[data-new-calendar-date]:not([data-bound])').forEach(button => {
    button.dataset.bound = 'true';
    button.addEventListener('click', () => openCalendarDialog(null, button.dataset.newCalendarDate));
  });
}

$('#month-prev').addEventListener('click', () => { state.calendarMonth.setMonth(state.calendarMonth.getMonth() - 1); loadCalendar(); });
$('#month-next').addEventListener('click', () => { state.calendarMonth.setMonth(state.calendarMonth.getMonth() + 1); loadCalendar(); });
$('#month-today').addEventListener('click', () => { state.calendarMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1); loadCalendar(); });
$('#new-calendar-entry').addEventListener('click', () => openCalendarDialog());
$('#week-new-entry').addEventListener('click', () => openCalendarDialog(null, localDate(state.calendarWeek)));
$('#week-prev').addEventListener('click', () => { state.calendarWeek = addDays(state.calendarWeek, -7); loadCalendar(); });
$('#week-next').addEventListener('click', () => { state.calendarWeek = addDays(state.calendarWeek, 7); loadCalendar(); });
$('#week-today').addEventListener('click', () => { state.calendarWeek = mondayFor(new Date()); loadCalendar(); });
$('#close-calendar-dialog').addEventListener('click', () => $('#calendar-dialog').close());
$('#cancel-calendar-dialog').addEventListener('click', () => $('#calendar-dialog').close());

$('#calendar-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  const id = $('#calendar-id').value;
  setBusy(button, true, 'Saving');
  try {
    await request(`${API.planning}/calendar${id ? `/${id}` : ''}`, {
      method: id ? 'PATCH' : 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: $('#calendar-title').value.trim(), description: $('#calendar-description').value.trim(),
        type: $('#calendar-type').value, subjectId: $('#calendar-subject').value || null, assessmentId: null,
        startAt: $('#calendar-start').value, endAt: $('#calendar-end').value,
        spacedRepetition: $('#calendar-spaced').checked })
    });
    $('#calendar-dialog').close();
    await loadCalendar(false);
    feedback(state.currentView === 'week' ? 'week-feedback' : 'calendar-feedback', id ? 'Calendar item updated.' : 'Calendar item created.', 'success');
  } catch (error) { feedback('calendar-dialog-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

$('#delete-calendar-entry').addEventListener('click', async event => {
  if (!window.confirm('Delete this calendar item?')) return;
  setBusy(event.currentTarget, true, 'Deleting');
  try {
    await request(`${API.planning}/calendar/${$('#calendar-id').value}`, { method: 'DELETE' });
    $('#calendar-dialog').close();
    await loadCalendar(false);
    feedback(state.currentView === 'week' ? 'week-feedback' : 'calendar-feedback', 'Calendar item deleted.', 'success');
  } catch (error) { feedback('calendar-dialog-feedback', error.message, 'error'); }
  finally { setBusy(event.currentTarget, false); }
});

$('#complete-calendar-entry').addEventListener('click', async event => {
  setBusy(event.currentTarget, true, 'Completing');
  try {
    const result = await request(`${API.planning}/calendar/${$('#calendar-id').value}/complete`, { method: 'POST' });
    $('#calendar-dialog').close();
    await loadCalendar(false);
    const message = result.nextReview ? `Completed. Your next spaced review is ${formatDateTime(result.nextReview.startAt)}.` : 'Calendar item completed.';
    feedback(state.currentView === 'week' ? 'week-feedback' : 'calendar-feedback', message, 'success');
  } catch (error) { feedback('calendar-dialog-feedback', error.message, 'error'); }
  finally { setBusy(event.currentTarget, false); }
});

function appendStudyChat(role, content) {
  state.assistantHistory.push({ role, content });
  const bubble = document.createElement('div');
  bubble.className = `chat-bubble ${role}`;
  bubble.textContent = content;
  $('#study-chat-messages').append(bubble);
  bubble.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

$('#study-chat-form').addEventListener('submit', async event => {
  event.preventDefault();
  const input = $('#study-chat-message');
  const message = input.value.trim();
  const history = state.assistantHistory.slice(-12);
  input.value = '';
  appendStudyChat('user', message);
  setBusy(event.submitter, true, 'Thinking');
  feedback('assistant-feedback', 'Your study assistant is considering your subjects and schedule…', 'info');
  try {
    const answer = await request(`${API.planning}/planning/assistant/chat`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message, history })
    });
    appendStudyChat('assistant', answer.reply);
    feedback('assistant-feedback', `Answered with ${answer.provider}.`, 'success');
  } catch (error) { appendStudyChat('assistant', error.message); feedback('assistant-feedback', error.message, 'error'); }
  finally { setBusy(event.submitter, false); }
});

$$('[data-study-prompt]').forEach(button => button.addEventListener('click', () => {
  $('#study-chat-message').value = button.dataset.studyPrompt;
  $('#study-chat-message').focus();
}));

$('#profile-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter;
  setBusy(button, true, 'Saving');
  try {
    state.account = await request(`${API.accounts}/profile`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ displayName: $('#profile-name').value.trim(), institution: $('#profile-institution').value.trim(),
        course: $('#profile-course').value.trim(), studyGoal: $('#profile-goal').value.trim(), timezone: $('#profile-timezone').value.trim() }) });
    populateAccount();
    feedback('profile-feedback', 'Profile saved.', 'success');
  } catch (error) { feedback('profile-feedback', error.message, 'error'); }
  finally { setBusy(button, false); }
});

const themePresets = {
  forest: ['#245d45', '#d69b38', '#f5f7f5', '#ffffff', '#17201d'],
  ocean: ['#175c70', '#e09c46', '#f1f7f9', '#ffffff', '#13242b'],
  plum: ['#694263', '#d6a34a', '#faf5f9', '#ffffff', '#291d27'],
  ember: ['#8a3f2d', '#e0a23c', '#fbf6f2', '#ffffff', '#2b1d19']
};

function previewTheme() {
  applyTheme({ primaryColor: $('#theme-primary').value, accentColor: $('#theme-accent').value,
    backgroundColor: $('#theme-background').value, surfaceColor: $('#theme-surface').value,
    textColor: $('#theme-text').value });
}

$$('[data-theme-preset]').forEach(button => button.addEventListener('click', () => {
  const [primary, accent, background, surface, text] = themePresets[button.dataset.themePreset];
  $('#theme-primary').value = primary; $('#theme-accent').value = accent;
  $('#theme-background').value = background; $('#theme-surface').value = surface; $('#theme-text').value = text;
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
        textColor: $('#theme-text').value }) });
    populateAccount(); feedback('settings-feedback', 'Theme saved to your profile.', 'success');
  } catch (error) { applyTheme(state.account); feedback('settings-feedback', error.message, 'error'); }
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
$('#plan-start').value = localDate(monday);

async function bootstrap() {
  document.body.classList.add('auth-required');
  if (!state.token) return;
  try {
    state.account = await request(`${API.accounts}/auth/session`);
    document.body.classList.remove('auth-required');
    $('#auth-shell').hidden = true;
    populateAccount();
    await load();
  } catch (error) {
    signOut(false);
    feedback('auth-feedback', 'Your remembered session expired. Sign in again.', 'info');
  }
}

bootstrap();
