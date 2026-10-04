LANGUAGES := frontend go rust java
CLIENTS := go rust java
SCRIPTS := bun run .github/scripts/src

.PHONY: test pretest vendor ci-scripts frontend \
	$(addprefix test-,$(LANGUAGES)) $(addprefix pretest-,$(LANGUAGES)) \
	$(addprefix vendor-,$(CLIENTS))

test: $(addprefix test-,$(LANGUAGES))

pretest: $(addprefix pretest-,$(LANGUAGES))

$(addprefix test-,$(LANGUAGES)): .github/scripts/node_modules
	$(SCRIPTS)/test --language $(@:test-%=%)

$(addprefix pretest-,$(LANGUAGES)): .github/scripts/node_modules
	$(SCRIPTS)/pretest --language $(@:pretest-%=%)

vendor: $(addprefix vendor-,$(CLIENTS))

$(addprefix vendor-,$(CLIENTS)): .github/scripts/node_modules
	$(SCRIPTS)/vendor --language $(@:vendor-%=%)

ci-scripts: .github/scripts/node_modules
	bun run --cwd .github/scripts test

.github/scripts/node_modules: .github/scripts/bun.lock
	bun install --cwd .github/scripts --frozen-lockfile
	@touch $@

frontend:
	cd frontend && npm ci && npm run build
