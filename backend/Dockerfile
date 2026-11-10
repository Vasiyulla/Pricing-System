# Hardened Multi-Stage Build for Price Bridge Go Backend
FROM golang:1.26-alpine AS builder

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /app/api ./cmd/api/main.go && \
    CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /app/worker ./cmd/worker/main.go

# Minimal runtime image running as non-root user
FROM alpine:3.20

RUN apk --no-cache add ca-certificates tzdata wget && \
    addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
RUN mkdir -p /app/data/evidence && chown -R appuser:appgroup /app

COPY --from=builder /app/api ./api
COPY --from=builder /app/worker ./worker

USER appuser:appgroup

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/health || exit 1

CMD ["./api"]
