# ADR-001: Estrutura de pacotes por feature

- **Status:** Aceita
- **Data:** 2026-11-25

## Contexto

A Travel Expense API é um monolito Spring Boot com poucos domínios bem definidos: viagens, gastos, conversão de moeda e resumo da viagem. Antes de escrever o primeiro código de negócio, é preciso decidir como organizar os pacotes dentro de `br.com.vanesssadb.travel_expense_api`.

A escolha afeta:

- a facilidade de encontrar todo o código de uma funcionalidade;
- o controle de visibilidade entre partes do sistema (o que é público e o que é detalhe interno);
- o custo de evoluir o monolito, ou de extrair um módulo no futuro, se for preciso.

## Opções

### 1. Pacotes por camada

```
controller/   TripController, ExpenseController
service/      TripService, ExpenseService, ExchangeRateService
repository/   TripRepository, ExpenseRepository
model/        Trip, Expense
dto/          ...
```

- **Prós:** padrão muito comum em tutoriais e projetos Spring; fácil de entender no início.
- **Contras:** uma mudança em "gastos" espalha edições por quatro ou cinco pacotes. Tudo precisa ser `public` para atravessar pacotes, então o compilador não impede que um controller de viagens use direto o repositório de gastos. Os pacotes crescem com o número de features e não dizem nada sobre o domínio.

### 2. Pacotes por feature

```
trip/         TripController, TripService, TripRepository, Trip, dto...
expense/      ExpenseController, ExpenseService, ExpenseRepository, Expense, dto...
exchange/     ExchangeRateClient, ExchangeRateService
summary/      TripSummaryController, TripSummaryService
shared/       tratamento de erros, configuração, utilitários
```

- **Prós:** o código de uma funcionalidade fica junto; a estrutura mostra o domínio. Classes internas podem ter visibilidade de pacote (sem `public`), e só a API da feature fica exposta. Fronteiras claras facilitam testes por feature e uma eventual extração de módulo.
- **Contras:** exige decidir onde fica o código compartilhado (`shared/`) e cuidar das dependências entre features (por exemplo, `expense` depende de `trip` e de `exchange`) para não criar ciclos.

### 3. Arquitetura hexagonal / clean architecture completa

Pacotes `domain`, `application`, `adapters` (in/out), com portas e adaptadores para cada integração.

- **Prós:** isolamento forte do domínio em relação a frameworks e infraestrutura.
- **Contras:** muita cerimônia (interfaces, mapeadores, camadas extras) para um domínio pequeno com uma única integração externa. O custo não se paga no escopo da v1.

## Decisão

Adotar **pacotes por feature** (opção 2).

Regras:

1. Cada feature (`trip`, `expense`, `exchange`, `summary`) tem seu próprio pacote, com controller, service, repository, entidades e DTOs.
2. Classes que não precisam ser usadas fora da feature ficam com visibilidade de pacote.
3. Uma feature usa outra apenas pelo seu service público, nunca pelo repositório ou pela entidade de outra feature diretamente.
4. Código transversal (handler de exceções global, configurações, formato de erro) fica em `shared/`. Só entra lá o que é usado por mais de uma feature.
5. Dependências entre features não podem formar ciclos.

## Consequências

**Positivas**

- Mudanças em uma funcionalidade ficam concentradas em um pacote.
- O compilador ajuda a manter as fronteiras, por causa da visibilidade de pacote.
- A estrutura do projeto documenta o domínio para quem chega.
- Se for necessário, uma feature pode virar um módulo Maven ou um serviço separado com pouco retrabalho.

**Negativas / riscos**

- `shared/` pode virar um depósito genérico. Mitigação: só entra o que é de fato usado por mais de uma feature.
- Dependências entre features podem crescer sem controle. Mitigação: revisar nas PRs e, se necessário, adicionar testes de arquitetura (ArchUnit ou Spring Modulith) para verificar ciclos e acessos indevidos.
- Quem está acostumado com pacotes por camada precisa se adaptar à convenção.
