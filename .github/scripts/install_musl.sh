set -e
mkdir -p ~/staticdeps/

ZLIB_VERSION="1.2.13"
MUSL_VERSION="9fa28ece75d8a2191de7c5bb53bed224c5947417" # v1.2.6

# install zlib
if [[ ! -f ~/staticdeps/include/zlib.h ]]; then
  # Download zlib tarball and signature
  curl -Lf "https://github.com/madler/zlib/releases/download/v${ZLIB_VERSION}/zlib-${ZLIB_VERSION}.tar.gz" -o /tmp/zlib.tar.gz
  curl -Lf "https://github.com/madler/zlib/releases/download/v${ZLIB_VERSION}/zlib-${ZLIB_VERSION}.tar.gz.asc" -o /tmp/zlib.tar.gz.asc

  # Import zlib GPG key
  gpg --batch --keyserver keyserver.ubuntu.com --recv-keys 5ED46A6721D365587791E2AA783FCD8E58BCAFBA

  # Verify GPG signature
  echo "Verifying zlib GPG signature..."
  gpg --verify /tmp/zlib.tar.gz.asc /tmp/zlib.tar.gz

  mkdir -p "/tmp/dep_zlib-${ZLIB_VERSION}"
  cd "/tmp/dep_zlib-${ZLIB_VERSION}"
  # shellcheck disable=SC2002
  cat /tmp/zlib.tar.gz | tar --strip-components=1 -xzC .

  echo "zlib-${ZLIB_VERSION}: configure..." 
  ./configure --static --prefix="$HOME"/staticdeps > /dev/null

  echo "zlib-${ZLIB_VERSION}: make..." 
  make -s -j4

  echo "zlib-${ZLIB_VERSION}: make install..." 
  make -s install

  rm -rf /tmp/dep_zlib-${ZLIB_VERSION}
fi

declare -a MUSL_REPOS=(
  https://git.musl-libc.org/git/musl
  https://gogs.librecmc.org/OWEALS/musl.git
)

# install musl
if [[ ! -f ~/staticdeps/bin/x86_64-linux-musl-gcc ]]; then
  # Download musl git repo
  cd /tmp
  for repo in "${MUSL_REPOS[@]}"; do
    if git clone --depth 1 --revision "$MUSL_VERSION" "$repo" /tmp/musl; then
      break
    else
      rm -rf /tmp/musl
    fi
  done
  cd /tmp/musl
  
  for patch in $GITHUB_WORKSPACE/.github/scripts/musl-*.patch; do
    echo "musl-${MUSL_VERSION}: applying $patch..."
    cat "$patch" | git apply
  done
  
  echo "musl-${MUSL_VERSION}: configure..." 
  ./configure --disable-shared --prefix="$HOME"/staticdeps > /dev/null

  echo "musl-${MUSL_VERSION}: make..." 
  make -s -j4

  echo "musl-${MUSL_VERSION}: make install..." 
  make -s install

  rm -rf "/tmp/dep_musl-${MUSL_VERSION}"

  # native-image expects to find an executable at this path.
  ln -s ~/staticdeps/bin/musl-gcc ~/staticdeps/bin/x86_64-linux-musl-gcc
fi

echo "${HOME}/staticdeps/bin" >> "$GITHUB_PATH"
