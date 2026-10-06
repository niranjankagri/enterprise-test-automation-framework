# ADR-007: Page Objects composed of Component Objects

Status: accepted

- **Problem:** the header, menu, tables, dialogs and toasts appear on many screens. Coding them into every page object duplicates locators and logic.
- **Options:** (a) one large page object per screen; (b) page objects with inheritance for shared parts; (c) page objects composed of component objects.
- **Decision:** (c). `BasePage` and `BaseComponent` provide only driver, waits and actions. `ShopPage<T>` adds the application's shared layout and its ready contract. Components are scoped to a root element, so the same `TableComponent` serves customers, products, orders and the cart.
- **Reason:** each locator lives in exactly one class; a change in the table markup is one fix. Composition also lets a page hold two tables without confusion, which inheritance cannot express.
- **Trade-offs:** more, smaller classes. Navigation methods return the next page already loaded, which ties some pages together, but keeps tests readable.

[All decisions](README.md) · [Architecture](../architecture.md)
