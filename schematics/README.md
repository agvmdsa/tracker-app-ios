Diagramas de circuito dos módulos ESP32 — exigidos pela disciplina de Sistemas Embarcados
(Projeto-Embarcados_CesarSchool-CSBC.pdf, seção 6).

## `esp32-node/` — nó ESP32 (beacon ou coletor)

O mesmo circuito serve para os dois papéis: só a placa ESP32 DevKit, alimentada e gravada pela USB,
usando a antena de PCB do módulo ESP32-WROOM-32. Nenhum sensor ou atuador externo.

- `esp32-node.pdf` / `esp32-node.png` — diagrama de blocos e fiação (fonte: `esp32-node.tex`,
  TikZ; recompilar com `pdflatex esp32-node.tex` e
  `pdftoppm -r 150 -png -singlefile esp32-node.pdf esp32-node`). Também é a Figura 2 do relatório.
- `diagram.json` — o mesmo nó no [Wokwi](https://wokwi.com) (ESP32 DevKit + monitor serial):
  crie um projeto ESP32 no Wokwi e cole este arquivo no `diagram.json`.

Se algum sensor/atuador do kit (LED RGB, ultrassônico, IR, potenciômetro) for incorporado ao
firmware, adicione a peça e as ligações ao `diagram.json` e ao `esp32-node.tex`, com os GPIOs
usados.
