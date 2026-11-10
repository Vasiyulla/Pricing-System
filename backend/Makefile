.PHONY: build run-api run-worker vet lint clean

build:
	go build -o bin/api ./cmd/api
	go build -o bin/worker ./cmd/worker

run-api:
	go run ./cmd/api

run-worker:
	go run ./cmd/worker

vet:
	go vet ./...

lint:
	golangci-lint run ./...

clean:
	rm -rf bin/ data/

