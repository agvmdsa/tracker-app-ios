# Atalhos para testar, compilar e instalar o app iOS (TrackerApp) no iPhone ligado ao Mac.
# Rode a partir de ios/. `make` sozinho lista os alvos.
#
# Precisa do Xcode 15 ou mais novo, com a sua conta Apple entrando em Xcode > Settings > Accounts
# (o xcodebuild cria o perfil de assinatura sozinho com -allowProvisioningUpdates). No iPhone:
# Modo Desenvolvedor ligado (Ajustes > Privacidade e Seguranca) e "Confiar" neste Mac.

PROJECT := TrackerApp.xcodeproj
SCHEME := TrackerApp
CONFIGURATION := Debug
DERIVED := .build/DerivedData
APP := $(DERIVED)/Build/Products/$(CONFIGURATION)-iphoneos/TrackerApp.app
SIM_APP := $(DERIVED)/Build/Products/$(CONFIGURATION)-iphonesimulator/TrackerApp.app
XCODEBUILD := xcodebuild -project $(PROJECT) -scheme $(SCHEME) -configuration $(CONFIGURATION) -derivedDataPath $(DERIVED)

# O time de assinatura. Vazio = o do projeto. Com conta gratuita, o Personal Team (o ID aparece
# em Xcode > Settings > Accounts). Exemplo: make run TEAM=ABCDE12345
TEAM ?=
# O bundle ID, se o do projeto ja estiver registrado por outro time. Exemplo: BUNDLE_ID=com.seunome.TrackerApp
BUNDLE_ID ?=
SIGNING := -allowProvisioningUpdates $(if $(TEAM),DEVELOPMENT_TEAM=$(TEAM),) $(if $(BUNDLE_ID),PRODUCT_BUNDLE_IDENTIFIER=$(BUNDLE_ID),)

# O iPhone a usar quando ha mais de um acessivel: nome, identificador ou UDID de `make devices`.
DEVICE ?=
# O simulador de `make test` e `make sim`.
SIM ?= iPhone 17

.DEFAULT_GOAL := help
.PHONY: help devices test test-scripts build install run sim open clean

help: ## Lista os alvos
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  make %-12s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo
	@echo "  Variaveis: DEVICE=<iPhone>  TEAM=<time de assinatura>  BUNDLE_ID=<bundle>  SIM=<simulador>"
	@echo "  Primeira vez: entre com a conta Apple no Xcode, ligue o iPhone no cabo e rode make run TEAM=<seu time>"

devices: ## Lista os iPhones pareados com este Mac e se estao acessiveis
	xcrun devicectl list devices

test: ## Testes no simulador (SIM=iPhone 17)
	$(XCODEBUILD) -destination 'platform=iOS Simulator,name=$(SIM)' test

test-scripts: ## Testes do script que escolhe o iPhone
	python3 -m unittest discover -s scripts -p "test_*.py"

build: ## Compila o app para iPhone, assinado
	$(XCODEBUILD) -destination 'generic/platform=iOS' $(SIGNING) build
	@echo "App: $(APP)"

install: build ## Compila e instala no iPhone ligado
	@id=$$(python3 scripts/pick_device.py --device "$(DEVICE)") || exit 1; \
	xcrun devicectl device install app --device $$id "$(APP)"

run: install ## Instala e abre o app no iPhone
	@id=$$(python3 scripts/pick_device.py --device "$(DEVICE)") || exit 1; \
	bundle=$$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$(APP)/Info.plist"); \
	xcrun devicectl device process launch --device $$id $$bundle

sim: ## Compila, instala e abre no simulador (SIM=iPhone 17)
	$(XCODEBUILD) -destination 'platform=iOS Simulator,name=$(SIM)' build
	xcrun simctl boot "$(SIM)" 2>/dev/null || true
	open -a Simulator
	xcrun simctl install "$(SIM)" "$(SIM_APP)"
	xcrun simctl launch "$(SIM)" $$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$(SIM_APP)/Info.plist")

open: ## Abre o projeto no Xcode
	open $(PROJECT)

clean: ## Apaga a saida da compilacao
	rm -rf $(DERIVED)
