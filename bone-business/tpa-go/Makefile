.PHONY: run test tidy build

run:
	go run ./cmd/server

test:
	go test ./...

tidy:
	go mod tidy

build:
	go build -o bin/tpa-go ./cmd/server
