# Atalhos da raiz do repositorio. Cada projeto tem o proprio Makefile (applications/android/,
# applications/ios/, esp32-esp8266/); estes alvos so chamam o deles. `make` sozinho lista os alvos.

DEVICE ?=
TEAM ?=
BUNDLE_ID ?=
ANDROID_MAKE = "$(MAKE)" --no-print-directory -C applications/android $(if $(DEVICE),DEVICE="$(DEVICE)",)
IOS_MAKE = "$(MAKE)" --no-print-directory -C applications/ios $(if $(DEVICE),DEVICE="$(DEVICE)",) $(if $(TEAM),TEAM=$(TEAM),) $(if $(BUNDLE_ID),BUNDLE_ID=$(BUNDLE_ID),)

.DEFAULT_GOAL := help
.PHONY: help android android-setup android-build android-test android-devices android-logs android-replay ios ios-build ios-test ios-devices ios-sim ios-open

help: ## Lista os alvos
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  make %-16s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo
	@echo "  Com mais de um celular ligado: make android DEVICE=<serial>  ou  make ios DEVICE=<nome do iPhone>"
	@echo "  Assinatura do iOS: make ios TEAM=<seu time>  (e BUNDLE_ID=<bundle> se o do projeto for de outro time)"
	@echo "  Firmware das ESP32: veja make -C esp32-esp8266"

android: ## Compila, instala e abre o app Android no celular ligado por USB
	@$(ANDROID_MAKE) run

android-setup: ## Confere o Android SDK e o Java (uma vez por PC)
	@$(ANDROID_MAKE) setup

android-build: ## So compila o APK, sem celular
	@$(ANDROID_MAKE) build

android-test: ## Testes do app Android na JVM, sem celular
	@$(ANDROID_MAKE) test

android-devices: ## Lista os celulares ligados e se autorizaram o PC
	@$(ANDROID_MAKE) devices

android-logs: ## Log do app aberto no celular (Ctrl+C sai)
	@$(ANDROID_MAKE) logs

# Roda daqui, nao de applications/android, para os caminhos relativos de ARGS valerem a partir da raiz.
android-replay: ## Recalcula gravacoes de fingerprint no computador (ARGS="pasta ou .csv"; ARGS=--help)
	@$(ANDROID_MAKE) replay-tool
	@applications/android/tools/replay/build/install/replay/bin/replay $(ARGS)

ios: ## Compila, instala e abre o app iOS no iPhone ligado ao Mac
	@$(IOS_MAKE) run

ios-build: ## So compila o app iOS para iPhone, assinado
	@$(IOS_MAKE) build

ios-test: ## Testes do app iOS no simulador
	@$(IOS_MAKE) test

ios-devices: ## Lista os iPhones pareados com este Mac e se estao acessiveis
	@$(IOS_MAKE) devices

ios-sim: ## Compila, instala e abre o app iOS no simulador
	@$(IOS_MAKE) sim

ios-open: ## Abre o projeto iOS no Xcode
	@$(IOS_MAKE) open
