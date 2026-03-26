# Documentation

Use the root [README.md](../README.md) for setup and first run. Use the pages here when you want the details behind the product flow.

## Reading order

| Document                                       | When to read it                                                                                              |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| [architecture.md](architecture.md)             | You want the full system picture from React UI to Spring Boot, database, and AI provider                     |
| [negotiation-engine.md](negotiation-engine.md) | You want to understand how the backend scores offers, applies limits, and decides accept, counter, or reject |
| [parsing.md](parsing.md)                       | You want the supplier-message parsing rules, intent types, clarification path, and concrete examples         |
| [testing.md](testing.md)                       | You want the test strategy, how to run the suites, interpret the matrix, and see remaining gaps              |
| [strategies.md](strategies.md)                 | You want to compare the supported buyer strategies and see how they differ in practice                       |
| [api.md](api.md)                               | You want the REST endpoints, payloads, and concrete request examples                                         |
| [../TASK.md](../TASK.md)                       | You want the original challenge brief and evaluation context                                                 |

## Documentation scope

- Root setup and quick start live in [../README.md](../README.md)
- System structure lives in [architecture.md](architecture.md)
- Negotiation mechanics live in [negotiation-engine.md](negotiation-engine.md)
- Supplier-message parsing rules and examples live in [parsing.md](parsing.md)
- Test strategy, suites, and testing gaps live in [testing.md](testing.md)
- Strategy behavior lives in [strategies.md](strategies.md)
- API surface and examples live in [api.md](api.md)

## Notes

- The backend represents the buyer.
- The frontend is the supplier-facing web interface.
- The same buyer engine can also be used behind email or chat communication with suppliers.
- AI helps parse supplier messages and generate wording, but the final negotiation decision remains rule-based.
- Strategy changes are manual.
