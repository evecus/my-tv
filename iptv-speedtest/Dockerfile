# ── Stage 1: Builder ─────────────────────────────────────────────
FROM rust:1.82-alpine AS builder

# musl-dev 提供 musl libc；rustls 不依赖 openssl，无需 openssl-dev
RUN apk add --no-cache musl-dev

WORKDIR /app

# 先只复制依赖声明，利用层缓存加速重复构建
COPY Cargo.toml Cargo.lock* ./

# 缓存依赖编译
RUN mkdir src && echo 'fn main(){}' > src/main.rs \
    && cargo build --release \
    && rm -rf src

# 复制真正的源码并编译
COPY src ./src
RUN touch src/main.rs && cargo build --release

# ── Stage 2: Runtime ─────────────────────────────────────────────
FROM alpine:3.19

RUN apk add --no-cache tzdata ca-certificates

ENV TZ=Asia/Shanghai

WORKDIR /app
RUN mkdir -p /app/data

COPY --from=builder /app/target/release/iptv-speed-tester /app/iptv

ENV PORT=3030
ENV WORKERS=20
ENV TOP=5
ENV INTERVAL=6h

WORKDIR /app/data
EXPOSE 3030

ENTRYPOINT ["/app/iptv"]
CMD ["--port", "3030", "--workers", "20", "--top", "5", "--interval", "6h"]
