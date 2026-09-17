"""Repeatable integration evidence. Requires running services; uses only Python's standard library.
Creates two disposable demo accounts, not fabricated screenshots or claimed results.
"""
import argparse
import datetime as dt
import json
import queue
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid


def api(port, path, token=None, body=None, method=None):
    headers = {'Content-Type': 'application/json', 'X-Study-Timezone': 'UTC'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(f'http://localhost:{port}/api/{path}',
                                     data=None if body is None else json.dumps(body).encode(),
                                     headers=headers, method=method or ('POST' if body is not None else 'GET'))
    with urllib.request.urlopen(request, timeout=15) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def eventually(message, check, timeout=90):
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                print('PASS:', message, flush=True)
                return result
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            last_error = type(error).__name__
        time.sleep(0.5)
    raise AssertionError(f'{message} did not converge in {timeout}s ({last_error or "unexpected state"})')


class DashboardObserver:
    def __init__(self, token):
        self.token = token
        self.messages = queue.Queue()
        self.stopped = threading.Event()
        self.response = None
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def run(self):
        while not self.stopped.is_set():
            try:
                request = urllib.request.Request('http://localhost:8084/api/planning/dashboard/stream',
                    headers={'Authorization': 'Bearer ' + self.token, 'X-Study-Timezone': 'UTC',
                             'Accept': 'text/event-stream'})
                with urllib.request.urlopen(request, timeout=45) as response:
                    self.response = response
                    event, data = '', []
                    while not self.stopped.is_set():
                        try:
                            raw = response.readline()
                        except (OSError, ValueError, AttributeError):
                            # urllib's buffered reader can be invalidated by close() on shutdown.
                            # Do not hide the same failure during an active stream.
                            if self.stopped.is_set():
                                return
                            raise
                        if not raw:
                            break
                        line = raw.decode('utf-8').rstrip('\r\n')
                        if not line:
                            if data and event == 'dashboard':
                                self.messages.put(json.loads('\n'.join(data)))
                            event, data = '', []
                            if response.closed:
                                break
                        elif line.startswith('event:'):
                            event = line[6:].strip()
                        elif line.startswith('data:'):
                            data.append(line[5:].lstrip())
            except (urllib.error.URLError, TimeoutError, ConnectionError, ValueError):
                if not self.stopped.is_set():
                    time.sleep(0.5)

    def wait(self, message, predicate):
        def check():
            while True:
                try:
                    item = self.messages.get_nowait()
                except queue.Empty:
                    return False
                if predicate(item):
                    return item
        return eventually(message, check, timeout=45)

    def close(self):
        self.stopped.set()
        if self.response:
            self.response.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--isolate-upstream', action='store_true',
                        help='Stop Assessment/Activity temporarily to prove query independence (CI only).')
    parser.add_argument('--compose-project', default='study-leftovers-ci')
    options = parser.parse_args()
    if options.isolate_upstream and options.compose_project != 'study-leftovers-ci':
        raise ValueError('Isolation is restricted to the explicitly named disposable CI project')
    suffix = uuid.uuid4().hex[:12]
    account = eventually('Account service accepts registration', lambda: api(8085, 'auth/register', body={
        'username': 'stream_' + suffix, 'password': 'Demo-Only-Password-123', 'displayName': 'Streaming Demo'}), timeout=240)
    token = account['token']
    eventually('Planning is ready', lambda: api(8084, 'planning/stream-status', token), timeout=240)
    today = dt.date.today()
    monday = today - dt.timedelta(days=today.weekday())
    due = today + dt.timedelta(days=45)
    subject = eventually('Subject service accepts a manual subject', lambda: api(8081, 'subjects', token, {
        'code': 'CSCI318', 'name': 'Software Engineering', 'creditPoints': 6,
        'weeklyStudyTargetMinutes': 120, 'assessments': []}), timeout=240)
    observer = DashboardObserver(token)
    isolated = False
    try:
        observer.wait('SSE connection provides an initial local snapshot', lambda data: data['stream']['source'] == 'KAFKA_STREAMS')
        assessment = eventually('Assessment service accepts an assessment', lambda: api(8082, 'assessments', token, {
            'subjectId': subject['id'], 'title': 'Streaming Demo Report', 'type': 'Report', 'weighting': 30,
            'dueDate': due.isoformat(), 'estimatedMinutes': 180, 'priority': 'HIGH'}), timeout=240)
        observer.wait('Assessment event pushes workload to the browser without page reload',
                      lambda data: data['week']['workload']['incompleteAssessments'] == 1)
        eventually('RT-01 materialized workload has the correct estimated minutes',
                   lambda: api(8084, 'planning/workload', token)['estimatedMinutes'] == 180)
        session = eventually('Activity service records study work', lambda: api(8083, 'study-sessions', token, {
            'subjectId': subject['id'], 'durationMinutes': 45, 'studyDate': today.isoformat(), 'description': 'Lecture revision'}), timeout=240)
        observer.wait('Study event pushes progress to the browser',
                      lambda data: any(item['studiedMinutes'] == 45 for item in data['week']['studyProgress']))
        progress_path = f"planning/progress/{subject['id']}"
        eventually('RT-02 joins the subject target with weekly study minutes',
                   lambda: (lambda result: result['targetMinutes'] == 120 and result['studiedMinutes'] == 45)(api(8084, progress_path, token)))
        api(8083, f"study-sessions/{session['id']}", token, {'durationMinutes': 60,
            'studyDate': (monday - dt.timedelta(days=1)).isoformat(), 'description': 'Corrected to last week'}, method='PATCH')
        eventually('A session correction retracts minutes from the current week',
                   lambda: (lambda result: result['totalMinutes'] == 60 and result['studiedMinutes'] == 0)(api(8084, progress_path, token)))
        api(8083, f"study-sessions/{session['id']}", token, {'durationMinutes': 45,
            'studyDate': today.isoformat(), 'description': 'Corrected to today'}, method='PATCH')
        block = api(8084, 'calendar', token, {'title': 'Report study block', 'type': 'STUDY_SESSION',
            'subjectId': subject['id'], 'assessmentId': assessment['id'], 'description': 'Linked work',
            'startAt': today.isoformat() + 'T18:00:00', 'endAt': today.isoformat() + 'T18:30:00', 'spacedRepetition': False})
        api(8084, f"calendar/{block['id']}/complete", token, method='POST')
        eventually('Completed calendar blocks also update progress exactly once',
                   lambda: api(8084, progress_path, token)['studiedMinutes'] == 75)
        plan = api(8084, 'planning/plans', token, {'startDate': today.isoformat(), 'dailyAvailabilityMinutes': {
            (today + dt.timedelta(days=day)).isoformat(): 120 for day in range(7)}})
        assert plan['endDate'] == due.isoformat()
        assert sum(item['allocatedMinutes'] for item in plan['items']) == 150
        assert any(item['repetitionStage'] > 0 for item in plan['items'])
        assert all(item['date'] < due.isoformat() for item in plan['items'])
        print('PASS: deadline schedule contains 150 remaining minutes and spaced reviews through the due date', flush=True)
        api(8082, f"assessments/{assessment['id']}/complete", token, method='POST')
        observer.wait('Completing the assessment pushes a reduced workload',
                      lambda data: data['week']['workload']['incompleteAssessments'] == 0)
        other = api(8085, 'auth/register', body={'username': 'other_' + suffix,
            'password': 'Demo-Only-Password-456', 'displayName': 'Other Student'})['token']
        assert api(8084, 'planning/workload', other)['incompleteAssessments'] == 0
        assert api(8084, 'planning/progress', other) == []
        print('PASS: read models remain isolated by authenticated account', flush=True)
        if options.isolate_upstream:
            subprocess.run(['docker', 'compose', '-p', options.compose_project, 'stop',
                            'assessment-service', 'study-activity-service'], check=True)
            isolated = True
            assert api(8084, 'planning/workload', token)['incompleteAssessments'] == 0
            assert api(8084, progress_path, token)['studiedMinutes'] == 75
            assert api(8084, 'planning/this-week', token)['studyProgress'][0]['studiedMinutes'] == 75
            print('PASS: dashboard queries work with Assessment and Activity stopped; no upstream REST fallback', flush=True)
    finally:
        observer.close()
        if isolated:
            subprocess.run(['docker', 'compose', '-p', options.compose_project, 'start',
                            'assessment-service', 'study-activity-service'], check=True)
    print('All live Kafka integration checks passed.', flush=True)


if __name__ == '__main__':
    main()
