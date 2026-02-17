/**
 * Shared outbox infrastructure for cross-module async events.
 *
 * <p>This package includes write-path contracts, optional JPA persistence, Redis Stream relay,
 * and consumer-group scaffolding. DDL and business consumer handlers are deferred until entity
 * model review and module integration are finalized.
 */
package com.wtfrepo.backend.shared.outbox;
