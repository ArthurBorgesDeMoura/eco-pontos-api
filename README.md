# EcoPonto · backend

API REST em Java 21 com Spring Boot 3.3, num projeto Maven (BE-01). Ela atende em http://localhost:8080, e todas as rotas começam por `/api`.

## Requisitos

- **JDK 21**, por exemplo o Eclipse Temurin 21. Confira com `java -version`, ou aponte a variável `JAVA_HOME` para a pasta do JDK.
- **Maven:** vem junto, pelo Maven Wrapper. Na primeira vez, o `mvnw` baixa o Maven 3.9.16 e confere o arquivo. Quem tem o Maven instalado pode trocar `mvnw` por `mvn`.
- **MariaDB 10.11:** em contêiner, com o Docker Desktop (recomendado, DA-10), ou instalado na máquina.

Os comandos `./mvnw` abaixo são do Linux e do macOS; se der "Permission denied", rode `chmod +x mvnw` uma vez. No Windows, use `.\mvnw.cmd` no PowerShell e `./mvnw.cmd` no Git Bash: o git do Windows costuma gravar o `mvnw` com fim de linha CRLF, e aí ele não roda.

## 1. Configurar

Na raiz do repositório, copie o `.env.example` para `.env` e preencha as senhas: a `ECOPONTO_DB_SENHA` sempre, e a `ECOPONTO_DB_ROOT_SENHA` se for usar o Docker. Use só letras sem acento e números: o compose e a API leem o `.env` de jeitos diferentes, e um acento vira duas senhas diferentes. O `.env` nunca vai para o repositório.

No perfil dev, a API também lê um `backend/.env` opcional, que ganha do `.env` da raiz. O `npm start` do frontend procura a `ECOPONTO_API_PORTA` nessa mesma ordem. O `docker compose` só lê o `.env` da raiz, então deixe as senhas lá.

```bash
# Git Bash
cp .env.example .env
```

```powershell
# PowerShell
Copy-Item .env.example .env
```

## 2. Subir o banco

IMPORTANTE: o `../db/ecoponto.sql` apaga e recria o banco `ecoponto`. Rode só no MariaDB da sua máquina ou no contêiner do projeto.

**Com Docker (recomendado).** Na raiz do repositório:

```bash
docker compose up -d
docker compose ps
```

Espere o estado `healthy`. Na primeira subida, o contêiner carrega o `../db/ecoponto.sql` e cria o usuário `ecoponto_app`, que só lê e grava no banco (`../db/permissoes-app.sql`). Os dados ficam no volume `ecoponto_dados` e continuam lá depois de um `docker compose down`.

**Sem Docker.** Instale o MariaDB 10.11:

- **Windows, com administrador:** o instalador `.msi` de https://mariadb.org/download. Deixe marcada a opção de UTF-8 como padrão do servidor e guarde a senha do root.
- **Windows, sem administrador:** o `.zip` da mesma página. Extraia, por exemplo, em `C:\mariadb`, crie a pasta de dados uma vez e ligue o servidor. O segundo comando ocupa o terminal enquanto o banco estiver ligado.

  ```powershell
  C:\mariadb\bin\mariadb-install-db.exe --datadir=C:\mariadb-dados --password=SENHA_DO_ROOT
  C:\mariadb\bin\mariadbd.exe --datadir=C:\mariadb-dados --bind-address=127.0.0.1 --character-set-server=utf8mb4 --console
  ```

- **Linux e macOS:** o mais simples é o Docker. Sem ele, instale o MariaDB 10.11 pelo gerenciador de pacotes: o `mariadb-server` da distribuição, ou `brew install mariadb@10.11`, que não entra no PATH sozinho e liga com `brew services start mariadb@10.11`. Nos dois, o root costuma entrar sem senha, pelo usuário do sistema: troque `mariadb -u root -p` por `sudo mariadb` no Linux e por `mariadb` no macOS.

Depois, nesta pasta (`backend/`), carregue o script e crie o usuário da aplicação. Troque `SENHA_DO_ENV` pela `ECOPONTO_DB_SENHA` do `.env`. O cliente `mariadb` fica na pasta `bin` do MariaDB, como `C:\mariadb\bin\mariadb.exe`.

```bash
mariadb -u root -p --default-character-set=utf8mb4 -e "source ../db/ecoponto.sql"
mariadb -u root -p -e "CREATE USER IF NOT EXISTS 'ecoponto_app'@'localhost' IDENTIFIED BY 'SENHA_DO_ENV'; GRANT SELECT, INSERT, UPDATE, DELETE ON ecoponto.* TO 'ecoponto_app'@'localhost';"
```

No Windows, rode os dois no PowerShell. No Git Bash, ponha `winpty` antes de `mariadb`, senão a pergunta da senha pode travar. O `--default-character-set=utf8mb4` evita acentos trocados ("João" gravado como "JoÃ£o").

## 3. Preparar as contas de teste

O script grava as senhas das três contas de teste com SHA2, que o login não aceita (RNF03). A preparação refaz o hash delas com o bcrypt da aplicação, usando as senhas do próprio script, e sai sozinha:

```bash
./mvnw spring-boot:run -Ppreparar
```

O fim esperado é "Preparação concluída: 3 contas de teste com hash bcrypt". Rode de novo sempre que recarregar o `../db/ecoponto.sql`. A preparação só roda com o banco na própria máquina (localhost, 127.0.0.1 ou ::1).

## 4. Rodar a API

```bash
./mvnw spring-boot:run
```

Confira com `curl -i http://localhost:8080/api/saude`:

| Banco | Resposta |
| --- | --- |
| Ligado | 200 `{"status":"ok","banco":"ok"}` |
| Desligado | 503 `{"codigo":"BANCO_INDISPONIVEL","mensagem":"…"}`, em até 3 s |

- **Log:** uma linha por requisição, como `GET /api/saude 200 4 ms`. Nunca registra o corpo, os cabeçalhos, os cookies nem o IP, e parâmetros com nome de senha ou segredo saem como `***`.
- **Frontend:** o `npm start` (em `../frontend/`) chama esta API de verdade, sem respostas simuladas: encaminha `/api` para a `ECOPONTO_API_PORTA` do `backend/.env` ou do `.env` da raiz (8080 sem ela). Com a API fora do ar, as telas mostram erro e não caem para a simulação. Só o `npm run start:simulacao` dispensa o backend (veja o [README do frontend](../frontend/README.md#api-e-respostas-simuladas)). O CORS libera as origens de `ECOPONTO_CORS_ORIGENS`, com o cookie de sessão.

## Perfis e variáveis

A configuração comum fica em `src/main/resources/application.properties`, e a de cada ambiente em `application-<perfil>.properties`. Troque o perfil com a variável `SPRING_PROFILES_ACTIVE`.

| Perfil | Quando | De onde vem a configuração |
| --- | --- | --- |
| `dev` (padrão) | Na máquina da equipe | Do `.env` da raiz e do `backend/.env` (que ganha), com padrões locais. Uma variável de ambiente de verdade ganha do `.env`. |
| `prod` | Fora da máquina local, com HTTPS | Só de variáveis de ambiente, todas obrigatórias. O cookie de sessão exige HTTPS. |
| `test` | Nos testes sem banco | De `src/test/resources/application-test.properties`. |

| Variável | Padrão no dev | Para quê |
| --- | --- | --- |
| `ECOPONTO_DB_HOST`, `ECOPONTO_DB_PORTA`, `ECOPONTO_DB_NOME` | `localhost`, `3306`, `ecoponto` | Onde está o banco |
| `ECOPONTO_DB_USUARIO` | `ecoponto_app` | O usuário da aplicação; no contêiner, é sempre esse |
| `ECOPONTO_DB_SENHA` | nenhum | A senha desse usuário. Vazia ou ausente, a API não sobe e diz o que preencher. |
| `ECOPONTO_DB_ROOT_SENHA` | nenhum | A senha do root no contêiner. A API não usa. |
| `ECOPONTO_API_PORTA` | `8080` | A porta da API |
| `ECOPONTO_CORS_ORIGENS` | `http://localhost:4200` | As origens do frontend liberadas no CORS, separadas por vírgula |
| `ECOPONTO_BCRYPT_CUSTO` | `12` | O custo do bcrypt, de 4 a 31 |

A sessão (DA-2) fica guardada no servidor, e o cookie leva só um identificador aleatório, HttpOnly e SameSite Lax. Por isso não há segredo de sessão para configurar.

- **CSRF:** toda resposta traz o cookie `XSRF-TOKEN`, e todo POST, PUT e DELETE precisa devolvê-lo no cabeçalho `X-XSRF-TOKEN`; sem ele, 403. O Angular faz isso sozinho, mas só em URLs relativas e se ler o cookie: por isso o frontend chama `/api` na mesma origem (o proxy do `npm start`). Num `curl`, faça antes um GET e mande o cookie e o cabeçalho.
- **Login:** cada login troca o id da sessão e o token CSRF. A sessão dura 30 minutos sem uso, o padrão do Tomcat.
- **Bloqueio:** 5 falhas seguidas no mesmo e-mail bloqueiam o login por 5 minutos (`ecoponto.login.falhas-para-bloquear` e `ecoponto.login.bloqueio`). A contagem fica na memória da API: reiniciar a API zera, e duas instâncias não a dividem. Cada tentativa é reservada antes de conferir a senha, então pedidos simultâneos não passam do limite. E-mail vazio não conta, e só entra a conta com o e-mail exato: a coluna ignora acentos (`utf8mb4_general_ci`), e `ádmin@example.com` acharia o admin.

## Contrato e erros

O contrato da API está em [`docs/api/`](../docs/api/README.md) (BE-02): o `openapi.yaml` e os casos de exemplo, com os pedidos e as respostas.

- **Erros:** toda falha sai como `{ codigo, mensagem, campos }`, com o status do contrato e o cabeçalho `X-Content-Type-Options: nosniff`. Isso inclui:
  - a rota que não existe (404);
  - o JSON malformado (400);
  - o erro inesperado (500, sem detalhe interno);
  - o pedido que o próprio Tomcat recusa antes da aplicação, como uma URL com `%2F` ou `%00` (`configuracao/ErrosDoTomcat`, BE-15).

  O log nunca registra o corpo da requisição. A exceção é a recusa do CORS (uma origem fora da `ECOPONTO_CORS_ORIGENS`): 403 com o texto "Invalid CORS request", do Spring, que a página de outra origem nem consegue ler.
- **Limite do corpo:** 64 KB, com 400 `DADOS_INVALIDOS` e "O corpo do pedido passa de 64 KB." (`configuracao/LimiteDoCorpoFilter`, BE-15). O maior corpo válido, um ponto com 100 materiais, tem menos de 2 KB.
  - Com o tamanho declarado (`Content-Length`), o pedido é recusado sem que o corpo seja lido.
  - Enviado em partes, a leitura para ao passar do limite.
  - O formulário tem o mesmo teto, e o multipart está desligado, porque a API só recebe JSON.
- **Erro previsto:** quem atende a rota lança `ErroDaApi` com um `CodigoDeErro` (`src/main/java/br/com/ecoponto/erro/`). O login bloqueado usa `ErroDaApi.muitasTentativas(espera)`, que manda o `Retry-After`. O banco que não responde vira 503 sozinho.
- **Entradas:** os corpos são `CadastroEntrada`, `LoginEntrada`, `PontoEntrada` e `CategoriaEntrada`, com os tamanhos das colunas e os textos da tela. Com `@Valid`, o 400 já traz o erro de cada campo.
- **Erro que depende do banco:** quando a rota confere algo no banco, como o bairro e os materiais do ponto, ela usa o `ValidadorDeEntradas` no lugar do `@Valid`. Assim junta esses erros aos do corpo numa resposta só.
- **Filtros de lista:** a rota recebe os parâmetros como texto e os lê com a classe `Filtros` (`src/main/java/.../validacao/`). Ela junta o erro de todos os parâmetros inválidos, como os casos do contrato pedem.
- **Teste:** o `ContratoTest`, que roda no `./mvnw verify`, confere os casos contra o `openapi.yaml`.

## Rotas

As 21 rotas do contrato, todas prontas. Todo o prefixo `/api/admin` exige ADMIN (sem sessão, 401; USUARIO, 403), e as rotas de quem está logado dão 401 sem sessão.


| Rota | Cartão | O que faz |
| --- | --- | --- |
| `GET /api/saude` | BE-01 | 200 com o banco ligado, 503 sem ele |
| `POST /api/auth/cadastro` | BE-03 | Cria uma conta USUARIO, com a senha em bcrypt; e-mail repetido dá 409 |
| `POST /api/auth/login` | BE-04 | Abre a sessão; login errado dá 401 com a mesma mensagem, e 5 falhas seguidas dão 429 |
| `POST /api/auth/logout` | BE-04 | Encerra a sessão (204, também sem sessão) |
| `GET /api/auth/eu` | BE-04 | Quem está logado, ou 401 |
| `GET /api/bairros` | BE-06 | Os bairros, em ordem alfabética (a do banco, que ignora acentos); público |
| `GET /api/categorias` | BE-06 | As categorias, na ordem do id, com a descrição vazia como `null`; público e sem cache |
| `GET /api/pontos` | BE-07 | A busca: só os APROVADO, com `bairro`, `categoria`, `pagina` e `tamanho`; ordem por bairro, nome e id; 3 consultas por pedido (planos e tempos em [`docs/desempenho.md`](../docs/desempenho.md)) |
| `GET /api/pontos/contagem` | BE-08 | Quantos APROVADO aceitam cada material, com o filtro `bairro`: o `total` à parte e as 10 categorias em ordem de id, com 0 onde não há pontos; 2 consultas por pedido |
| `GET /api/pontos/{id}` | BE-09 | O detalhe, com os materiais e a descrição de cada um; horário e contato vazios vêm como `null`. Ponto que não existe e ponto não aprovado (para quem não é ADMIN) dão o mesmo 404; id que não é só dígitos dá 400 |
| `POST /api/pontos` | BE-10 | Sugere um ponto, para quem entrou (sem sessão, 401). Nasce PENDENTE, com o autor da sessão; `status` e `idUsuario` do corpo não contam. Bairro e materiais conferidos no banco, com os erros de campo numa resposta só (400). Ponto e vínculos na mesma transação; responde 201 com o ponto criado |
| `GET /api/minhas-sugestoes` | BE-11 | As sugestões da conta da sessão (sem sessão, 401), da mais recente para a mais antiga, com o status; `status` opcional (PENDENTE, APROVADO ou RECUSADO; outro valor dá 400), `pagina` e `tamanho` como na busca. Um id de usuário na URL não é lido |
| `GET /api/admin/resumo` | BE-12 | Os contadores do painel: pendentes, pontos aprovados e categorias. Só ADMIN (sem sessão, 401; USUARIO, 403), como todo o prefixo `/api/admin` |
| `GET /api/admin/pontos` | BE-12 | Os pontos de um status, com quem sugeriu (sem o e-mail); sem `status`, os pendentes, do mais antigo para o mais novo. Os outros status vêm na ordem padrão |
| `POST /api/admin/pontos/{id}/aprovar`, `POST /api/admin/pontos/{id}/recusar` | BE-12 | Decidem uma sugestão, só a partir de PENDENTE: o já decidido dá 409, e duas decisões ao mesmo tempo nunca passam as duas. Respondem com o ponto e o status novo, e o log registra quem decidiu, qual ponto e qual decisão |
| `PUT /api/admin/pontos/{id}` | BE-13 | Edita um ponto de qualquer status: troca os dados e os materiais de uma vez, com a validação do `POST /api/pontos`. Status, autor e data de cadastro do corpo não contam. Ponto que não existe dá 404; se algo falhar no meio, o ponto fica como estava |
| `DELETE /api/admin/pontos/{id}` | BE-13 | Exclui um ponto e os vínculos com os materiais: 204, ou 404 se ele não existe. Bairro, categorias e autor ficam |
| `GET /api/admin/categorias` | BE-14 | As categorias em ordem de id, com quantos pontos APROVADOS usam cada uma (`pontos`), com `pagina` e `tamanho` |
| `POST /api/admin/categorias`, `PUT /api/admin/categorias/{id}` | BE-14 | Criam (201) e editam uma categoria: nome obrigatório até 60, sem as pontas, e descrição opcional até 255. Nome de outra categoria dá 409 no campo nome, sem diferenciar maiúsculas nem acentos; a que não existe dá 404. Não há exclusão (RF08) |

**Quem acessa (BE-05):**
- Todo o prefixo `/api/admin` exige ADMIN: 401 sem sessão e 403 para USUARIO. Vale também para uma rota que ainda não existe, então uma rota nova ali já nasce protegida.
- `GET /api/auth/eu`, `POST /api/pontos` e `GET /api/minhas-sugestoes` exigem login. O resto é público.
- As regras ficam em `SegurancaConfig.quemAcessaCadaRota`. O perfil é o que o login gravou na sessão, lido do banco; trocar o perfil no banco só vale no próximo login.
- O `AutorizacaoTest` lê as rotas do `openapi.yaml` e confere os 3 perfis em cada uma. Uma rota nova de quem está logado fora de `/api/admin` precisa de `security: [{ sessao: [] }]` no contrato e de uma linha em `quemAcessaCadaRota`; sem a linha, o teste falha.

O acesso ao banco é pelo Spring Data JPA, com o Hibernate: uma entidade por tabela (`Usuario` é a tabela `usuario`) e um repositório por entidade. O Hibernate nunca cria nem altera tabela, porque o esquema é o do `../db/ecoponto.sql`.

## Testar e montar

| Comando | O que faz |
| --- | --- |
| `./mvnw verify` | Lint (Checkstyle e o compilador com avisos tratados como erro), os testes sem banco e o jar em `target/ecoponto-backend.jar`. |
| `./mvnw verify -Pbanco` | O mesmo, e mais os testes com o banco do `.env` ligado (`*IT`). Entre eles: as rotas contra os casos do contrato, o acesso dos 3 perfis em todas as rotas, as entradas de ataque, a varredura de segredos nas respostas e no log, e o roteiro de ponta a ponta (veja "Antes da entrega"). Cada teste devolve no fim o que mudou no banco. |
| `./mvnw verify -Pdesempenho` | Só a medição do RNF01, no banco `ecoponto_desempenho` (veja "Desempenho"). Grava os tempos em `target/desempenho.md` e falha se algum pedido passar de 2 s. |

## Desempenho (RNF01, BE-15)

O script tem só 15 pontos, pouco para medir. O `../db/desempenho.sql` cria um banco separado, o `ecoponto_desempenho`, com as mesmas tabelas e mais 5.000 pontos de teste, gerados sempre iguais. Ele lê o `ecoponto` e só apaga e recria o `ecoponto_desempenho`. Os resultados estão em [`docs/desempenho.md`](../docs/desempenho.md).

1. Carregue e prepare o banco principal (passos 2 e 3): o banco de desempenho copia dele as contas, já com o bcrypt.
2. Gere o banco de desempenho, como root:
   - **Com Docker:** `docker compose exec -T banco sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" --default-character-set=utf8mb4' < ../db/desempenho.sql`. A senha vem da variável do próprio contêiner, porque a entrada do comando é o arquivo. Este comando não foi testado: a máquina do BE-15 não tinha Docker.
   - **Sem Docker:** `mariadb -u root -p --default-character-set=utf8mb4 -e "source ../db/desempenho.sql"`
3. Dê ao usuário da aplicação a mesma permissão do banco principal (uma vez só; ela continua valendo quando o banco é recriado):
   - **Com Docker:** `docker compose exec banco mariadb -u root -p -e "GRANT SELECT, INSERT, UPDATE, DELETE ON ecoponto_desempenho.* TO 'ecoponto_app'@'%';"`
   - **Sem Docker:** `mariadb -u root -p -e "GRANT SELECT, INSERT, UPDATE, DELETE ON ecoponto_desempenho.* TO 'ecoponto_app'@'localhost';"`
4. Meça: `./mvnw verify -Pdesempenho`. O teste confere antes que está no `ecoponto_desempenho` e que há pelo menos 5.000 pontos.
5. Para comparar com os índices sugeridos, rode `../db/desempenho-indices.sql` do mesmo jeito do passo 2 e meça de novo. Para voltar sem eles, gere o banco de novo. Esses índices não estão no `../db/ecoponto.sql`: sem eles, tudo já responde bem abaixo de 2 s.

Para usar a API inteira com esse banco, rode-a com `ECOPONTO_DB_NOME=ecoponto_desempenho`.

## Antes da entrega

O roteiro de ponta a ponta (`RoteiroDePontaAPontaIT`) faz, pela API, o caminho todo:

1. cadastra uma conta e entra;
2. busca, filtra e abre um detalhe;
3. sugere um ponto;
4. o admin aprova, edita e exclui o ponto;
5. as duas contas saem.

Ele exige o banco como o script deixa e apaga no fim o que criou. Antes da entrega, rode-o num banco recém-criado:

1. Recarregue o banco do zero (veja abaixo).
2. Rode a preparação das contas (passo 3).
3. Rode `./mvnw verify -Pbanco`. Ele roda o roteiro e os outros testes com o banco.

As provas do RNF03 (senha, acesso, consultas, ataques e segredos) estão em [`docs/seguranca.md`](../docs/seguranca.md).

## Recarregar o banco do zero

- **Com Docker:** `docker compose down -v` apaga o volume, e `docker compose up -d` cria tudo de novo.
- **Sem Docker:** repita o `source ../db/ecoponto.sql` do passo 2. O usuário da aplicação e as permissões dele continuam valendo.
- **Nos dois casos:** rode a preparação (passo 3) de novo.

## Trocar a senha do banco

O `CREATE USER IF NOT EXISTS` não muda a senha de um usuário que já existe, e o contêiner só lê a `ECOPONTO_DB_SENHA` na primeira subida, com o volume vazio. Depois de trocar a senha no `.env`, troque também no MariaDB:

- **Com Docker:** `docker compose exec banco mariadb -u root -p -e "ALTER USER 'ecoponto_app'@'%' IDENTIFIED BY 'SENHA_NOVA';"`, com a `ECOPONTO_DB_ROOT_SENHA` como senha do root. Outro caminho é `docker compose down -v` e `docker compose up -d`, que apagam e recriam o banco.
- **Sem Docker:** `mariadb -u root -p -e "ALTER USER 'ecoponto_app'@'localhost' IDENTIFIED BY 'SENHA_NOVA';"`.

Com a senha antiga no banco, a API recebe "Access denied", o `/api/saude` responde 503 e os testes `-Pbanco` falham.

## Avisos

- O Spring Boot 3.3 está sem suporte gratuito desde 30/06/2025 e sem o comercial desde 30/06/2026. O Spring Framework 6.1 e o Spring Security 6.3 não recebem mais correções de segurança. O Tomcat foi atualizado à mão para 10.1.61 (`tomcat.version` no `pom.xml`).
- O `./mvnw` precisa do fim de linha LF e da permissão de execução. Um repositório criado no Windows grava o `mvnw` sem essa permissão; rode `git update-index --chmod=+x backend/mvnw` antes do primeiro commit, ou `chmod +x mvnw` depois de clonar no Linux ou no macOS.
