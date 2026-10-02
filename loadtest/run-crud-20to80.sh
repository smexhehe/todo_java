#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

plan="loadtest/jmeter-tasks-crud-steps20.jmx"
jmeter_bin="${JMETER_BIN:-/f/apache-jmeter-5.6.3/bin/jmeter.bat}"

if [[ ! -f "$plan" ]]; then
  printf 'JMeter plan not found: %s\n' "$plan" >&2
  exit 1
fi
if [[ ! -f "$jmeter_bin" ]]; then
  printf 'JMeter executable not found: %s\n' "$jmeter_bin" >&2
  exit 1
fi

mapfile -t group_users < <(sed -nE 's/.*<intProp name="ThreadGroup.num_threads">([0-9]+)<\/intProp>.*/\1/p' "$plan")
if [[ "${group_users[*]}" != '20 20 20 20' ]]; then
  total=0
  stages=()
  for users in "${group_users[@]}"; do
    total=$((total + users))
    stages+=("$total")
  done
  printf 'Plan has %s users per group (total stages: %s). Expected 20 20 20 20 (20 40 60 80).\n' \
    "${group_users[*]:-none}" "${stages[*]:-none}" >&2
  exit 1
fi

mkdir -p diagnostics
run_id="crud-20to80-$(date +%Y%m%d-%H%M%S)-$$"
recording="jmeter-$run_id"
result="diagnostics/$run_id.jtl"
log="diagnostics/$run_id.log"
report="diagnostics/$run_id-report"
recording_started=0

finish() {
  status=$?
  trap - EXIT INT TERM
  if (( recording_started )); then
    if MSYS_NO_PATHCONV=1 docker compose exec -T todo jcmd 1 JFR.stop \
      "name=$recording" "filename=/diagnostics/$recording.jfr"; then
      if ! MSYS_NO_PATHCONV=1 docker compose cp \
        "todo:/diagnostics/$recording.jfr" "./diagnostics/$recording.jfr"; then
        status=1
      fi
    else
      status=1
    fi
  fi
  printf '\nJMeter results: %s\n' "$result"
  [[ ! -f "$report/index.html" ]] || printf 'HTML report: %s/index.html\n' "$report"
  [[ ! -f "diagnostics/$recording.jfr" ]] || printf 'JFR recording: diagnostics/%s.jfr\n' "$recording"
  exit "$status"
}

printf 'Users by stage: 20 -> 40 -> 60 -> 80\n'
docker compose up -d

ready=0
for (( attempt=0; attempt<30; attempt++ )); do
  if curl -fsS --max-time 2 'http://127.0.0.1:7540/actuator/health' >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
if (( ! ready )); then
  printf 'Application did not become ready at http://127.0.0.1:7540/actuator/health\n' >&2
  exit 1
fi

MSYS_NO_PATHCONV=1 docker compose exec -T todo jcmd 1 JFR.start "name=$recording" settings=profile
recording_started=1
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"$jmeter_bin" -n -t "$plan" -l "$result" -j "$log" -e -o "$report"
