FROM node:24-slim AS build

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable && corepack prepare pnpm@11.21.0 --activate

WORKDIR /app

COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,id=pnpm,target=/pnpm/store pnpm install --frozen-lockfile --ignore-scripts

COPY index.html env.d.ts tsconfig.json tsconfig.app.json tsconfig.node.json vite.config.ts ./
COPY public ./public
COPY src ./src

RUN pnpm run build

ARG CADDY_VERSION=2.9.1
ARG TARGETARCH
RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates curl \
    && case "$TARGETARCH" in \
        amd64) CADDY_SHA512="0412057ba591c33bdb66034d7f32209619ce635147dac3084c79abcbc118a761145e695199f57c6798d88fab077f2b0d9cb999f52ded904c6d464a4eba202932" ;; \
        arm64) CADDY_SHA512="80b917c2bd8aa0892f1386ff97af9e776067dd8cdc4793238869aca7e13f7280f89d1799a1fef08b49af92dbd86684c3ba7a38b9d44afc55ef2eeb33b49e1cbd" ;; \
        *) echo "Unsupported architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac \
    && curl --proto '=https' --proto-redir '=https' --tlsv1.2 --fail --show-error --location \
        "https://github.com/caddyserver/caddy/releases/download/v${CADDY_VERSION}/caddy_${CADDY_VERSION}_linux_${TARGETARCH}.tar.gz" \
        --output /tmp/caddy.tar.gz \
    && echo "${CADDY_SHA512}  /tmp/caddy.tar.gz" | sha512sum --check --strict \
    && tar xzf /tmp/caddy.tar.gz -C /usr/local/bin caddy \
    && chmod 0755 /usr/local/bin/caddy \
    && rm -rf /tmp/caddy.tar.gz /var/lib/apt/lists/*

RUN mkdir -p /etc/caddy && \
    printf '%s\n' \
    ':8080 {' \
    '    root * /usr/share/nginx/html' \
    '    try_files {path} /index.html' \
    '    file_server' \
    '}' \
    > /etc/caddy/Caddyfile

FROM gcr.io/distroless/static-debian12:nonroot

COPY --from=build --chown=root:root /app/dist /usr/share/nginx/html
COPY --from=build --chown=root:root /usr/local/bin/caddy /usr/local/bin/caddy
COPY --from=build --chown=root:root /etc/caddy /etc/caddy

USER nonroot:nonroot

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/caddy", "run", "--config", "/etc/caddy/Caddyfile"]
