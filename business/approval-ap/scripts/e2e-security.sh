#!/usr/bin/env bash
#
# 一条命令跑完「开放 API 安全（G9） + 业务域隔离（G3）」的 17 条 E2E。
#
# 为什么需要它：verify-security.mjs 覆盖的是「真服务 + 真签名」的全链路，单测与 dev 放行态
# 都看不见（前者不装配 Security 链，后者不认证）。而它此前一直靠「手工起服务、手工跑脚本」
# —— 于是「认证一开服务间面全断」那次，是隔了一轮才被发现的。
#
# 用法：
#   ./scripts/e2e-security.sh            # 认证态（默认，与生产同形态）
#   ./scripts/e2e-security.sh dev        # cim.auth.enabled=false（回归 dev 通道未被破坏）
#   ./scripts/e2e-security.sh both       # 两种形态各跑一遍
#   PORT=18085 ./scripts/e2e-security.sh # 换端口
#
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
PORT="${PORT:-8085}"
MODE="${1:-auth}"
JAR="$ROOT/server/target/cim-approval-ap-server.jar"
LOG="$ROOT/server/target/e2e-security.log"

app_pid=""

cleanup() {
  if [ -n "$app_pid" ] && kill -0 "$app_pid" 2>/dev/null; then
    kill "$app_pid" 2>/dev/null
    for _ in $(seq 1 20); do
      kill -0 "$app_pid" 2>/dev/null || return 0
      sleep 0.5
    done
    kill -9 "$app_pid" 2>/dev/null
  fi
}
trap cleanup EXIT INT TERM

# 端口被占就直接退出：连别人的服务跑测试，结论是假的。
if command -v netstat >/dev/null 2>&1 \
   && netstat -ano 2>/dev/null | grep -q "LISTENING.*:$PORT "; then
  echo "✗ 端口 $PORT 已被占用，先停掉那个进程（或 PORT=xxxx 换端口）。"
  exit 1
fi

# jar 缺失或源码比它新就重打包。-DskipTests：本脚本验的是运行时链路，不重复跑单测。
need_build=0
[ -f "$JAR" ] || need_build=1
if [ "$need_build" -eq 0 ] && [ -n "$(find "$ROOT/server/src" -type f -newer "$JAR" 2>/dev/null | head -1)" ]; then
  need_build=1
fi
if [ "$need_build" -eq 1 ]; then
  echo "▶ 构建 jar（源码有变更）…"
  ( cd "$ROOT/server" && mvn -o -q package -DskipTests ) || { echo "✗ 构建失败"; exit 1; }
fi

run_mode() {
  local mode="$1" extra=() rc=0
  if [ "$mode" = "dev" ]; then
    extra=(--cim.auth.enabled=false)
  fi

  echo
  echo "══════════════════════════════════════════"
  echo " 形态：$mode   （端口 $PORT）"
  echo "══════════════════════════════════════════"

  ( cd "$ROOT/server" && exec java -jar "$JAR" \
      --server.port="$PORT" \
      --cim.notify.cluster.enabled=false \
      "${extra[@]}" ) > "$LOG" 2>&1 &
  app_pid=$!

  local started=0
  for _ in $(seq 1 90); do
    if grep -q "Started ApprovalApApplication" "$LOG" 2>/dev/null; then started=1; break; fi
    if grep -q "APPLICATION FAILED TO START" "$LOG" 2>/dev/null; then break; fi
    kill -0 "$app_pid" 2>/dev/null || break
    sleep 2
  done
  if [ "$started" -ne 1 ]; then
    echo "✗ 启动失败，日志尾部："
    tail -40 "$LOG"
    cleanup
    return 1
  fi
  echo "  ✓ 已启动"

  ( cd "$ROOT/web" && node scripts/verify-security.mjs )
  rc=$?

  cleanup
  app_pid=""
  return $rc
}

overall=0
case "$MODE" in
  auth|dev) run_mode "$MODE" || overall=1 ;;
  both)
    run_mode auth || overall=1
    run_mode dev  || overall=1
    ;;
  *) echo "用法：$0 [auth|dev|both]"; exit 2 ;;
esac

echo
if [ "$overall" -eq 0 ]; then
  echo "✓ E2E 全部通过（$MODE）"
else
  echo "✗ 有失败用例，见上方输出与服务日志 $LOG"
fi
exit "$overall"
