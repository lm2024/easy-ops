#!/usr/bin/env bash
# ============================================================================
# 磁盘防爆盘脚本（prevent-disk-full.sh）
# ----------------------------------------------------------------------------
# 三层防线里的"自动压缩 + 水位告警"落地：
#   1) check   —— 只检查磁盘水位，超阈值记告警日志（可接通知），安全只读
#   2) compact —— 磁盘水位超阈值才执行"停机压缩"，未超则跳过（避免无谓停机）
#
# 用法（两个 cron 各司其职）：
#   # 每小时：水位检查告警
#   5 * * * *  /opt/easyops/prevent-disk-full.sh check >> /opt/easyops/logs/disk-monitor.log 2>&1
#   # 每周日凌晨：超阈值才停机压缩（需 server 已停止或本脚本代为停止）
#   30 3 * * 0 /opt/easyops/prevent-disk-full.sh compact >> /opt/easyops/logs/disk-compact.log 2>&1
# ============================================================================
set -uo pipefail

# ---------------- 配置（按你的环境修改） ----------------
DATA_DIR="${DATA_DIR:-/opt/easyops/data}"            # ops.mv.db 所在目录
RESCUE_SCRIPT="${RESCUE_SCRIPT:-/opt/easyops/rescue-h2.sh}"  # 上一版的救援脚本路径
CHECK_THRESHOLD="${CHECK_THRESHOLD:-75}"             # check 模式告警阈值（%）
COMPACT_THRESHOLD="${COMPACT_THRESHOLD:-85}"         # compact 模式触发压缩阈值（%）
STOP_CMD="${STOP_CMD:-/opt/easyops/stop.sh}"         # server 停止命令（可留空=手动停）
START_CMD="${START_CMD:-/opt/easyops/start.sh}"      # server 启动命令
LOG_FILE="${LOG_FILE:-/opt/easyops/logs/prevent-disk-full.log}"

# ---------------- 工具函数 ----------------
log() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG_FILE"; }

disk_usage_pct() {
  df -k "$DATA_DIR" | awk 'NR==2{print int($5)}' 2>/dev/null
}

notify() {
  # 告警通知钩子：默认只写日志。可在这里接钉钉/企业微信 webhook：
  #   curl -s -X POST 'https://oapi.dingtalk.com/robot/send?access_token=XXX' \
  #        -H 'Content-Type: application/json' \
  #        -d "{\"msgtype\":\"text\",\"text\":{\"content\":\"$1\"}}"
  log "[告警] $1"
}

# ---------------- 模式 1：水位检查 ----------------
check_mode() {
  local usage
  usage=$(disk_usage_pct)
  [ -z "$usage" ] && { log "无法获取磁盘使用率，检查 DATA_DIR=$DATA_DIR"; exit 1; }
  log "磁盘水位检查: ${usage}% (阈值 ${CHECK_THRESHOLD}%)"
  if [ "$usage" -ge "$CHECK_THRESHOLD" ]; then
    notify "EasyOps 服务器磁盘使用率已达 ${usage}%（阈值 ${CHECK_THRESHOLD}%）。数据目录: ${DATA_DIR}。请安排执行 compact 或清理。"
  fi
}

# ---------------- 模式 2：超阈值停机压缩 ----------------
compact_mode() {
  local usage
  usage=$(disk_usage_pct)
  [ -z "$usage" ] && { log "无法获取磁盘使用率，检查 DATA_DIR=$DATA_DIR"; exit 1; }
  log "压缩检查: 磁盘 ${usage}% (触发阈值 ${COMPACT_THRESHOLD}%)"
  if [ "$usage" -lt "$COMPACT_THRESHOLD" ]; then
    log "磁盘水位正常，跳过压缩（无需停机）"
    exit 0
  fi
  [ -f "$RESCUE_SCRIPT" ] || { log "找不到救援脚本: $RESCUE_SCRIPT"; exit 1; }

  log "磁盘 ${usage}% 超阈值，执行停机压缩…"
  # 1) 停 server（STOP_CMD 留空表示你自己已手动停）
  if [ -n "$STOP_CMD" ] && [ -x "$STOP_CMD" ]; then
    "$STOP_CMD" || log "stop.sh 返回非零，继续尝试压缩"
  fi
  # 2) 压缩（rescue-h2.sh 内部会处理锁文件、磁盘紧张时先清日志）
  bash "$RESCUE_SCRIPT" "$DATA_DIR" --yes || { log "压缩失败，请人工介入"; exit 1; }
  # 3) 启动 server
  if [ -n "$START_CMD" ] && [ -x "$START_CMD" ]; then
    "$START_CMD"
  fi
  log "压缩完成，server 已重启（若配置了启动命令）"
}

# ---------------- 入口 ----------------
case "${1:-check}" in
  check)   check_mode ;;
  compact) compact_mode ;;
  *) echo "用法: $0 [check|compact]"; exit 1 ;;
esac
