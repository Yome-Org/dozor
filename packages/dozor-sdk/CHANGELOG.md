# Changelog

All notable changes to this package will be documented in this file.

The format is based on Keep a Changelog and this project follows Semantic Versioning.

## [0.1.0] - 2026-03-07

### Added
- Initial `@yome-network/dozor-sdk` release.
- Typed client for Dozor signal ingestion (`DozorClient`).
- Runtime request/response validation using Zod.
- Pluggable transport interface with first HTTP transport implementation.
- Typed error model (`DozorValidationError`, `DozorTransportError`, `DozorProtocolError`).
- Retry policy for transient transport failures.
- Client hooks (`onRequest`, `onResponse`, `onError`).
- Optional logger injection and request payload redaction for observability.
- Unit tests, coverage thresholds, integration-test entrypoint.
