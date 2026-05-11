#!/usr/bin/env bash

rollout_json_escape() {
  local value="${1:-}"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  echo "${value}"
}

rollout_targets_json() {
  local targets="${1:-}"
  if [[ -z "${targets}" || "${targets}" == "(none)" ]]; then
    echo "[]"
    return
  fi

  local json="["
  local first=true
  local target
  local -a target_list
  IFS=',' read -r -a target_list <<< "${targets}"
  for target in "${target_list[@]}"; do
    target="${target//[[:space:]]/}"
    if [[ -z "${target}" ]]; then
      continue
    fi
    if [[ "${first}" == "true" ]]; then
      first=false
    else
      json+=","
    fi
    json+="\"$(rollout_json_escape "${target}")\""
  done
  json+="]"
  echo "${json}"
}

rollout_arguments_json() {
  local json="["
  local first=true
  local argument
  for argument in "${ORIGINAL_ARGS[@]}"; do
    if [[ "${first}" == "true" ]]; then
      first=false
    else
      json+=","
    fi
    json+="\"$(rollout_json_escape "${argument}")\""
  done
  json+="]"
  echo "${json}"
}

rollout_write_evidence() {
  local command_name="$1"
  local version_value="$2"
  local before_read="$3"
  local before_write="$4"
  local after_read="$5"
  local after_write="$6"
  local result="$7"
  local error_message="$8"
  local timestamp evidence_file file_version error_json
  timestamp="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  mkdir -p "${EVIDENCE_DIR}"
  file_version="${version_value:-na}"
  evidence_file="${EVIDENCE_DIR}/${timestamp//[:]/}-${command_name}-${file_version}.json"
  if [[ -z "${error_message}" ]]; then
    error_json="null"
  else
    error_json="\"$(rollout_json_escape "${error_message}")\""
  fi

  cat > "${evidence_file}" <<JSON
{
  "command": "$(rollout_json_escape "${command_name}")",
  "arguments": $(rollout_arguments_json),
  "timestamp": "$(rollout_json_escape "${timestamp}")",
  "elasticsearchUrl": "$(rollout_json_escape "${ELASTICSEARCH_BASE_URL}")",
  "readAlias": "$(rollout_json_escape "${READ_ALIAS}")",
  "writeAlias": "$(rollout_json_escape "${WRITE_ALIAS}")",
  "before": {
    "readTargets": $(rollout_targets_json "${before_read}"),
    "writeTargets": $(rollout_targets_json "${before_write}")
  },
  "after": {
    "readTargets": $(rollout_targets_json "${after_read}"),
    "writeTargets": $(rollout_targets_json "${after_write}")
  },
  "mappingHash": "$(rollout_json_escape "$(mapping_hash)")",
  "result": "$(rollout_json_escape "${result}")",
  "error": ${error_json}
}
JSON
  echo "Wrote rollout evidence: ${evidence_file}"
}
