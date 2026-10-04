#!/data/data/com.termux/files/usr/bin/bash
# Run inside the Spherical folder in Termux:  bash publish.sh [v1.0.0]
set -e
pkg update -y && pkg install -y git gh
[ -d .git ] || git init -b main
gh auth status >/dev/null 2>&1 || gh auth login
git add -A
git commit -m "Spherical" >/dev/null 2>&1 || true
git remote get-url origin >/dev/null 2>&1 || gh repo create spherical --public --source=. --remote=origin
git push -u origin main
TAG=${1:-v1.0.0}
git tag -f "$TAG" && git push -f origin "$TAG"
echo "Pushed. GitHub Actions builds the APK; find it under Releases (tag $TAG) in a few minutes."
