import sys, re, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
pat = re.compile(sys.argv[1])
path = r"C:\Users\ANORMAL444\AppData\Local\Temp\ymap3\mappings\mappings.tiny"
try:
    lines = open(path, encoding='utf-8').read().splitlines()
except OSError as e:
    print("STALE: ymap3 not exported:", e)
    sys.exit(1)
cls = None
hits = 0
for l in lines:
    if l.startswith('c\t'):
        cls = l.split('\t')[-1]
    if pat.search(l):
        prefix = cls + ' :: ' if (cls and l.startswith('\t')) else ''
        print(prefix + l)
        hits += 1
        if hits > int(sys.argv[2]) if len(sys.argv) > 2 else True:
            pass
print('total hits:', hits)
