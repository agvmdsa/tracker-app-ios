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
- [x] ~~**Base do Hilt**~~ — feito: `@HiltAndroidApp`, `@AndroidEntryPoint`, `hiltViewModel()` (de
  `androidx.hilt:hilt-lifecycle-viewmodel-compose`), qualificadores `@IoDispatcher` e
  `@RecordingsDirectory` (`di/Qualifiers.kt`). O `@ApplicationScope` entra com o scan compartilhado.
- [x] ~~**Módulos de DI**~~ — feito, em `di/` (pacote raiz, a raiz de composição): `SystemModule`
  (`MonotonicClock`, `DeviceInfo` em `core/system`, despachante de I/O), `BleModule`, `DataModule`
  (um `AnchorStore` e um `SavedLocationStore` para o app todo), `NetworkModule` (um
  `PositioningApiClient`, um `HttpClient`), `SensorsModule`, `FeatureModule`. O domínio continua
  sem anotações de DI: classes puras são montadas com `@Provides`; só as implementações Android
  têm `@Inject`.
- [x] ~~**Um único scan compartilhado**~~ — feito: `core/ble/SharedBleScanner` (Kotlin puro), um
  scan do rádio para o Positioning e o gravador, `shareIn(@ApplicationScope, WhileSubscribed(5 s))`
  — trocar de modo rápido não reinicia o hardware. Falhas não chegam a quem escuta: o scan
  recomeça sozinho com espera crescente (1, 2, 5, 10, 30 s; volta à primeira quando chegam
  leituras), o que também o inicia quando a permissão é concedida depois — antes, a aba
  Fingerprint ficava sem scan até ser recriada. `status` (`Idle`/`Scanning`/`Interrupted`) diz
  por quê. **Diferença do plano:** não observa o Bluetooth sendo desligado no meio do scan (pede
  um `BroadcastReceiver`; fica para a fase 4, com o serviço). **Falta:** mostrar o `status` na
  tela (`TODO(3c)` no `DeviceListViewModel`).
- [x] ~~**Repositórios com `Flow`**~~ — feito: `AnchorStore`, `CalibrationStore` e
  `SavedLocationStore` expõem `StateFlow` e são a fonte única de verdade (o `DeviceListViewModel`
  só mostra o que eles têm; a calibração entra na tela por uma intent só, `CalibrationChanged`).
  Os três usam `core/data/PersistedValue` (cópia em memória, leitura única do disco, atualização
  atômica com mutex). As interfaces de persistência viraram `suspend` e as implementações em
  SharedPreferences fazem o I/O no despachante injetado — mesmas chaves, então o que já está
  salvo nos aparelhos continua valendo. Rótulos A–Z viraram `AddAnchorUseCase` (escolhidos dentro
  da atualização do store). Saiu o `refreshAnchors()`.
- [x] ~~**ViewModels sem `Context`**~~ — feito (`@HiltViewModel`). As classes que recebem `scope` no
  construtor (UWB, AR) são criadas por fábricas simples (`create(scope)`: interface no domínio da
  feature, implementação em `data`) em vez de injeção assistida — o mesmo efeito com menos
  mágica. O `FingerprintViewModel` usa injeção assistida só para o id do aparelho (como `String`:
  o código gerado pelo Dagger não lida com value classes do Kotlin).
- [x] ~~**Testes**~~ — feito: `DeviceListViewModel` com fakes (o ciclo de 1 s que calcula e envia
  leituras e posição, com relógio e despachante de teste); `SharedBleScannerTest` (dois
  consumidores, um scan; falha vira status e o scan recomeça com espera crescente; o rádio para
  só depois do tempo de espera sem ninguém escutando).

Pronto quando: nenhum ViewModel recebe `Context`; uma instância de scanner, `HttpClient` e cada
repositório; beacon cadastrado no Positioning aparece no Fingerprint sem recarregar; testes e CI
passando. **Fase 1 concluída** (136 testes JVM, lint e build passando localmente); falta só rodar
no aparelho e o CI no GitHub, que roda depois do push.

#### Fase 2 — núcleo de posicionamento em Kotlin puro

- [x] ~~**Módulos `:core:model` e `:core:positioning`**~~ — feito: Kotlin/JVM, sem Android (a regra de
  dependência passa a ser garantida pelo build). `:core:model` tem `Point2D`, `DeviceId`, `Anchor`,
  `BleDevice`, `DeviceSighting`; `:core:positioning` tem `PathLossModel`, `RssiWindow`,
  `Multilateration`, `MotionLimit`. Mesmos pacotes, então nenhum import mudou; os testes foram
  junto (os de `DecimalInput`, que é da UI, ficaram no app como `DecimalInputTest`). CI e
  `make test` rodam também o `test` dos módulos. A configuração dos dois build files é repetida
  (poucas linhas); um plugin de convenção (`build-logic`) fica para a fase 5, com mais módulos.
- [x] ~~**`PositionEstimator`**~~ — feito: `fun interface` que coloca o celular a partir das
  distâncias de um refresh; `Multilateration` a implementa e é a ligada pelo Hilt
  (`di/PositioningModule`, uma linha). É aqui que entra um modelo treinado (correção do erro da
  multilateração, ou fingerprinting: cada `AnchorRange` traz o RSSI mediano além da distância).
  Cada chamada é independente; o limite de velocidade é aplicado depois, pelo motor.
- [x] ~~**`PositioningEngine`**~~ — feito, em `:core:positioning`: janelas de RSSI → distâncias e
  estatísticas → estimador → limite de velocidade, com `record`, `refresh`, `rangeAgain` (mesmo
  instante, nova calibração) e `reset` — a contraparte do `PositioningEngine` do iOS, sem scanner
  nem timer. `PositioningSetup` (âncoras, RSSI no centro/encostado, velocidade) diz com o que ele
  calcula. O ViewModel é o dono: entrega cada leitura, roda o ciclo de 1 s, recalcula na hora
  quando o setup muda e esquece tudo ao sair do modo observador. O reducer só guarda o que o motor
  diz (`PositioningUpdated`); saíram do estado da tela as janelas, a última posição mostrada e as
  estatísticas. Os testes de posicionamento do reducer foram para `PositioningEngineTest` com os
  mesmos números, mais o estimador plugável, `reset` e troca de âncoras; os do ViewModel conferem
  o recálculo na hora e o esquecimento (os dois pegam a falha quando a linha some do ViewModel).
  **Diferença de antes:** trocar as âncoras também recalcula na hora (antes só a calibração).
- [x] ~~**Replay**~~ — feito: `PositioningReplay.replay(leituras, setup, estimador)` passa as leituras
  gravadas (`RecordedSighting`: `beacon`, `rssi`, `t_ms` de cada linha) pelo mesmo motor, com um
  refresh a cada 1 s desde o início, cada um depois das leituras ouvidas até ele. As leituras vão
  na ordem das linhas (o `t_ms` pode voltar alguns ms, porque o scan entrega em rajadas); uma
  leitura com data anterior a um refresh, mas que chegou depois dele, entra no seguinte, como
  aconteceria ao vivo. Teste no ViewModel: 8 s de leituras entregues ao vivo e passadas pelo replay
  dão as mesmas distâncias e as mesmas posições a cada segundo, com o limite de velocidade fazendo
  diferença. O teste falha se o replay adiantar o refresh em meio segundo, ou se registrar leituras
  até 200 ms depois dele.

Fora da fase 2: o programa que lê os `.csv`/`.json` gravados e escreve a posição e o erro de
cada segundo (o dataset para o ML). Fica para a fase 3, junto com o formato das gravações e a
calibração nos metadados — sem ela, cada replay teria que receber a calibração à mão.

Pronto quando: `:core:positioning` não depende de Android; o ViewModel não calcula posição; trocar
o estimador é trocar uma ligação do Hilt; o replay reproduz o app; testes e CI passando. **Fase 2
concluída** (151 testes JVM: 94 no app, 57 em `:core:positioning`; lint e build passando
localmente); falta rodar no aparelho (a posição na tela deve se comportar como antes) e o CI no
GitHub.

#### Fase 3 — gravações: formato, calibração, armazenamento, upload e replay

- [x] ~~**Formato num módulo puro (`:core:dataset`)**~~ — feito: `FingerprintLabel`,
  `FingerprintRecord` (só dados, sem saber de CSV), `FingerprintCsv` (escreve e — novo — lê as
  linhas, uma a uma, recusando com o número da linha o que não for uma gravação) e
  `FingerprintRecordingMetadata` (escreve e lê; sem `schema_version` é a versão 1, chaves
  desconhecidas são ignoradas). As amostras dos sensores foram para `:core:model`. Os testes de
  contrato dos arquivos vieram junto e agora também leem os arquivos esperados de volta; os valores
  de que eles foram escritos (`ContractSamples`) ficam em `testFixtures`, usados também pelo teste
  de contrato da API no app.
- [x] ~~**Calibração nos metadados**~~ — feito: `schema_version` 2, com o objeto `positioning`
  (`center_rssi`, `touch_rssi`, `max_speed_kmh` — `null` com o limite desligado —,
  `reference_rssi`, `path_loss_exponent`, `uses_touch_rssi`, `rssi_window_ms`,
  `refresh_interval_ms`). É a calibração do começo da gravação, junto com as posições dos beacons
  que já iam; `metadata.positioningSetup` devolve o `PositioningSetup` para o replay. Um arquivo
  sem o campo é da versão 1 (há um esperado da versão 1 nos testes). O gravador recebe o setup
  da mesma conversão que a tela de Positioning usa (`CalibrationSettings.positioningSetup`), e um
  teste garante que as duas dão o mesmo setup. **Falta no iOS:** ele continua escrevendo a versão
  1, sem `positioning`.
- [x] ~~**Armazenamento na camada `data`**~~ — feito: `FingerprintRecordingStore` (domínio: listar,
  criar, apagar; `RecordingWriter` acrescenta linhas, descarrega, termina ou descarta) e
  `CsvFingerprintRecordingStore` (em `feature/fingerprint/data`: nomes dos arquivos, CSV, JSON e as
  descrições das convenções dos sensores Android, que são da plataforma). O gravador ficou só com o
  que gravar e quando: duração, contagens, quando descarregar, o estado. As gravações são
  `StoredRecording` (o CSV e, se terminou, o JSON), com `isComplete` e os arquivos a exportar
  calculados na listagem, na thread do gravador — **corrige** a tela, que fazia `File.exists()`
  na thread principal para cada item da lista. O único `java.io` que sobra no domínio é o `File`
  dentro de `StoredRecording`, que é o que o compartilhamento precisa entregar.
- [x] ~~**Upload por WorkManager**~~ — feito: terminada a gravação, o gravador a entrega a um
  `FingerprintUploadScheduler` (domínio); `WorkManagerFingerprintUploadScheduler` cria um trabalho
  único por gravação, que só roda com rede e tenta de novo com espera crescente a partir de 30 s
  (até 10 tentativas, umas 8 h; o tempo sem rede não conta). O `FingerprintUploadWorker` é fino: o
  envio é `RecordingUpload`, Kotlin puro e testado na JVM, que lê o CSV do disco em lotes de 1.000
  e, depois de cada lote aceito, anota quantas linhas a API já tem (em `noBackupFilesDir`, escrita
  inteira ou nada); uma nova tentativa continua dali, então nenhuma linha vai duas vezes. Erro de
  rede, 408, 429 e 5xx tentam de novo; outros 4xx, arquivo apagado ou ilegível desistem. A lista da
  aba mostra o envio de cada gravação (enviando, tentativa n, enviada com N linhas, não enviada e
  por quê, com "Retry"); apagar uma gravação cancela o envio. Hilt monta o Worker (`hilt-work`):
  a `BlueGuideApplication` dá a configuração ao WorkManager e o manifest tira a inicialização
  automática dele. O lint pegou o "%d rows" sem plural. **Diferenças de antes:** a API recebe os
  valores com a precisão do CSV (orientação com 4 casas etc.), os mesmos do dataset; e um envio
  que falha aparece na gravação, não mais como mensagem de erro no topo. **Falta:** o Worker e o
  agendador (Android) não têm teste automático (precisariam de Robolectric ou de um aparelho);
  conferir no aparelho gravando em modo avião e religando a rede. **Limite:** se a API gravar um
  lote e a resposta se perder no caminho, a nova tentativa manda esse lote de novo; só uma chave
  de idempotência no backend resolve (fase 6, com a autenticação por dispositivo).
- [x] ~~**Programa de replay**~~ — feito (antes do armazenamento e do upload: só depende dos
  módulos puros e é o que a pesquisa precisa primeiro): `:tools:replay`, rodado com
  `make android-replay ARGS="<pasta ou .csv>"` na raiz (ou `make replay` em `applications/android`).
  Lê as gravações (`.csv` + `.json`; um `.csv` sem `.json` foi interrompido e fica de fora, como no
  app), passa cada uma pelo `PositioningReplay` do início ao fim dela e escreve um CSV com uma linha
  por segundo: `recording, session, point, true_x, true_y, t_ms, elapsed_ms, x, y, error_m,
  uncertainty_m, anchors_ranged` e, para cada beacon, `rssi_<id>` (mediana) e `distance_<id>`. Na
  saída de erro, o resumo de cada gravação (segundos, com posição, erro médio, p95, máximo) e de
  todas. Usa a calibração guardada na gravação; `--center-rssi`, `--touch-rssi`,
  `--max-speed-kmh` e `--no-speed-limit` trocam por outra (para testar calibrações, ou dar uma
  às gravações da versão 1, que sem isso usam os padrões do app com um aviso). Lê uma gravação
  por vez, então uma pasta grande não enche a memória. README do Android atualizado: a seção
  da estrutura ainda descrevia os pacotes de antes da fase 1.

Pronto quando: o domínio da gravação não conhece CSV, JSON nem arquivos; o upload sobrevive ao app
fechado sem duplicar linhas; uma gravação nova pode ser reprocessada exatamente como o app
calcularia; testes e CI passando. **Fase 3 concluída** (182 testes JVM: 100 no app, 57 em
`:core:positioning`, 17 em `:core:dataset`, 8 em `:tools:replay`; lint e build passando
localmente); falta conferir no aparelho (gravação, envio sem rede e com rede, a lista) e o CI no
GitHub.

#### Preparação para reescrever o cálculo de posição

Antes de ler o cálculo inteiro e reescrever boa parte dele: deixar fácil de ler, de trocar e de
provar que a versão nova é melhor. Sem mudar o comportamento.

- [x] ~~**Organização para a leitura**~~ — feito: `RssiWindow` em arquivo próprio (estava em
  `PathLossModel.kt`); `AnchorRange` e `ObserverFix`, usados pelo motor, pela tela e pela API, saíram
  de `Multilateration.kt` para `PositionEstimator.kt`, ao lado do contrato que os usa. Um teste por
  arquivo (`MultilaterationTest`, `PathLossModelTest`, `RssiWindowTest`, `MotionLimitTest`), no lugar
  de `PositioningTest` e `CalibratedPositioningTest`; dois testes que estavam repetidos nos dois
  ficaram uma vez só. `core/positioning/README.md`: o pipeline em ordem de leitura, com o arquivo
  de cada etapa, e os acoplamentos que não aparecem nos tipos.
- [x] ~~**Calibração num lugar só**~~ — feito: o `DeviceListState` guarda as `CalibrationSettings`
  como foram salvas, e os valores que a tela mostra e o setup do motor saem delas
  (`centerRssiOrDefault` etc. e `positioningSetup(anchors)`, em `CalibrationSettings`), a mesma
  conversão que as gravações usam. Saíram os quatro campos copiados e o teste que garantia que as
  duas conversões davam o mesmo resultado (agora só existe uma, testada em `CalibrationSettingsTest`).
- [x] ~~**Motor atrás de uma interface**~~ — feito: `PositioningEngine` é o contrato (`record`,
  `refresh` uma vez por segundo, `rangeAgain`, `reset`, `setup`), e o motor atual é
  `PathLossPositioningEngine` (o histórico dele até aqui está em `PositioningEngine.kt`, cujo caminho a
  interface herdou: o Git não vê como renomeação). O
  ViewModel recebe o motor injetado; `di/PositioningModule` tem as duas linhas de troca (o motor
  inteiro, ou só o estimador dentro do motor do app). `PositioningReplay` recebe qualquer motor.
  No programa de replay, `--engine <nome>` (repetido, compara: uma coluna `engine` e um resumo por
  motor), com os motores registrados em `tools/replay/.../Engines.kt`. O README do módulo diz como
  plugar um motor novo. Os metadados das gravações continuam descrevendo o motor do app (a janela
  de RSSI é dele).
- [x] ~~**Cenários separados da implementação**~~ — feito: `PositioningScenarios`, 13 cenários que
  só usam a interface, cada um com a tolerância que aceita (RSSI igual de todas é o meio, a 1 cm;
  encostado num beacon é estar nele, a 10 cm; o beacon mais forte puxa o celular; a ordem das
  âncoras não importa; beacons que não são âncoras não mudam nada; quem não é ouvido há 10 s ou
  saiu do setup não conta; o limite de velocidade segura e, sem ele, o celular acompanha;
  `rangeAgain`, `reset`, uma distância e o RSSI por âncora ouvida, as contagens da API). Um motor
  novo ganha uma subclasse de três linhas, como `PathLossPositioningScenariosTest`. Os dois testes
  do motor atual que só usavam o contrato viraram cenários; os outros ficam com ele, e saem junto na
  reescrita. Conferido: tirar o limite de velocidade, o `rangeAgain` ou a limpeza do `reset` faz o
  cenário certo falhar. **Achado:** a ordem das âncoras muda a posição do motor atual em ~1 mm (a
  partida linear depende da primeira âncora, e o Gauss-Newton para assim que um passo não melhora),
  por isso esse cenário aceita 1 cm; anotado no README do módulo.

Pronto para a reescrita (194 testes JVM; lint e build passando localmente).

Fora daqui, por decisão: a linha de base com gravações reais (precisa das gravações) e o
`runCatching` que engole o cancelamento no `DeviceListViewModel`.

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
