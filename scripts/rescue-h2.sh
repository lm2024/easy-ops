#!/usr/bin/env bash
# ============================================================================
# EasyOps H2 数据库膨胀救援脚本（rescue-h2.sh）
# ----------------------------------------------------------------------------
# 适用场景：ops.mv.db 膨胀到数 GB、磁盘被占满、server 无法启动/写入失败。
# 特性：全程在服务器本地执行，不需要下载/上传任何大文件。
#
# 原理：
#   H2 的 MVStore 是日志结构存储，DELETE 的数据只是标记为空闲页，
#   文件【不会自动缩小】。因此必须先删掉历史垃圾数据，再执行
#   SHUTDOWN COMPACT 把空闲页回收掉，文件才会真正变小。
#   本脚本只清理「历史监控/统计/日志」类累积数据，不碰任何
#   系统配置/元数据表（sys_user / project_info / node_info /
#   version_package / nginx_access_source / 白名单 / 告警规则 等）。
#
# 用法：
#   ./rescue-h2.sh <数据目录> [server.jar路径] [--yes] [--dry-run]
#   环境变量：
#     KEEP_DAYS=7        监控/统计类(Nginx统计/监控快照)保留天数
#     LOG_KEEP_DAYS=30   日志类(操作/文件/通知/告警/分发记录)保留天数
#
# 示例：
#   ./rescue-h2.sh /opt/easyops/data ./easyops-server.jar
#   ./rescue-h2.sh /opt/easyops/data --yes
#   KEEP_DAYS=3 LOG_KEEP_DAYS=7 ./rescue-h2.sh /opt/easyops/data --yes
#
# 前置条件：必须先停止 server（否则压缩会打断运行中的服务）。
# ============================================================================
set -uo pipefail

DATA_DIR="${1:-$PWD/data}"
SERVER_JAR="${2:-}"
KEEP_DAYS="${KEEP_DAYS:-7}"
LOG_KEEP_DAYS="${LOG_KEEP_DAYS:-30}"
AUTO_YES=0
DRY_RUN=0
for a in "$@"; do
  [ "$a" = "--yes" ] && AUTO_YES=1
  [ "$a" = "--dry-run" ] && DRY_RUN=1
done

DB_FILE="$DATA_DIR/ops.mv.db"
# 注意：H2 URL 里不能带 .mv.db 后缀（否则会创建 xxx.mv.db.mv.db 新库）
URL="jdbc:h2:file:${DB_FILE%.mv.db};MODE=MySQL"
H2_JAR="${H2_JAR:-}"

# ---------------- 工具函数 ----------------
log()  { printf '[INFO] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*"; }
err()  { printf '[ERROR] %s\n' "$*"; exit 1; }

confirm() {
  [ "$AUTO_YES" = 1 ] && return 0
  local ans
  read -r -p "$* [y/N] " ans
  case "$ans" in y|Y|yes|YES) return 0;; *) return 1;; esac
}

run_sql() {
  java -cp "$H2_JAR" org.h2.tools.Shell -url "$URL" -user sa -password "" -sql "$1" 2>&1
}

# 取 SQL 结果第二行（去掉表头），返回纯数字
sql_val() {
  run_sql "$1" | sed -n '2p' | tr -d '[:space:]'
}

extract_h2_from_fat_jar() {
  local j="$1"
  local inner
  inner=$(unzip -l "$j" 2>/dev/null | grep -oE 'BOOT-INF/lib/h2-[0-9.]+\.jar' | head -1)
  [ -z "$inner" ] && return 1
  unzip -p "$j" "$inner" > /tmp/h2-easyops.jar 2>/dev/null || return 1
  H2_JAR=/tmp/h2-easyops.jar
  log "已从 $j 提取 h2 驱动: $inner"
  return 0
}

# ---------------- 0. 前置检查 ----------------
echo "=============================================================="
echo " EasyOps H2 数据库救援  (dry-run=${DRY_RUN}  keep=${KEEP_DAYS}d/log=${LOG_KEEP_DAYS}d)"
echo "=============================================================="

[ -f "$DB_FILE" ] || err "找不到数据库文件: $DB_FILE"
command -v java  >/dev/null || err "需要 java（java 8+ 均可）"
command -v unzip >/dev/null || err "需要 unzip"

AVAIL_KB=$(df -k "$(dirname "$DB_FILE")" | awk 'NR==2{print $4}')
SIZE_KB=$(du -k "$DB_FILE" | awk '{print $1}')
log "数据库文件: $DB_FILE  ($((SIZE_KB/1024)) MB)"
log "所在磁盘可用: $((AVAIL_KB/1024)) MB"

# 磁盘紧张时先安全清理日志（.log / .trace.db，绝不碰数据库与版本包）
if [ "$AVAIL_KB" -lt 2097152 ]; then
  warn "磁盘可用空间不足 2GB，先自动清理日志类文件腾空间…"
  find "$DATA_DIR" -maxdepth 3 -type f \( -name "*.log" -o -name "*.trace.db" \) -delete 2>/dev/null
  find "$(dirname "$DATA_DIR")" -maxdepth 2 -type f -name "*.log" -delete 2>/dev/null
  AVAIL_KB=$(df -k "$(dirname "$DB_FILE")" | awk 'NR==2{print $4}')
  log "清理后可用: $((AVAIL_KB/1024)) MB"
  [ "$AVAIL_KB" -lt 524288 ] && warn "磁盘空间仍非常紧张，若后续 DELETE/COMPACT 失败请再手动清理 /tmp、容器日志等"
fi

# ---------------- 1. 定位 h2 驱动 ----------------
if [ -z "$H2_JAR" ]; then
  if [ -n "$SERVER_JAR" ] && [ -f "$SERVER_JAR" ]; then
    extract_h2_from_fat_jar "$SERVER_JAR" || err "无法从指定 jar 提取 h2，请确认是 server 的 fat jar"
  else
    for cand in "$DATA_DIR/../server.jar" "$DATA_DIR/../easyops-server.jar" \
                "$PWD/server.jar" "$PWD/easyops-server.jar" "$PWD/../server.jar"; do
      if [ -f "$cand" ] && extract_h2_from_fat_jar "$cand"; then break; fi
    done
    if [ -z "$H2_JAR" ]; then
      found=$(find "$(dirname "$DATA_DIR")" -maxdepth 3 -name "*.jar" -size +30M 2>/dev/null | head -1)
      [ -n "$found" ] && extract_h2_from_fat_jar "$found"
    fi
  fi
fi
[ -f "$H2_JAR" ] || err "未找到 h2 驱动 jar。请用第 2 个参数指定 server fat jar 路径（如 ./rescue-h2.sh /opt/easyops/data /opt/easyops/server.jar）"

# ---------------- 2. 锁文件检查 ----------------
LOCK="$DATA_DIR/ops.mv.db.lock.db"
if [ -e "$LOCK" ]; then
  warn "检测到数据库锁文件 ($LOCK)，说明有进程正连接着数据库（server 未停止）。"
  confirm "确认 server 已停止？继续将接管数据库并删除锁文件" || err "已取消。请先停止 server 再重跑"
  rm -f "$LOCK"
  log "已移除锁文件"
fi

# ---------------- 3. 连接测试 ----------------
log "连接数据库测试…"
TBL_N=$(sql_val "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'")
[ -z "$TBL_N" ] || [ "$TBL_N" = "0" ] && err "连接失败或表数量为 0，请检查数据目录是否传对"
log "连接成功，共 $TBL_N 张业务表"

# ---------------- 4. 统计类表（监控/流量） ----------------
# 格式：表名:时间列   只删这些历史累积数据
STAT_TABLES="NGINX_MINUTE_STAT:BUCKET_TIME NGINX_IP_STAT:BUCKET_TIME
NGINX_UA_STAT:BUCKET_TIME NGINX_REFERER_STAT:BUCKET_TIME
NGINX_REQUEST_SAMPLE:TS MONITOR_SNAPSHOT:COLLECT_TIME"
LOG_TABLES="OPERATION_LOG:CREATE_TIME FILE_ACCESS_LOG:CREATE_TIME
NOTIFICATION_RECORD:CREATE_TIME ALARM_RECORD:CREATE_TIME
AI_DIAGNOSIS_RECORD:CREATE_TIME SELF_HEAL_EVENT:CREATE_TIME
CONFIG_DISTRIBUTE_RECORD:CREATE_TIME SCRIPT_DISTRIBUTE_RECORD:CREATE_TIME
GLOBAL_SCRIPT_DISTRIBUTE_RECORD:CREATE_TIME AGENT_UPGRADE_RECORD:CREATE_TIME
NODE_CONFIG_SNAPSHOT:UPDATE_TIME KB_RECENT_ACCESS:CREATE_TIME"

CUTOFF_STAT=$(( ($(date +%s) - KEEP_DAYS*86400) * 1000 ))
CUTOFF_LOG=$(( ($(date +%s) - LOG_KEEP_DAYS*86400) * 1000 ))

echo ""
echo "----- 行数诊断（清理前） -----"
diagnose() {
  local spec="$1"
  local tbl="${spec%%:*}" col="${spec##*:}"
  local has
  has=$(sql_val "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='PUBLIC' AND UPPER(TABLE_NAME)='$tbl' AND UPPER(COLUMN_NAME)='$col'")
  if [ "$has" != "1" ]; then
    warn "$tbl 无 $col 字段，跳过"
    return
  fi
  local cnt
  cnt=$(sql_val "SELECT COUNT(*) FROM $tbl")
  printf '  %-32s %12s 行\n' "$tbl" "${cnt:-?}"
}

for spec in $STAT_TABLES $LOG_TABLES; do diagnose "$spec"; done

echo ""
echo "----- 清理计划 -----"
echo "  统计/监控类(保留 ${KEEP_DAYS} 天): NGINX_*_STAT, NGINX_REQUEST_SAMPLE, MONITOR_SNAPSHOT"
echo "  日志类(保留 ${LOG_KEEP_DAYS} 天):  OPERATION_LOG, FILE_ACCESS_LOG, NOTIFICATION_RECORD 等"
echo "  不触碰: 用户/项目/节点/版本/配置/白名单/告警规则 等系统数据"

confirm "确认按上述计划清理历史数据？" || { warn "已取消，未做任何修改"; exit 0; }

# ---------------- 5. 执行清理 ----------------
clean_spec() {
  local spec="$1" cutoff="$2"
  local tbl="${spec%%:*}" col="${spec##*:}"
  local has
  has=$(sql_val "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='PUBLIC' AND UPPER(TABLE_NAME)='$tbl' AND UPPER(COLUMN_NAME)='$col'")
  [ "$has" != "1" ] && return
  local cnt
  cnt=$(sql_val "SELECT COUNT(*) FROM $tbl")
  [ -z "$cnt" ] || [ "$cnt" = "0" ] && { log "$tbl: 0 行，跳过"; return; }
  if [ "$DRY_RUN" = 1 ]; then
    log "[dry-run] 将执行: DELETE FROM $tbl WHERE $col < $cutoff"
    return
  fi
  log "清理 ${tbl}（当前 ${cnt} 行，删除 ${col} < ${cutoff} 的过期行，可能耗时较长，请耐心等待）…"
  run_sql "DELETE FROM ${tbl} WHERE ${col} < ${cutoff}" | tail -1
}

for spec in $STAT_TABLES; do clean_spec "$spec" "$CUTOFF_STAT"; done
for spec in $LOG_TABLES;  do clean_spec "$spec" "$CUTOFF_LOG"; done

echo ""
echo "----- 行数诊断（清理后） -----"
for spec in $STAT_TABLES $LOG_TABLES; do diagnose "$spec"; done

# ---------------- 6. 压缩（关键步骤） ----------------
if [ "$DRY_RUN" = 1 ]; then
  log "[dry-run] 跳过压缩"
else
  AVAIL_KB=$(df -k "$(dirname "$DB_FILE")" | awk 'NR==2{print $4}')
  log "压缩前可用磁盘: $((AVAIL_KB/1024)) MB"
  confirm "执行 SHUTDOWN COMPACT 原地压缩数据库？（压缩后文件才会真正缩小）" || {
    warn "跳过压缩。注意：不压缩文件不会变小，磁盘仍被占用；下次启动 server 前请务必重跑压缩"
    exit 0
  }
  log "正在压缩（SHUTDOWN COMPACT），请耐心等待…"
  COMPACT_OUT=$(run_sql "SHUTDOWN COMPACT" 2>&1)
  echo "$COMPACT_OUT" | tail -3
  if echo "$COMPACT_OUT" | grep -q "already closed"; then
    log "注：上面的 already closed 提示是 H2 Shell 收尾重复关闭的无害信息，压缩实际已完成"
  fi
fi

# ---------------- 7. 结果汇总 ----------------
echo ""
echo "=============================================================="
echo " 处理完成"
echo "=============================================================="
ls -lh "$DB_FILE"
df -h "$(dirname "$DB_FILE")" | tail -1
echo ""
if [ "$DRY_RUN" = 1 ]; then
  warn "本次为 dry-run，未删除/压缩任何数据。确认无误后去掉 --dry-run 正式执行"
else
  log "现在可以启动 server 了。建议："
  log "  1) 定期清理：把本脚本加入 crontab（每周一次），例如："
  log "     0 3 * * 1  KEEP_DAYS=7 LOG_KEEP_DAYS=30 /opt/easyops/rescue-h2.sh /opt/easyops/data --yes >> /opt/easyops/logs/rescue-h2.log 2>&1"
  log "  2) 若业务允许，可把监控快照/统计表保留期调短，从源头控制增长速度"
fi
