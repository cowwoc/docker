#!/usr/bin/env bash
set -e

if [ -d buildkit-repository ]; then
  echo "Pulling changes..."

  LOCAL_HEAD=$(git -C buildkit-repository rev-parse HEAD)
  REMOTE_HEAD=$(git -C buildkit-repository ls-remote --heads origin master | awk '{ print $1 }')
  if [ "${LOCAL_HEAD}" = "${REMOTE_HEAD}" ]; then
    echo "Repository is up to date; skipping regeneration of protobuf files."
    exit 0
  fi
  git -C buildkit-repository fetch --depth=1 origin
  git -C buildkit-repository reset --hard origin/master
else
  git clone --config core.autocrlf=input --config core.eol=lf https://github.com/moby/buildkit.git buildkit-repository
fi

# Based on https://github.com/moby/buildkit/blob/f2ce016376e5e61ff87d95cd20ccdf108fa77230/hack/dockerfiles/generated-files.Dockerfile#L78
mkdir -p buildkit-protobuf/include/github.com/moby/buildkit
rsync -av --no-compress --ignore-existing --prune-empty-dirs \
  --exclude='vendor/***' \
  --include='*/' \
  --include='*.proto' \
  --exclude='*' \
  buildkit-repository/ buildkit-protobuf/include/github.com/moby/buildkit/

# Speed up the build by only extracting the files we need
grep -q "FROM scratch AS minimal-protobuf" buildkit-repository/hack/dockerfiles/generated-files.Dockerfile || \
cat >> buildkit-repository/hack/dockerfiles/generated-files.Dockerfile <<'EOF'

FROM scratch AS minimal-protobuf
COPY --from=protobuf /include/github.com/tonistiigi/fsutil/types/stat.proto /include/github.com/tonistiigi/fsutil/types/stat.proto
COPY --from=protobuf /include/github.com/tonistiigi/fsutil/types/wire.proto /include/github.com/tonistiigi/fsutil/types/wire.proto
COPY --from=protobuf /include/github.com/planetscale/vtprotobuf/vtproto/ext.proto /include/github.com/planetscale/vtprotobuf/vtproto/ext.proto
EOF

## shellcheck disable=SC2154
docker buildx build --file buildkit-repository/hack/dockerfiles/generated-files.Dockerfile \
  --target minimal-protobuf \
  --output "${proto.relative.directory}" \
  buildkit-repository