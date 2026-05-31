#!/usr/bin/env bash
set -euo pipefail

VERSION="${ROSTRUM_VERSION:-1.0.0}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="${ROOT_DIR}/dist"

targets=(
  "linux/amd64"
  "linux/arm64"
  "linux/arm"
  "darwin/amd64"
  "darwin/arm64"
)

mkdir -p "${DIST_DIR}"

for target in "${targets[@]}"; do
  os="${target%/*}"
  arch="${target#*/}"
  out_dir="${DIST_DIR}/rostrum-server-${os}-${arch}"
  archive="${DIST_DIR}/rostrum-server-${os}-${arch}.tar.gz"

  rm -rf "${out_dir}"
  mkdir -p "${out_dir}"

  echo "Building ${os}/${arch}"
  goarm=""
  if [[ "${os}/${arch}" == "linux/arm" ]]; then
    goarm="7"
  fi

  GOOS="${os}" GOARCH="${arch}" GOARM="${goarm}" CGO_ENABLED=0 go build \
    -trimpath \
    -ldflags="-s -w" \
    -o "${out_dir}/rostrum-server" \
    "${ROOT_DIR}"

  cp "${ROOT_DIR}/install.sh" "${out_dir}/install.sh"
  cp "${ROOT_DIR}/DESIGN.md" "${out_dir}/DESIGN.md"
  printf '%s\n' "${VERSION}" > "${out_dir}/VERSION"

  tar -C "${out_dir}" -czf "${archive}" .
  rm -rf "${out_dir}"
done

(
  cd "${DIST_DIR}"
  sha256sum rostrum-server-*.tar.gz > SHA256SUMS
)

echo "Release artifacts written to ${DIST_DIR}"
