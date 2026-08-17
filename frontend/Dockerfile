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

FROM golang:1.26.6-bookworm AS caddy-build

ARG CADDY_VERSION=2.11.4
ARG CADDY_COMMIT=e2eee6a7fce366321294c9c2a79f3146891dcbdf
WORKDIR /src
RUN git clone --depth 1 --branch "v${CADDY_VERSION}" https://github.com/caddyserver/caddy.git . \
    && test "$(git rev-parse HEAD)" = "${CADDY_COMMIT}" \
    && sed -i 's|github.com/google/cel-go v0.28.1|github.com/google/cel-go v0.29.2|' go.mod \
    && sed -i 's|\[\]interpreter.Interpretable{reqAttr}|[]interpreter.InterpretableV2{reqAttr}|' modules/caddyhttp/celmatcher.go \
    && go mod edit -require=golang.org/x/net@v0.56.0 -require=golang.org/x/text@v0.39.0 -require=google.golang.org/grpc@v1.82.1 \
    && go mod tidy \
    && CGO_ENABLED=0 go build -trimpath -o /usr/local/bin/caddy ./cmd/caddy \
    && /usr/local/bin/caddy version

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
COPY --from=caddy-build --chown=root:root /usr/local/bin/caddy /usr/local/bin/caddy
COPY --from=caddy-build --chown=root:root /etc/caddy /etc/caddy

USER nonroot:nonroot

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/caddy", "run", "--config", "/etc/caddy/Caddyfile"]
