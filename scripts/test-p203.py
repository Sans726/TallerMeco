#!/usr/bin/env python3
"""Compatibility entrypoint: repository/customer tests now run in the disposable-schema suite."""
import subprocess,sys
from pathlib import Path
sys.exit(subprocess.run([sys.executable,str(Path(__file__).with_name('test-customer-admin.py'))]).returncode)
