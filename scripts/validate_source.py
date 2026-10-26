#!/usr/bin/env python3
"""Fast source-level checks run before every Jabra Extension Hub APK build."""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors = []
checks = 0

def fail(message):
    errors.append(message)

def check(condition, message):
    global checks
    checks += 1
    if not condition:
        fail(message)

# Parse every JSON file, including sample plugin manifests.
for path in ROOT.rglob('*.json'):
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
        checks += 1
        if path.name == 'manifest.json' and 'app/src/main/assets' in path.as_posix():
            check(isinstance(data, dict), f'{path}: manifest root must be an object')
            check(bool(str(data.get('id', '')).strip()), f'{path}: missing plugin id')
            check(bool(str(data.get('name', '')).strip()), f'{path}: missing plugin name')
            check(bool(str(data.get('version', '')).strip()), f'{path}: missing plugin version')
            entry = str(data.get('entry', 'index.html'))
            check(not entry.startswith('/') and '\\' not in entry and '..' not in entry.split('/'), f'{path}: unsafe entry path')
            required = data.get('requiredCapabilities', [])
            check(isinstance(required, list) and all(isinstance(x, str) and x.strip() for x in required), f'{path}: requiredCapabilities must be a string array')
    except Exception as exc:
        fail(f'{path}: invalid JSON: {exc}')

for path in ROOT.rglob('*.xml'):
    try:
        ET.parse(path)
        checks += 1
    except Exception as exc:
        fail(f'{path}: invalid XML: {exc}')

build_file = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
version_name = re.search(r'versionName\s*=\s*"([^"]+)"', build_file)
version_code = re.search(r'versionCode\s*=\s*(\d+)', build_file)
check(bool(version_name), 'app/build.gradle.kts: missing versionName')
check(bool(version_code), 'app/build.gradle.kts: missing versionCode')
if version_name:
    check(bool(re.fullmatch(r'\d+\.\d+\.\d+', version_name.group(1))), f'Invalid semantic versionName: {version_name.group(1)}')
    release_note = ROOT / f"README_JABRA_V{version_name.group(1)}.txt"
    check(release_note.is_file(), f'Missing release notes for version {version_name.group(1)}')
    if release_note.is_file():
        check(version_name.group(1) in release_note.read_text(encoding='utf-8'), 'Release notes do not mention the app version')
if version_code:
    check(int(version_code.group(1)) > 0, f'versionCode must be positive, found {version_code.group(1)}')

contract = (ROOT / 'app/src/main/java/com/jabra/hub/JabraApiContract.kt').read_text(encoding='utf-8')
capability_block = re.search(r'val capabilities: List<String> = listOf\((.*?)\)', contract, re.S)
supported_capabilities = set(re.findall(r'"([A-Za-z][A-Za-z0-9]+)"', capability_block.group(1))) if capability_block else set()
for manifest_path in (ROOT / 'app/src/main/assets').rglob('manifest.json'):
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    for capability in manifest.get('requiredCapabilities', []):
        check(capability in supported_capabilities, f'{manifest_path}: required capability {capability!r} is not in the host contract')
bridge = (ROOT / 'app/src/main/java/com/jabra/hub/PluginActivity.kt').read_text(encoding='utf-8')
store = (ROOT / 'app/src/main/java/com/jabra/hub/PluginStore.kt').read_text(encoding='utf-8')
main = (ROOT / 'app/src/main/java/com/jabra/hub/MainActivity.kt').read_text(encoding='utf-8')
for token, source, label in [
    ('API_VERSION = 1', contract, 'API contract version'),
    ('recordDiagnostic', bridge, 'native diagnostic bridge'),
    ('minJabraApiVersion', store, 'minimum API compatibility validation'),
    ('requiredCapabilities', store, 'required capability validation'),
    ('showApiContract', main, 'developer API contract screen'),
    ('APK_FILENAME=Jabra-Extension-Hub-v${VERSION_NAME}-debug.apk', (ROOT / '.github/workflows/android-build.yml').read_text(encoding='utf-8'), 'versioned debug APK artifact'),
    ('JABRA_SIGNING_KEYSTORE_BASE64', (ROOT / '.github/workflows/android-build.yml').read_text(encoding='utf-8'), 'stable signing secret support'),
    ('Generate Jabra release signing key (one-time)', (ROOT / '.github/workflows/generate-signing-key.yml').read_text(encoding='utf-8'), 'one-time signing key workflow'),
    ('STABLE_SIGNING_SETUP.md', '\n'.join(str(p.relative_to(ROOT)) for p in ROOT.rglob('STABLE_SIGNING_SETUP.md')), 'stable signing migration instructions'),
]:
    check(token in source, f'Missing expected release feature: {label}')

if errors:
    print(f'Source validation FAILED ({len(errors)} errors, {checks} checks attempted):')
    for error in errors:
        print(f' - {error}')
    sys.exit(1)
print(f'Source validation passed: {checks} checks; version {version_name.group(1)} (code {version_code.group(1)}).')
