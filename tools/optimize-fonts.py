"""Generate mobile web fonts from the Android copy, leaving source fonts intact."""
from pathlib import Path
import argparse
import sys

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools' / 'python-packages'))

from fontTools import subset
from fontTools.ttLib import TTFont

PUBLIC = ROOT / 'web' / 'public'
OUTPUT = PUBLIC / 'fonts'
OUTPUT.mkdir(exist_ok=True)

characters = set(chr(code) for code in range(128))
for leading in range(0xB0, 0xF8):
    for trailing in range(0xA1, 0xFF):
        try:
            characters.update(bytes([leading, trailing]).decode('gb2312'))
        except UnicodeDecodeError:
            pass

for source in (ROOT / 'web' / 'src').rglob('*'):
    if source.suffix in ('.js', '.vue', '.css'):
        characters.update(source.read_text(encoding='utf-8'))

def convert(name, target, flavor, latin_only=False, pdf_embedding=False):
    font = TTFont(PUBLIC / name, fontNumber=0)
    options = subset.Options()
    options.layout_features = ['*']
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=(range(0x400) if latin_only else map(ord, characters)))
    subsetter.subset(font)
    if pdf_embedding:
        # fontkit copies glyph bytes into a PDF subset, then may use short loca
        # offsets (offset / 2). Odd-length glyph data would truncate those offsets.
        # Keep each glyph aligned, matching the original desktop font.
        font['glyf'].padding = 4
    font.flavor = flavor
    font.save(OUTPUT / target)
    print(f'{name}: {(PUBLIC / name).stat().st_size:,} -> {(OUTPUT / target).stat().st_size:,} bytes')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pdf-only', action='store_true', help='Regenerate only the PDF font')
    args = parser.parse_args()
    if not args.pdf_only:
        convert('Roboto.ttf', 'Roboto.woff2', 'woff2', True)
        convert('times.ttf', 'times.woff2', 'woff2', True)
        convert('OCRB.ttf', 'OCRB.woff2', 'woff2', True)
        convert('FZCDXK.TTF', 'FZCDXK.woff2', 'woff2')
        convert('simsun.ttf', 'simsun.woff2', 'woff2')
    convert('google_sans_rounded_regular.ttf', 'train-pdf.ttf', None, pdf_embedding=True)


if __name__ == '__main__':
    main()
