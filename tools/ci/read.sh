#!/usr/bin/env bash
# Print what the last (or given) GitHub Actions run did: failed steps, Kotlin errors, BUILD result.
#   tools/ci/read.sh [run_id]
# Reads the logs through the API, NOT raw.githubusercontent.com: the raw CDN caches files for ~5 minutes and shows stale logs.
if [ -z "${GH_TOKEN:-}" ] && [ -f /tmp/.gh_env ]; then . /tmp/.gh_env; fi
: "${GH_TOKEN:?set GH_TOKEN first}"
R=${REPO:-KillswitchYT/tally}; H="Authorization: Bearer $GH_TOKEN"
RID=${1:-$(curl -s -m 25 -H "$H" "https://api.github.com/repos/$R/actions/runs?per_page=1" | python3 -c "import sys,json; print(json.load(sys.stdin)['workflow_runs'][0]['id'])")}
curl -s -m 25 -H "$H" "https://api.github.com/repos/$R/actions/runs/$RID/jobs" | python3 -c "
import sys,json
for j in json.load(sys.stdin).get('jobs',[]):
    print('JOB',j['name'],j['conclusion'] or j['status'])
    for s in j['steps']:
        if s['conclusion'] not in ('success','skipped'): print('   step',s['number'],s['name'],'->',s['conclusion'] or s['status'])"
OUT=${OUT:-/tmp/ci}; mkdir -p "$OUT"; rm -f "$OUT/build.log" "$OUT/run.txt"
for f in build.log sdk.log run.txt; do
  curl -s -m 40 -H "$H" -H "Accept: application/vnd.github.raw" "https://api.github.com/repos/$R/contents/$f?ref=build-logs" -o "$OUT/$f"
done
echo "log is from: $(cat "$OUT/run.txt")"
echo "--- Kotlin errors: $(grep -c '^e: ' "$OUT/build.log")"
grep -E '^e: ' "$OUT/build.log" | sed 's|file:///home/runner/work/[^/]*/[^/]*/||' | cut -c1-260 | head -40
echo "--- result:"; grep -E 'BUILD (SUCCESSFUL|FAILED)' "$OUT/build.log" | head -2
grep -E '^FAILURE|What went wrong' -A3 "$OUT/build.log" | grep -v '^\s*at ' | head -8 | cut -c1-220
echo "(full logs: $OUT/build.log and $OUT/sdk.log)"
