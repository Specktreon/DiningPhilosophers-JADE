import os
import sys
import zlib
import urllib.request

CHARS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_'

def encode_6bit(b):
    return CHARS[b & 0x3F]

def encode_3bytes(b1, b2, b3):
    c1 = b1 >> 2
    c2 = ((b1 & 0x3) << 4) | (b2 >> 4)
    c3 = ((b2 & 0xF) << 2) | (b3 >> 6)
    c4 = b3 & 0x3F
    return encode_6bit(c1) + encode_6bit(c2) + encode_6bit(c3) + encode_6bit(c4)

def plantuml_encode(text):
    z = zlib.compressobj(9, zlib.DEFLATED, -15)
    data = z.compress(text.encode('utf-8')) + z.flush()
    res = []
    i = 0
    while i < len(data):
        b1 = data[i]
        b2 = data[i+1] if i+1 < len(data) else 0
        b3 = data[i+2] if i+2 < len(data) else 0
        res.append(encode_3bytes(b1, b2, b3))
        i += 3
    return ''.join(res)

def render_file(puml_path):
    base_name = os.path.splitext(puml_path)[0]
    print(f"Processing {puml_path}...")
    with open(puml_path, 'r', encoding='utf-8') as f:
        content = f.read()

    encoded = plantuml_encode(content)

    for ext in ['png', 'svg']:
        url = f"http://www.plantuml.com/plantuml/{ext}/{encoded}"
        out_file = f"{base_name}.{ext}"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                data = resp.read()
                with open(out_file, 'wb') as out_f:
                    out_f.write(data)
            print(f"  -> Generated {out_file} ({len(data)} bytes)")
        except Exception as e:
            print(f"  [ERROR] Failed to fetch {ext}: {e}")

def main():
    uml_dir = os.path.dirname(os.path.abspath(__file__))
    files = [f for f in os.listdir(uml_dir) if f.endswith('.puml')]
    for f in sorted(files):
        render_file(os.path.join(uml_dir, f))
    print("\nAll UML diagrams processed successfully!")

if __name__ == '__main__':
    main()
