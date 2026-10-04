#!/usr/bin/env bash
set -euo pipefail

main() {
    cd "$(dirname "${BASH_SOURCE[0]}")/.."
    local version="1.0.0-rc4" tag="v1.0.0-rc4"
    local manifest="release-notes/rc4-files.sha256"
    local repo="stasolejnik/elektron"
    [[ "${1:-}" == "" || "${1:-}" == "--check" ]] || { echo "Użycie: bash scripts/publish-rc4.sh [--check]"; return 1; }
    [[ -f "$manifest" ]] || { echo "Brakuje listy plików RC4."; return 1; }
    sha256sum --check --quiet "$manifest" || { echo "Pliki zmieniły się od przygotowania RC4. Przerwano publikację."; return 1; }
    [[ "$(git branch --show-current)" == "main" ]] || { echo "Ten skrypt publikuje z gałęzi main."; return 1; }
    case "$(git remote get-url origin)" in
        https://github.com/stasolejnik/elektron|https://github.com/stasolejnik/elektron.git|git@github.com:stasolejnik/elektron.git) ;;
        *) echo "Repozytorium origin nie jest repozytorium eLektron."; return 1 ;;
    esac
    local checksum file known item
    local files=("$manifest")
    while read -r checksum file; do files+=("$file"); done < "$manifest"
    while IFS= read -r file; do
        known=false
        for item in "${files[@]}"; do [[ "$file" != "$item" ]] || known=true; done
        if [[ "$known" != true ]]; then
            echo "Poza RC4 zmieniono także: $file. Przerwano, aby nie dołączyć obcych zmian."
            return 1
        fi
    done < <(git diff --name-only HEAD)
    if [[ "${1:-}" == "--check" ]]; then
        echo "Pliki RC4 i repozytorium są zgodne. Niczego nie opublikowano."
        return
    fi
    command -v gh >/dev/null || { echo "Brakuje programu gh. Zainstaluj go: sudo pacman -S github-cli"; return 1; }
    [[ -f app/google-services.json ]] || { echo "Brakuje app/google-services.json."; return 1; }
    [[ -f keystore.properties ]] || { echo "Brakuje keystore.properties z konfiguracją podpisu."; return 1; }
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
    [[ -x "$JAVA_HOME/bin/java" ]] || { echo "Brakuje Javy 17."; return 1; }
    export PATH="$JAVA_HOME/bin:$PATH"
    gh auth status --hostname github.com >/dev/null 2>&1 || gh auth login --hostname github.com --web --git-protocol https
    git fetch origin main --tags
    git merge-base --is-ancestor origin/main HEAD || {
        echo "Na GitHubie są nowsze zmiany. Przerwano bez nadpisywania historii."; return 1;
    }
    echo "Sprawdzam testy i buduję podpisane RC4..."
    # Osobny przebieg FOSS sprawdza też pracę bez wtyczki Google Services.
    ./gradlew :app:testFossDebugUnitTest :app:assembleFossRelease
    ./gradlew :app:testGmsDebugUnitTest :app:assembleGmsRelease
    local built="app/build/outputs/apk/gms/release/app-gms-release.apk"
    [[ -f "$built" ]] || { echo "Nie znaleziono podpisanego APK."; return 1; }
    local sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
    local signer="" candidate aapt badging
    for candidate in "$sdk"/build-tools/*/apksigner; do
        [[ ! -x "$candidate" ]] || signer="$candidate"
    done
    [[ -n "$signer" ]] || { echo "Nie znaleziono apksigner w Android SDK."; return 1; }
    "$signer" verify "$built"
    aapt="$(dirname "$signer")/aapt"
    badging="$("$aapt" dump badging "$built")"
    [[ "$badging" == *"name='pl.zse.bydgoszcz.elektron'"* && "$badging" == *"versionCode='23'"* && "$badging" == *"versionName='$version'"* ]] || {
        echo "APK ma niewłaściwy numer wersji lub pakiet."; return 1;
    }
    sha256sum --check --quiet "$manifest"
    local output="$HOME/eLektron-apk"
    local apk="$output/eLektron-$version.apk"
    mkdir -p "$output"
    cp "$built" "$apk"
    cp "release-notes/$version.md" "$output/WYDANIE_$version.md"
    git add -- "${files[@]}"
    if ! git diff --cached --quiet; then
        git commit -m "Release $version: poprawki sieci, baterii i stabilności"
    fi
    if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
        [[ "$(git rev-parse "$tag^{commit}")" == "$(git rev-parse HEAD)" ]] || {
            echo "Tag $tag wskazuje inne zmiany. Przerwano bez nadpisywania tagu."; return 1;
        }
    else
        git tag -a "$tag" -m "eLektron $version"
    fi
    git push --atomic origin HEAD:refs/heads/main "refs/tags/$tag"
    if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then
        echo "Wydanie $tag już istnieje. Nie nadpisuję opublikowanych plików."
        gh release view "$tag" --repo "$repo" --json url --jq .url
        return
    fi
    gh release create "$tag" "$apk" --repo "$repo" --verify-tag --prerelease --latest=false \
        --title "eLektron $version" --notes-file "release-notes/$version.md"
    echo "Gotowe. Plik do instalacji: $apk"
}

main "$@"
