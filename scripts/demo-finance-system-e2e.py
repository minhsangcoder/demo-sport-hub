"""Fresh labelled PostgreSQL/Redis plus genuine BFF/Gateway/service DEMO E2E.

No .env, user database, existing container or live bank integration is used.
Requires isolated built artifacts in tmp/backend-demo-system and tmp/demo-system-ui.
Keeps synthetic DB/evidence; stops only this run's own processes/containers.
"""
import importlib.util
import os
from pathlib import Path
import time

ROOT = Path(__file__).resolve().parents[1]
os.environ['SPORTHUB_E2E_TEST_SCRIPT'] = 'demo-finance-e2e.mjs'
os.environ['SPORTHUB_E2E_ARTIFACT_ROOT'] = str(ROOT / 'tmp/backend-demo-system')
os.environ['SPORTHUB_E2E_FRONTEND_ROOT'] = str(ROOT / 'tmp/demo-system-ui')
os.environ['SPORTHUB_E2E_OUTPUT'] = str(ROOT / 'tmp/demo-system-evidence')
spec = importlib.util.spec_from_file_location('system_e2e_harness', ROOT / 'scripts/system-e2e.py')
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)
own_containers = []
try:
    harness.PG = 'sporthub-finance-test-pg-' + harness.RUN
    harness.REDIS = 'sporthub-finance-test-redis-' + harness.RUN
    harness.PG_PORT, harness.REDIS_PORT = str(harness.free_port()), str(harness.free_port())
    harness.PG_USER, harness.PG_PASSWORD = 'finance_test', 'synthetic_finance_test_only'
    harness.command(['docker','run','-d','--name',harness.PG,'--label','sporthub.test=demo-finance',
        '-p',f'127.0.0.1:{harness.PG_PORT}:5432','-e','POSTGRES_USER='+harness.PG_USER,
        '-e','POSTGRES_PASSWORD='+harness.PG_PASSWORD,'postgres:16-alpine'])
    own_containers.append(harness.PG)
    harness.command(['docker','run','-d','--name',harness.REDIS,'--label','sporthub.test=demo-finance',
        '-p',f'127.0.0.1:{harness.REDIS_PORT}:6379','redis:7-alpine'])
    own_containers.append(harness.REDIS)
    for attempt in range(60):
        try:
            harness.command(['docker','exec',harness.PG,'pg_isready','-U',harness.PG_USER])
            break
        except Exception:
            time.sleep(.5)
    else:
        raise RuntimeError('Isolated test PostgreSQL did not become ready')
    raise SystemExit(harness.main())
finally:
    for name in own_containers:
        harness.command(['docker','stop','-t','3',name])
