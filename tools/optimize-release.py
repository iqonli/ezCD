"""Compact an unsigned release APK before zipalign/signing (Build Tools 36+).

App resources are referenced by ID; name lookup is used only for android resources.
Keep ID names for Android view state/debugging; collapse other resource names.
Verify every resource ID, configuration and value and every packaged file survives.
"""
from pathlib import Path
import argparse
import re
import subprocess
import tempfile
import zipfile


def optimize(aapt2, source, target):
    def run(*args):
        return subprocess.check_output([str(aapt2), *map(str, args)], text=True)

    before = run('dump', 'resources', source)
    with tempfile.TemporaryDirectory() as tmp:
        keep = Path(tmp) / 'resources.cfg'
        ids = re.findall(r'resource 0x[0-9a-f]+ (id/[^\s]+)', before)
        keep.write_text(''.join(f'{name}#no_collapse\n' for name in ids))
        run('optimize', '--collapse-resource-names', '--deduplicate-entry-values',
            '--resources-config-path', keep, '-o', target, source)
    after = run('dump', 'resources', target)
    # Resource names change both in declarations and symbolic references.
    def values(text):
        return re.sub(r'\b(color|drawable|id|layout|mipmap|plurals|string|style|xml)/[\w.$-]+',
                      r'\1/NAME', text)
    assert values(before) == values(after), 'Resource IDs/configurations/values changed'
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(target) as new:
        assert new.testzip() is None
        assert set(old.namelist()) == set(new.namelist())
        for name in old.namelist():
            if name != 'resources.arsc':
                assert old.read(name) == new.read(name), name
    assert target.stat().st_size <= source.stat().st_size
    print(f'Verified resources and files; unsigned APK: {source.stat().st_size} -> {target.stat().st_size} bytes')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--aapt2', type=Path, required=True)
    p.add_argument('source', type=Path)
    p.add_argument('target', type=Path)
    args = p.parse_args()
    optimize(args.aapt2, args.source, args.target)
