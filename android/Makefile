# Atalhos para testar, compilar e instalar o app Android (BlueGuide) no celular ligado ao PC.
# Rode a partir de android/. `make` sozinho lista os alvos.
#
# Precisa de um Java 17 ou mais novo no PATH (ou em JAVA_HOME) para abrir o Gradle, que baixa sozinho
# o JDK 21 que o projeto pede, e do Android SDK (o do Android Studio serve). O Gradle tambem baixa
# sozinho a plataforma do Android que faltar.

# SDK: o da variavel de ambiente ou o lugar padrao do Android Studio (macOS e Linux).
ANDROID_HOME ?= $(or $(ANDROID_SDK_ROOT),$(firstword $(wildcard $(HOME)/Library/Android/sdk $(HOME)/Android/Sdk)))
export ANDROID_HOME

ADB := $(ANDROID_HOME)/platform-tools/adb
GRADLE := ./gradlew --console=plain
PACKAGE := br.org.cesar.blueguide
ACTIVITY := $(PACKAGE)/.MainActivity
APK := app/build/outputs/apk/debug/app-debug.apk

# Celular a usar quando ha mais de um ligado (o numero de serie de `make devices`).
# O adb e o Gradle leem ANDROID_SERIAL. Exemplo: make run DEVICE=R58N12ABCDE
DEVICE ?=
ifneq ($(strip $(DEVICE)),)
export ANDROID_SERIAL := $(DEVICE)
endif

.DEFAULT_GOAL := help
.PHONY: help setup devices test lint build install run logs uninstall clean check-sdk check-adb check-device

help: ## Lista os alvos
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  make %-10s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo
	@echo "  Variaveis: DEVICE=<numero de serie>  ANDROID_HOME=<pasta do SDK>"
	@echo "  Primeira vez: make setup, ligue o celular com a depuracao USB ativada e rode make run"

check-sdk:
	@test -n "$(ANDROID_HOME)" -a -d "$(ANDROID_HOME)" || { \
		echo "Android SDK nao encontrado. Instale o Android Studio (ele cria ~/Library/Android/sdk ou ~/Android/Sdk)"; \
		echo "ou informe a pasta: make setup ANDROID_HOME=/caminho/do/sdk"; exit 1; }

check-adb: check-sdk
	@test -x "$(ADB)" || { echo "Falta o adb em $(ADB). No Android Studio: SDK Manager > SDK Tools > Android SDK Platform-Tools."; exit 1; }

local.properties: | check-sdk
	@echo "sdk.dir=$(ANDROID_HOME)" > local.properties
	@echo "Criado local.properties (ignorado pelo Git) apontando para $(ANDROID_HOME)"

setup: check-adb local.properties ## Confere SDK e JDK e cria o local.properties (uma vez)
	@echo "SDK:  $(ANDROID_HOME)"
	@command -v java >/dev/null 2>&1 || test -n "$$JAVA_HOME" || { echo "Java nao encontrado. Instale um JDK 17 ou mais novo (ex: brew install --cask temurin@21)."; exit 1; }
	@echo "Java: $${JAVA_HOME:-$$(command -v java)}"
	@"$(ADB)" version | head -1
	$(GRADLE) --version

# Para com uma mensagem clara quando nao ha celular, ele nao autorizou o PC ou ha mais de um.
check-device: check-adb
	@"$(ADB)" start-server >/dev/null 2>&1 || true
	@if [ -n "$$ANDROID_SERIAL" ]; then \
		state=$$("$(ADB)" -s "$$ANDROID_SERIAL" get-state 2>/dev/null); \
		[ "$$state" = "device" ] || { echo "O celular $$ANDROID_SERIAL nao esta pronto (estado: $${state:-nao encontrado}). Veja make devices (na raiz: make android-devices)."; exit 1; }; \
	else \
		ready=$$("$(ADB)" devices | awk 'NR > 1 && $$2 == "device"' | wc -l | tr -d ' '); \
		unauthorized=$$("$(ADB)" devices | awk 'NR > 1 && $$2 == "unauthorized"' | wc -l | tr -d ' '); \
		if [ "$$ready" = "0" ]; then \
			if [ "$$unauthorized" != "0" ]; then echo "O celular esta ligado mas nao autorizou este PC: desbloqueie a tela e aceite \"Permitir depuracao USB\"."; \
			else echo "Nenhum celular encontrado. Ative Opcoes do desenvolvedor > Depuracao USB, ligue o cabo e confira com make devices (na raiz: make android-devices)."; fi; \
			exit 1; \
		elif [ "$$ready" != "1" ]; then \
			echo "Ha $$ready celulares ligados. Escolha um: make run DEVICE=<numero de serie>"; "$(ADB)" devices -l; exit 1; \
		fi; \
	fi

devices: check-adb ## Lista os celulares ligados (e se autorizaram o PC)
	@"$(ADB)" devices -l

test: local.properties ## Testes na JVM (sem celular)
	$(GRADLE) testDebugUnitTest

lint: local.properties ## Lint do Android
	$(GRADLE) lintDebug

build: local.properties ## Compila o APK de debug
	$(GRADLE) assembleDebug
	@echo "APK: $(APK)"

install: local.properties check-device ## Compila e instala no celular ligado
	$(GRADLE) installDebug

run: install ## Instala e abre o app no celular
	"$(ADB)" shell am start -n $(ACTIVITY)

logs: check-device ## Mostra o log do app aberto no celular (Ctrl+C sai)
	@pid=$$("$(ADB)" shell pidof -s $(PACKAGE) | tr -d '\r'); \
	test -n "$$pid" || { echo "O app nao esta aberto no celular. Rode make run primeiro."; exit 1; }; \
	"$(ADB)" logcat --pid=$$pid

uninstall: check-device ## Remove o app do celular (apaga as ancoras e a calibracao salvas nele)
	"$(ADB)" uninstall $(PACKAGE)

clean: local.properties ## Apaga a saida da compilacao
	$(GRADLE) clean
