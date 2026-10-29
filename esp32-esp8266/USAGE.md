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
make flash ID=9F3C1A01   # compila, grava e já define o ID da placa (sem ID=, o ID padrão do config.h)
make id ID=9F3C1A01      # só define o ID de uma placa que já está gravada
                         # (com mais de uma placa: acrescente PORT=/dev/cu.usbserial-XXXX)
make monitor         # monitor serial
make monitor-log     # monitor serial que também grava em logs/device-monitor-<data>.log
make flash-monitor   # grava e abre o monitor
make test-scripts    # testes dos scripts em Python (ID da placa e conversor de dados)
make                 # lista os alvos
```

Com o `make` você não precisa dos `export` nem do `.venv/bin/pio` abaixo; eles continuam aqui para
quem preferir os comandos diretos.

**Ordem do primeiro dia (com `make`):**

1. `make setup` só é preciso em um computador novo. Neste Mac o venv e o `include/config.h` já
   existem, e o alvo apenas mostra a versão do PlatformIO.
2. `make test` (cerca de 40 s, opcional) e `make build` (cerca de 1 min) confirmam que tudo compila.
3. **Uma placa por vez** no USB (cabo de dados). `make ports` mostra a porta; `make flash ID=9F3C1A01`
   grava **e já dá o ID à placa** (se travar em `Connecting......`, segure **BOOT**); `make monitor`
   abre a serial. Com mais de uma placa conectada, use `PORT=/dev/cu.usbserial-XXXX`.
4. Na primeira placa confira o banner (`# fw=0.1.0 … role=beacon id=9F3C1A02`) e o evento
   `advertising` com o `packet=`; teste `id 9F3C1A0` e `id ZZZZZZZZ` (devem dar `ERR bad_id`) e veja se
   o **TrackerApp** do iPhone lista o ID (**Indoor → "Sou o observador" → Sinais próximos**). Só depois
   siga para as outras.
5. **Cada placa precisa de um ID diferente.** Use `make flash ID=9F3C1A01` (depois `…02`, `…03`): o
   comando grava, manda `id` e `reboot` pela serial e **confere no banner** que a placa passou a
   dizer `id=…` (mostra `OK: board <node> now advertises as 9F3C1A01`). Para uma placa que já está
   gravada, `make id ID=9F3C1A02`, sem regravar. Sem `ID=`, a placa sai com o `DEFAULT_BEACON_ID`
   do `config.h` (`9F3C1A02`), igual a todas as outras. O binário é o mesmo para todas: o ID fica
   na memória da placa (NVS), então não é preciso editar o `config.h` nem compilar de novo. Se o
   comando falhar (placa desligada, cabo só de carga, monitor aberto), ele diz o que conferir.
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

### Onde colocar as placas

O erro de distância por RSSI cresce com a distância (com 3 dB de ruído, cerca de 35% dela: 0,7 m a
2 m e 2,8 m a 8 m). Por isso a posição das placas pesa tanto quanto o código:

- **Em triângulo, nunca em linha reta.** Em linha a posição fica ambígua (espelhada em torno da
  linha) e o cálculo degenera. Com você **dentro** do triângulo os erros se compensam; fora dele,
  crescem.
- **Na mesma altura, a do celular** (cerca de 1 a 1,2 m do chão). O app calcula em 2D, mas a
  distância medida é 3D: uma placa a 2,5 m de altura, com você a 1 m dela no chão, "mede" cerca de
  2,7 m, um erro enorme perto da placa.
- **Perto da área de uso.** Quanto menor a distância máxima, mais leituras ficam na faixa em que o
  RSSI é informativo (abaixo de uns 5 m). Numa área de 6 × 5 m, as placas nos cantos mantêm qualquer
  ponto a 4 m ou menos da mais próxima; uma placa na sala ao lado quase não ajuda.
- **Segure o celular sempre do mesmo jeito.** O corpo bloqueia o sinal e o RSSI muda conforme você
  vira.
- Meça a posição de cada placa com trena a partir de uma origem fixa e digite as mesmas coordenadas
  no app.

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
#define API_HOST "tracker-app-ios.onrender.com"
#define API_KEY "a-mesma-chave-configurada-no-render"
```

Deixe `API_HOST` vazio (`""`) pra desligar o envio (o padrão do `config.example.h`) — por exemplo,
enquanto estiver testando só com a serial, sem querer gastar a cota do Render. `API_KEY` precisa
ser **exatamente** o mesmo valor da variável `API_KEY` do serviço da API no Render.

**Como funciona:**

- **Só a coletora liga o Wi-Fi.** Um beacon nunca liga, mesmo gravado com `ENABLE_WIFI 1`: o mesmo
  `config.h` serve para todas as placas, e o rádio do beacon fica só para o BLE (o Wi-Fi dividia o
  rádio e o pico de corrente dele disparava o brownout). Como o papel muda com `role` + `reboot`, o
  Wi-Fi acompanha: ao virar coletora, a placa liga o Wi-Fi no próximo boot. O `status` mostra o
  Wi-Fi em uso (`wifi=off` num beacon).
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
- Se a conexão falhar (rede não encontrada, senha recusada, conexão perdida), a placa tenta de novo
  a cada 10 segundos.

**Como conferir pelo monitor serial.** A placa avisa em linhas `E` quando o estado muda (não a cada
tentativa):

| Linha | Quer dizer |
|---|---|
| `E,…,info,wifi_connected,ip=192.168.0.23` | Conectou; o IP que a rede deu |
| `E,…,warn,wifi_no_network,…` | A rede não foi encontrada: nome errado em `WIFI_SSID`, ou é só 5 GHz (o ESP32 só tem 2,4 GHz) |
| `E,…,error,wifi_auth_failed,…` | A rede recusou a senha (`WIFI_PASSWORD`) |
| `E,…,warn,wifi_lost,…` | Estava conectada e caiu; vai tentar de novo |
| `E,…,info,upload_ok,http=201 rows=20` | O primeiro lote subiu (os seguintes que derem certo não geram linha) |
| `E,…,warn,upload_failed,http=401 …` | A API recusou a chave: confira `API_KEY` |
| `E,…,warn,upload_failed,http=-1 …` | Sem conexão com a API: confira `API_HOST` e o Wi-Fi |

Como a placa costuma conectar antes de você abrir o monitor, o mais fácil é o comando `status`, que
tem uma terceira linha com o estado da rede:

```
# status net wifi=connected ip=192.168.0.23 uploaded=40 failed=0 dropped=0
```

`wifi` é `off` (desligado no `config.h`), `connecting`, `connected`, `no_network`, `auth_failed` ou
`lost`; `uploaded` são as linhas que a API aceitou, `failed` os lotes recusados ou sem conexão, e
`dropped` as linhas que não couberam na fila (o envio não acompanhou a gravação). Ou aperte o botão
EN da placa com o monitor aberto para ver tudo desde o início.

## 8. Publicar no broker MQTT

Diferente do upload de fingerprint (capítulo 7, que só sobe em lote depois de uma gravação
terminada), o MQTT carrega a telemetria **contínua** do coletor — uma mensagem por anúncio
recebido, ociosa ou gravando, o mesmo dado das linhas `L`/`R` da serial. É o canal pensado pro
monitoramento ao vivo, não pro dataset rotulado de ML.

**Configuração** (`include/config.h`):

```c
#define ENABLE_WIFI 1
#define WIFI_SSID "sua-rede"
#define WIFI_PASSWORD "sua-senha"
#define ENABLE_MQTT 1
#define MQTT_HOST "192.168.0.10"   // IP do broker (Mosquitto local, Raspberry Pi, ou mosquitto do docker-compose da API)
#define MQTT_HOST_V6 ""            // opcional, ver "IPv6 com fallback" abaixo
#define MQTT_PORT 1883
#define MQTT_USER ""               // vazio = sem autenticação
#define MQTT_PASSWORD ""
#define TOPIC_PREFIX "tracker"
```

`ENABLE_MQTT` só tem efeito com `ENABLE_WIFI 1` também — sem Wi-Fi, não tem MQTT. Com `MQTT_USER`
vazio, conecta sem usuário/senha (ok pro Mosquitto local do `docker-compose.yml` da API, que aceita
conexão anônima; não use assim num broker exposto à internet).

**IPv6 com fallback para IPv4** (requisito de detecção de falha de infraestrutura): se
`MQTT_HOST_V6` for preenchido com o literal IPv6 do mesmo broker (ex.: `"2001:db8::10"`), cada
tentativa de conexão nova tenta IPv6 primeiro; se não conectar em 5 segundos, troca para
`MQTT_HOST` (IPv4) sozinho, sem reiniciar a placa. Deixar `MQTT_HOST_V6` vazio (padrão) pula essa
etapa e vai direto para IPv4, igual antes dessa opção existir. A troca de família gera eventos na
serial (`mqtt_dual_stack`, `mqtt_connected`, `mqtt_ipv6_fallback`) — é esse log que demonstra a
detecção de falha, já que o broker atual (Mosquitto local e o Render) não oferece IPv6 real para
testar a conexão de ponta a ponta; a lógica de timing do fallback tem teste automatizado
(`test/test_dual_stack_strategy`), mas o caminho IPv6 em si não foi validado em hardware real por
falta de rede IPv6 disponível.

**Como validar o caminho IPv6 de verdade** (precisa de uma rede com IPv6 real — Wi-Fi de casa ou
da faculdade, não dá pra fazer isso contra o Render, que não tem IPv6):

1. Confirme que a rede Wi-Fi tem IPv6, num computador conectado nela:
   ```
   ifconfig en0 | grep inet6
   ```
   Procure um endereço que **não** comece com `fe80` (esse é link-local, não serve) — algo como
   `2804:...`/`2800:...`. Se não aparecer nenhum, essa rede não oferece IPv6 e esse teste não é
   possível nela.
2. Suba o broker local nesse mesmo computador (dentro de `applications/api`):
   ```
   docker compose up
   ```
3. Pegue o IPv4 e o IPv6 desse computador (`ifconfig en0 | grep "inet "` e `grep inet6`) e coloque
   em `MQTT_HOST`/`MQTT_HOST_V6` no `config.h`, grave no ESP32, e ligue-o na mesma rede Wi-Fi.
4. Acompanhe a serial do ESP32: `mqtt_connected` dizendo "connected over IPv6" confirma que a
   conexão real aconteceu por IPv6; só cai pra `mqtt_ipv6_fallback` se o IPv6 falhar de verdade.
5. Esse teste grava no Postgres **local** do `docker compose`, não no banco do Render — é só pra
   provar que o fallback funciona, não atualiza o mapa/dashboard publicados.

**Tópico**: `<TOPIC_PREFIX>/<node_id>/sightings` — por exemplo, `tracker/A1B2C3/sightings`. A API
(`applications/api`) já tem um consumidor rodando (`app/mqtt_consumer.py`) que assina
`tracker/+/sightings` (o `+` casa qualquer placa) e grava cada mensagem no Postgres, do mesmo jeito
que `POST /sightings` grava.

**Formato da mensagem** (JSON, não é o formato CSV-ish da serial):

```json
{"observerID": "A1B2C3", "anchorID": "9F3C1A02", "rssi": -67}
```

Só esses três campos — a placa não tem relógio de parede confiável nem sabe quantos
`sampleCount`/`rssiStdDev` calcular sozinha, então a API usa o horário de recebimento no lugar de
`seenAt`.

**Testar rápido sem a placa** (simula o que o firmware manda):

```bash
# precisa do broker rodando: docker compose up mosquitto (dentro de applications/api)
pip install paho-mqtt
python3 -c "
import paho.mqtt.client as mqtt, json
c = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
c.connect('localhost', 1883)
c.publish('tracker/TESTE01/sightings', json.dumps({'observerID': 'TESTE01', 'anchorID': 'BEAC0001', 'rssi': -55}))
"
```

Depois confira se virou linha na tabela `sightings` do Postgres.

## 9. Solução de problemas

- **`pip`/`pio` com `CERTIFICATE_VERIFY_FAILED`:** faltaram as variáveis do capítulo 1.
- **O iPhone não vê o beacon:** confira o ID no `status`, deixe o app do iPhone na tela que lista os
  sinais próximos e aproxime os aparelhos. Se continuar sem aparecer, avise: existe uma variante do
  anúncio (nome na resposta de varredura) preparada para esse caso.
- **Placa não aparece em `pio device list`:** troque o cabo (precisa ser de dados) e confira o
  driver USB-serial da placa.
- **Monitor aberto, mas nada aparece:** a placa já escreveu a linha inicial antes de o monitor abrir.
  Digite `status` e Enter, ou aperte o botão EN para reiniciá-la.
- **`# status net wifi=connecting` que não muda:** a placa ainda não conseguiu falar com a rede. Se
  depois de uns 30 s aparecer `no_network` ou `auth_failed`, veja a tabela do capítulo 7. Redes com
  página de login (portal) não funcionam com o ESP32.
- **Mais itens:** completados na tarefa T067.
