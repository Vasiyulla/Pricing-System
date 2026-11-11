# Hardened Multi-Stage Build for Price Bridge Go Backend
FROM golang:1.26-alpine AS builder

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /app/api ./cmd/api/main.go && \
    CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /app/worker ./cmd/worker/main.go

# Minimal runtime image
FROM alpine:3.20

RUN apk --no-cache add ca-certificates tzdata

WORKDIR /app
RUN mkdir -p /app/data/evidence

COPY --from=builder /app/api /app/api
COPY --from=builder /app/worker /app/worker
RUN chmod 755 /app/api /app/worker && \
    ln -sf /app/api /app/server && \
    mkdir -p /root && ln -sf /app/api /root/server

EXPOSE 8080

CMD ["/bin/sh", "-c", "echo '[boot] launching /app/api on port '${PORT:-8080}; exec /app/api"]
