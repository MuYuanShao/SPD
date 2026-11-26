# -*- coding: utf-8 -*-
"""One-shot patch helper: apply exact string replacements to a file (UTF-8, no BOM)."""
import io
import sys

def apply(path, old, new, count=1):
    with io.open(path, 'r', encoding='utf-8') as f:
        text = f.read()
    n = text.count(old)
    if n < count:
        raise SystemExit('PATTERN NOT FOUND (%d found, need %d) in %s' % (n, count, path))
    text = text.replace(old, new, count)
    with io.open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(text)
    print('patched %s (1 occurrence)' % path)

if __name__ == '__main__':
    pass
