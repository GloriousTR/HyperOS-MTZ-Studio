# v2.2.5 test notes

- Modern Theme Manager bridge dispatch now requires the module's current-process readiness marker, not only an approved Vector/LSPosed scope.
- When the marker is absent, Studio retains the verified MTZ backup and opens Xiaomi Themes' manual local-library route instead of starting a bridge request that cannot return a callback.
- The change preserves user-selected scopes: it does not force-stop Themes, rewrite scopes or enable/disable modules.
