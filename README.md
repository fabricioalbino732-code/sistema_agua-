# Sistema de Gestao de Faturacao de Agua

Sistema completo reconstruido do zero, mantendo os mesmos modulos das versoes anteriores (v1-v4), mas com arquitetura corrigida para eliminar os bugs conhecidos.

## Requisitos

- Java 17+
- Maven 3.8+
- PostgreSQL 14+

Nota: o projeto NAO usa Lombok. Todas as entidades e DTOs tem getters/setters
escritos explicitamente, para evitar a necessidade de configurar plugins de
annotation processing no Eclipse/IDE.

## Configuracao

1. Cria a base de dados:
   ```sql
   CREATE DATABASE sistema_agua;
   ```

2. Edita `src/main/resources/application.properties` com as tuas credenciais do PostgreSQL:
   ```
   spring.datasource.username=SEU_USUARIO
   spring.datasource.password=SUA_SENHA
   ```

3. Compila e corre:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

4. Acede a http://localhost:8080

## Correcoes de arquitetura em relacao as versoes anteriores

### Bug do ConfiguracaoService (configuracao a resetar ao reiniciar)
A causa raiz era o metodo de salvar criar uma NOVA entidade a cada save, gerando
multiplas linhas na tabela em vez de atualizar a existente. O sistema podia
ler uma linha antiga apos reiniciar.

**Correcao:** a entidade `Configuracao` usa um ID fixo (`Configuracao.ID_CONFIGURACAO = 1L`),
e o `ConfiguracaoService.atualizarConfiguracao()` SEMPRE busca a entidade
existente antes de alterar os campos, garantindo um UPDATE.

### Bug do insert-vira-update nas faturas (valores zerados)
Causado por expor entidades JPA diretamente nos formularios Thymeleaf, o que
podia confundir o Hibernate sobre se uma operacao era insert ou update.

**Correcao:** todos os formularios agora usam DTOs (`ClienteDTO`, `LeituraDTO`,
`PagamentoDTO`, `ConfiguracaoDTO`) em vez de expor entidades diretamente.
Os services convertem DTO -> Entidade de forma explicita.

## Pagamentos via M-Pesa / e-Mola — ZumboPay (NOVO)

O sistema pode cobrar diretamente o cliente (STK push — pedido de PIN no
telemovel dele) e confirmar o pagamento automaticamente, via integracao
com o ZumboPay.

**Como funciona:**
1. Na pagina de qualquer fatura, os botoes "Cobrar via M-Pesa" / "Cobrar
   via e-Mola" iniciam o pedido
2. O cliente recebe o pedido de PIN no telemovel e confirma
3. O ZumboPay avisa o sistema automaticamente (webhook) quando o
   pagamento e confirmado
4. O sistema regista o pagamento sozinho, sem intervencao manual

**Seguranca do webhook (3 camadas):**
1. Verificacao de assinatura HMAC-SHA256 (garante que o pedido e mesmo do
   ZumboPay)
2. Idempotencia — nao processa o mesmo evento duas vezes
3. Re-verificacao autoritativa — nunca confia so no conteudo do webhook,
   confirma sempre com uma chamada direta `GET /payments/{reference}`
   antes de marcar qualquer fatura como paga

**Para ativar**, edita `application.properties`:
```
zumbopay.ativado=true
zumbopay.api-key=zk_live_...
zumbopay.merchant-id=MCH_...
zumbopay.wallet-id-mpesa=<uuid da carteira M-Pesa>
zumbopay.wallet-id-emola=<uuid da carteira e-Mola>
zumbopay.webhook-secret=<secret gerado no painel ZumboPay>
```

Os `wallet_id` obtêm-se em `GET /wallets` no painel do ZumboPay (Painel ->
Carteiras). O `webhook-secret` e gerado ao configurar o URL do webhook em
Painel -> Programadores -> Webhooks — o URL a configurar la e:
```
https://o-teu-dominio-ou-ip/webhooks/zumbopay
```
(so funciona com o sistema hospedado publicamente — nao funciona em
localhost, porque o ZumboPay precisa de conseguir alcancar esse endereco
pela internet)

**IMPORTANTE:** os nomes exatos de alguns campos do corpo do webhook
(`data.reference`, `data.source_id`, `data.channel`, `data.amount`) foram
inferidos a partir da documentacao disponivel e da resposta do endpoint
de cobranca — se o webhook real chegar com nomes de campos diferentes,
o log (`Webhook ZumboPay recebido: tipo=..., eventId=...`) vai mostrar o
evento recebido, o que ajuda a ajustar rapidamente o codigo em
`ZumboPayWebhookController` caso necessario.

## Notificacoes por SMS (NOVO)

O sistema envia automaticamente um SMS ao cliente sempre que uma fatura
nova e gerada (usando a API do MozeSMS).

**Por padrao, o envio esta DESATIVADO** (`notificacoes.sms.ativado=false`
no application.properties) — enquanto estiver assim, o sistema so regista
no log (console do Eclipse) a mensagem que TERIA enviado, sem fazer
pedidos reais. Isto permite testar o fluxo sem token real.

**Para ativar de verdade**, edita `application.properties`:
```
notificacoes.sms.ativado=true
mozesms.api-key=a_tua_api_key_aqui
mozesms.api-secret=o_teu_api_secret_aqui
mozesms.from=NomeDaEmpresa
```

Estas credenciais (API Key + API Secret) sao geradas em
`my.mozesms.com -> Gestao de APIs -> Nova Chave`, com a permissao
"Enviar SMS" marcada. E o metodo recomendado pela documentacao do MozeSMS
para integracoes servidor-servidor (o Bearer Token e so para o painel web
deles, nao para isto).

**Reenviar manualmente:** na pagina de detalhes de qualquer fatura, o
botao "Reenviar SMS" permite reenviar a notificacao sem precisar gerar
a fatura de novo.

**Falhas no envio nunca bloqueiam o sistema** — se o SMS falhar (token
invalido, sem internet, etc.), a fatura continua a ser gerada
normalmente; o erro fica so registado no log.

## Leitura Remota do Fiscalizador (NOVO)

Fiscalizadores podem agora registar leituras diretamente do telemovel/tablet,
em `/leituras/remota`, sem precisar de acesso ao resto do sistema.

**Como funciona:**
1. Cria um utilizador com perfil **LEITOR** em `/usuarios` (só ADMIN consegue)
2. O fiscalizador faz login com essas credenciais — e-mail e senha
3. E redirecionado automaticamente para `/leituras/remota` (nao ve o resto do sistema)
4. Escolhe o cliente, tira/carrega foto do contador (obrigatoria), digita a leitura atual
5. A localizacao GPS e capturada automaticamente pelo navegador do telemovel
   (pede permissao de localizacao na primeira vez — o fiscalizador tem de aceitar)
6. A leitura anterior e sugerida automaticamente, sem precisar de digitar

**Onde ficam guardadas as fotos:**
Na pasta `./fotos-leituras` (configuravel via `fotos.pasta` no
application.properties), fora da base de dados — isto mantem o backup
(`pg_dump`) leve e rapido, ja que fotos sao ficheiros pesados. A base de
dados guarda so o nome do ficheiro.

**IMPORTANTE:** ao migrares para hospedagem online, lembra-te de mover
tambem a pasta `fotos-leituras` para o servidor (nao e coberta pelo backup
da base de dados) — vale a pena incluir essa pasta numa rotina de backup
separada (ex: copiar para outro local de vez em quando).

**Seguranca:** as fotos so sao acessiveis a utilizadores autenticados
(atraves de `/leituras/foto/{id}`), nunca por link publico direto.

## Backup (NOVO)

O sistema agora tem duas formas de backup, usando a ferramenta 'pg_dump'
(que ja vem junto com a instalacao do PostgreSQL):

1. **Backup manual sob demanda:** com um utilizador ADMIN logado, clica em
   "Backup" no menu — descarrega imediatamente um ficheiro .sql com toda a
   base de dados atual.

2. **Backup automatico diario:** todos os dias as 02:00, se a aplicacao
   estiver em execucao, um backup e gerado automaticamente na pasta
   `./backups` (na raiz do projeto). Os ultimos 14 backups sao mantidos;
   os mais antigos sao apagados automaticamente.

**Requisito:** o comando `pg_dump` precisa de estar acessivel no PATH do
sistema operativo. Se der erro a dizer que nao encontra o `pg_dump`, edita
`application.properties` e define o caminho completo:
```
backup.pgdump.path=C:/Program Files/PostgreSQL/16/bin/pg_dump.exe
```
(ajusta a versao/caminho conforme a tua instalacao)

**Limitacao importante:** o backup automatico diario SO funciona enquanto a
aplicacao estiver a correr. Se o computador for desligado as 02:00, nao ha
backup nesse dia. Para garantia total mesmo com a aplicacao desligada, o
ideal e complementar com uma tarefa agendada do proprio sistema operativo
(Agendador de Tarefas no Windows, cron no Linux/Mac) chamando `pg_dump`
diretamente — mas isso e trabalho de configuracao do servidor, fora do
codigo da aplicacao.

## Login (NOVO)

O sistema agora exige autenticacao. Na primeira vez que correres a aplicacao,
um utilizador administrador padrao e criado automaticamente:

```
username: admin
senha:    admin123
```

**MUDA ESTA SENHA IMEDIATAMENTE** apos o primeiro login (ainda nao existe
tela de "alterar minha senha" propria — pede a um administrador para criar
um novo utilizador com a senha certa e depois inativa o "admin" padrao, ou
altera a senha diretamente na base de dados por agora).

So utilizadores com perfil **ADMIN** conseguem aceder a `/usuarios` para
criar novos utilizadores (operadores ou outros admins).

## Correcao aplicada: CHECK CONSTRAINTS automaticos do Hibernate

O Hibernate 6 gera automaticamente uma `CHECK CONSTRAINT` para colunas baseadas
em `enum` (ex: `status`, `tipo_cliente`, `forma_pagamento`), listando os valores
validos no momento da criacao da tabela. O problema: essa restricao **nunca e
atualizada automaticamente** quando se adiciona um novo valor ao enum no
codigo (ex: adicionar `TRANSFERIDA` ao `StatusFatura`), mesmo com
`ddl-auto=update`. Isso causa o erro:

```
ERROR: new row for relation "fatura" violates check constraint "fatura_status_check"
```

**Correcao no codigo:** todas as colunas enum (`Fatura.status`,
`HistoricoCorte.status`, `Cliente.tipoCliente`, `Pagamento.formaPagamento`)
agora tem `columnDefinition = "varchar(20)"` explicito, o que faz o Hibernate
tratar a coluna como texto simples, sem gerar a restricao automatica. Isto
evita que o problema se repita ao adicionar novos valores a qualquer enum
no futuro, em instalacoes novas do sistema.

**Se estiveres a atualizar uma base de dados JA EXISTENTE** (que ja tem as
restricoes antigas criadas), precisas de remover as restricoes manualmente
UMA VEZ, correndo este script no psql:

```sql
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT con.conname, rel.relname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE con.contype = 'c'
        AND rel.relname IN ('fatura', 'cliente', 'pagamento', 'historico_corte')
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.relname, r.conname);
    END LOOP;
END $$;
```

Depois disso, reinicia a aplicacao normalmente — nao precisas de repetir isto
outra vez, mesmo se adicionares novos valores aos enums no futuro.

## Modulos

- **Clientes** — cadastro, busca, edicao, inativacao
- **Leituras de Contador** — registo mensal, sugestao automatica de leitura anterior
- **Faturas** — geracao a partir de leituras, PDF com 2 vias por folha A4
- **Pagamentos** — suporte a pagamento parcial, calculo automatico de saldo devedor
- **Cortes/Religacoes** — historico de corte e religacao de abastecimento
- **Configuracao Global** — preco por m3, taxa fixa, dia de vencimento, multa por atraso (singleton corrigido)
- **Dashboard** — estatisticas de clientes ativos, faturas por status, valores faturados/recebidos, taxa de cobranca

## Nota sobre este build

Este projeto foi gerado num ambiente sem acesso ao Maven Central, portanto
**nao foi possivel compilar e testar automaticamente**. O codigo foi revisado
manualmente (estrutura, imports, chaves balanceadas), mas recomenda-se correr
`mvn clean install` localmente e testar cada modulo antes de usar em producao.
Se encontrares algum erro de compilacao, volta a mim com a mensagem de erro
exata e corrijo rapidamente.
