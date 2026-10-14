"""Run the current build on isolated, persistent, loopback-only development data.

Usage: python scripts/local-development.py [start|status|stop]
Build the JARs and Next standalone first. Runtime paths can be overridden with
SPORTHUB_DEV_JAVA and SPORTHUB_DEV_NODE. Never loads the repository .env.
Stop retains all databases, media, and containers; no reset/delete operation.
"""
import base64
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'tmp/current-development'
SERVICES = ['identity', 'facility', 'schedule', 'booking', 'payment', 'transfer']
LABEL = 'sporthub.development=current-6bd0'
PREFIX = 'sporthub-current-6bd0-'
NO_WINDOW = getattr(subprocess, 'CREATE_NO_WINDOW', 0)
JAVA = os.environ.get('SPORTHUB_DEV_JAVA', 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe')
NODE = os.environ.get('SPORTHUB_DEV_NODE', str(Path.home()/'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'))


def command(args):
    result = subprocess.run(args, capture_output=True, text=True, creationflags=NO_WINDOW)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return result.stdout.strip()


def ready(url, seconds=180, process=None):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if process and process.poll() is not None:
            raise RuntimeError('Process exited; inspect logs in ' + str(OUT))
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                if response.status == 200:
                    return
        except Exception:
            pass
        time.sleep(.5)
    raise RuntimeError('Readiness timeout: ' + url)


def amqp_ready(port):
    try:
        with socket.create_connection(('127.0.0.1', port), timeout=2) as sock:
            sock.sendall(b'AMQP\x00\x00\x09\x01')
            data = b''
            while len(data) < 11:
                part = sock.recv(11-len(data))
                if not part:
                    return False
                data += part
            return data[:3] == b'\x01\x00\x00' and data[7:11] == b'\x00\x0a\x00\x0a'
    except OSError:
        return False


def container(kind, image, args):
    name = PREFIX + kind
    found = subprocess.run(['docker', 'inspect', name], capture_output=True, text=True, creationflags=NO_WINDOW)
    if found.returncode == 0:
        metadata = json.loads(found.stdout)[0]
        if metadata['Config'].get('Labels', {}).get('sporthub.development') != 'current-6bd0':
            raise RuntimeError('Refusing unrelated container: ' + name)
        if not metadata['State']['Running']:
            command(['docker', 'start', name])
    else:
        command(['docker', 'run', '-d', '--name', name, '--label', LABEL, *args, image,
                 *(['server', '/data'] if kind == 'minio' else [])])
    return name


def save(config):
    (OUT/'runtime.json').write_text(json.dumps(config, indent=2), encoding='utf-8')


def status(config):
    checks = {}
    for name in SERVICES + ['gateway', 'frontend']:
        url = config['baseURL'] if name == 'frontend' else f'http://127.0.0.1:{config["ports"][name]}/actuator/health/liveness'
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                checks[name] = 'UP' if response.status == 200 else str(response.status)
        except Exception:
            checks[name] = 'DOWN'
    print(json.dumps({'baseURL':config['baseURL'], 'services':checks}, indent=2))
    return all(value == 'UP' for value in checks.values())


def stop(config):
    for saved in reversed(config.get('processes', [])):
        # Query before terminating: a saved PID can have been recycled.
        query = f'Get-CimInstance Win32_Process -Filter "ProcessId={int(saved["pid"])}" | Select-Object -ExpandProperty CommandLine'
        line = command(['powershell','-NoProfile','-Command',query])
        if saved['target'].replace('/', '\\') not in line.replace('/', '\\'):
            continue
        command(['powershell','-NoProfile','-Command',f'Stop-Process -Id {int(saved["pid"])} -Force -ErrorAction SilentlyContinue'])
    for name in config.get('containers', []):
        metadata = json.loads(command(['docker', 'inspect', name]))[0]
        if metadata['Config'].get('Labels', {}).get('sporthub.development') == 'current-6bd0':
            command(['docker', 'stop', '-t', '5', name])
    print('Stopped only this development stack. All data retained.')


def frontend(config):
    """Restart only our Next server after rebuilding it; retain Backend processes."""
    for saved in config.get('processes', []):
        if saved['name'] != 'frontend':
            continue
        query = f'Get-CimInstance Win32_Process -Filter "ProcessId={int(saved["pid"])}" | Select-Object -ExpandProperty CommandLine'
        line = command(['powershell','-NoProfile','-Command',query])
        if saved['target'].replace('/', '\\') in line.replace('/', '\\'):
            command(['powershell','-NoProfile','-Command',f'Stop-Process -Id {int(saved["pid"])} -Force -ErrorAction SilentlyContinue'])
    standalone = ROOT/'frontend/.next/standalone'
    shutil.copytree(ROOT/'frontend/.next/static',standalone/'.next/static',dirs_exist_ok=True)
    shutil.copytree(ROOT/'frontend/public',standalone/'public',dirs_exist_ok=True)
    target = str(standalone/'server.js')
    env = dict(os.environ,GATEWAY_URL=f'http://127.0.0.1:{config["ports"]["gateway"]}',PORT=str(config['ports']['frontend']),HOSTNAME='127.0.0.1',NEXT_TELEMETRY_DISABLED='1')
    with (OUT/'frontend.log').open('a',encoding='utf-8') as log:
        process = subprocess.Popen([NODE,target],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=NO_WINDOW)
    config['processes'] = [entry for entry in config['processes'] if entry['name']!='frontend']
    config['processes'].append({'name':'frontend','pid':process.pid,'target':target})
    save(config)
    ready(config['baseURL'],process=process)
    print('READY frontend '+config['baseURL'])


def start(config):
    if config.get('processes') and status(config):
        return
    if config.get('processes'):
        stop(config)
    for port in config['ports'].values():
        with socket.socket() as sock:
            try:
                sock.bind(('127.0.0.1', port))
            except OSError:
                raise RuntimeError('Port already used; no unrelated process will be stopped: ' + str(port))
    p = config['ports']
    pg = container('postgres', 'postgres:16-alpine', ['-p', f'127.0.0.1:{p["postgres"]}:5432', '-e', 'POSTGRES_USER=sporthub_dev', '-e', 'POSTGRES_PASSWORD=sporthub_local_only', '-v', PREFIX+'postgres-data:/var/lib/postgresql/data'])
    redis = container('redis', 'redis:7-alpine', ['-p',f'127.0.0.1:{p["redis"]}:6379'])
    rabbit = container('rabbit', 'rabbitmq:3.13-management-alpine', ['-p',f'127.0.0.1:{p["rabbit"]}:5672','-e','RABBITMQ_DEFAULT_USER=sporthub_dev','-e','RABBITMQ_DEFAULT_PASS=sporthub_local_only','-e','RABBITMQ_DEFAULT_VHOST=sporthub-dev','-v',PREFIX+'rabbit-data:/var/lib/rabbitmq'])
    minio = container('minio', 'ghcr.io/coollabsio/minio@sha256:a4938f37f1be1841b8e7b627ad0207b265345fd0d063e42d7410c78af0e63e68', ['-p',f'127.0.0.1:{p["minio"]}:9000','-e','MINIO_ROOT_USER=sporthub_dev','-e','MINIO_ROOT_PASSWORD=sporthub_local_only','-v',PREFIX+'minio-data:/data'])
    mail = container('mail', 'axllent/mailpit:v1.28.2', ['-p',f'127.0.0.1:{p["smtp"]}:1025','-p',f'127.0.0.1:{p["mail"]}:8025'])
    config['containers'] = [pg,redis,rabbit,minio,mail]
    config['pg'], config['pgUser'] = pg, 'sporthub_dev'
    save(config)
    deadline = time.monotonic()+100
    while time.monotonic() < deadline:
        check = subprocess.run(['docker','exec',pg,'pg_isready','-U','sporthub_dev'],capture_output=True,creationflags=NO_WINDOW)
        if check.returncode == 0 and amqp_ready(p['rabbit']):
            break
        time.sleep(1)
    else:
        raise RuntimeError('PostgreSQL or AMQP is not ready')
    ready(f'http://127.0.0.1:{p["minio"]}/minio/health/live')
    ready(f'http://127.0.0.1:{p["mail"]}/api/v1/info')
    existing = command(['docker','exec',pg,'psql','-U','sporthub_dev','-d','postgres','-At','-c','SELECT datname FROM pg_database']).splitlines()
    for db in config['databases'].values():
        if db not in existing:
            command(['docker','exec',pg,'createdb','-U','sporthub_dev',db])
        command(['docker','exec',pg,'psql','-U','sporthub_dev','-d',db,'-v','ON_ERROR_STOP=1','-c','CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; CREATE EXTENSION IF NOT EXISTS btree_gist;'])
    env = dict(os.environ)
    env.update({'SERVER_ADDRESS':'127.0.0.1','MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED':'true',
        'SPRING_DATASOURCE_USERNAME':'sporthub_dev','SPRING_DATASOURCE_PASSWORD':'sporthub_local_only',
        'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'4','SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE':'1',
        'SPRING_RABBITMQ_HOST':'127.0.0.1','SPRING_RABBITMQ_PORT':str(p['rabbit']),
        'SPRING_RABBITMQ_USERNAME':'sporthub_dev','SPRING_RABBITMQ_PASSWORD':'sporthub_local_only',
        'SPRING_RABBITMQ_VIRTUAL_HOST':'sporthub-dev','DEMO_SEED_ENABLED':'false',
        'JWT_SECRET':config['jwtSecret'],'SERVICE_CALL_SECRET':config['serviceSecret'],
        'OWNER_APPLICATION_DATA_KEY':config['dataKey'],'REDIS_HOST':'127.0.0.1','REDIS_PORT':str(p['redis']),
        'REDIS_PASSWORD':'','SPRING_DATA_REDIS_PASSWORD':'',
        'SPORTHUB_MEDIA_ACCESS_KEY':'sporthub_dev','SPORTHUB_MEDIA_SECRET_KEY':'sporthub_local_only',
        'SPORTHUB_MEDIA_ENDPOINT':f'http://127.0.0.1:{p["minio"]}','SPORTHUB_MEDIA_BUCKET':'sporthub-development',
        'MINIO_PUBLIC_ENDPOINT':f'http://127.0.0.1:{p["minio"]}',
        'SPRING_MAIL_HOST':'127.0.0.1','SPRING_MAIL_PORT':str(p['smtp']),
        'SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH':'false','SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE':'false',
        'FRONTEND_BASE_URL':config['baseURL'],'APP_PUBLIC_URL':config['baseURL'],
        'CORS_ALLOWED_ORIGIN':config['baseURL'],'SECURE_COOKIE':'false','DEMO_SMS_ENABLED':'false',
        'SPORTHUB_EVENTS_OUTBOX_DELAY_MS':'500',
        'DEMO_PAYMENT_CALLBACK_SECRET':config['callbackSecret']})
    for name in SERVICES:
        env[name.upper()+'_SERVICE_URL'] = f'http://127.0.0.1:{p[name]}'
    processes = []
    config['processes'] = []
    for name in SERVICES + ['gateway','frontend']:
        child = dict(env,SERVER_PORT=str(p[name]),SPORTHUB_EVENTS_OUTBOX_ENABLED='true' if name in ['facility','booking','payment','transfer'] else 'false')
        if name == 'frontend':
            standalone = ROOT/'frontend/.next/standalone'
            shutil.copytree(ROOT/'frontend/.next/static',standalone/'.next/static',dirs_exist_ok=True)
            shutil.copytree(ROOT/'frontend/public',standalone/'public',dirs_exist_ok=True)
            target = str(standalone/'server.js')
            child.update(GATEWAY_URL=f'http://127.0.0.1:{p["gateway"]}',PORT=str(p[name]),HOSTNAME='127.0.0.1',NEXT_TELEMETRY_DISABLED='1')
            args = [NODE,target]
        else:
            module = 'api-gateway' if name == 'gateway' else name+'-service'
            target = str(ROOT/'backend'/module/'target'/(module+'-0.0.1-SNAPSHOT.jar'))
            child['SPRING_PROFILES_ACTIVE'] = 'local' if name == 'payment' else 'default'
            if name != 'gateway':
                child['SPRING_DATASOURCE_URL'] = f'jdbc:postgresql://127.0.0.1:{p["postgres"]}/{config["databases"][name]}'
            args = [JAVA,'-Xmx256m','-jar',target]
        with (OUT/(name+'.log')).open('a',encoding='utf-8') as log:
            process = subprocess.Popen(args,cwd=ROOT,env=child,stdout=log,stderr=subprocess.STDOUT,creationflags=NO_WINDOW)
        processes.append((name,process))
        config['processes'].append({'name':name,'pid':process.pid,'target':target})
        save(config)
    for name,process in processes:
        url = config['baseURL'] if name == 'frontend' else f'http://127.0.0.1:{p[name]}/actuator/health/liveness'
        ready(url,process=process)
        print('READY '+name,flush=True)
    config['readyAt'] = time.strftime('%Y-%m-%dT%H:%M:%S%z')
    save(config)
    print('Project left running: '+config['baseURL'],flush=True)
    print('Local verification mailbox: '+f'http://127.0.0.1:{p["mail"]}',flush=True)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    OUT.mkdir(parents=True,exist_ok=True)
    file = OUT/'runtime.json'
    if file.exists():
        config = json.loads(file.read_text(encoding='utf-8'))
    else:
        config = {'baseURL':'http://127.0.0.1:3000','output':str(OUT),'run':'development',
            'ports':dict(zip(SERVICES,[18101,18102,18103,18104,18105,18106]),gateway=18090,frontend=3000,postgres=55490,redis=56390,rabbit=56790,minio=59090,smtp=51090,mail=58090),
            'databases':{name:name+'_current_development' for name in SERVICES},
            'jwtSecret':base64.b64encode(os.urandom(48)).decode(),'serviceSecret':base64.b64encode(os.urandom(48)).decode(),
            'dataKey':base64.b64encode(os.urandom(32)).decode(),'callbackSecret':base64.b64encode(os.urandom(48)).decode()}
        save(config)
    action = sys.argv[1] if len(sys.argv)>1 else 'start'
    if action == 'start':
        start(config)
    elif action == 'stop':
        stop(config)
    elif action == 'status':
        sys.exit(0 if status(config) else 1)
    elif action == 'frontend':
        frontend(config)
    else:
        raise SystemExit('Expected start, status, stop, or frontend')
