"""Lossless container conversion: every cmap/glyph and the original license are retained."""
import json
from pathlib import Path
from fontTools.ttLib import TTFont

root = Path(__file__).resolve().parents[2]
results = []
for source in sorted((root / 'frontend/public/fonts').glob('be-vietnam-pro-*.ttf')):
    original = TTFont(source, recalcTimestamp=False)
    cmap, glyphs = original.getBestCmap(), original.getGlyphOrder()
    target = source.with_suffix('.woff2')
    original.flavor = 'woff2'
    original.save(target)
    restored = TTFont(target)
    assert restored.getBestCmap() == cmap
    assert restored.getGlyphOrder() == glyphs
    assert all(ord(char) in cmap for char in 'ĐđĂăÂâÊêÔôƠơƯưẮằấệộớựỵ')
    results.append(dict(source=source.name, target=target.name, originalBytes=source.stat().st_size,
                        bytes=target.stat().st_size, codepoints=len(cmap), glyphs=len(glyphs)))
output = root / 'tmp/loading-performance/fonts.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(results, indent=2), encoding='utf-8')
print(json.dumps(results))
