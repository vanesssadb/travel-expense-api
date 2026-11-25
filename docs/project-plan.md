```markdown
# Plano do Projeto: Plataforma de Gastos de Viagem (v1)

## 1. Escopo Fechado

### Requisitos Funcionais
- Criar, listar, consultar, editar e excluir **viagens**.
- Registrar, listar (com filtro por categoria), editar e excluir **gastos** de uma viagem.
- Converter automaticamente o gasto para a moeda base da viagem.
- Consultar o **resumo** da viagem: total gasto, total por categoria, orçamento restante e percentual usado.

### Regras de Negócio
| # | Regra |
|---|---|
| RN01 | Orçamento deve ser maior que zero. |
| RN02 | Data final da viagem não pode ser anterior à inicial. |
| RN03 | Valor do gasto deve ser maior que zero. |
| RN04 | Moedas devem ser códigos ISO 4217 válidos (USD, EUR, BRL, etc.). |
| RN05 | Se a moeda do gasto é igual à moeda base, a cotação é `1` e **não se chama a API**. |
| RN06 | A cotação usada é gravada junto com o gasto e não muda depois. |
| RN07 | Ultrapassar o orçamento **não bloqueia** o gasto, mas o resumo sinaliza. |
| RN08 | Se a API de câmbio falhar, o gasto **não é salvo** e a API responde com erro claro. |

### Fora do Escopo (v1)
- Autenticação e múltiplos usuários
- Front-end
- Recursos de IA
- Cache distribuído
- Upload de comprovantes
- Relatórios em PDF

---

## 2. Modelo de Dados

### Entidades

**Trip**
- `id`
- `name`
- `destination`
- `baseCurrency` (moeda em que o orçamento é definido, ex.: `BRL`)
- `budget` (`NUMERIC(19,2)`)
- `startDate`
- `endDate`

**Expense**
- `id`
- `trip_id` (FK)
- `description`
- `category` (enum: `FOOD`, `TRANSPORT`, `LODGING`, `LEISURE`, `SHOPPING`, `OTHER`)
- `originalAmount` (`NUMERIC(19,2)`)
- `originalCurrency`
- `exchangeRate` (`NUMERIC(19,8)`)
- `convertedAmount` (`NUMERIC(19,2)` — valor na moeda base da viagem)
- `expenseDate`
- `createdAt`

### Especificações Técnicas de Dados
- **Precisão monetária:** Utilizar `BigDecimal` no Java e `NUMERIC(19,2)` no banco para valores monetários, e `NUMERIC(19,8)` para cotações.
- **Arredondamento:** Centralizar a regra de arredondamento em um único ponto (ex.: `RoundingMode.HALF_UP`, 2 casas decimais).
- **Persistência do valor convertido:** `convertedAmount` e `exchangeRate` são persistidos no momento do registro para preservar o histórico exato da transação, independentemente de flutuações futuras da taxa de câmbio.

---

## 3. Arquitetura

**Monolito em camadas**, organizado **por funcionalidade (feature)**:

```text
com.vanessa.travel
├── trip
│   ├── Trip.java (entity)
│   ├── TripRepository.java
│   ├── TripService.java
│   ├── TripController.java
│   └── dto/ (TripRequest, TripResponse)
├── expense
│   ├── Expense.java
│   ├── ExpenseCategory.java
│   ├── ExpenseRepository.java
│   ├── ExpenseService.java
│   ├── ExpenseController.java
│   └── dto/
├── summary
│   ├── TripSummaryService.java
│   └── TripSummaryResponse.java
├── exchange
│   ├── ExchangeRateProvider.java (interface)
│   └── AwesomeApiExchangeRateProvider.java (implementação)
└── shared
    ├── exception/ (exceções customizadas + GlobalExceptionHandler)
    └── config/
```

### Fluxo de Requisição

```text
Cliente → Controller → Service → Repository → PostgreSQL
                          │
                          └→ ExchangeRateProvider → API externa
```

### Responsabilidade das Camadas
- **Controller:** Recebe requisições HTTP, valida o formato de entrada (`@Valid`), aciona o service e retorna DTOs. Sem regras de negócio.
- **Service:** Concentra as regras de negócio e orquestra repositórios e o provedor de câmbio.
- **Repository:** Responsável exclusivamente pelo acesso e persistência de dados.
- **ExchangeRateProvider:** Abstrai a integração com a API externa de câmbio por meio de uma interface:

```java
public interface ExchangeRateProvider {
    BigDecimal getRate(String fromCurrency, String toCurrency);
}
```

---

## 4. Decisões Técnicas e Arquiteturais

| Decisão | Opções | Escolha e Justificativa |
|---|---|---|
| **Estrutura de pacotes** | Por camada vs. por feature | **Por feature.** Mantém os artefatos de cada domínio coesos e facilita a manutenção. |
| **Cliente HTTP** | `RestTemplate`, `WebClient`, `RestClient` | **`RestClient` (Spring 6.1+).** Cliente moderno e síncrono, sem a complexidade reativa do `WebClient`. |
| **Contrato da API** | Expor entity vs. DTO | **DTO (`record`).** Desacopla o contrato da API do schema do banco e evita vazamento de dados internos. |
| **Mapeamento** | MapStruct vs. manual | **Manual.** Adequado ao volume de entidades do projeto, mantendo total controle das transformações. |
| **Migrations** | `ddl-auto=update` vs. Flyway | **Flyway.** Garante controle de versão determinístico e rastreável do schema do banco. |
| **Camadas de validação** | Controller, service, banco | **Nos três níveis:** `@Valid` no controller (formato), validações no service (regras de negócio) e constraints no banco (`NOT NULL`, `CHECK (amount > 0)`). |
| **Tratamento de erros** | `try/catch` local vs. handler global | **`@RestControllerAdvice` com `ProblemDetail` (RFC 7807).** Padroniza as respostas de erro da API. |
| **Orçamento excedido** | Bloquear vs. permitir e sinalizar | **Permitir e sinalizar.** O gasto já ocorreu; o sistema registra e exibe o alerta no resumo (RN07). |
| **Agregação por categoria** | Streams em memória vs. `GROUP BY` | **`GROUP BY` no banco (JPQL).** Delega a agregação ao banco de dados, evitando carregar todos os registros em memória. |
| **Edição de gasto** | Recalcular cotação vs. manter | **Condicional:** manter a cotação original se apenas descrição/categoria forem alteradas; consultar nova cotação apenas se valor ou moeda mudarem. |
| **Exclusão de viagem** | Cascade vs. bloquear | **Cascade.** Gastos não existem de forma independente sem uma viagem associada. |
| **Indisponibilidade da API externa** | Fallback, cache, erro | **Erro `503` (RN08).** Evolução futura (v2): cache de curta duração (ex.: 1h com Caffeine). |

---

## 5. Contrato da API (Endpoints)

```http
POST   /api/trips
GET    /api/trips
GET    /api/trips/{id}
PUT    /api/trips/{id}
DELETE /api/trips/{id}

POST   /api/trips/{tripId}/expenses
GET    /api/trips/{tripId}/expenses?category=FOOD
GET    /api/trips/{tripId}/expenses/{id}
PUT    /api/trips/{tripId}/expenses/{id}
DELETE /api/trips/{tripId}/expenses/{id}

GET    /api/trips/{tripId}/summary
```

### Exemplo de Resposta (`GET /api/trips/{tripId}/summary`)

```json
{
  "tripId": 1,
  "baseCurrency": "BRL",
  "budget": 10000.00,
  "totalSpent": 6450.75,
  "remaining": 3549.25,
  "percentUsed": 64.51,
  "overBudget": false,
  "byCategory": [
    { "category": "LODGING", "total": 3200.00 },
    { "category": "FOOD", "total": 1850.75 }
  ]
}
```

### Códigos HTTP Padronizados
- `201 Created`: Recurso criado com sucesso.
- `400 Bad Request`: Erro de validação de formato/entrada.
- `404 Not Found`: Recurso não encontrado.
- `422 Unprocessable Entity`: Violação de regra de negócio.
- `503 Service Unavailable`: API externa de câmbio indisponível.

---

## 6. Estratégia de Testes

| Tipo | Escopo | Ferramentas |
|---|---|---|
| **Unitário** | Regras do `ExpenseService` (conversão, RN05, RN08) e cálculos do `TripSummaryService` | JUnit 5 + Mockito (mock de `ExchangeRateProvider`) |
| **Repositório** | Query de agregação de totais por categoria | `@DataJpaTest` + Testcontainers (PostgreSQL) |
| **Controller** | Status codes HTTP, validação de payloads e formato `ProblemDetail` | `@WebMvcTest` + MockMvc |

---

## 7. Plano de Execução

Fluxo de trabalho baseado em **1 issue no GitHub por etapa**, **1 branch por issue** e commits seguindo **Conventional Commits** (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).

- **Etapa 0 — Fundação:** Estruturação do `README.md` com requisitos e regras, inicialização do projeto Spring Boot (Java 21, Web, JPA, Validation, PostgreSQL, Flyway, Lombok opcional), configuração do `docker-compose.yml` com PostgreSQL e primeira migration Flyway.
- **Etapa 1 — Viagens:** Implementação completa do domínio `trip` (entity, repository, service, controller, DTOs, validações e testes).
- **Etapa 2 — Gastos (Moeda Base):** CRUD de gastos vinculado à viagem operando inicialmente apenas na moeda base.
- **Etapa 3 — Câmbio:** Criação da interface `ExchangeRateProvider`, implementação via `RestClient`, lógica de conversão, persistência da cotação, tratamento de falhas e testes unitários com mock.
- **Etapa 4 — Resumo:** Query de agregação por categoria, montagem do DTO de resumo e cálculo da flag `overBudget`.
- **Etapa 5 — Qualidade e Tratamento de Erros:** Implementação do `GlobalExceptionHandler` (`ProblemDetail`), padronização das respostas de erro e conclusão da suíte de testes.
- **Etapa 6 — Entrega e Documentação:** Configuração do Swagger (`springdoc-openapi`), criação do `Dockerfile` da aplicação, ajuste do `docker-compose.yml` completo e atualização final do `README.md`.

---

## 8. Registro de Decisões Arquiteturais (ADRs)

Armazenar em `docs/decisions/` utilizando o template abaixo:

```markdown
# ADR-003: Guardar a cotação no momento do gasto

## Contexto
Gastos são feitos em moedas diferentes e a cotação varia diariamente.

## Opções
1. Guardar apenas o valor original e converter no momento da consulta.
2. Guardar a cotação e o valor convertido no momento do registro.

## Decisão
Opção 2.

## Consequências
+ Histórico fiel ao valor efetivamente pago na data do gasto.
+ Consulta de resumo rápida, sem dependência de chamadas externas.
- Redundância de dados (o valor convertido é derivável da cotação e do valor original).
- Correções de cotações registradas incorretamente exigem atualização explícita do registro.
```

---

## 9. Restrições e Boas Práticas Técnicas

- **Tipos numéricos:** Nunca utilizar `double` ou `float` para valores monetários ou taxas de câmbio; utilizar exclusivamente `BigDecimal`.
- **Isolamento de camadas:** Não inserir regras de negócio nos controllers nem expor entidades JPA diretamente nos endpoints.
- **Performance de integração:** Nunca realizar chamadas à API de câmbio dentro de laços de repetição (ex.: ao calcular o resumo).
- **Resiliência:** Tratar respostas inesperadas, timeouts ou indisponibilidade da API externa de câmbio (RN08).
- **Controle de escopo:** Novas ideias fora do escopo definido devem ser registradas como issues separadas com o rótulo `v2`.

```