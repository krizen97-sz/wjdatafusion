#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_env_file
echo "停止 RYNEW ONLYOFFICE 测试实例；数据卷、镜像、JWT 配置均保留。"
compose stop
compose ps
echo "ROLLBACK_STOPPED_DATA_PRESERVED"
