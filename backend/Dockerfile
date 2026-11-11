# Hardened Multi-Stage Build for Price Bridge Go Backend
FROM golang:1.26-alpine AS builder

WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /out/api ./cmd/api && \
    CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /out/worker ./cmd/worker

# Minimal runtime image
FROM alpine:3.20

RUN apk --no-cache add ca-certificates tzdata

WORKDIR /app
RUN mkdir -p /app/bin /app/data/evidence

COPY --from=builder /out/api /app/bin/api
COPY --from=builder /out/worker /app/bin/worker
RUN chmod 755 /app/bin/api /app/bin/worker && \
    ln -sf /app/bin/api /app/server && \
    mkdir -p /root && ln -sf /app/bin/api /root/server

EXPOSE 8080

CMD ["/bin/sh", "-c", "echo '[boot] launching /app/bin/api on port '${PORT:-8080}; exec /app/bin/api"]
