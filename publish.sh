#!/data/data/com.termux/files/usr/bin/bash
# Run inside the Spherical folder in Termux:  bash publish.sh v1.4.0
# Every release needs a NEW tag higher than the last one (v1.4.0 -> v1.4.1 ...).
set -e
TAG=${1:?"give a version tag, e.g.  bash publish.sh v1.4.0"}
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Tag must look like v1.4.0"; exit 1; }
[ -f spherical-release.p12 ] && [ -f signing.txt ] || { echo "spherical-release.p12 / signing.txt missing (they come in the zip). Keep them!"; exit 1; }
pkg update -y && pkg install -y git gh
gh auth status >/dev/null 2>&1 || gh auth login
gh auth setup-git
[ -d .git ] || git init -b main
git config user.name  >/dev/null 2>&1 || git config user.name  "Spherical"
git config user.email >/dev/null 2>&1 || git config user.email "spherical@users.noreply.github.com"
git add -A
git commit -m "Spherical $TAG" >/dev/null 2>&1 || true
if ! git remote get-url origin >/dev/null 2>&1; then
  USER=$(gh api user --jq .login)
  if gh repo view "$USER/spherical" >/dev/null 2>&1; then
    git remote add origin "https://github.com/$USER/spherical.git"   # repo already exists: reuse it
  else
    gh repo create spherical --public --source=. --remote=origin
  fi
fi
# the signing key goes to GitHub as a private secret (never into the repo)
base64 -w0 spherical-release.p12 | gh secret set KEYSTORE_B64
gh secret set KS_PASS < signing.txt
git push -u -f origin main
git tag -f "$TAG" && git push -f origin "$TAG"
echo "Pushed $TAG. In ~5 min the APK appears under Releases, and installed copies offer the update in-app (menu > UPDATE)."
