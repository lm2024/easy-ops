# 演示用 Nginx：托管 EasyOps 前端 + 标准 access.log

FROM nginx:1.24-alpine

COPY nginx.conf /etc/nginx/nginx.conf
COPY html /usr/share/nginx/html
COPY docker-entrypoint.sh /docker-entrypoint.sh

RUN chmod +x /docker-entrypoint.sh \
    && mkdir -p /var/log/nginx \
    && chmod 755 /var/log/nginx

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
