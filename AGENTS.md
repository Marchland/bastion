# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and
sharp-edge notes that should travel with the code.

- Add durable project-specific notes here as they are discovered through real work.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.

## Build and test

- `./gradlew test` runs the full suite; the Spring Boot tests use Testcontainers (needs Docker).
- `./gradlew ktlintCheck` runs the linter (ktlint 1.8.0).
- Modulith module and named-interface metadata lives in Kotlin `ModuleMetadata.kt` files annotated with
  `@PackageInfo` (the Kotlin-friendly alternative to `package-info.java`). Keep one per package that needs
  `@ApplicationModule` or `@NamedInterface`.
- Jackson 3 (`tools.jackson.*`) is used, not Jackson 2; `@JsonProperty` still comes from
  `com.fasterxml.jackson.annotation`.
- JobRunr: methods invoked from a scheduled/enqueued job lambda must not use Kotlin default parameter values - JobRunr
  fails to schedule them (`UnsupportedOperationException` at `JobDetailsBuilder`). Lambda args are also serialized once
  at registration and replayed on every fire, so never pass a computed time (e.g. `Instant.now()`) through the lambda -
  compute it inside the invoked method, as `IndieAuthRowPurgeService.purge()` does.

## Module boundaries and CQRS

- Module roots contain only `ModuleMetadata.kt` plus public named-interface packages. Everything implementation-only
  lives under `internal/`; named interfaces are direct children of the module root.
- `post` public contracts:
  - `post::post-commands`: `PostCommandService`, create/update/delete commands, and `PostPatch` (protocol-neutral
    replace/add/remove/removeAll delta).
  - `post::post-query`: authenticated/internal reads (`PostQueryService`, `PostStateView`). `list` returns state views;
    protocol-specific projection (for example Micropub `q=source` property filtering) belongs in the adapter.
  - `post::post-metadata`: protocol-neutral `PostTypeView`/`PostPropertyView` metadata. Micropub maps these to its
    `q=config`/`q=properties` wire shape.
  - `post::post-syndication`: target definitions for Micropub.
  - `post::post-read`: public API reads.
  - `post::post-events`, `post::post-types`.
  - Post URL generation lives in `post/internal/url/PostUrlService`; it is not public.
- `media` is a supporting module with only `media::media-storage` public. S3 configuration/client and upload
  implementation stay in `media/internal`; the `/micropub/media` endpoint stays in Micropub because it uses Micropub
  token scopes.
- `micropub` has no public surface. Its `internal/command` and `internal/query` packages are protocol adapters over
  post commands/queries/metadata/syndication plus `media::media-storage`. Post updates are sent as `PostPatch`; the
  adapter does not merge the document itself.
- `indieauth` exposes only `indieauth::indieauth-token-query`. `AccessTokenQueryService` resolves only tokens for the
  configured Bastion identity. Token exchange/refresh/revocation commands stay internal.
- `webmention` exposes only `webmention::webmention-read`. Inbound and outbound commands stay internal. It uses
  `url::ContentUrlMatcher` for own-content checks, not `post::post-url`.
- `api` exposes nothing; it depends only on `post::post-read` and `webmention::webmention-read`.
- `config` exposes `config::config-security`; this includes the permissive `publicCorsConfigurationSource` used by
  IndieAuth and Micropub. Everything else is internal. The Micropub multipart filter lives in Micropub, not `config`.
- `PostReadService` owns public feed/lookup/tag/syndication read behavior, including query parsing, date ranges, and
  pagination validation. `api` controllers only translate HTTP params and map `PostView`/`PostLookup` to response DTOs.
- `WebmentionReadService` owns webmention read views and per-post counts. `api` maps its views to response DTOs and does
  not import internal webmention types.

## Module events

- `post` owns post-change events: `PostService` publishes `PostChangedEvent` (`post/events/`) from `create` and
  `updatePost`, classifying delete/restore. The event is intentionally thin (`postId`, change type, before/after feed
  visibility); consumers load the latest post instead of applying an event delta, so duplicate or out-of-order delivery
  converges on current state.
- Event listeners use `@ApplicationModuleListener`, which is async and includes `AFTER_COMMIT` plus
  `REQUIRES_NEW`. The `REQUIRES_NEW` is required: after-commit listeners still run with the completed transaction bound,
  so writes made without a new transaction silently do not persist (and JobRunr enqueues can be lost). Listener methods
  must not catch exceptions; the event publication registry marks failures `FAILED` so the resubmission job can retry
  them.
- `spring-modulith-starter-jdbc` owns the `event_publication` table. Modulith's JDBC schema initializer creates it at
  startup, like JobRunr owns its tables; do not add a Flyway migration. `ddl-auto: validate` does not see it.
- `@EnableAsync` on `BastionApplication` is required for `@ApplicationModuleListener`. The `spring.task.execution` pool
  is bounded so event bursts cannot create unbounded threads.
- Multi-instance event recovery: `republish-outstanding-events-on-restart=false` prevents every replica from
  republishing all incomplete publications during rolling deploys or autoscaling. The staleness monitor marks stuck
  `PUBLISHED`, `PROCESSING`, and `RESUBMITTED` publications `FAILED`. The JobRunr recurring
  `event-publication-resubmission` job has a fixed ID, so only one instance runs it per occurrence; it resubmits
  failed publications older than 2 minutes, up to 5 completion attempts. Publications at the attempt limit remain
  `FAILED` for actuator inspection.
- `completion-mode=delete` keeps the event table bounded.
- Syndication dispatch uses deterministic JobRunr IDs keyed by post id, target uid, action, and the event occurrence
  timestamp, so concurrent or replayed deliveries of the same event dedupe. A syndication job that exhausts its retries
  still needs the scaling-track periodic reconciliation.
- Async listener tests must await delivery with Awaitility or Mockito timeout. Integration tests can inspect
  `event_publication` for in-flight or `FAILED` rows; with `completion-mode=delete`, completed rows are removed.
- Webmention reconciliation (`WebmentionService.reconcile`) is idempotent: it compares notifications by `post_id`,
  current source URL, and currently linked targets, then activates, retracts, or silently inactivates as needed.
- WebSub pings when the public feed changes (`Post.isFeedVisible`: published + PUBLIC + not deleted). This includes
  demotions and deletions, but not draft or UNLISTED edits, because UNLISTED posts are reachable by URL but absent from
  the public feed.

## IndieAuth

- Bastion is its own IndieAuth provider (`dev.jacobandersen.bastion.indieauth`): no local accounts, GitHub is the only
  identity provider, the browser UI is delegated to the "Herald" service via `bastion.indieauth.herald.*`.
- Server metadata (`/.well-known/oauth-authorization-server`, `type/AuthorizationServerMetadata.kt`) advertises the
  `issuer` (trailing-slash normalized, https-only via `util/Issuers.kt`), token/introspection/revocation/userinfo
  endpoints, `service_documentation`, and `authorization_response_iss_parameter_supported=true`. The metadata URL
  itself is discovered via the `indieauth-metadata` link relation published manually on the external `me` site
  (Bastion serves no profile pages); see `DiscoveryController.kt`.
- The authorization redirect carries `code` + `state` + `iss` (`util/Redirects.kt`); `iss` must equal the metadata
  issuer for mix-up protection.
- Code redemption is split per spec: POST `/indieauth/auth` returns `{me}` only (`ProfileUrlController` +
  `ProfileUrlService`), POST `/indieauth/token` returns tokens (`TokenController` + `AccessTokenCommandService`) and
  rejects empty-scope codes with `invalid_grant`. Token responses carry `expires_in` and rotate a `refresh_token`
  (`grant_type=refresh_token`, same-or-narrower scope, single-use rotation via `RefreshTokenService` and the
  `indieauth_refresh_tokens` table, TTLs `access-token-ttl` 30d / `refresh-token-ttl` 90d).
- Introspection (`/indieauth/introspect`, Bearer-gated, RFC 7662 plus `me`), revocation (`/indieauth/revocation`,
  always 200), and userinfo (`/indieauth/userinfo`, needs `profile`/`email` scope) are served and advertised.
  Profile claims come from static `bastion.indieauth.profile.*` config (`ProfileClaimService`); `email` needs both
  `profile` and `email` scopes. Default scopes include `email` (`IndieAuthConfig`).
- Micropub token validation (`MicropubTokenValidator`) uses `AccessTokenQueryService` from
  `indieauth::indieauth-token-query`; the query service enforces the configured `bastion.indieauth.me`, so Micropub does
  not read that config directly. Token commands stay inside IndieAuth.
- Raw `state`/`code`/`access_token`/`refresh_token` values are never persisted - only their SHA-256 digests
  (`indieauth/internal/security/Tokens.kt`). PKCE is lenient for max client compat (deprecated): a missing `code_challenge`
  is accepted with a warning, S256-only when present, and the redemption enforces the conditional rule (no challenge
  means no verifier, challenge means a matching verifier is required).
- Client validation (`indieauth/internal/util/IndieAuthUrls.kt`, `indieauth/internal/service/ClientMetadataFetcher.kt`) enforces strict profile/client URL
  rules and the 4.2.2 cross-host redirect allowlist (JSON `redirect_uris`, `Link rel=redirect_uri`, HTML link tags).
  Loopback clients are never fetched; inconclusive fetches allow with a warning (fail-open), a fetched allowlist
  missing the target blocks.
- Adding a provider = one new `IdentityProvider` implementation plus config.

## Syndication

- Syndication now lives in `post/internal/syndication`; `micropub` only exposes target choices and sends requested
  target UIDs through `PostCommandService`. The config prefix remains `bastion.micropub.syndication.targets` for
  deployment compatibility.
- `PostCommandService` syncs desired target rows (`post_syndications.desired`), and `PostChangedEvent` triggers
  `PostSyndicationListener` -> `SyndicationService.reconcile`. The listener is `AFTER_COMMIT` with `REQUIRES_NEW`.
- `post_syndications` also stores the canonical `source_url` of the held copy. Reconciliation creates missing copies,
  sends excerpt updates for existing copies, rebases when the post URL moved, and retracts copies when the post is
  deleted, non-public, or the target is no longer desired. `markRetracted` clears the held-copy state so repeated
  events do not enqueue duplicate retractions.
- Dispatch jobs are best-effort `@Job(retries = 0)` and never fail the post command. Non-public posts are never sent.
- The downstream copy is an excerpt plus permalink (`SyndicationContentMapper.kt`): notes send `{excerpt}\n\n{url}`,
  articles (`type == "article"`) send `{name}: {excerpt}\n\n{url}`. `maxGraphemes` counts grapheme clusters; the
  permalink is never dropped and `summary` is overwritten with the same text so full content cannot leak.
- Updates use `SyndicationHttpClient.serializeExcerptUpdate`: a full `content`/`summary`/`url` (and article `name`)
  replace built from current post state, not the original Micropub delta. This keeps reconciliation idempotent.
- `mp-syndicate-to` is translated by Micropub into a protocol-neutral `SyndicationTargetChange`
  (`replace`/`add`/`remove`); the post command applies it to the current desired target set.

## Webmention

- Outbound notification state is reconciled from post events (`PostWebmentionListener` ->
  `WebmentionService.reconcile`). Notifications are keyed to a post via `webmention_notifications.post_id`; a source
  URL that no longer matches the post retracts delivered notifications (fixes slug moves) and silently inactivates
  undelivered ones. `refresh` re-sends to delivered targets on updates; deletions only retract.
- Self-webmentions are suppressed: `ContentUrlMatcher.isOwnContentUrl` (host + port match on `bastion.content.base-url`,
  lenient on scheme) filters targets in `WebmentionService.targetUrlsOf` plus a guard in `sendWebmention`, and
  `WebmentionController` rejects sources on the own content domain.
