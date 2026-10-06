/**
 * API layer. Tests call services, services call {@code ApiClient}, the client calls the REST API:
 * <pre>
 * Test → ApiSession (account) → Service (one per resource) → ApiClient → REST API
 * </pre>
 * {@code ApiClient} owns base URL, JSON, bearer token and logging ({@code ApiLoggingFilter}, secrets
 * masked by {@code SecretMasker}); {@code ApiAssertions} checks status and JSON schemas.
 */
package com.enterprise.automation.api;
