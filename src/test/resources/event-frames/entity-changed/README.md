# Captured `EntityChanged` frames — backlog item 124(a)

One frame per product, each a copy of the `EntityChanged` event that product publishes on its
`<product>.event` channel. `EntityChangedEnvelopeTest` iterates every `*.json` in this directory and
asserts the common envelope — add a fifth producer's frame here and it is covered the moment the file
exists, with no test edit.

## ⚠ A fixture is a copy, and this directory is a NARROWING, not a guarantee

If a producer changes its frame and nobody updates the copy here, the copy is stale and the test goes
on passing. What this artefact buys is that the **agreed envelope, as verified on 2026-09-24, fails
mechanically in this repo if a future edit here or a new fixture diverges from it** — nothing more.
Schema validation at consume time (item 109 / decision (c)) is the half that sees a live divergence.

## Provenance

Each frame is derived from the producer class at the commit named below — envelope keys, `type`,
`version`, `source` and `action` values are the producer's; the identifier values (`eventId`,
`entityId`, `actorAccountId`, `occurredAt`, `entityType`) are exemplar, since the frame carries
identifiers and metadata only and no real capture would be more authoritative about the shape.

Three shape facts the exemplar values deliberately preserve, because erasing them would hide real
cross-product differences: `hc-vendor.json`'s `entityId` is **`Long`-shaped text** — their
`VendorEvent.Subject` javadoc says _"Every entity in this subsystem has a `Long` id; it is carried
as text"_, and `VendorEntityEventIT:163` asserts `String.valueOf(saved.getId())` — where the three
Mongo products carry ObjectId-shaped strings; `hc-professional.json`'s `data` **omits
`actorAccountId`**; and `hc-patient.json` carries it as an **explicit `null`**. The last two are
divergence (iii) below, one pole each.

| frame                  | producer class (at that SHA)                                   | `origin/main` SHA                          |
| ---------------------- | -------------------------------------------------------------- | ------------------------------------------ |
| `hc-admin.json`        | `src/main/java/net/jojoaddison/broker/AdminEntityEvent.java`   | `d0675e1b8888612c877d005fc0a4d27466767fe5` |
| `hc-patient.json`      | `src/main/java/net/jojoaddison/service/event/EntityEvent.java` | `216a44ca29ae1fe0b62259e59dbc4568482b22af` |
| `hc-professional.json` | `src/main/java/net/jojoaddison/broker/EntityChangeEvent.java`  | `2cc8b3b5c9ff1b1f487773425f4150d2d93de5df` |
| `hc-vendor.json`       | `src/main/java/net/jojoaddison/vendor/broker/VendorEvent.java` | `751766161f402cc75c42e2cf1c7ccae02cffe35c` |

## The three real divergences these frames make visible

Recorded here and in the test's javadoc so the next reader does not rediscover them. **Do not fix
any of them** — each is another product's code or an argued decision.

**(i) `data.action` values do not agree.** hc-admin publishes `SAVED`/`DELETED`; the other three
publish `CREATED`/`UPDATED`/`DELETED`. hc-admin's `SAVED` is deliberate and argued at length in
`AdminEntityEvent.SAVED`'s javadoc — records here carry client-assigned ids almost everywhere, so the
id heuristic the siblings use would report nearly every insert as `UPDATED`. A consumer reading all
four channels sees **four** values. The test therefore asserts only that the key exists as a
non-blank string, never that the value is in a fixed set.

**(ii) `source` has two naming conventions**, and the four exact values a consumer switching on it
needs: `hcAdminService`, `hcPatientService` (camelCase) vs `hc-professional-service`,
`hc-vendor-service` (kebab-case). `source` is free-form by design, so the test asserts only a
non-blank string.

**(iii) An actorless write is spelled two opposite ways, both argued, under one `type` string.**
hc-professional **omits the key** — `DomainEventPublisher:494-498` at `2cc8b3b5`:

> `// OMITTED, not "system" and not null — see the javadoc. Absence is the only unambiguous way`
> `// to say that no account was behind this write.`

hc-patient puts it **unconditionally, null and all** — `EntityEventPublisher` at `216a44ca`, pinned
on real broker bytes in `EntityEventRoundTripIT:89,95`:

> `// Put unconditionally, null and all, so the payload has one shape rather than two. A consumer`
> `// that has to [distinguish] ... an explicit null says which.`

Both publishers fire on **every** write, so seeds and system jobs produce actorless frames
routinely — this is not an edge. The test therefore requires `action` and permits no key outside
`{action, actorAccountId}`, and never requires both keys: a consumer built with `actorAccountId`
required (item 109 / decision (c)) would refuse every actorless hc-professional frame while the
binding binds and lag stays zero.

**Decided — backlog item 129, architect, 2026-09-24: omit the key everywhere.** hc-admin's producer
now omits it on actorless writes (a `@JsonInclude` on `AdminEntityEvent.ChangeData.actorAccountId`),
joining hc-professional's pole. hc-patient's explicit null is still what **their** producer publishes
until their own change lands, so the divergence remains live on the wire and `hc-patient.json` stays
as captured — this directory records what the producers do, not what was decided. The test's
never-require-both-keys shape is unchanged and is now doubly right.
