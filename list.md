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

- [x] ~~Reconciliar o trabalho paralelo que ficou na `master`~~ — a `master` tinha 14 commits
  feitos direto na estrutura antiga (pré-monorepo: `android/`, `ios/`, `embedded/` soltos na
  raiz), com um port de Android quase idêntico a este (mesmo pacote/arquitetura) mas mais
  completo em alguns pontos: calibração de path-loss (`CalibrationRepository`), locais salvos
  (`SavedLocation`), e no iOS melhorias em `Multilateration`/`PathLossModel`/`PositioningEngine`
  + uma tela nova (`ObserverView`). Em vez de um merge direto (que duplicaria as pastas, já que
  os caminhos não coincidem mais), o conteúdo foi portado manualmente pra dentro de
  `applications/android`, `applications/ios` e `esp32-esp8266`, e o merge da `master` em si foi
  feito com `git merge -s ours` (registra a `master` como mesclada, pro PR não dar conflito,
  sem recriar as pastas antigas — o conteúdo que valia a pena já estava portado). Android:
  110 testes passando, `lintDebug` limpo. **Não portado** (porque não dava pra validar sem poder
  compilar o Xcode 15+ necessário neste ambiente): o diff grande de `PositioningTests.swift`
  (+693 linhas) e `FingerprintRecorderTests.swift` (+2) da `master` — os testes do iOS cobrindo
  calibração/`ObserverView` continuam só na versão antiga da `master`, que não existe mais na
  árvore depois do merge. Fica como pendência: alguém com Xcode 15+ revisar
  `git show master:ios/Tests/TrackerAppTests/PositioningTests.swift` e portar os testes que
  ainda fizerem sentido pra `applications/ios/Tests/TrackerAppTests/PositioningTests.swift`.

### Reestruturação do Android (plano)

Objetivo: deixar o app fácil de entender e manter, e pronto pra produção, aplicando os princípios
da Clean Architecture sem a cerimônia. Cada fase deixa o app funcionando e os testes passando.

**Regras:**

- **Regra de dependência:** domínio não importa Android, banco nem rede. Na fase 2 isso passa a ser
  garantido por módulos Gradle (`:core:positioning` é Kotlin/JVM puro).
- **Use case só onde há lógica.** Nada de use case que só repassa pro repositório: aí o ViewModel
  chama o repositório direto.
- **DTOs só em `data`.** Formato da API e do CSV/JSON de fingerprint não vaza pro domínio.
- **Organização por feature**, com camadas dentro de cada uma (não `data/domain/presentation`
  globais).

**Contratos que não mudam** (iOS, ESP32 e API dependem deles): protocolo BLE (UUID do serviço, ID no
nome local / manufacturer data), formato CSV/JSON das gravações, endpoints e campos da API.
Protegidos por testes de contrato (fase 0).

| Fase | Conteúdo |
|---|---|
| 0 | Rede de segurança (testes de contrato, ktlint, lint, CI, StrictMode) e bugs que afetam o dataset |
| 1 | Injeção de dependência (Hilt), instâncias únicas, repositórios com `Flow`, pacotes por feature |
| 2 | `:core:positioning` (JVM puro) + `PositioningEngine` + interface `PositionEstimator` (destrava ML e replay offline) |
| 3 | Gravação: armazenamento na camada `data`, upload por WorkManager lendo o arquivo, calibração nos metadados |
| 4 | Serviço em primeiro plano dono do scan e da gravação |
| 5 | Demais módulos, DataStore, navegação tipada, UI padronizada por feature, permissões por feature |
| 6 | Release: R8, assinatura, configuração por ambiente, relatório de falhas; autenticação por dispositivo (depende do backend) |

#### Fase 0 — rede de segurança e correções que afetam o dataset

- [x] ~~**Testes de contrato**~~ — feito: `app/src/test/java/.../contract/` + arquivos esperados em
  `app/src/test/resources/contracts/` (cabeçalho e linhas do CSV com escape de aspas, JSON de
  metadados, JSON enviado a `/sightings`, `/fixes` e `/fingerprints` com a configuração de produção
  do cliente, constantes do protocolo BLE). Chaves do JSON conferidas contra os `CodingKeys` do
  iOS; campos da API contra `applications/api/app/schemas.py`. **Achou um bug:** o JSON de
  metadados do Android saía **sem `schema_version`** (o kotlinx.serialization não grava valores
  padrão); corrigido com `encodeDefaults`. Gravações Android já feitas podem não ter o campo: o
  pipeline de ML deve ler a ausência como versão 1.
- [x] ~~**Formatação (Spotless + ktlint)**~~ — feito: configurado (`build.gradle.kts` da raiz do
  Android, `.editorconfig`), `spotlessCheck` no CI, código formatado num commit só de formatação.
  Estilo `intellij_idea`, 140 colunas, regras de assinatura desligadas (não juntar parâmetros numa
  linha). As regras ficam no Gradle (`editorConfigOverride`): o ktlint do Spotless ignorou o
  `.editorconfig`.
- [x] ~~**Android Lint** falhando o build~~ — feito: `warningsAsErrors` + `lint-baseline.xml` com os
  14 avisos e 1 dica atuais (recursos sem uso, etc.). Os avisos de "versão mais nova disponível"
  estão desligados: mudam sozinhos a cada lançamento de biblioteca (atualizar dependências é
  trabalho para Dependabot/Renovate).
- [x] ~~**CI**~~ — escrito (`.github/workflows/android.yml`, só para `applications/android/**`):
  `spotlessCheck lintDebug testDebugUnitTest assembleDebug`, JDK 21, `Secrets.kt` do `.example`.
  Ainda não rodou: só roda no GitHub, depois do push.
- [x] ~~**`BlueGuideApplication` + StrictMode**~~ — feito (debug, só log; `buildConfig = true`).
  **Falta:** rodar o app num aparelho e listar as violações no logcat (tag `StrictMode`).
- [x] ~~**`t_ms` do Android vem do scan**~~ — feito: `ScanResult.timestampNanos`, mesmo relógio do
  `elapsedRealtime()`, como o README documenta.
- [x] ~~**Gravador fora da thread principal**~~ — feito: todo o trabalho num despachante de uma
  thread só (`Dispatchers.IO.limitedParallelism(1)`), `flush` no máximo 1 vez por segundo,
  listagem de gravações no `start()`. O gravador é dono do próprio escopo, então o `stop()` no
  `onCleared` ainda descarta a gravação pela metade. **Falta:** conferir no aparelho (gravação de
  10 min sem aviso do StrictMode vindo do gravador).
- [x] ~~**Sem custo quadrático e upload em lotes**~~ — feito: `ArrayList` e lotes de 1.000 linhas, um
  depois do outro; a primeira falha para os demais e a mensagem diz quantas linhas foram.
- [x] ~~**Limpeza**~~ — feito: testes de exemplo do template removidos, `FakeBleScanner` do código
  principal apagado (não era usado: o teste do gravador tem o seu), notas de estudo do
  `DeviceListViewModel` tiradas.

Fora da fase 0: a chave da API dentro do APK (só resolve com autenticação por dispositivo no
backend, fase 6) e o scan duplicado (fase 1).

#### Fase 1 — injeção de dependência e organização por feature

- [x] ~~**Compatibilidade**~~ — confirmado: Hilt 2.60.1 + KSP 2.3.12 + `androidx.hilt` 1.4.0
  funcionam com o Kotlin embutido do AGP 9.4 e Kotlin 2.2.10 (o Hilt gerou o componente e as
  classes `Hilt_*` via KSP). O contêiner manual não é necessário.
- [x] ~~**Pacotes por feature**~~ — feito, só movimentação: `core/{model,ble,ble/android,sensors,
  sensors/android,dataset,positioning,network,network/http,data,data/prefs,ui,ui/theme}` e
  `feature/{positioning,fingerprint,find,ar}/{ui,domain,data}`. `MainActivity` e
  `BlueGuideApplication` ficam no pacote raiz (renomear a activity de entrada quebra atalhos já
  fixados na tela inicial). `core` não importa `feature`, nenhuma feature importa outra, e os
  pacotes puros de `core` não importam Android. O `Secrets.kt` foi para `core/network/http/`
  (Makefile, `.gitignore` e CI atualizados).
- [ ] **Base do Hilt:** `@HiltAndroidApp`, `@AndroidEntryPoint`, `hiltViewModel()`, qualificadores
  `@ApplicationScope` e `@IoDispatcher`.
- [ ] **Módulos de DI** (`app/di/`): BLE, rede (um `HttpClient`), dados, sensores, sistema
  (`MonotonicClock`, `DeviceInfo`, `LocalDeviceIdProvider`), ligados às interfaces com `@Binds`.
- [ ] **Um único scan compartilhado** (`SharedBleScanner`): erros viram eventos antes do `shareIn`;
  o fluxo observa permissão e estado do Bluetooth e reinicia o scan sozinho, com espera entre
  tentativas.
- [ ] **Repositórios com `Flow`** (beacons, calibração, locais salvos), ainda sobre
  SharedPreferences. Rótulos A–Z viram `AddAnchorUseCase`. Some o `refreshAnchors()`.
- [ ] **ViewModels sem `Context`** (`@HiltViewModel`); classes que recebem `scope` no construtor
  ganham fábricas com injeção assistida (transição até a fase 4).
- [ ] **Testes:** ViewModels com fakes; scan compartilhado (dois consumidores, um scan; falha vira
  evento e reinicia).

Pronto quando: nenhum ViewModel recebe `Context`; uma instância de scanner, `HttpClient` e cada
repositório; beacon cadastrado no Positioning aparece no Fingerprint sem recarregar; testes e CI
passando.

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

- [x] ~~Reconectar os serviços do Render pro repositório `tracker-app-ios`~~ — feito: banco
  Postgres e os dois serviços web recriados do zero (o serviço antigo tinha ficado configurado
  como buildpack nativo Python em vez de usar o `Dockerfile` do projeto, e não dava pra trocar
  isso depois de criado). API em `https://tracker-app-ios.onrender.com` (`/health` → `200
  {"status":"ok"}`), Grafana em `https://tracker-app-ios-1.onrender.com` (`/api/health` → `200`,
  datasource do Postgres OK). Detalhes em `applications/api/README.md`, seção "Deploying to
  Render".
- [ ] Decidir se o `applications/web` também vai pro Render, ou só roda localmente por enquanto.
- [x] ~~Trocar a convenção de API key inline por um arquivo de secrets gitignorado~~ — feito nos
  3 lugares: `Secrets.swift`/`Secrets.kt` (novos, gitignorados, com `.example` commitado como
  modelo) e `esp32-esp8266/include/config.h` (já era gitignorado). Falta só colar o valor real
  da `API_KEY` do Render nesses 3 arquivos locais — isso é responsabilidade de cada pessoa que
  for rodar o projeto, não vai pro Git.

## Documentação / Entrega (critérios de nota do PDF de Embarcados)

- [ ] **Relatório técnico** (modelo CSBC/ABNT2) — `docs/`, zero progresso até agora. Maior peso
  de nota (20 pts) sem nenhum trabalho feito ainda.
- [ ] **Diagramas de circuito** (`schematics/`) — mesmo que seja só o ESP32 e a antena, falta
  documentar.
- [ ] **Declarar o tema oficial do projeto** no relatório (provavelmente "Computação" ou
  "Tecnologias Assistivas", dos 5 temas permitidos).
- [ ] **Esclarecer com os professores se IPv6 é obrigatório ou só bônus** — aparece nos dois
  lugares no PDF, ambíguo. A implementação (fallback IPv6→IPv4 no MQTT, ver item do ESP32 acima)
  já existe; falta confirmar se cobre o requisito e se o fato de não ter rede IPv6 real pra
  demonstrar o caminho feliz é um problema pra nota.
- [ ] Histórico de commits do backend/frontend não foi preservado na migração pro monorepo —
  decidir se isso importa pro critério "GitHub (Organização)" (commits colaborativos).
