FROM nginx:1.27-alpine
# Official nginx image runs envsubst on /etc/nginx/templates/*.template → conf.d/
# Railway injects PORT; Compose leaves the default 80 (host maps FRONTEND_PORT→80).
ENV PORT=80
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY . /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=6 \
  CMD wget -qO- http://127.0.0.1:${PORT}/ >/dev/null || exit 1
