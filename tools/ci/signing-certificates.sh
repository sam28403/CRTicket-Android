#!/usr/bin/env bash

verify_signing_certificates() {
  local report="$1" expected="$2" actual
  local -a fingerprints
  mapfile -t fingerprints < <(
    printf '%s\n' "$report" |
      sed -n 's/^.*certificate SHA-256 digest: //p' |
      tr -d '\r:' | tr '[:upper:]' '[:lower:]'
  )
  if [[ "${#fingerprints[@]}" -eq 0 ]]; then
    echo 'Cannot read signing certificate SHA-256 from apksigner output' >&2
    return 1
  fi
  for actual in "${fingerprints[@]}"; do
    if [[ ! "$actual" =~ ^[0-9a-f]{64}$ || "$actual" != "$expected" ]]; then
      printf 'Signing certificate mismatch\nExpected: %s\nActual: %s\n' \
        "$expected" "$actual" >&2
      return 1
    fi
  done
}
