.PHONY: build test clean fmt vet lint help

build:
	go build -v ./...

test:
	go test -v ./...

clean:
	go clean -v ./...
	rm -f bone-metadata-go

fmt:
	go fmt ./...

vet:
	go vet ./...

lint:
	golangci-lint run

help:
	@echo "Available targets:"
	@echo "  build  - Build the project"
	@echo "  test   - Run tests"
	@echo "  clean  - Clean build artifacts"
	@echo "  fmt    - Format code"
	@echo "  vet    - Run go vet"
	@echo "  lint   - Run linter"
