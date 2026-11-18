#!/bin/bash
# Usage: regression.sh <base-ref>
# Converts every built-in sample and every file in the local corpus through import and all exports,
# on the committed HEAD and on <base-ref>, and prints the paths whose result changed.
set -u
BASE=${1:-origin/develop}
REPO=$(git rev-parse --show-toplevel)
HERE=$(cd "$(dirname "$0")" && pwd)
CORPUS=${UTAFORMATIX_CORPUS:-$HOME/.claude/state/utaformatix-corpus}
OUT=${TMPDIR:-/tmp}/utaformatix-regression
FIX=$REPO/src/jsTest/resources/fixtures
TEST=$REPO/src/jsTest/kotlin/io/RegressionDumpTest.kt
cd "$REPO"
if [ -n "$(git status --porcelain -- core src)" ]; then echo "working tree not clean; commit first" >&2; exit 2; fi
mkdir -p "$OUT"
cleanup() {
  git checkout -q HEAD -- core/ 2>/dev/null
  git clean -fdq core/ 2>/dev/null
  rm -f "$TEST" "$FIX"/corpus_*
}
trap cleanup EXIT
cp "$HERE/RegressionDumpTest.kt" "$TEST"
: > "$FIX/corpus_index.txt"
if [ -d "$CORPUS" ]; then
  i=0
  for f in "$CORPUS"/*; do
    [ -f "$f" ] || continue
    fmt=$(basename "$f" | cut -d_ -f1)
    i=$((i+1))
    if file -b --mime-encoding "$f" | grep -q binary; then
      base64 -i "$f" > "$FIX/corpus_${i}.b64.txt"; echo "corpus_${i}.b64.txt $fmt" >> "$FIX/corpus_index.txt"
    else
      cp "$f" "$FIX/corpus_${i}.txt"; echo "corpus_${i}.txt $fmt" >> "$FIX/corpus_index.txt"
    fi
  done
fi
dump() {
  JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew jsTest -x kotlinStoreYarnLock >/dev/null 2>&1
  python3 -I -c '
import html,sys
s=html.unescape(open(sys.argv[1]).read())
a=s.find("REGRESSION_DUMP_BEGIN"); b=s.find("REGRESSION_DUMP_END")
if a<0: sys.exit("dump missing")
print(s[a+len("REGRESSION_DUMP_BEGIN"):b].strip())
' build/test-results/jsBrowserTest/TEST-io.RegressionDumpTest.xml > "$1"
}
dump "$OUT/head1.txt" || exit 3
dump "$OUT/head2.txt" || exit 3
git checkout -q "$BASE" -- core/
dump "$OUT/base.txt" || exit 3
git checkout -q HEAD -- core/
git clean -fdq core/
python3 -I - "$OUT" <<'PY'
import sys
d=sys.argv[1]
def load(n):
    r={}
    for l in open(f"{d}/{n}"):
        l=l.rstrip("\n")
        key=l[:l.index("]")+1] if " -> " in l else " ".join(l.split(" ")[:2])
        r[key]=l
    return r
h1,h2,b=load("head1.txt"),load("head2.txt"),load("base.txt")
flaky={k for k in h1 if h1.get(k)!=h2.get(k)}
changed=[k for k in sorted(set(h1)|set(b)) if k not in flaky and h1.get(k)!=b.get(k)]
print(f"paths: {len(h1)}  flaky (ignored): {len(flaky)}  changed: {len(changed)}")
for k in changed:
    print("BASE:", b.get(k, "<missing>")); print("HEAD:", h1.get(k, "<missing>"))
PY
