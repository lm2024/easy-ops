#!/bin/sh
set -e

# 官方镜像默认把 access.log 链到 /dev/stdout，Agent 无法用 RandomAccessFile 增量读取。
# 共享卷场景下必须落成真实文件。
mkdir -p /var/log/nginx
if [ -L /var/log/nginx/access.log ] || [ ! -f /var/log/nginx/access.log ]; then
  rm -f /var/log/nginx/access.log /var/log/nginx/error.log
  touch /var/log/nginx/access.log /var/log/nginx/error.log
  chmod 644 /var/log/nginx/access.log /var/log/nginx/error.log
fi

exec nginx -g 'daemon off;'
