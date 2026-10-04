#!/usr/bin/env python3
"""Regenerate committed Java sources with the shared, checksum-pinned ANTLR jar."""
import hashlib
import os
from pathlib import Path
import subprocess
import urllib.request
root = Path(__file__).resolve().parent.parent
jar = root / '.gradle/antlr/antlr-4.13.2-complete.jar'
jar.parent.mkdir(parents=True, exist_ok=True)
data = jar.read_bytes() if jar.exists() else urllib.request.urlopen('https://www.antlr.org/download/antlr-4.13.2-complete.jar').read()
assert hashlib.sha256(data).hexdigest() == 'eae2dfa119a64327444672aff63e9ec35a20180dc5b8090b7a6ab85125df4d76', 'ANTLR checksum mismatch'
jar.write_bytes(data)
output = root / 'app/src/main/java/org/dergigi/ants/query/generated'
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'
subprocess.run([java, '-jar', str(jar), '-Dlanguage=Java', '-no-listener', '-visitor', '-package', 'org.dergigi.ants.query.generated', '-Xexact-output-dir', '-o', str(output), 'grammar/AntsQuery.g4'], cwd=root, check=True)
for path in output.iterdir():
    if path.suffix != '.java': path.unlink()
