# Atalhos da raiz do repositorio. Cada projeto tem o proprio Makefile (android/, embedded/);
# estes alvos so chamam o deles. `make` sozinho lista os alvos.

DEVICE ?=
ANDROID_MAKE = "$(MAKE)" --no-print-directory -C android $(if $(DEVICE),DEVICE=$(DEVICE),)

.DEFAULT_GOAL := help
.PHONY: help android android-setup android-build android-test android-devices android-logs

help: ## Lista os alvos
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  make %-16s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo
	@echo "  Com mais de um celular ligado: make android DEVICE=<numero de serie de make android-devices>"
	@echo "  Firmware das ESP32: veja make -C embedded"

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
