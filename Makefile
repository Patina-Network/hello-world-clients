.PHONY: test test-go test-rust test-java test-frontend generate-go frontend

test:
	bash scripts/test.sh all

test-go test-rust test-java test-frontend:
	bash scripts/test.sh $(@:test-%=%)

generate-go:
	bash scripts/generate-go.sh

frontend:
	cd frontend && npm ci && npm run build
