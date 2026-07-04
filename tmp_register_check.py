import json
import urllib.request

body = json.dumps({
    'email': 'register-test@example.com',
    'password': 'Password123!',
    'fullName': 'Register Test'
}).encode()
req = urllib.request.Request('http://localhost:8080/api/auth/register', data=body, headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(req, timeout=10) as f:
    print(f.status)
    print(f.read().decode())
