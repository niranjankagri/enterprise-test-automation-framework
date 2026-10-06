# ADR-010: API services return raw responses and typed results

Status: accepted

- **Problem:** API tests need two different things: negative tests must inspect status codes, headers and error bodies, while setup, clean-up and happy paths just want "create a customer and give me its id".
- **Options:** (a) services return only models (negative tests cannot see status codes); (b) services return only raw responses (every test repeats status checks and JSON mapping); (c) both, clearly named.
- **Decision:** (c). `create(...)`, `get(...)`... return the REST Assured `Response`; `createCustomer(...)`, `getCustomer(...)`... check the expected status (failure message includes the masked body) and return a record. `ApiClient` is immutable and builds every request from scratch; the framework owns its Jackson mapper.
- **Reason:** tests read as intent in both cases, and no test re-implements HTTP plumbing. Owning the mapper keeps (de)serialization stable regardless of which JSON library REST Assured detects.
- **Trade-offs:** two methods per operation. Response models ignore unknown fields, so contract drift is caught by the strict JSON schemas rather than by the models.

[All decisions](README.md) · [Architecture](../architecture.md)
