# syntax=docker/dockerfile:1.7

############################
# Stage 0: build cosyfs (.so)
############################
FROM rust:1.97-alpine AS cosyfs-builder
WORKDIR /work/cosyfs

# Buildx populates these automatically; still OK if you build without buildx.
ARG TARGETARCH
ARG TARGETPLATFORM

# Alpine deps commonly needed for Rust cdylib builds
RUN apk add --no-cache \
    musl-dev \
    build-base \
    clang \
    llvm \
    lld \
    pkgconfig \
    openssl-dev

# Copy manifests first for better caching
COPY cosyfs/Cargo.toml cosyfs/Cargo.lock ./

# Dummy src to prime dependency cache (optional but helps)
RUN mkdir -p src && echo "pub fn _dummy() {}" > src/lib.rs

# Pre-fetch dependencies (keeps later builds fast)
RUN cargo fetch

# Now copy full source
COPY cosyfs/ ./

RUN set -eux; \
    case "${TARGETARCH:-amd64}" in \
      amd64)  RUST_TARGET="x86_64-unknown-linux-musl";  RES_ARCH="x86_64" ;; \
      arm64)  RUST_TARGET="aarch64-unknown-linux-musl"; RES_ARCH="aarch64" ;; \
      *) echo "Unsupported TARGETARCH=${TARGETARCH} (TARGETPLATFORM=${TARGETPLATFORM})" >&2; exit 1 ;; \
    esac; \
    rustup target add "$RUST_TARGET"; \
    RUSTFLAGS="-C target-feature=-crt-static" cargo build --release --target "$RUST_TARGET"; \
    mkdir -p /out/native/linux-"$RES_ARCH"; \
    cp "target/$RUST_TARGET/release/libcosyfs.so" "/out/native/linux-$RES_ARCH/libcosyfs.so"; \
    strip "/out/native/linux-$RES_ARCH/libcosyfs.so" || true

############################
# Stage 1: build the jar
############################
FROM maven:3.9.15-eclipse-temurin-26-alpine AS builder
WORKDIR /app

ARG TARGETARCH
ARG TARGETPLATFORM

COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy sources
COPY src src

# Copy the just-built native lib into resources before packaging
RUN set -eux; \
    case "${TARGETARCH:-amd64}" in \
      amd64) RES_ARCH="x86_64" ;; \
      arm64) RES_ARCH="aarch64" ;; \
      *) echo "Unsupported TARGETARCH=${TARGETARCH} (TARGETPLATFORM=${TARGETPLATFORM})" >&2; exit 1 ;; \
    esac; \
    mkdir -p "src/main/resources/native/linux-$RES_ARCH"

COPY --from=cosyfs-builder /out/native/ /app/src/main/resources/native/

# Sanity check: fail the image build if the native lib didn't make it in
RUN set -eux; \
    case "${TARGETARCH:-amd64}" in \
      amd64) RES_ARCH="x86_64" ;; \
      arm64) RES_ARCH="aarch64" ;; \
    esac; \
    test -f "src/main/resources/native/linux-$RES_ARCH/libcosyfs.so"

RUN mvn clean package -DskipTests -B

############################
# Stage 2: run the jar
############################
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN apk add --no-cache zip libc6-compat libgcc

# Copy the fat jar
COPY --from=builder /app/target/*.jar app.jar

RUN printf '%s\n' \
'#!/bin/sh' \
'set -eu' \
'ARCH="$(uname -m)"' \
'case "$ARCH" in' \
'  x86_64) RES_ARCH="x86_64" ;;' \
'  aarch64|arm64) RES_ARCH="aarch64" ;;' \
'  *) echo "Unsupported runtime arch: $ARCH" >&2; exit 1 ;;' \
'esac' \
'# verify the lib is packaged in the jar (Spring Boot jar layout)' \
'if ! zipinfo -1 /app/app.jar | grep -q "^BOOT-INF/classes/native/linux-${RES_ARCH}/libcosyfs.so$"; then' \
'  echo "FATAL: native cosyfs library missing for linux-${RES_ARCH} in app.jar (would fall back to unsafe path)" >&2' \
'  echo "Found native entries:" >&2' \
'  zipinfo -1 /app/app.jar | grep "BOOT-INF/classes/native/" >&2 || true' \
'  exit 1' \
'fi' \
'exec java -jar /app/app.jar' \
> /app/entrypoint.sh && chmod +x /app/entrypoint.sh

RUN chown -R 1000:1000 /app
USER 1000:1000

EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
