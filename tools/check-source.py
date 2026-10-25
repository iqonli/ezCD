"""Offline resource/branding/source checks. Python standard library only."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'
for folder in res.glob('values*'):
    seen = set()
    for file in folder.glob('*.xml'):
        tree = ET.parse(file)
        for node in tree.getroot():
            key = (node.tag, node.get('name'))
            if key[1] and key in seen:
                raise AssertionError(f'Duplicate {folder.name}: {key}')
            seen.add(key)
for file in res.rglob('*.xml'):
    ET.parse(file)
for qualifier in ('values-b+zh+Hans', 'values-b+zh+Hant'):
    localized = ET.parse(res / qualifier / 'ezcd_strings.xml').getroot()
    localnames = {n.get('name') for n in localized}
    default = ET.parse(res / 'values/ezcd_strings.xml').getroot()
    assert {n.get('name') for n in default} <= localnames, qualifier
sources = list((root / 'app/src/main/java').rglob('*.kt'))
assert sources
assert not any('com.santiagorodriguez.countaway' in f.read_text() for f in sources)
assert 'android.permission.INTERNET' not in (root / 'app/src/main/AndroidManifest.xml').read_text()
assert 'Theme.Material.Light' in (res / 'values/styles.xml').read_text()
assert not any(res.glob('values-night*'))
assert all((root / path).is_file() for path in ('LICENSE','NOTICE','licenses/Apache-2.0-CountAway.txt','licenses/OFL-NotoSans.txt','gradle/wrapper/gradle-wrapper.jar'))
print(f'PASS: {len(sources)} Kotlin files; XML, localization, identity, license and light-theme checks.')
