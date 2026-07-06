FROM node:24-slim AS build

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /app

COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,id=pnpm,target=/pnpm/store pnpm install --frozen-lockfile

COPY . .

RUN pnpm run build

ADD https://github.com/caddyserver/caddy/releases/download/v2.9.1/caddy_2.9.1_linux_amd64.tar.gz /tmp/caddy.tar.gz
RUN tar xzf /tmp/caddy.tar.gz -C /usr/local/bin caddy && chmod +x /usr/local/bin/caddy

RUN mkdir -p /etc/caddy && \
    printf '%s\n' \
    ':8080 {' \
    '    root * /usr/share/nginx/html' \
    '' \
    '    handle /api/* {' \
    '        reverse_proxy backend:8000' \
    '    }' \
    '' \
    '    handle {' \
    '        try_files {path} /index.html' \
    '        file_server' \
    '    }' \
    '}' \
    > /etc/caddy/Caddyfile

FROM debian:12-slim

COPY --from=build /app/dist /usr/share/nginx/html
COPY --from=build /usr/local/bin/caddy /usr/local/bin/caddy
COPY --from=build /etc/caddy /etc/caddy

EXPOSE 8080

CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile"]
