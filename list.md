# O que falta fazer

Lista de trabalho, organizada por área. Atualizar conforme os itens forem resolvidos — não é
parte do relatório final (isso vai em `docs/`), é só controle interno do time.

## Frontend (`applications/web`)

- [ ] **`src/lib/floor.ts` com posições reais** — hoje tem coordenadas de exemplo (`FLOOR`,
  `STATIONS`). Precisa medir o ambiente de verdade (posição de cada âncora ESP32 em metros) e
  editar o arquivo.
- [ ] **Visualizar inferências do modelo de ML no dashboard** — depende do modelo existir
  primeiro (ver seção "Modelo de ML / MLOps" abaixo). O requisito de ML pede isso explicitamente
  no item "Desenvolvimento de Dashboard".
- [ ] Nenhum dashboard próprio de "status do pipeline" (Airflow rodando, quando foi o último
  treino, etc.) — também depende do pipeline existir.

## Backend (`applications/api`)

- [x] ~~MQTT de verdade~~ — feito: Mosquitto no `docker-compose.yml`, ESP32 publica telemetria
  contínua (`esp32-esp8266/src/hal/esp_mqtt_publisher.*`, tópico `tracker/<node>/sightings`),
  backend consome e persiste (`app/mqtt_consumer.py`). Validado de ponta a ponta com broker real.
- [x] ~~Dashboards do Grafana~~ — feito: `grafana/provisioning/dashboards/json/positioning-overview.json`,
  4 painéis (hora de pico, densidade, ocupação, tempo de permanência), validados contra Postgres
  real via `docker compose up` + API do Grafana.
- [ ] **Sem testes automatizados** — toda validação até agora foi manual (`curl` contra Postgres
  real). Funciona, mas não tem suíte de testes no repositório.
- [ ] **Sem migrations** (Alembic) — tabelas são criadas com `Base.metadata.create_all()` no
  startup. Baixa prioridade enquanto o schema ainda está mudando.
- [ ] Auth é uma única chave compartilhada (`API_KEY`), não por dispositivo — aceitável pra um
  projeto de faculdade, mas não é o que uma implantação real usaria.

## Modelo de ML / MLOps (ver tabela de requisitos da disciplina)

Nada disso foi começado ainda — é a maior pendência geral do projeto:

- [ ] **Coletar o dataset real** (bootstrap dataset) — precisa andar fisicamente pelo ambiente
  com as âncoras instaladas, gravando fingerprints rotulados. Sem isso, nada abaixo tem o que
  processar.
- [ ] Notebook de Análise Exploratória (EDA) sobre o dataset coletado.
- [ ] Pipeline automatizado com **Apache Airflow** (ingestão → preparo → disponibilização).
- [ ] Modelo baseline (k-NN, regressão linear/logística, ou árvore de decisão) + registrar no
  **MLflow**.
- [ ] Treinar e comparar **pelo menos 3 modelos distintos**, com métricas de regressão
  (MAE/RMSE/R², não confusion matrix/ROC — o problema é prever posição x,y, não classificar) e
  gráficos de resíduos.
- [ ] Otimização de hiperparâmetros (Grid/Random Search ou Optuna).
- [ ] Validação cruzada (k-fold).
- [ ] Integrar o modelo treinado ao pipeline (inferência automática sobre dados novos).
- [ ] Expandir o `docker-compose.yml` com os serviços que faltam: Airflow, MLflow (o broker MQTT já
  está lá).

## Android (`applications/android`)

App "BlueGuide" (pacote `br.org.cesar.blueguide`), antes um repositório separado, trazido pro
monorepo e levado em direção à paridade com o iOS. SDK instalado localmente via
`brew install --cask android-commandlinetools` (sem Android Studio) só pra poder compilar/testar.

- [x] ~~Mover pro monorepo~~ — feito, em `applications/android/`. `./gradlew testDebugUnitTest
  lintDebug` passa limpo.
- [x] ~~Generalizar o posicionamento pra N âncoras, como o iOS~~ — feito:
  `Multilateration.kt` (mínimos quadrados, substitui o `Trilateration.kt` fixo de 3) +
  `AnchorStore`/`AnchorRepository` persistidos (substitui o `AnchorLayout` de triângulo
  automático) + editor de âncora na UI (toque num dispositivo da lista). 24 testes JVM passando.
- [x] ~~Integração com o backend~~ — feito: `PositioningApiClient` (Ktor + kotlinx.serialization)
  manda `POST /sightings`/`POST /fixes` com `X-API-Key`, mesma API do iOS/ESP32. Upload
  periódico (1s) ligado no `DeviceListViewModel` em modo Observer. `uploadFingerprints()` já
  existe no cliente, esperando a Fase 3 (gravação) pra ser chamado. Testado com Ktor
  `MockEngine` (sem bater na rede real) — 3 testes novos, 22 no total passando.
- [x] ~~Gravação de fingerprint pra ML~~ — feito: `FingerprintRecorder` (CSV + metadata JSON,
  mesmo schema do iOS, `platform: "Android"`) + leitores de sensor
  (`AndroidOrientationReader`/`AndroidAltitudeReader` via `SensorManager`,
  `AndroidMotionActivityReader` via Google Play Services `ActivityRecognitionClient`, precisa
  da permissão `ACTIVITY_RECOGNITION`) + tela nova (`FingerprintCollectorScreen`, aba separada
  no topo do app). Upload automático via `PositioningApiClient.upload()`. 7 testes novos (29 no
  total), `lintDebug` limpo. Convenções de orientação (eixos do `getOrientation()` do Android,
  diferentes do `CMDeviceMotion` do iOS) e confiança de atividade (0-100 do Android vs 0/1/2 do
  iOS) documentadas no metadata, não convertidas — ver comentários em
  `AndroidOrientationReader.kt`/`MotionActivityReading.kt`.
  ~~**Gap conhecido**: a gravação não sobrevive a uma rotação de tela~~ — corrigido:
  `FingerprintViewModel` agora encapsula o `FingerprintRecorder` (mesma garantia que o
  `DeviceListViewModel` já dava pra aba de Posicionamento).
- [x] ~~Ranging UWB~~ ("achar este celular") — feito: `androidx.core.uwb` 1.0.0 (API real
  conferida via `javap` no `.aar` baixado, não só lembrada — mesmo cuidado que o
  `espMqttClient` do ESP32 teve). Handshake BLE próprio (`BleUwbHandshakeChannel`, serviço
  GATT separado do `BlueGuideProtocol`) decide quem é controller/controlee comparando IDs
  (igual à regra "menor ID inicia" do `BLEHandshake` do iOS) e troca
  `UwbHandshakePayload` (endereço + canal/sessão pro lado controller). `UwbProximityTracker`
  junta os dois, com retomada automática quando o par desconecta — equivalente ao
  `ProximityTracker` do iOS. Nova aba "Find" no app. 16 testes novos (45 no total) cobrindo a
  lógica pura (comparação de ID, codificação do payload, orquestração com fakes);
  `lintDebug` limpo.
  **Não verificado em hardware real** — precisa de dois aparelhos com chip UWB (Pixel 6
  Pro+, Galaxy S21 Ultra+ ou equivalente) que não existem neste ambiente; só dá pra garantir
  que compila e que a lógica de decisão tem teste. UWB é Android-Android só — o protocolo do
  iOS (NearbyInteraction) e o do Android (Jetpack UWB) não têm como interoperar aqui, mesmo
  que o recurso "achar o celular" exista nos dois lados.
- [x] ~~Tracking de posição via câmera~~ (equivalente ao ARKit) — feito: ARCore 1.57.0 (API
  conferida via `javap` no `.aar` baixado, igual UWB/ESP32). `AndroidArPositionTracker`
  resolve a posição a partir da pose da câmera (`session.update()` — ARCore é "pull", sem
  callback de delegate como o ARKit, então faz polling a ~30 Hz); o espaço de câmera do
  ARCore segue a mesma convenção OpenGL do ARKit (X direita, Y cima, -Z pra frente), então o
  mapeamento `x = translation.x, y = -translation.z` do iOS vale sem alteração. Nova aba
  "AR" no app, demo isolada (não plugada no fix BLE das outras abas — o equivalente no iOS,
  `ARAnchorCaptureView`/`NavigateToLocationView`, é um fluxo de locais salvos separado que
  não foi replicado, só o tracker em si). `lintDebug` limpo (precisou declarar
  `android.hardware.camera` como opcional, senão o Play Store escondia o app em Chromebooks
  sem câmera). 45 testes no total (sem teste novo isolado pra esse tracker — a lógica é só
  mapeamento de eixos e polling, pouco a testar sem o ARCore real rodando).
  **Não verificado em hardware real** — precisa de um aparelho com câmera e suporte a ARCore;
  só dá pra garantir que compila.
- [x] ~~Navegação de topo "de verdade"~~ (Navigation-Compose 2.10.2) — feito: `NavHost` com
  uma rota por aba (Posicionamento/Fingerprint/Find/AR), troca de aba via
  `navController.navigate` com `popUpTo`/`launchSingleTop`/`restoreState` (comportamento de
  aba, não pilha de telas empilhando). Precisou subir `compileSdk` de 36.1 pra 37.2 (o
  `navigation-compose` trouxe uma versão mais nova de `lifecycle-viewmodel-compose`/
  `lifecycle-runtime-compose` que exige isso) — `targetSdk` continua 36, só o nível de
  compilação mudou. 45 testes, `lintDebug` limpo.

**Com isso, as 6 fases do plano original de port do Android estão completas** (mover pro
monorepo, generalizar posicionamento, backend, fingerprint/ML, UWB, ARCore, navegação).
O que resta é só o que já estava listado como gap conhecido em cada item acima — hardware
real pra validar UWB/ARCore, e persistir fingerprint recorder durante configuração — e
qualquer melhoria de UI/paridade adicional que não estava no escopo original.

## ESP32 / Embarcados (`esp32-esp8266`)

- [ ] **Usar pelo menos 1-2 sensores/atuadores do kit físico** (LED RGB, sensor ultrassônico,
  infravermelho, potenciômetro) — o projeto hoje só usa o rádio BLE, e o critério de
  "Prototipagem" do Check Point avalia uso de sensores/atuadores e organização do circuito.
- [x] ~~Publicar via MQTT~~ — feito (ver item do backend acima).
- [x] ~~IPv6 com fallback para IPv4 (detecção de falha de infra) no publisher MQTT~~ — feito:
  `lib/domain/dual_stack_strategy.h` (lógica pura, 5/5 testes) + `src/hal/esp_mqtt_publisher.h/.cpp`
  (tenta IPv6 primeiro quando `MQTT_HOST_V6` está preenchido, cai para IPv4 sozinho após 5s, loga
  `mqtt_dual_stack`/`mqtt_connected`/`mqtt_ipv6_fallback`). Build real (`esp32dev`) passou
  (RAM 25.8%, Flash 51.3%). Documentado no `USAGE.md` cap. 8. O caminho IPv6 em si não foi validado
  em hardware real — nem o Mosquitto local nem o Render oferecem IPv6 para testar ponta a ponta;
  só a lógica de timing do fallback tem teste automatizado.
  - [ ] **Falta rodar a validação de verdade**: passo a passo em `esp32-esp8266/USAGE.md`
    cap. 8, seção "Como validar o caminho IPv6 de verdade" — precisa de uma rede Wi-Fi com
    IPv6 real (de casa/faculdade, não o Render) e o `docker compose up` local
    (`applications/api`) rodando no mesmo computador, servindo de broker. Grava no Postgres
    local, não no do Render — só prova que o fallback funciona, não é o banco de produção.

## Infraestrutura / Deploy

- [ ] **Reconectar os serviços do Render** pro repositório `tracker-app-ios` (hoje apontam pro
  `api_tracker_app` antigo). Passo a passo (serviço por serviço, no painel do Render):
  1. **Postgres** — não precisa tocar. É um banco gerenciado pelo Render, não está conectado a
     nenhum repositório do GitHub — continua funcionando igual, independente da migração pro
     monorepo. Só guarde os dados de conexão da página dele (host, porta, usuário, senha,
     `Internal Database URL`), vai precisar deles no passo 2.
  2. **API** (serviço web existente) — abrir o serviço → **Settings** → procurar
     "Repository"/"Build & Deploy": trocar o repositório conectado de `api_tracker_app` pro
     novo `tracker-app-ios`, e setar **Root Directory** = `applications/api` (é isso que diz
     pro Render onde está o `Dockerfile` agora que ele não está mais na raiz do repo). Se o
     painel não deixar trocar o repositório de um serviço existente, apagar e criar de novo
     (New → Web Service → conectar `tracker-app-ios`, Root Directory `applications/api`).
     Variáveis de ambiente a recriar: `DATABASE_URL` (a Internal Database URL do passo 1,
     trocando `postgresql://` por `postgresql+psycopg://`), `API_KEY` (valor novo, só precisa
     ser igual ao que for colocado no iOS/ESP32 — ver item abaixo).
  3. **Grafana** (serviço web existente) — mesma troca de repositório, mas Root Directory =
     `applications/api/grafana`. Variáveis: `POSTGRES_HOST`/`POSTGRES_PORT`/`POSTGRES_DB`/
     `POSTGRES_USER`/`POSTGRES_PASSWORD` (do Postgres do passo 1), `POSTGRES_SSLMODE=require`,
     `GF_SECURITY_ADMIN_USER`/`GF_SECURITY_ADMIN_PASSWORD` (não deixar `admin`/`admin` — esse
     Grafana fica acessível por qualquer pessoa que achar a URL).
  4. Depois do deploy dos dois, confirmar: `GET https://<api>.onrender.com/health` responde, e
     o Grafana abre com o datasource do Postgres já provisionado (painel de hora de
     pico/densidade/ocupação/permanência aparecendo, mesmo vazio).
  Detalhes completos (incluindo o histórico de troca Railway→Render) em
  `applications/api/README.md`, seção "Deploying to Render".
- [ ] Decidir se o `applications/web` também vai pro Render, ou só roda localmente por enquanto.
- [ ] Gerar/colar a `API_KEY` real nos 3 lugares (Render, `PositioningAPIClient.swift`,
  `esp32-esp8266/include/config.h`) — anotado antes, ainda pendente.

## Documentação / Entrega (critérios de nota do PDF de Embarcados)

- [x] ~~**Relatório técnico**~~ (modelo CSBC/SBC) — feito: `docs/relatorio/relatorio.tex` + PDF
  compilado (introdução, fundamentação, metodologia, resultados, conclusão, referências, códigos
  em apêndice lidos direto do repo). **Falta o grupo preencher os trechos em vermelho**
  (`\pendente{...}`): nomes dos integrantes, medições de precisão/taxa, validação IPv6 real e
  capturas de tela dos painéis. Ver `docs/README.md`.
- [x] ~~**Diagramas de circuito**~~ — feito: `schematics/esp32-node/` (diagrama em PDF/PNG a partir
  de TikZ + `diagram.json` do Wokwi). Atualizar se algum sensor do kit for adicionado.
- [x] ~~**Declarar o tema oficial**~~ — declarado como **Computação** no relatório (macro `\tema`,
  uma linha pra trocar se o grupo preferir "Tecnologias Assistivas").
- [ ] **Esclarecer com os professores se IPv6 é obrigatório ou só bônus** — aparece nos dois
  lugares no PDF, ambíguo. A implementação (fallback IPv6→IPv4 no MQTT, ver item do ESP32 acima)
  já existe; falta confirmar se cobre o requisito e se o fato de não ter rede IPv6 real pra
  demonstrar o caminho feliz é um problema pra nota.
- [ ] Histórico de commits do backend/frontend não foi preservado na migração pro monorepo.
  **Decisão: importa** (critério "commits colaborativos" vale 10 pts, e quem fez API/web/Android
  hoje não aparece no histórico). Recuperar sem reescrever nada do `main`, enxertando o histórico
  antigo com um merge que não muda arquivos (repetir pro `btloc` → `applications/android`):
  ```bash
  git clone https://github.com/<dono>/api_tracker_app /tmp/old-api && cd /tmp/old-api
  pip install git-filter-repo && git filter-repo --to-subdirectory-filter applications/api
  # (separar web: rodar de novo num clone com --path front --path-rename front:applications/web, se for o caso)
  cd <tracker-app-ios> && git remote add old-api /tmp/old-api && git fetch old-api
  git merge -s ours --allow-unrelated-histories old-api/main -m "Import api_tracker_app history"
  ```
  `-s ours` mantém os arquivos atuais intactos; só os commits antigos (com os autores) entram no
  histórico e no gráfico de contribuidores. Fazer num branch + PR.
