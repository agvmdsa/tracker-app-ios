# Guia de uso do firmware ESP32 (beacon BLE e coleta de RSSI)

> Documento de uso do firmware em `embedded/`. **Não é um README.** Os passos de preparar,
> compilar, gravar, escolher o papel e monitorar estão completos (necessários para validar o
> beacon). Os demais capítulos são completados na tarefa T067. Especificação:
> `specs/002-esp32-ble-firmware/` (não versionada).
>
> **Estado:** os papéis de **beacon** (US1) e de **coletor** (US2) estão implementados. As janelas,
> o MQTT e a saúde ainda não existem; o comando `window` responde `ERR not_supported`.

## 1. Preparar o ambiente

Você precisa de um Mac com Python 3, uma placa ESP32 e um **cabo USB de dados** (alguns cabos só
carregam).

**Atalho com `make`.** O `Makefile` de `embedded/` já faz as variáveis de ambiente, o ambiente
virtual e o `config.h` descritos neste capítulo e os comandos dos capítulos 2, 3 e 5:

```bash
cd embedded
make setup           # uma vez: cria o venv, instala o PlatformIO e cria include/config.h
make test            # testes de lógica no computador (make test-one T=test_label para um só)
make build           # compila o firmware
make flash           # compila e grava (com mais de uma placa: make flash PORT=/dev/cu.usbserial-XXXX)
make monitor         # monitor serial
make monitor-log     # monitor serial que também grava em logs/device-monitor-<data>.log
make flash-monitor   # grava e abre o monitor
make                 # lista os alvos
```

Com o `make` você não precisa dos `export` nem do `.venv/bin/pio` abaixo; eles continuam aqui para
quem preferir os comandos diretos.

**Ordem do primeiro dia (com `make`):**

1. `make setup` só é preciso em um computador novo. Neste Mac o venv e o `include/config.h` já
   existem, e o alvo apenas mostra a versão do PlatformIO.
2. `make test` (cerca de 40 s, opcional) e `make build` (cerca de 1 min) confirmam que tudo compila.
3. **Uma placa por vez** no USB (cabo de dados). `make ports` mostra a porta; `make flash` grava (se
   travar em `Connecting......`, segure **BOOT**); `make monitor` abre a serial. Com mais de uma
   placa conectada, use `PORT=/dev/cu.usbserial-XXXX` nos dois comandos.
4. Na primeira placa confira o banner (`# fw=0.1.0 … role=beacon id=9F3C1A02`) e o evento
   `advertising` com o `packet=`; teste `id 9F3C1A0` e `id ZZZZZZZZ` (devem dar `ERR bad_id`) e veja se
   o **TrackerApp** do iPhone lista o ID (**Indoor → "Sou o observador" → Sinais próximos**). Só depois
   siga para as outras.
5. **Dê um ID diferente a cada placa antes de ligar duas juntas.** Todas saem de fábrica com o
   `DEFAULT_BEACON_ID` do `config.h` (`9F3C1A02`) e o papel `beacon`. Em cada uma, no monitor:
   `id 9F3C1A01` (depois `…02`, `…03`) e `reboot`. O binário é o mesmo para todas: o ID fica na
   memória da placa (NVS), então não é preciso editar o `config.h` nem compilar de novo.
6. Depois de configuradas, as placas só precisam de energia USB (carregador ou power bank); o PC
   volta a ser necessário apenas para o coletor (capítulo 6), que usa o monitor serial.

O PlatformIO Core fica num ambiente virtual dentro do projeto (`embedded/.venv`). Em **cada novo
terminal**, entre em `embedded/` e exporte as variáveis abaixo antes de usar o `pio`:

```bash
cd embedded
export PIP_CERT=/etc/ssl/cert.pem SSL_CERT_FILE=/etc/ssl/cert.pem REQUESTS_CA_BUNDLE=/etc/ssl/cert.pem
export PLATFORMIO_CORE_DIR="$PWD/.pio/core"
```

- As três primeiras fazem o Python verificar o TLS com a lista de CAs do sistema (o Python do
  python.org não traz uma). A verificação continua **ligada**.
- A última mantém as ferramentas baixadas dentro do projeto (`embedded/.pio/core`), que o Git ignora.
- O domínio `registry.platformio.org` precisa estar liberado no ambiente do Claude Code.

Configuração local (nunca vai para o Git):

```bash
cp include/config.example.h include/config.h
# edite include/config.h: no mínimo DEFAULT_BEACON_ID (8 caracteres hexadecimais)
```

## 2. Compilar

```bash
.venv/bin/pio run -e esp32dev
```

A primeira compilação baixa a plataforma, o núcleo Arduino e as bibliotecas (cerca de 4 minutos).
Depois leva menos de 1 minuto. Resultado esperado: `[SUCCESS]`.

Testes de lógica no computador, sem a placa:

```bash
.venv/bin/pio test -e native
```

## 3. Gravar na placa

1. Conecte a placa por USB e descubra a porta: `.venv/bin/pio device list` (algo como
   `/dev/cu.usbserial-XXXX` ou `/dev/cu.SLAB_USBtoUART`).
2. Grave: `.venv/bin/pio run -e esp32dev -t upload`
   (com mais de uma porta, acrescente `--upload-port /dev/cu.usbserial-XXXX`).
3. Se travar em `Connecting......`, segure o botão **BOOT** da placa até a gravação começar.

## 4. Escolher o papel e o ID

Pela serial (veja o capítulo 5). Cada placa precisa de um **ID de 8 caracteres hexadecimais**
diferente:

```text
id 9F3C1A02
role beacon
reboot
```

As configurações são gravadas na memória da placa (NVS) e valem depois do `reboot`. Um ID inválido
(`9F3C1A0`, `ZZZZZZZZ`) é recusado com `ERR bad_id`. Para voltar aos valores do `config.h`:
`config reset` e `reboot`.

## 5. Monitorar pela serial

```bash
.venv/bin/pio device monitor -b 115200
```

Digite os comandos e tecle Enter. Sair: `Ctrl+C`. Na partida você deve ver:

```text
# fw=0.1.0 node=A1B2C3 boot=1 role=beacon id=9F3C1A02 baud=115200
E,<t_ms>,1,info,advertising,id=9F3C1A02 power=0 interval=100 packet=31
```

- `packet=31` é o tamanho real do pacote de anúncio (o limite legado é 31 bytes). **Anote esse
  valor**: é uma das medições que a especificação pede.
- Comandos: `help`, `status`, `id <8hex>`, `power <dBm>`, `interval <ms>`, `role beacon|collector`,
  `config save|reset`, `reboot`. Veja `help` na placa.

## 6. Rotular e gravar (coletor)

O coletor escuta os anúncios do protocolo do projeto (beacons ESP32 e âncoras do iPhone) e imprime
uma linha por anúncio. Use **uma placa como coletor** e as outras como beacons. Só vale um anúncio
com o UUID do projeto **e** o nome com exatamente 8 caracteres hexadecimais; qualquer outro
aparelho é ignorado. A identidade é o ID, nunca o endereço.

1. Troque o papel e reinicie: `role collector`, `config save`, `reboot`. O banner mostra
   `role=collector` e depois `E,<t_ms>,<boot>,info,scanning,interval=100 window=99`.
2. **Vista ao vivo (ocioso):** uma linha por beacon por segundo, sem rótulo:
   `L,<t_ms>,<boot>,<node>,<beacon>,<rssi_último>,<taxa_por_s>`.
3. **Gravar um ponto:**

   ```text
   label S1 P3 2.0 4.5
   rec start
   ```

   espere (por exemplo, 60 s) e termine com `rec stop`.

   - `label <sessão> <ponto> <x> <y>`: sessão e ponto têm até 16 caracteres de `[A-Za-z0-9_-]`;
     x e y são metros. Vale na hora: o que já foi recebido mantém o rótulo antigo.
   - Gravando, sai uma linha por anúncio:
     `R,<t_ms>,<boot>,<node>,<beacon>,<rssi>,<sessão>,<ponto>,<x>,<y>` (a vista ao vivo pausa).
   - `rec stop` imprime uma linha `S` por beacon
     (`S,<t_ms>,<boot>,<node>,<sessão>,<ponto>,<beacon>,<registros>,<taxa_por_s>`), depois
     `OK recording stopped records=N beacons=M`, e a vista ao vivo volta.
   - `rec start` sem `label` responde `ERR no_label`; com uma gravação em curso, `ERR already_recording`.
4. Para o próximo ponto, mude o `label` e repita `rec start` e `rec stop`. Não precisa regravar a placa.

O Wi-Fi fica desligado por padrão: ele divide o rádio com o BLE e pode cortar a varredura.

## 7. Enviar os dados pra API (upload automático)

Cada linha `R` que o coletor grava (capítulo 6) também é enfileirada pra subir, em lote, pro
`POST /fingerprints` da API de posicionamento — o mesmo endpoint que o app iOS usa pra treinar o
modelo de fingerprinting. Isso evita o passo manual de copiar o log serial e converter à mão.

**Configuração** (`include/config.h`):

```c
#define ENABLE_WIFI 1
#define WIFI_SSID "sua-rede"
#define WIFI_PASSWORD "sua-senha"
#define API_HOST "api-tracker-app.onrender.com"
#define API_KEY "a-mesma-chave-configurada-no-render"
```

Deixe `API_HOST` vazio (`""`) pra desligar o envio (o padrão do `config.example.h`) — por exemplo,
enquanto estiver testando só com a serial, sem querer gastar a cota do Render. `API_KEY` precisa
ser **exatamente** o mesmo valor da variável `API_KEY` do serviço da API no Render.

**Como funciona:**

- Grava local e sobe em lote são independentes: a linha `R` sempre sai na serial, com ou sem
  Wi-Fi. O upload só acontece além disso, se `ENABLE_WIFI 1` e `API_HOST` não estiver vazio.
- Lotes de até 20 linhas, ou a cada 5 segundos se tiver menos que isso esperando — não precisa de
  `rec stop` pra subir, sobe durante a gravação mesmo.
- Um lote que falhar (Wi-Fi caiu, API fora do ar, resposta de erro) é descartado, não é tentado de
  novo — mais simples, ao custo de perder esse lote específico. Se isso importar, mantenha o log
  serial como backup (`make monitor-log`).
- **O Wi-Fi disputa o rádio com o BLE** (por isso fica desligado por padrão — capítulo 6). Ligar
  `ENABLE_WIFI` pode reduzir a taxa de anúncios que o coletor recebe; se notar isso, teste com e
  sem Wi-Fi ligado e compare a taxa (`taxa_por_s` nas linhas `S`).

## 8. Publicar no broker MQTT

_A completar (T067)._

## 8. Publicar no broker MQTT

_A completar (T067)._

## 9. Solução de problemas

- **`pip`/`pio` com `CERTIFICATE_VERIFY_FAILED`:** faltaram as variáveis do capítulo 1.
- **O iPhone não vê o beacon:** confira o ID no `status`, deixe o app do iPhone na tela que lista os
  sinais próximos e aproxime os aparelhos. Se continuar sem aparecer, avise: existe uma variante do
  anúncio (nome na resposta de varredura) preparada para esse caso.
- **Placa não aparece em `pio device list`:** troque o cabo (precisa ser de dados) e confira o
  driver USB-serial da placa.
- **Mais itens:** completados na tarefa T067.
