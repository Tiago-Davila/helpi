#!/usr/bin/env bash
# Baja los SDK de Robolectric: su descarga propia falla si Maven Central responde HTTP 429.
set -euo pipefail

repository="$HOME/.m2/repository"
# El espejo de Google cubre el 429: Central puede pedir una espera más larga que el job.
sources=(
  'https://repo1.maven.org/maven2'
  'https://maven-central.storage-download.googleapis.com/maven2'
)

# Prueba cada fuente en orden hasta que una entregue el archivo.
download() {
  local path="$1" target="$2" source
  for source in "${sources[@]}"; do
    if curl --fail --silent --show-error --location --retry 3 --retry-max-time 60 \
      --output "$target" "$source/$path"; then
      return 0
    fi
    echo "No disponible en $source: $path" >&2
  done
  return 1
}

# Deja el archivo en el repositorio local y exige el SHA-512 fijado, también si vino del caché.
fetch() {
  local path="$1" sha512="$2" target="$repository/$1"
  if ! echo "$sha512  $target" | sha512sum --check --status 2>/dev/null; then
    mkdir -p "$(dirname "$target")"
    download "$path" "$target"
    echo "$sha512  $target" | sha512sum --check --status
  fi
  printf '%s' "$sha512" > "$target.sha512"
  echo "$path"
}

# Versiones que pide Robolectric 4.14.1 para los tests con sdk 28 y sdk 35.
artifact() {
  local base="org/robolectric/android-all-instrumented/$1/android-all-instrumented-$1"
  fetch "$base.pom" "$2"
  fetch "$base.jar" "$3"
}

artifact 9-robolectric-4913185-2-i7 \
  0c0048dd6cb9dc1fc81038ae9ef77383c842941e17ca5e961d0f9d3f0dda91c2ce31ca8ca11634e613927dbcc75259e39ed5e39ad5406a0ed4316576e0dc7a77 \
  24687b9ea919eb20f5e27e47274f2aa711e98eb332d4769d34044a3b4952a71393831d0a3f61ba726dc8fe220673a76138c11a0716f304999e5b029146fe88eb
artifact 15-robolectric-12650502-i7 \
  2fd37e590a1cc4e661975041a53a307f27e01aa56a4f391325d6644a435d46ce194f1ee9f8acaf8dfd164f8a9fe9b86fe2a0045b515964a3e8d7f02042732c3c \
  87bdd0341838f5f630e1ff412ab29cb2f06e0f8cb7751f14679d11b4ee6084873001f8f6a8581ae6e139fa7c9a654b632412827430f984ebecc3c2588674f7e7
