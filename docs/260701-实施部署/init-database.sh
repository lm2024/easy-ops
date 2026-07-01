#!/bin/bash
# ============================================================
# EasyOps H2 数据库初始化脚本
# 
# 用途：手动初始化 H2 数据库（通常不需要，Spring Boot 自动处理）
# 场景：Server 无法正常启动，需要提前建表
#
# 使用方法：
#   1. 确保 JDK 8 已安装
#   2. 确保 Server 已编译（target/ops-platform-server-1.0.0-SNAPSHOT.jar 存在）
#   3. 修改下方 BASE_DIR 为实际部署路径
#   4. 执行：bash init-database.sh
# ============================================================

set -e

# ===== 配置区（按实际部署路径修改） =====
BASE_DIR="/opt/easy-ops/backend"
JAR="$BASE_DIR/server/target/ops-platform-server-1.0.0-SNAPSHOT.jar"
DATA_DIR="$BASE_DIR/server/data"
DB_URL="jdbc:h2:${DATA_DIR}/ops;MODE=MySQL"
SCHEMA_SQL="$BASE_DIR/server/src/main/resources/db/schema.sql"

# ===== 检查 =====
echo "=========================================="
echo "  EasyOps H2 数据库初始化"
echo "=========================================="

if [ ! -f "$JAR" ]; then
    echo "[ERROR] JAR 文件不存在: $JAR"
    echo "请先编译: cd $BASE_DIR && mvn clean package -DskipTests"
    exit 1
fi

if [ ! -f "$SCHEMA_SQL" ]; then
    echo "[ERROR] schema.sql 不存在: $SCHEMA_SQL"
    exit 1
fi

# 创建数据目录
mkdir -p "$DATA_DIR/logs"

# 检查数据库是否已存在
if [ -f "${DATA_DIR}/ops.mv.db" ]; then
    echo "[INFO] 数据库文件已存在: ${DATA_DIR}/ops.mv.db"
    read -p "是否重新初始化（将清空所有数据）? [y/N]: " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        echo "取消初始化"
        exit 0
    fi
    echo "删除旧数据库..."
    rm -f "${DATA_DIR}/ops.mv.db"
    rm -f "${DATA_DIR}/ops.trace.db"
fi

# ===== 执行初始化 =====
echo ""
echo "数据库路径: $DB_URL"
echo "执行脚本: $SCHEMA_SQL"
echo ""

java -cp "$JAR" org.h2.tools.RunScript \
    -url "$DB_URL" \
    -user sa \
    -script "$SCHEMA_SQL"

if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "  [OK] 数据库初始化成功！"
    echo "  数据库文件: ${DATA_DIR}/ops.mv.db"
    echo "=========================================="
    echo ""
    echo "默认管理员账号: admin / admin123"
    echo ""
    echo "现在可以启动 Server："
    echo "  cd $BASE_DIR/server"
    echo "  export JWT_SECRET='your-secret-here'"
    echo "  java -jar target/ops-platform-server-1.0.0-SNAPSHOT.jar"
else
    echo ""
    echo "[ERROR] 数据库初始化失败！"
    exit 1
fi
