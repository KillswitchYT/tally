#!/usr/bin/env bash
# Push the project to GitHub, wait for the Actions build, print the result.
#   tools/ci/cycle.sh "commit message" [max_seconds_to_wait]
# Needs GH_TOKEN in the environment, or in /tmp/.gh_env as:  export GH_TOKEN='github_pat_...'
# Token needs, on the repo only: Contents, Actions and Workflows = read and write.
set -u
if [ -z "${GH_TOKEN:-}" ] && [ -f /tmp/.gh_env ]; then . /tmp/.gh_env; fi
: "${GH_TOKEN:?set GH_TOKEN first}"
R=${REPO:-KillswitchYT/tally}
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=${SRC:-$(cd "$HERE/../.." && pwd)}      # the project to push
PUSH=${PUSH:-/tmp/push}                      # scratch checkout of the repo
H="Authorization: Bearer $GH_TOKEN"
URL="https://x-access-token:${GH_TOKEN}@github.com/$R.git"   # only ever used on the command line, never saved in git config
api(){ curl -s -m 25 -H "$H" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$R/$1"; }

# 1. a checkout whose history matches the remote, so pushes are normal fast-forwards
if [ ! -d "$PUSH/.git" ]; then
  mkdir -p "$PUSH" && cd "$PUSH" && git init -q -b main && git config user.name "Claude" && git config user.email "noreply@anthropic.com"
  git fetch -q "$URL" main 2>/dev/null && git reset -q --soft FETCH_HEAD
fi
# 2. replace the working tree with the project and push if anything changed
find "$PUSH" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
(cd "$SRC" && tar cf - --exclude='./.gradle' --exclude='./build' --exclude='./app/build' --exclude='./local.properties' --exclude='./.git' .) | (cd "$PUSH" && tar xf -)
cd "$PUSH" && git add -A
if git diff --cached --quiet; then echo "nothing changed, not pushing"; else
  git commit -q -m "${1:-update}" && git push -q "$URL" HEAD:main 2>&1 | sed "s/${GH_TOKEN}/***/g" | tail -2
  echo "pushed $(git rev-parse --short=7 HEAD)"
fi
SHA=$(git rev-parse --short=7 HEAD); echo "waiting for the run on $SHA"

# 3. wait for that commit's run (about 3 minutes), then read it
end=$((SECONDS+${2:-300})); st=""; RID=""; concl=""
while [ $SECONDS -lt $end ]; do
  out=$(api "actions/runs?per_page=5" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('workflow_runs',[]):
    if r['head_sha'].startswith('$SHA'): print(r['id'],r['status'],r['conclusion'],sep='|'); break
else: print('||')")
  IFS='|' read -r RID st concl <<< "$out"
  [ "$st" = "completed" ] && break
  sleep 20
done
echo "run=$RID status=$st conclusion=$concl"
if [ "$st" != "completed" ]; then echo "still running: call  tools/ci/read.sh  again in a minute"; exit 0; fi
"$HERE/read.sh" "$RID"
