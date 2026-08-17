#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  tools/codex-worktree.sh doctor
  tools/codex-worktree.sh list
  tools/codex-worktree.sh verify <module> [--fetch]
  tools/codex-worktree.sh create <module> <task-slug>

Modules:
  auto-inspection | site-fusion | document-management | ipam
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || die "当前目录不在 Git 仓库中"

validate_module() {
  case "${1:-}" in
    auto-inspection|site-fusion|document-management|ipam) ;;
    *) die "不支持的模块：${1:-<empty>}" ;;
  esac
}

is_generated_path() {
  case "$1" in
    .playwright-cli/*|RuoYi-Vue3-master/.playwright-cli/*|*/graphify-out/*|graphify-out/*|*/output/*|output/*|*/outputs/*|outputs/*|deliverables/*|*/target/*|*/dist/*|*/node_modules/*|tools/__pycache__/*) return 0 ;;
    *) return 1 ;;
  esac
}

is_shared_path() {
  case "$1" in
    AGENTS.md|README.md|docs/CODEX_WORKTREES.md|tools/codex-worktree.sh|\
    RuoYi-Vue3-master/package.json|RuoYi-Vue3-master/src/router/index.js|\
    RuoYi-Vue3-master/src/views/support/version/releaseNotes.js|\
    WDF100.0/pom.xml|WDF100.0/*/pom.xml|WDF100.0/sql/support_v1.sql|\
    WDF100.0/sql/support_deploy_all.sql|*/application.yml|*/application-*.yml|\
    *GlobalExceptionHandler.java|*RouterVo.java|*SysMenuServiceImpl.java) return 0 ;;
    *) return 1 ;;
  esac
}

is_module_path() {
  module=$1
  path=$2
  case "$module:$path" in
    auto-inspection:*autoInspection*|auto-inspection:*AutoInspection*|auto-inspection:*auto_inspection*|auto-inspection:*auto-version*|auto-inspection:*auto_version*) return 0 ;;
    site-fusion:*support/site/*|site-fusion:*SupportSite*|site-fusion:*SupportServer*|site-fusion:*SupportHardware*|site-fusion:*SupportEquipment*|site-fusion:*equipmentLocation*|site-fusion:*EquipmentLocation*|site-fusion:*hardware_asset*|site-fusion:*server_credential*) return 0 ;;
    document-management:*document*|document-management:*Document*|document-management:*doc_*) return 0 ;;
    ipam:*ipam*|ipam:*Ipam*) return 0 ;;
    *) return 1 ;;
  esac
}

show_doctor() {
  branch=$(git branch --show-current)
  head=$(git rev-parse --short HEAD)
  origin_head=$(git rev-parse --short origin/main 2>/dev/null || printf 'missing')
  dirty_count=$(git status --porcelain=v1 -uall | wc -l | tr -d ' ')
  printf 'repository: %s\n' "$repo_root"
  printf 'branch:     %s\n' "${branch:-DETACHED}"
  printf 'HEAD:       %s\n' "$head"
  printf 'origin/main:%s\n' "$origin_head"
  printf 'dirty:      %s paths\n' "$dirty_count"
  git worktree list
}

verify_module() {
  module=$1
  fetch_mode=${2:-}
  validate_module "$module"

  if [ "$fetch_mode" = "--fetch" ]; then
    git fetch origin main
  elif [ -n "$fetch_mode" ]; then
    die "未知参数：$fetch_mode"
  fi

  git rev-parse --verify origin/main >/dev/null 2>&1 || die "缺少 origin/main，请先执行 git fetch origin main"
  git merge-base --is-ancestor origin/main HEAD || die "当前 HEAD 未包含最新 origin/main，禁止继续修改"

  branch=$(git branch --show-current)
  [ "$branch" != "main" ] || die "禁止在 main 分支直接开发"
  [ -n "$branch" ] || die "当前为 detached HEAD，请先使用 Create branch here 或创建功能分支"

  cross_count=0
  shared_count=0
  generated_count=0
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    path=${line:3}
    case "$path" in
      *" -> "*) path=${path##* -> } ;;
    esac
    if is_generated_path "$path"; then
      generated_count=$((generated_count + 1))
    elif is_shared_path "$path"; then
      shared_count=$((shared_count + 1))
    elif ! is_module_path "$module" "$path"; then
      printf 'CROSS-MODULE: %s\n' "$path" >&2
      cross_count=$((cross_count + 1))
    fi
  done < <(git status --porcelain=v1 -uall)

  printf 'module:     %s\n' "$module"
  printf 'branch:     %s\n' "$branch"
  printf 'baseline:   %s contains origin/main %s\n' "$(git rev-parse --short HEAD)" "$(git rev-parse --short origin/main)"
  printf 'shared:     %s paths require integration review\n' "$shared_count"
  printf 'generated:  %s paths are excluded from source commits\n' "$generated_count"

  [ "$cross_count" -eq 0 ] || die "发现 $cross_count 个跨模块修改，请改用独立 Worktree"
  printf 'OK: worktree baseline and module boundary verified\n'
}

create_worktree() {
  module=$1
  slug=${2:-}
  validate_module "$module"
  [ -n "$slug" ] || die "请提供 task-slug"

  safe_slug=$(printf '%s' "$slug" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9-]+/-/g; s/^-+|-+$//g')
  [ -n "$safe_slug" ] || die "task-slug 无有效字符"

  git fetch origin main
  worktree_root=${RYNEW_WORKTREE_ROOT:-"$(dirname "$repo_root")/rynew-worktrees"}
  target="$worktree_root/$module-$safe_slug"
  branch="codex/$module-$safe_slug"

  [ ! -e "$target" ] || die "Worktree 目录已存在：$target"
  git show-ref --verify --quiet "refs/heads/$branch" && die "本地分支已存在：$branch"
  git ls-remote --exit-code --heads origin "$branch" >/dev/null 2>&1 && die "远端分支已存在：$branch"

  mkdir -p "$worktree_root"
  git worktree add "$target" -b "$branch" origin/main

  printf 'created worktree: %s\n' "$target"
  printf 'created branch:   %s\n' "$branch"
  printf 'next: cd %q && tools/codex-worktree.sh verify %q\n' "$target" "$module"
}

command=${1:-}
case "$command" in
  doctor) show_doctor ;;
  list) git worktree list ;;
  verify)
    [ "$#" -ge 2 ] || { usage; exit 1; }
    verify_module "$2" "${3:-}"
    ;;
  create)
    [ "$#" -eq 3 ] || { usage; exit 1; }
    create_worktree "$2" "$3"
    ;;
  *) usage; exit 1 ;;
esac
