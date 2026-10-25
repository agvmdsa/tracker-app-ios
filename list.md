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

## Infraestrutura / Deploy

- [ ] **Reconectar os serviços do Render** pro repositório `tracker-app-ios` (hoje apontam pro
  `api_tracker_app` antigo) — Root Directory `applications/api` (API) e
  `applications/api/grafana` (Grafana). Provavelmente precisa apagar e recriar os serviços.
  Variáveis de ambiente (`DATABASE_URL`, `API_KEY`, `POSTGRES_*`) precisam ser recriadas.
- [ ] Decidir se o `applications/web` também vai pro Render, ou só roda localmente por enquanto.
- [ ] Gerar/colar a `API_KEY` real nos 3 lugares (Render, `PositioningAPIClient.swift`,
  `esp32-esp8266/include/config.h`) — anotado antes, ainda pendente.

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
