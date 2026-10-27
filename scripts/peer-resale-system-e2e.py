"""Real service graph against fresh test resources; no bank or user's environment is contacted."""
import os
from pathlib import Path
import runpy

root=Path(__file__).resolve().parents[1]
os.environ['SPORTHUB_E2E_TEST_SCRIPT']='peer-resale-system-e2e.mjs'
os.environ.setdefault('SPORTHUB_E2E_ARTIFACT_ROOT',str(root/'tmp/backend-peer-system-fixed'))
os.environ.setdefault('SPORTHUB_E2E_FRONTEND_ROOT',str(root/'frontend'))
os.environ.setdefault('SPORTHUB_E2E_OUTPUT',str(root/'tmp/peer-resale-performance/system-evidence'))
runpy.run_path(str(root/'scripts/real-payments-system-e2e.py'),run_name='__main__')
