# Travel Expense API

API REST para registrar e acompanhar gastos de viagem em múltiplas moedas. Cada viagem tem um orçamento em uma moeda base. Cada gasto é convertido automaticamente para essa moeda usando a cotação do momento do registro, e o resumo mostra quanto do orçamento já foi usado.

## Escopo da v1

- **Viagens:** criar, listar, consultar, editar e excluir.
- **Gastos:** registrar, listar (com filtro por categoria), editar e excluir os gastos de uma viagem.
- **Conversão de moeda:** cada gasto é convertido para a moeda base da viagem por uma API de câmbio externa. A cotação usada fica gravada junto com o gasto.
- **Resumo da viagem:** total gasto, total por categoria, orçamento restante, percentual usado e aviso de orçamento estourado.

## Modelo de dados

### `trips`

| Coluna | Tipo | Regras |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `name` | `VARCHAR(120)` | obrigatório |
| `destination` | `VARCHAR(120)` | obrigatório |
| `base_currency` | `CHAR(3)` | código ISO 4217 (RN04) |
| `budget` | `NUMERIC(19,2)` | `> 0` (RN01) |
| `start_date` | `DATE` | obrigatório |
| `end_date` | `DATE` | `>= start_date` (RN02) |

### `expenses`

| Coluna | Tipo | Regras |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `trip_id` | `BIGINT` | FK para `trips`, `ON DELETE CASCADE` |
| `description` | `VARCHAR(255)` | obrigatório |
| `category` | `VARCHAR(20)` | `FOOD`, `TRANSPORT`, `LODGING`, `LEISURE`, `SHOPPING` ou `OTHER` |
| `original_amount` | `NUMERIC(19,2)` | `> 0` (RN03) |
| `original_currency` | `CHAR(3)` | código ISO 4217 (RN04) |
| `exchange_rate` | `NUMERIC(19,8)` | `> 0`; gravada no registro e não muda depois (RN06) |
| `converted_amount` | `NUMERIC(19,2)` | `> 0`; valor na moeda base da viagem |
| `expense_date` | `DATE` | obrigatório |
| `created_at` | `TIMESTAMPTZ` | preenchido pelo banco |

No Java, todos os valores monetários e cotações usam `BigDecimal`. O schema é versionado com Flyway (`src/main/resources/db/migration`).

## Fora do escopo da v1

| Item | Motivo |
|---|---|
| Autenticação e múltiplos usuários | A v1 valida o domínio (viagens, gastos, câmbio). Controle de acesso é uma camada à parte, que pode ser adicionada depois sem mudar as regras de negócio. |
| Front-end | O projeto é uma API. O contrato fica documentado via Swagger. |
| IA | Não resolve nenhum problema do escopo atual. |
| Cache distribuído | Não se justifica com um único usuário. Se a API de câmbio falhar, o gasto não é salvo e a API retorna um erro claro. Um cache local curto é a próxima evolução natural. |
| Upload de comprovantes | Exige armazenamento de arquivos, o que não faz parte do problema central. |
| Relatórios em PDF | O resumo em JSON já atende. Gerar documentos é uma feature separada. |

Ideias novas vão para issues com o rótulo `v2`.

As regras de negócio e as decisões técnicas estão documentadas em [`docs/decisions/`](docs/decisions/).
