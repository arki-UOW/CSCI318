"""Real Docker document-reader/OCR acceptance with no provider key (labelled-text extraction).

Run after Docker services are ready. This checks transport, readers, review and confirmation;
it is not live-Gemini interpretation evidence. Creates a disposable account and five subjects.
"""
import time
import io
import json
from pathlib import Path
import urllib.request
import urllib.error
import uuid
import zipfile
import importlib.util

spec = importlib.util.spec_from_file_location('readiness', Path(__file__).with_name('verify-readiness.py'))
readiness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(readiness)

def api(port, route, token=None, body=None, content_type='application/json'):
    headers = {'Content-Type':content_type}
    if token: headers['Authorization'] = 'Bearer ' + token
    data = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(urllib.request.Request(f'http://localhost:{port}/api/{route}', data=data, headers=headers), timeout=60) as response:
        return json.load(response)

def docx(text):
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w') as z:
        z.writestr('[Content_Types].xml','<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        z.writestr('_rels/.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        z.writestr('word/document.xml','<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'+''.join('<w:p><w:r><w:t>'+line+'</w:t></w:r></w:p>' for line in text.splitlines())+'</w:body></w:document>')
    return output.getvalue()

def main():
    # Streaming verification restarts its isolated upstream services before returning.
    for port, route in [(8081, 'subjects'), (8082, 'assessments'), (8083, 'study-sessions'), (8085, 'auth/session')]:
        deadline = time.monotonic() + 120
        while True:
            try:
                with urllib.request.urlopen(f'http://localhost:{port}/api/{route}', timeout=5) as response:
                    if response.status == 200: break
            except urllib.error.HTTPError as error:
                if error.code in (400, 401): break  # Missing required auth header / unauthenticated.
            except (OSError, ValueError):
                pass
            if time.monotonic() >= deadline: raise RuntimeError(f'Service {port} did not become ready')
            time.sleep(2)
    token=api(8085,'auth/register',body={'username':'formats_'+uuid.uuid4().hex[:8], 'password':'Format-Fixture-1234','displayName':'Document format fixture'})['token']
    text='Subject Code: CSCI319\nSubject Name: Format Acceptance\nCredit Points: 6\nAssessment 1: Architecture Report 25% Due: 30/11/2026'
    jpeg=Path(__file__).with_name('fixtures').joinpath('outline-ocr.jpg').read_bytes()
    for index,(extension,mime,data) in enumerate([('pdf','application/pdf',readiness.outline_pdf()),
            ('docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',docx(text)),
            ('txt','text/plain',text.encode()),('jpg','image/jpeg',jpeg),('jpeg','image/jpeg',jpeg)]):
        boundary='OutlineBoundary'
        body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="outline.{extension}"\r\nContent-Type: {mime}\r\n\r\n').encode()+data+f'\r\n--{boundary}--\r\n'.encode()
        review=api(8081,'subject-outlines',token,body,'multipart/form-data; boundary='+boundary)
        extraction=review['extraction']
        assert extraction['subjectCode']=='CSCI319', (extension,extraction)
        # Distinct codes exercise confirmation without triggering duplicate-subject conflicts.
        extraction['subjectCode']='FORMAT'+str(100+index)
        confirmed=api(8081,'subject-outlines/'+review['importId']+'/confirm',token,{'weeklyStudyTargetMinutes':240,'extraction':extraction})
        assert confirmed, extension
        print('PASS: UC-01 '+extension.upper()+' read, reviewed and confirmed through Docker',flush=True)
    assert len(api(8081,'subjects',token))==5
    print('PASS: all five supported formats saved separately; JPG/JPEG used the real Tesseract binary',flush=True)

if __name__=='__main__': main()
