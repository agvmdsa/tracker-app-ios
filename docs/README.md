Relatório técnico do projeto (modelo CSBC/ABNT2), exigido pela disciplina de Sistemas Embarcados
(Projeto-Embarcados_CesarSchool-CSBC.pdf, seção 5/6).

- `relatorio/relatorio.tex` — fonte LaTeX (introdução, fundamentação, metodologia, resultados,
  conclusão, referências e códigos em apêndice).
- `relatorio/relatorio.pdf` — versão compilada.
- `relatorio/sbc-template.sty` — estilo SBC (margens, fontes, resumo/abstract). Usa a mesma
  interface do `.sty` oficial da SBC: se a disciplina exigir o arquivo oficial, basta substituir.

## Compilar

```bash
cd docs/relatorio
latexmk -pdf relatorio.tex        # ou: pdflatex relatorio.tex (duas vezes)
```

Precisa de uma distribuição TeX com `babel-portuges`, `listings` e `tikz` (MacTeX/TeX Live
completo já tem).

**Overleaf:** envie só a pasta `relatorio/` (`relatorio.tex` + `sbc-template.sty`) e compile com
pdfLaTeX. Os códigos do apêndice e a figura do circuito estão embutidos no `.tex`; nada é lido de
fora da pasta. Por isso são cópias: se um desses arquivos mudar no repositório, atualize a
listagem correspondente (cada uma tem um comentário `% Copiado de <caminho>` indicando a origem).

## Antes de entregar

Todo trecho em **vermelho** no PDF (`\pendente{...}` no `.tex`) depende de dados que só o grupo
tem: nomes dos integrantes, medições de precisão/taxa, validação do IPv6 em rede real e capturas de
tela dos painéis. `grep -n '\\pendente' relatorio/relatorio.tex` lista todos.

O tema oficial declarado é **Computação** (macro `\tema` no topo do `.tex`) — trocar ali se o grupo
escolher outro dos 5 temas.
