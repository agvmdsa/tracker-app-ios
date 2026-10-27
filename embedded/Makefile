# Atalhos para preparar, testar, compilar e gravar o firmware (specs/002-esp32-ble-firmware).
# Rode a partir de embedded/. `make` sozinho lista os alvos.
#
# O Python do python.org nao traz lista de CAs, entao as variaveis abaixo apontam para a do
# macOS (a verificacao TLS continua ligada). PLATFORMIO_CORE_DIR mantem tudo dentro do projeto.

CA_BUNDLE ?= /etc/ssl/cert.pem
export PIP_CERT := $(CA_BUNDLE)
export SSL_CERT_FILE := $(CA_BUNDLE)
export REQUESTS_CA_BUNDLE := $(CA_BUNDLE)
export PLATFORMIO_CORE_DIR := $(CURDIR)/.pio/core
export PLATFORMIO_CHECK_PLATFORMIO_INTERVAL := 9999

PYTHON ?= python3
PIO_VERSION := 6.2.0
PIO := .venv/bin/pio
ENV := esp32dev
BAUD := 115200

# Porta serial da placa. Vazio = o PlatformIO detecta sozinho. Exemplo: make flash PORT=/dev/cu.usbserial-0001
PORT ?=
UPLOAD_PORT := $(if $(PORT),--upload-port $(PORT),)
MONITOR_PORT := $(if $(PORT),--port $(PORT),)

.DEFAULT_GOAL := help
.PHONY: help setup test test-one test-scripts build flash id monitor monitor-log flash-monitor ports clean

help: ## Lista os alvos
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  make %-14s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo
	@echo "  Variaveis: ID=<8 hex>  PORT=<porta serial>  T=<pasta de teste>"
	@echo "  Exemplos:  make flash ID=9F3C1A01    make test-one T=test_label"

$(PIO):
	$(PYTHON) -m venv .venv
	.venv/bin/pip install platformio==$(PIO_VERSION)

include/config.h:
	cp include/config.example.h include/config.h
	@echo "Criado include/config.h a partir do exemplo (ignorado pelo Git). Wi-Fi e MQTT so precisam dele a partir do US4."

setup: $(PIO) include/config.h ## Cria o venv com o PlatformIO e o include/config.h (uma vez)
	@$(PIO) --version

test: $(PIO) ## Testes no host (ambiente native, sem placa)
	$(PIO) test -e native

test-one: $(PIO) ## Um teste so: make test-one T=test_label
	@test -n "$(T)" || { echo "Informe o teste: make test-one T=test_label"; exit 1; }
	$(PIO) test -e native -f $(T)

test-scripts: $(PIO) ## Testes dos scripts em Python (ID da placa e conversor de dados)
	.venv/bin/python -m unittest discover -s scripts -p "test_*.py"
	.venv/bin/python -m unittest discover -s ../scripts -p "test_*.py"

build: $(PIO) include/config.h ## Compila o firmware (esp32dev)
	$(PIO) run -e $(ENV)

flash: $(PIO) include/config.h ## Compila e grava na placa (com ID=<8 hex> ja define o ID dela)
	$(PIO) run -e $(ENV) -t upload $(UPLOAD_PORT)
	@$(if $(ID),"$(MAKE)" --no-print-directory id,true)

id: $(PIO) ## Define o ID da placa pela serial e confere: make id ID=9F3C1A01
	@test -n "$(ID)" || { echo "Informe o ID: make id ID=9F3C1A01"; exit 1; }
	.venv/bin/python scripts/set_beacon_id.py --id $(ID) $(if $(PORT),--port $(PORT),)

monitor: $(PIO) ## Abre o monitor serial (Ctrl+C sai)
	$(PIO) device monitor -b $(BAUD) $(MONITOR_PORT)

monitor-log: $(PIO) ## Monitor serial que tambem grava em logs/device-monitor-<data>.log
	$(PIO) device monitor -b $(BAUD) $(MONITOR_PORT) -f default -f log2file

flash-monitor: flash monitor ## Grava e ja abre o monitor

ports: $(PIO) ## Lista as portas seriais visiveis
	$(PIO) device list

clean: $(PIO) ## Apaga a saida da compilacao (mantem o venv e o cache do PlatformIO)
	$(PIO) run -e $(ENV) -t clean
