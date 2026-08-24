const API = {
  subjects: 'http://localhost:8081/api',
  assessments: 'http://localhost:8082/api',
  activity: 'http://localhost:8083/api',
  planning: 'http://localhost:8084/api'
};

const state = {
  subjects: [],
  assessments: [],
  week: null,
  plan: null,
  review: null,
  selectedFile: null,
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
    activity: 'Study activity'
  }[view];
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
  let response;
  try {
    response = await fetch(url, options);
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
    throw new Error([payload.message || 'Request failed', validation].filter(Boolean).join(': '));
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : response.text();
}

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
    ${assessment.status === 'INCOMPLETE' ? `<button class="link complete" data-id="${esc(assessment.id)}">Complete</button>` : ''}
  </div>`;
}

async function load() {
  const calls = [
    ['subjects', request(`${API.subjects}/subjects`)],
    ['assessments', request(`${API.assessments}/assessments`)],
    ['week', request(`${API.planning}/planning/this-week`)],
    ['plan', request(`${API.planning}/planning/plans/latest`)]
  ];
  const results = await Promise.allSettled(calls.map(([, promise]) => promise));
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') state[calls[index][0]] = result.value;
  });
  const unavailable = results.filter(result => result.status === 'rejected').length;
  if (unavailable) {
    feedback('dashboard-feedback',
      `${unavailable} service view${unavailable === 1 ? '' : 's'} could not be refreshed. Start all services, then reload.`,
      'error');
  } else {
    feedback('dashboard-feedback', '');
  }
  render();
}

function render() {
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

  renderAssessments();
  renderPlan('#dashboard-plan');
  renderPlan('#plan-list');
  bindComplete();
}

function renderAssessments() {
  const filter = $('#assessment-filter')?.value || '';
  const assessments = state.assessments.filter(assessment => !filter || assessment.status === filter);
  $('#assessment-list').innerHTML = assessments.length
    ? assessments.map(assessmentRow).join('')
    : 'No assessments match this view.';
  bindComplete();
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

$('#assessment-filter').addEventListener('change', renderAssessments);

const dropZone = $('#outline-drop');
const fileInput = $('#outline-file');
const extractButton = $('#extract-btn');
let dragDepth = 0;

function validatePdf(file) {
  if (!file) return 'Choose or drop a PDF first.';
  if (!file.name.toLowerCase().endsWith('.pdf')) return 'Only PDF subject outlines are supported.';
  if (file.size > 10_000_000) return 'The PDF must be 10 MB or smaller.';
  if (file.size === 0) return 'The selected PDF is empty.';
  return null;
}

function selectFile(file, source) {
  const error = validatePdf(file);
  if (error) {
    feedback('upload-feedback', error, 'error');
    return;
  }
  state.selectedFile = file;
  $('#file-name').textContent = file.name;
  $('#file-size').textContent = `${(file.size / 1024 / 1024).toFixed(2)} MB · ${source}`;
  $('#file-summary').hidden = false;
  $('#clear-file-btn').hidden = false;
  extractButton.disabled = false;
  dropZone.classList.add('has-file');
  feedback('upload-feedback', `${file.name} is ready to analyse.`, 'success');
}

function clearSelectedFile() {
  state.selectedFile = null;
  fileInput.value = '';
  $('#file-summary').hidden = true;
  $('#clear-file-btn').hidden = true;
  extractButton.disabled = true;
  dropZone.classList.remove('has-file', 'dragging');
  feedback('upload-feedback', 'Choose or drop a PDF when you are ready.', 'info');
}

fileInput.addEventListener('change', () => selectFile(fileInput.files[0], 'selected from this device'));
$('#clear-file-btn').addEventListener('click', clearSelectedFile);
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
  feedback('upload-feedback', 'Release the PDF to add it.', 'info');
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
  selectFile(event.dataTransfer?.files?.[0], 'dropped into Study Leftovers');
});
document.addEventListener('dragover', event => {
  if (event.dataTransfer?.types?.includes('Files')) event.preventDefault();
});
document.addEventListener('drop', event => {
  if (!dropZone.contains(event.target)) event.preventDefault();
});

extractButton.addEventListener('click', async () => {
  const file = state.selectedFile || fileInput.files[0];
  const error = validatePdf(file);
  if (error) return feedback('upload-feedback', error, 'error');
  const data = new FormData();
  data.append('file', file);
  setBusy(extractButton, true, 'Analysing');
  $('#upload-progress').hidden = false;
  feedback('upload-feedback', 'Upload received. Extracting subject and assessment details…', 'info');
  try {
    state.review = await request(`${API.subjects}/subject-outlines`, { method: 'POST', body: data });
    renderReview();
    feedback('upload-feedback', 'Extraction complete. Review and correct every value below.', 'success');
    $('#review').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (requestError) {
    feedback('upload-feedback', requestError.message, 'error');
  } finally {
    $('#upload-progress').hidden = true;
    setBusy(extractButton, false);
    extractButton.disabled = !state.selectedFile;
  }
});

function renderReview() {
  const extraction = state.review?.extraction;
  if (!extraction) return;
  extraction.assessments = extraction.assessments || [];
  const warnings = extraction.warnings || [];
  $('#review').innerHTML = `<div class="review-card">
    <p class="eyebrow">REVIEW REQUIRED</p>
    <h3>Check extracted information</h3>
    <p class="review-intro">Nothing is permanent yet. Correct missing or uncertain fields, then confirm.</p>
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
    <button id="confirm-btn" class="primary">Confirm subject and assessments</button>
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
    <label>Due week<input class="due-week" type="number" min="1" max="20" value="${esc(assessment.dueWeek)}"></label>
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
  if (!body.extraction.assessments.length) {
    return feedback('review-feedback', 'Add at least one assessment before confirming.', 'error');
  }
  setBusy(button, true, 'Confirming');
  feedback('review-feedback', 'Saving the subject, then confirming assessments with Assessment Service…', 'info');
  try {
    await request(`${API.subjects}/subject-outlines/${state.review.importId}/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    state.review = null;
    $('#review').replaceChildren();
    clearSelectedFile();
    await load();
    show('dashboard');
    feedback('dashboard-feedback', 'Subject and assessments confirmed successfully.', 'success');
  } catch (error) {
    feedback('review-feedback', error.message, 'error');
    setBusy(button, false);
  }
}

$('#activity-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.submitter || event.currentTarget.querySelector('button');
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
    feedback('activity-feedback', 'Study session recorded. Kafka will update your progress view.', 'success');
    event.currentTarget.reset();
    $('#activity-date').value = localDate(new Date());
    await load();
  } catch (error) {
    feedback('activity-feedback', error.message, 'error');
  } finally {
    setBusy(button, false);
  }
});

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
  setBusy(button, true, 'Generating');
  feedback('plan-feedback', 'Reading current assessments, progress, and availability…', 'info');
  try {
    state.plan = await request(`${API.planning}/planning/plans`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ startDate: start, dailyAvailabilityMinutes: availability })
    });
    feedback('plan-feedback', 'A validated seven-day plan was saved.', 'success');
    render();
  } catch (error) {
    feedback('plan-feedback', error.message, 'error');
  } finally {
    setBusy(button, false);
  }
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
$('#plan-start').value = localDate(monday);
load();
