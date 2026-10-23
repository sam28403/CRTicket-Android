#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/signing-certificates.sh"

expected=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
other=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789

verify_signing_certificates "Signer #1 certificate SHA-256 digest: $expected" "$expected"
verify_signing_certificates "V2 Signer: certificate SHA-256 digest: $expected" "$expected"
verify_signing_certificates "V3 Signer: certificate SHA-256 digest: $expected" "$expected"
verify_signing_certificates "$(printf 'V2 Signer: certificate SHA-256 digest: %s\r\n' "${expected^^}")" "$expected"
verify_signing_certificates "$(printf 'V2 Signer: certificate SHA-256 digest: %s\nV3 Signer: certificate SHA-256 digest: %s' "$expected" "$expected")" "$expected"

must_reject() {
  if verify_signing_certificates "$1" "$expected" >/dev/null 2>&1; then
    echo 'Expected certificate verification to fail' >&2
    exit 1
  fi
}
must_reject ''
must_reject "V2 Signer: public key SHA-256 digest: $expected"
must_reject 'V2 Signer: certificate SHA-256 digest: invalid'
must_reject "V2 Signer: certificate SHA-256 digest: $other"
must_reject "$(printf 'Signer #1 certificate SHA-256 digest: %s\nSigner #2 certificate SHA-256 digest: %s' "$expected" "$other")"
echo 'Signing certificate tests passed (10 cases)'
