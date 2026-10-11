"""Isolated HTTP/JPA acceptance with a local OpenAI-protocol fixture, never a paid model.

Run after mvn verify: python scripts/verify-readiness.py [--java PATH] [--output DIR]
Starts five built jars with separate in-memory databases, Kafka disabled, ports 18081-5.
Real Kafka/event delivery is verified separately by verify-streaming.py.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

REPO = Path(__file__).resolve().parents[1]
MODE = "valid"
MODEL_CALLS = []
TODAY = dt.datetime.now(dt.timezone.utc).date()
START = TODAY + dt.timedelta(days=1)
END = START + dt.timedelta(days=6)


class ModelFixture(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        payload = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        MODEL_CALLS.append('tools' if payload.get('tools') else 'text')
        if MODE == 'quota':
            self.send_response(429)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(b'{"error":{"message":"429 quota PRIVATE_SENTINEL","type":"rate_limit_error"}}')
            return
        messages = payload['messages']
        if payload.get('tools'):
            goal = next(m['content'] for m in messages if m['role'] == 'user')
            action = 'REGENERATE' if 'Workflow: REGENERATE' in goal else 'GENERATE'
            if messages[-1]['role'] == 'user':
                names = ['getIncompleteAssessments', 'getCurrentWorkload', 'getStudyProgress']
                if action == 'REGENERATE':
                    names.append('getExistingStudyPlan')
                calls = [{'id': 'call_' + str(i), 'type': 'function', 'function': {'name': name, 'arguments': '{}'}} for i, name in enumerate(names)]
            else:
                candidate = json.loads(goal.split('Candidate plan (remaining workload already accounted for): ')[1].split('\n')[0])
                items = candidate['items']
                for item in items:
                    item['title'] = 'Fixture submitted study block'
                if items and MODE == 'fractional': items[0]['allocatedMinutes'] += 0.5
                if items and MODE == 'missing-stage': items[0].pop('repetitionStage', None)
                if items and MODE == 'extra-field': items[0]['unapprovedField'] = True
                if items and MODE == 'string-minute': items[0]['allocatedMinutes'] = str(items[0]['allocatedMinutes'])
                if MODE == 'invalid' and items:
                    items[0]['date'] = (END + dt.timedelta(days=1)).isoformat()
                args = {'action': action, 'summary': 'Reviewed current workload and progress' + (' and revised the previous plan.' if action == 'REGENERATE' else '.'), 'items': items}
                calls = [{'id': 'save', 'type': 'function', 'function': {'name': 'saveStudyPlan', 'arguments': json.dumps(args)}}]
            message = {'role': 'assistant', 'content': None, 'tool_calls': calls}
            finish = 'tool_calls'
        else:
            # An outline extraction fixture exercises document parsing, SDK transport and confirmation.
            extraction = {'subjectCode': 'CSCI319', 'subjectName': 'Acceptance Fixture', 'creditPoints': 6,
                          'assessments': [{'title': 'Fixture Report', 'type': 'Report', 'weighting': 30,
                                           'dueDate': (START + dt.timedelta(days=5)).isoformat(),
                                           'description': 'Reviewable fixture assessment', 'estimatedHours': 2, 'confidence': 1.0}], 'warnings': []}
            message = {'role': 'assistant', 'content': json.dumps(extraction)}
            finish = 'stop'
        body = json.dumps({'id': 'fixture', 'object': 'chat.completion', 'created': int(time.time()),
                           'model': 'local-fixture', 'choices': [{'index': 0, 'message': message, 'finish_reason': finish}],
                           'usage': {'prompt_tokens': 1, 'completion_tokens': 1, 'total_tokens': 2}}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def api(port, path, token=None, body=None, method=None, content_type='application/json'):
    headers = {'Content-Type': content_type, 'X-Study-Timezone': 'UTC'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = body if isinstance(body, bytes) else None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(f'http://127.0.0.1:{port}/api/{path}', headers=headers, data=data,
                                     method=method or ('GET' if body is None else 'POST'))
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        text = response.read().decode()
        return response.status, json.loads(text) if text else None


def check(name, actual, expected):
    assert actual == expected, f'{name}: expected {expected!r}, received {actual!r}'
    results.append({'check': name, 'passed': True})
    print('PASS: ' + name, flush=True)


def main():
    global MODE, results
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java')
    parser.add_argument('--output', default=str(Path(tempfile.gettempdir()) / ('study-readiness-' + uuid.uuid4().hex[:8])))
    options = parser.parse_args()
    out = Path(options.output).resolve()
    out.mkdir(parents=True, exist_ok=True)
    results, processes, handles = [], [], []
    fixture = ThreadingHTTPServer(('127.0.0.1', 0), ModelFixture)
    threading.Thread(target=fixture.serve_forever, daemon=True).start()
    try:
        for module, port in [('account', 18085), ('subject', 18081), ('assessment', 18082), ('study-activity', 18083), ('planning', 18084)]:
            name = module + '-service'
            handle = open(out / (name + '.log'), 'w', encoding='utf-8')
            handles.append(handle)
            args = [options.java, '-Xms32m', '-Xmx256m', '-jar', str(REPO / name / 'target' / f'{name}-0.1.0-SNAPSHOT.jar'),
                    f'--server.port={port}', '--spring.datasource.url=jdbc:h2:mem:acceptance;DB_CLOSE_DELAY=-1',
                    '--spring.cloud.stream.output-bindings=', '--spring.cloud.function.definition=',
                    '--spring.cloud.stream.function.autodetect=false', '--study.events.delivery-delay-ms=3600000',
                    '--services.account-url=http://127.0.0.1:18085', '--services.subject-url=http://127.0.0.1:18081',
                    '--services.assessment-url=http://127.0.0.1:18082', '--services.activity-url=http://127.0.0.1:18083',
                    '--study.ai.provider=openai', '--study.ai.gemini.api-key=', '--study.ai.openai.api-key=fixture-only-not-a-real-key',
                    '--study.ai.openai.model=local-fixture', f'--study.ai.openai.base-url=http://127.0.0.1:{fixture.server_port}/v1']
            if module == 'planning':
                args.append('--spring.autoconfigure.exclude=org.springframework.cloud.stream.binder.kafka.streams.function.KafkaStreamsFunctionAutoConfiguration')
            processes.append(subprocess.Popen(args, cwd=out, stdout=handle, stderr=subprocess.STDOUT,
                                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0))
        for index, port in enumerate([18085, 18081, 18082, 18083, 18084]):
            deadline = time.monotonic() + 120
            while True:
                if processes[index].poll() is not None:
                    raise RuntimeError(f'Startup failed on {port}; see {out}')
                try:
                    api(port, 'readiness-probe')
                    break
                except (OSError, TimeoutError):
                    if time.monotonic() > deadline:
                        raise RuntimeError(f'Startup timed out on {port}')
                    time.sleep(0.5)
        check('five isolated service processes started', len(processes), 5)
        status, account = api(18085, 'auth/register', body={'username': 'ready_' + uuid.uuid4().hex[:8], 'password': 'FixtureOnly-1234', 'displayName': 'Readiness', 'timezone': 'UTC'})
        check('register account', status, 201)
        token = account['token']
        subject_body = {'code': 'CSCI318', 'name': 'Software Engineering', 'creditPoints': 6, 'weeklyStudyTargetMinutes': 300, 'assessments': []}
        status, subject = api(18081, 'subjects', token, subject_body)
        check('create subject', status, 201)
        sid = subject['id']
        status, _ = api(18081, 'subjects/' + sid + '/study-target', token, {'minutes': None}, 'PATCH')
        check('null weekly target rejected', status, 400)
        status, _ = api(18081, 'subjects/' + sid + '/study-target', token, {'minutes': 240}, 'PATCH')
        check('update weekly target', status, 200)
        body = {'subjectId': sid, 'title': 'Report', 'type': 'Report', 'weighting': 30, 'dueDate': (START + dt.timedelta(days=30)).isoformat(), 'estimatedMinutes': 120, 'description': 'Short', 'priority': 'HIGH'}
        status, assessment = api(18082, 'assessments', token, body)
        check('create assessment', status, 201)
        aid = assessment['id']
        status, updated = api(18082, 'assessments/' + aid, token, {**body, 'description': 'x' * 2000}, 'PATCH')
        check('2000-character assessment description persists', (status, len(updated.get('description', ''))), (200, 2000))
        status, _ = api(18082, 'assessments/' + aid, token, {**body, 'description': 'x' * 2001}, 'PATCH')
        check('oversized assessment description rejected', status, 400)
        session = {'subjectId': sid, 'studyDate': TODAY.isoformat(), 'durationMinutes': 30, 'description': 'x' * 2000}
        status, study = api(18083, 'study-sessions', token, session)
        check('2000-character study notes persist', (status, len(study.get('description', ''))), (201, 2000))
        for name, patch in [('null date', {'studyDate': None}), ('missing subject', {'subjectId': None}), ('zero minutes', {'durationMinutes': 0}), ('oversized notes', {'description': 'x' * 2001})]:
            status, error = api(18083, 'study-sessions', token, {**session, **patch})
            check('study input: ' + name, status, 400)
            check('structured validation: ' + name, set(error), {'timestamp', 'status', 'error', 'message', 'path', 'validationErrors'})
        for port, path in [(18085, 'auth/register'), (18081, 'subjects'), (18082, 'assessments'), (18083, 'study-sessions'), (18084, 'calendar')]:
            status, error = api(port, path, token, b'{broken')
            check('malformed JSON consistent on ' + str(port), (status, set(error)), (400, {'timestamp', 'status', 'error', 'message', 'path', 'validationErrors'}))
        entry = {'title': 'x' * 160, 'type': 'STUDY_SESSION', 'subjectId': sid, 'assessmentId': aid, 'description': '',
                 'startAt': TODAY.isoformat() + 'T08:00:00', 'endAt': TODAY.isoformat() + 'T09:00:00', 'spacedRepetition': True}
        status, created = api(18084, 'calendar', token, entry)
        check('create linked spaced review', status, 201)
        status, completion = api(18084, 'calendar/' + created['id'] + '/complete', token, method='POST')
        check('maximum-length title completes and creates review', (status, completion.get('completed', {}).get('status'), len(completion.get('nextReview', {}).get('title', ''))), (200, 'COMPLETED', 160))
        for label, refs in [('nonexistent', {'subjectId': str(uuid.uuid4()), 'assessmentId': str(uuid.uuid4())}), ('missing subject', {'subjectId': None, 'assessmentId': aid})]:
            status, _ = api(18084, 'calendar', token, {**entry, **refs})
            check('calendar rejects ' + label + ' references', status, 400)
        status, other = api(18085, 'auth/register', body={'username': 'other_' + uuid.uuid4().hex[:8], 'password': 'FixtureOnly-1234', 'displayName': 'Other'})
        other_token = other['token']
        status, _ = api(18084, 'calendar', other_token, entry)
        check('calendar rejects foreign account references', status, 400)
        status, subject2 = api(18081, 'subjects', token, {**subject_body, 'code': 'CSCI317'})
        status, _ = api(18084, 'calendar', token, {**entry, 'subjectId': subject2['id']})
        check('calendar rejects mismatched subject and assessment', status, 400)
        request = {'startDate': START.isoformat(), 'dailyAvailabilityMinutes': {START.isoformat(): 120},
                   'availabilitySlots': {START.isoformat(): [{'start': '14:00', 'end': '16:00'}]}}
        status, first = api(18084, 'planning/plans', token, request)
        check('actual SDK tool loop generates plan', status, 201)
        check('exact seven-day horizon', first['endDate'], END.isoformat())
        check('model items are persisted', all(i['title'] == 'Fixture submitted study block' for i in first['items']), True)
        check('generation uses required factual tools', all(t in first['explanation'] for t in ['getIncompleteAssessments', 'getCurrentWorkload', 'getStudyProgress', 'saveStudyPlan']), True)
        status, calendar = api(18084, f'calendar?from={START}&to={END}', token)
        generated = [e for e in calendar if e.get('planId') == first['id']]
        check('generated calendar respects exact slots', all(e['startAt'][11:16] >= '14:00' and e['endAt'][11:16] <= '16:00' for e in generated) and bool(generated), True)
        api(18082, 'assessments/' + aid, token, {**body, 'estimatedMinutes': 30, 'dueDate': (START + dt.timedelta(days=2)).isoformat()}, 'PATCH')
        status, second = api(18084, f"planning/plans/{first['id']}/regenerate", token, request)
        check('regeneration reflects changed workload', (status, second.get('version'), sum(i['allocatedMinutes'] for i in second.get('items', []))), (201, 2, 30))
        check('regeneration retrieves previous plan', 'getExistingStudyPlan' in second['explanation'], True)
        status, history = api(18084, 'planning/plans', token)
        check('history retains both versions', [p['id'] for p in history], [second['id'], first['id']])
        status, old = api(18084, 'planning/plans/' + first['id'], token)
        check('prior version query remains intact', old['items'], first['items'])
        status, _ = api(18084, 'planning/plans/' + first['id'], other_token)
        check('plan detail isolation', status, 404)
        check('plan history isolation', api(18084, 'planning/plans', other_token)[1], [])
        before_invalid_calendar = api(18084, f'calendar?from={START}&to={END}', token)[1]
        MODE = 'invalid'
        status, _ = api(18084, 'planning/plans', token, request)
        check('out-of-week model output rejected', status, 400)
        check('invalid model output does not create a version', len(api(18084, 'planning/plans', token)[1]), 2)
        check('invalid model output retains calendar', api(18084, f'calendar?from={START}&to={END}', token)[1], before_invalid_calendar)
        MODE = 'quota'
        status, error = api(18084, 'planning/plans', token, request)
        check('SDK quota error becomes safe 503', status, 503)
        check('provider raw details do not escape', 'PRIVATE_SENTINEL' not in json.dumps(error) and 'quota' in error['message'], True)
        MODE = 'valid'
        api(18082, 'assessments/' + aid, token, {**body, 'estimatedMinutes': 0}, 'PATCH')
        status, zero = api(18084, 'planning/plans', token, request)
        check('explicit zero workload stays zero end to end', (status, zero.get('items')), (201, []))
        boundary = 'ReadinessBoundary'
        upload = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="fixture.pdf"\r\nContent-Type: application/pdf\r\n\r\n'.encode()
                  + outline_pdf() + f'\r\n--{boundary}--\r\n'.encode())
        status, review = api(18081, 'subject-outlines', token, upload, content_type='multipart/form-data; boundary=' + boundary)
        check('PDF upload and SDK extraction create a review', status, 201)
        check('extracted subject remains reviewable', review['extraction']['subjectCode'], 'CSCI319')
        confirm = {'extraction': review['extraction'], 'weeklyStudyTargetMinutes': 180}
        status, imported = api(18081, f"subject-outlines/{review['importId']}/confirm", token, confirm)
        check('confirm outline imports subject and assessment', status, 201)
        assessment_rows = api(18082, 'assessments', token)[1]
        check('confirmed assessment exists in owning service', len([a for a in assessment_rows if a['subjectId'] == imported['id']]), 1)
        status, repeated = api(18081, f"subject-outlines/{review['importId']}/confirm", token, confirm)
        check('outline confirmation retry is idempotent', (status, repeated.get('id')), (201, imported['id']))
        # Regression coverage for the fresh professor-perspective audit.
        for port, path, payload in [
            (18081, 'subjects', {**subject_body, 'code': 'CSCI321', 'assessments': [None]}),
            (18082, 'assessments/import', {'subjectId': sid, 'assessments': [None]}),
            (18081, f"subject-outlines/{review['importId']}/confirm", {'extraction': {**review['extraction'], 'assessments': [None]}, 'weeklyStudyTargetMinutes': 180})]:
            check('null nested item rejected by ' + path, api(port, path, token, payload)[0], 400)
        check('null input never creates a partial subject', any(row['code'] == 'CSCI321' for row in api(18081, 'subjects', token)[1]), False)
        for length, expected in [(80, 201), (81, 400)]:
            check('display-name boundary ' + str(length), api(18085, 'auth/register', body={'username': 'limits_' + uuid.uuid4().hex[:8], 'password': 'FixtureOnly-1234', 'displayName': 'n' * length, 'timezone': 'UTC'})[0], expected)
        profile = {'displayName': 'n' * 80, 'institution': 'i' * 255, 'course': 'c' * 255, 'studyGoal': 'g' * 800, 'timezone': 'UTC'}
        check('profile maximum text lengths persist', api(18085, 'profile', token, profile, 'PATCH')[0], 200)
        for field in ['displayName', 'institution', 'course', 'studyGoal']:
            check('profile rejects excessive ' + field, api(18085, 'profile', token, {**profile, field: profile[field] + 'x'}, 'PATCH')[0], 400)
        check('failed profile update preserves goal', api(18085, 'auth/session', token)[1]['studyGoal'], profile['studyGoal'])
        MODE = 'quota'
        status, failure = api(18081, 'subject-outlines', token, upload, content_type='multipart/form-data; boundary=' + boundary)
        check('outline quota failure returns 503', status, 503)
        check('outline provider details stay private', 'PRIVATE_SENTINEL' not in json.dumps(failure), True)
        MODE = 'valid'
        status, audit_subject = api(18081, 'subjects', other_token, {**subject_body, 'code': 'CSCI320'})
        check('create concurrency subject', status, 201)
        status, audit_assessment = api(18082, 'assessments', other_token, {**body, 'subjectId': audit_subject['id'], 'estimatedMinutes': 30})
        check('create concurrency assessment', status, 201)
        from concurrent.futures import ThreadPoolExecutor
        barrier = threading.Barrier(8)
        def concurrent_generate(_):
            barrier.wait(timeout=10)
            return api(18084, 'planning/plans', other_token, request)
        with ThreadPoolExecutor(max_workers=8) as pool:
            simultaneous = list(pool.map(concurrent_generate, range(8)))
        check('all concurrent nonempty plans commit', [code for code, plan in simultaneous], [201] * 8)
        check('concurrent versions are unique and ordered', sorted(plan['version'] for code, plan in simultaneous), list(range(1, 9)))
        audit_history = api(18084, 'planning/plans', other_token)[1]
        active = api(18084, f'calendar?from={START}&to={END}', other_token)[1]
        check('concurrent calendar retains exactly newest plan', set(e['planId'] for e in active), {audit_history[0]['id']})
        check('concurrent calendar does not duplicate minutes', sum(int((dt.datetime.fromisoformat(e['endAt']) - dt.datetime.fromisoformat(e['startAt'])).total_seconds() / 60) for e in active), 30)
        barrier = threading.Barrier(8)
        previous_id = audit_history[0]['id']
        def concurrent_regenerate(_):
            barrier.wait(timeout=10)
            return api(18084, 'planning/plans/' + previous_id + '/regenerate', other_token, request)
        with ThreadPoolExecutor(max_workers=8) as pool:
            simultaneous = list(pool.map(concurrent_regenerate, range(8)))
        check('all concurrent regenerations commit', [code for code, plan in simultaneous], [201] * 8)
        check('concurrent regeneration versions are unique', sorted(plan['version'] for code, plan in simultaneous), list(range(9, 17)))
        audit_history = api(18084, 'planning/plans', other_token)[1]
        active = api(18084, f'calendar?from={START}&to={END}', other_token)[1]
        check('concurrent regeneration calendar retains newest plan', set(e['planId'] for e in active), {audit_history[0]['id']})
        check('concurrent regeneration does not duplicate minutes', sum(int((dt.datetime.fromisoformat(e['endAt']) - dt.datetime.fromisoformat(e['startAt'])).total_seconds() / 60) for e in active), 30)
        for invalid_mode in ['fractional', 'missing-stage', 'extra-field', 'string-minute']:
            MODE = invalid_mode
            check('reject model schema violation: ' + invalid_mode, api(18084, 'planning/plans', other_token, request)[0], 400)
            check('invalid schema preserves history: ' + invalid_mode, len(api(18084, 'planning/plans', other_token)[1]), 16)
            check('invalid schema preserves calendar: ' + invalid_mode, api(18084, f'calendar?from={START}&to={END}', other_token)[1], active)
        MODE = 'valid'
        check('complete final assessment', api(18082, 'assessments/' + audit_assessment['id'] + '/complete', other_token, method='POST')[0], 200)
        status, empty_plan = api(18084, 'planning/plans/' + audit_history[0]['id'] + '/regenerate', other_token, request)
        check('all-completed regeneration stores empty next version', (status, empty_plan.get('version'), empty_plan.get('items'), empty_plan.get('endDate')), (201, 17, [], END.isoformat()))
        check('empty regeneration removes obsolete calendar blocks', api(18084, f'calendar?from={START}&to={END}', other_token)[1], [])
        check('empty regeneration preserves historical plan', bool(api(18084, 'planning/plans/' + audit_history[0]['id'], other_token)[1]['items']), True)
        api(18082, 'assessments/' + audit_assessment['id'], other_token, method='DELETE')
        check('deleted-all workload also generates empty plan', api(18084, 'planning/plans', other_token, request)[1].get('items'), [])
        status, zero_capacity_empty = api(18084, 'planning/plans', other_token, {'startDate': START.isoformat(), 'dailyAvailabilityMinutes': {}, 'availabilitySlots': {}})
        check('no workload needs no artificial availability', (status, zero_capacity_empty.get('items')), (201, []))
        next_date = START + dt.timedelta(days=1)
        overnight = {**entry, 'subjectId': None, 'assessmentId': None, 'title': 'Overnight', 'spacedRepetition': False, 'startAt': START.isoformat() + 'T23:00:00', 'endAt': next_date.isoformat() + 'T01:00:00'}
        status, overnight_entry = api(18084, 'calendar', other_token, overnight)
        check('overnight entry accepted', status, 201)
        check('overnight entry appears on continuation day', overnight_entry['id'] in [e['id'] for e in api(18084, f'calendar?from={next_date}&to={next_date}', other_token)[1]], True)
        api(18084, 'calendar/' + overnight_entry['id'], other_token, {**overnight, 'endAt': next_date.isoformat() + 'T00:00:00'}, 'PATCH')
        check('midnight endpoint does not occupy next day', overnight_entry['id'] in [e['id'] for e in api(18084, f'calendar?from={next_date}&to={next_date}', other_token)[1]], False)
        # Reverification edge cases: REST number types, all zero-work routes, and clock conflicts.
        status, edge_account = api(18085, 'auth/register', body={'username': 'edge_' + uuid.uuid4().hex[:8], 'password': 'FixtureOnly-1234', 'displayName': 'Edge regressions', 'timezone': 'UTC'})
        check('create edge-case account', status, 201)
        edge_token = edge_account['token']
        edge_subject_body = {**subject_body, 'code': 'EDGE318'}
        status, edge_subject = api(18081, 'subjects', edge_token, edge_subject_body)
        check('create edge-case subject', status, 201)
        edge_assessment_body = {**body, 'subjectId': edge_subject['id'], 'title': 'Zero workload', 'estimatedMinutes': 0}
        status, edge_assessment = api(18082, 'assessments', edge_token, edge_assessment_body)
        check('direct zero workload accepted', (status, edge_assessment.get('estimatedMinutes')), (201, 0))
        for minutes in [15.5, -0.5]:
            invalid_body = {**edge_assessment_body, 'estimatedMinutes': minutes}
            for label, path, method in [('create', 'assessments', 'POST'), ('update', 'assessments/' + edge_assessment['id'], 'PATCH')]:
                status, error = api(18082, path, edge_token, invalid_body, method)
                check(f'assessment {label} rejects fractional {minutes}', (status, set(error)), (400, {'timestamp', 'status', 'error', 'message', 'path', 'validationErrors'}))
        check('rejected numeric updates preserve estimate', api(18082, 'assessments/' + edge_assessment['id'], edge_token)[1]['estimatedMinutes'], 0)
        check('rejected numeric creates leave no assessment', len(api(18082, 'assessments', edge_token)[1]), 1)
        edge_session = {'subjectId': edge_subject['id'], 'studyDate': TODAY.isoformat(), 'durationMinutes': 30, 'description': 'Whole minutes'}
        status, edge_study = api(18083, 'study-sessions', edge_token, edge_session)
        check('integer study minutes accepted', status, 201)
        for label, path, method in [('create', 'study-sessions', 'POST'), ('update', 'study-sessions/' + edge_study['id'], 'PATCH')]:
            check('study ' + label + ' rejects fractional minutes', api(18083, path, edge_token, {**edge_session, 'durationMinutes': 15.5}, method)[0], 400)
        check('rejected study changes preserve minutes', api(18083, 'study-sessions', edge_token)[1][0]['durationMinutes'], 30)
        check('fractional subject credit points rejected', api(18081, 'subjects', edge_token, {**edge_subject_body, 'code': 'FRACTION', 'creditPoints': 6.5})[0], 400)
        check('fractional subject target rejected', api(18081, 'subjects/' + edge_subject['id'] + '/study-target', edge_token, {'minutes': -0.5}, 'PATCH')[0], 400)
        check('fractional planning map capacity rejected', api(18084, 'planning/plans', edge_token, {'startDate': START.isoformat(), 'dailyAvailabilityMinutes': {START.isoformat(): 60.5}})[0], 400)
        check('invalid capacity creates no plan', api(18084, 'planning/plans', edge_token)[1], [])
        zero_candidate = {'title': 'Imported zero', 'estimatedHours': 0, 'weighting': 12.5}
        status, imported_zero = api(18082, 'assessments/import', edge_token, {'subjectId': edge_subject['id'], 'assessments': [zero_candidate]})
        check('zero import and fractional weighting accepted', (status, imported_zero[0]['estimatedMinutes'], imported_zero[0]['weighting']), (201, 0, 12.5))
        check('negative import estimate still rejected', api(18082, 'assessments/import', edge_token, {'subjectId': edge_subject['id'], 'assessments': [{**zero_candidate, 'estimatedHours': -0.5}]})[0], 400)
        status, manual_zero = api(18081, 'subjects', edge_token, {**edge_subject_body, 'code': 'EDGE319', 'assessments': [zero_candidate]})
        check('manual subject imports zero workload', (status, [a['estimatedMinutes'] for a in api(18082, 'assessments', edge_token)[1] if a['subjectId'] == manual_zero['id']]), (201, [0]))
        status, zero_review = api(18081, 'subject-outlines', edge_token, upload, content_type='multipart/form-data; boundary=' + boundary)
        zero_extraction = zero_review['extraction']
        zero_extraction['assessments'][0]['estimatedHours'] = 0
        status, zero_outline = api(18081, 'subject-outlines/' + zero_review['importId'] + '/confirm', edge_token, {'extraction': zero_extraction, 'weeklyStudyTargetMinutes': 180})
        check('outline confirmation preserves zero workload', (status, [a['estimatedMinutes'] for a in api(18082, 'assessments', edge_token)[1] if a['subjectId'] == zero_outline['id']]), (201, [0]))
        empty_request = {'startDate': START.isoformat(), 'dailyAvailabilityMinutes': {}}
        status, zero_version = api(18084, 'planning/plans', edge_token, empty_request)
        check('incomplete zero estimates generate without availability', (status, zero_version.get('items'), zero_version.get('endDate')), (201, [], END.isoformat()))
        status, zero_regeneration = api(18084, 'planning/plans/' + zero_version['id'] + '/regenerate', edge_token, empty_request)
        check('incomplete zero estimates regenerate without availability', (status, zero_regeneration.get('version'), zero_regeneration.get('items')), (201, 2, []))
        api(18082, 'assessments/' + edge_assessment['id'], edge_token, {**edge_assessment_body, 'estimatedMinutes': 60}, 'PATCH')
        check('positive work with no availability still rejected', api(18084, 'planning/plans', edge_token, empty_request)[0], 400)
        minutes_only_request = {'startDate': START.isoformat(), 'dailyAvailabilityMinutes': {START.isoformat(): 60}}
        status, nonempty_version = api(18084, 'planning/plans', edge_token, minutes_only_request)
        check('positive workload creates calendar before zero correction', (status, bool(api(18084, f'calendar?from={START}&to={END}', edge_token)[1])), (201, True))
        api(18082, 'assessments/' + edge_assessment['id'], edge_token, edge_assessment_body, 'PATCH')
        status, cleared_version = api(18084, 'planning/plans/' + nonempty_version['id'] + '/regenerate', edge_token, empty_request)
        check('zero estimate correction clears obsolete plan calendar', (status, cleared_version.get('items'), api(18084, f'calendar?from={START}&to={END}', edge_token)[1]), (201, [], []))
        api(18082, 'assessments/' + edge_assessment['id'], edge_token, {**edge_assessment_body, 'estimatedMinutes': 60}, 'PATCH')
        commitment = {'title': 'Existing commitment', 'type': 'TASK', 'startAt': START.isoformat() + 'T18:00:00', 'endAt': START.isoformat() + 'T19:00:00', 'spacedRepetition': False}
        status, busy = api(18084, 'calendar', edge_token, commitment)
        check('create evening calendar commitment', status, 201)
        status, conflict_free = api(18084, 'planning/plans', edge_token, minutes_only_request)
        current_calendar = api(18084, f'calendar?from={START}&to={END}', edge_token)[1]
        generated_entries = [e for e in current_calendar if e.get('planId') == conflict_free['id']]
        check('minutes-only plan avoids manual commitment', (status, bool(generated_entries), all(e['startAt'] >= busy['endAt'] or e['endAt'] <= busy['startAt'] for e in generated_entries)), (201, True, True))
        check('minutes-only plan keeps all sixty allocated minutes', sum(int((dt.datetime.fromisoformat(e['endAt']) - dt.datetime.fromisoformat(e['startAt'])).total_seconds() / 60) for e in generated_entries), 60)
        api(18084, 'calendar/' + busy['id'], edge_token, {**commitment, 'startAt': START.isoformat() + 'T00:00:00', 'endAt': (START + dt.timedelta(days=1)).isoformat() + 'T00:00:00'}, 'PATCH')
        before_conflict = api(18084, f'calendar?from={START}&to={END}', edge_token)[1]
        before_history = api(18084, 'planning/plans', edge_token)[1]
        for label, path in [('generation', 'planning/plans'), ('regeneration', 'planning/plans/' + conflict_free['id'] + '/regenerate')]:
            check('full-day conflict rejects ' + label, api(18084, path, edge_token, minutes_only_request)[0], 400)
            check('full-day conflict retains calendar after ' + label, api(18084, f'calendar?from={START}&to={END}', edge_token)[1], before_conflict)
            check('full-day conflict retains history after ' + label, api(18084, 'planning/plans', edge_token)[1], before_history)
        # Hours and percentages remain decimal fields; only integer fields reject decimal JSON.
        status, half_hour = api(18082, 'assessments/import', edge_token, {'subjectId': edge_subject['id'], 'assessments': [{'title': 'Half hour', 'estimatedHours': 0.5}]})
        check('fractional hours still convert to whole minutes', (status, half_hour[0]['estimatedMinutes']), (201, 30))
        check('no private provider account used', bool(MODEL_CALLS), True)
        print(f'All {len(results)} readiness checks passed. This fixture is not live-provider or Kafka evidence.', flush=True)
    finally:
        for process in processes:
            process.terminate()
        for process in processes:
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        fixture.shutdown()
        fixture.server_close()
        for handle in handles:
            handle.close()
        (out / 'results.json').write_text(json.dumps({'provider': 'local OpenAI-protocol fixture', 'kafka': False, 'checks': results}, indent=2), encoding='utf-8')


def outline_pdf():
    text = b'BT /F1 12 Tf 50 750 Td (CSCI319 Acceptance Fixture - Credit points: 6 - Fixture Report 30 percent) Tj ET'
    objects = [b'<< /Type /Catalog /Pages 2 0 R >>', b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
               b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
               b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
               b'<< /Length ' + str(len(text)).encode() + b' >>\nstream\n' + text + b'\nendstream']
    data, offsets = b'%PDF-1.4\n', [0]
    for index, obj in enumerate(objects, 1):
        offsets.append(len(data))
        data += str(index).encode() + b' 0 obj\n' + obj + b'\nendobj\n'
    xref = len(data)
    data += b'xref\n0 6\n0000000000 65535 f \n'
    data += b''.join(f'{offset:010d} 00000 n \n'.encode() for offset in offsets[1:])
    return data + f'trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF'.encode()


if __name__ == '__main__':
    main()
