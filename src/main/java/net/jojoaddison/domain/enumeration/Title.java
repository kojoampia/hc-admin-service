package net.jojoaddison.domain.enumeration;

/**
 * Abofonsa BridgeCare — console domain model, applied to hc-admin-service.
 *
 * This is `app/hc-admin.jdl` with two things removed:
 *
 * 1. the `application { ... }` block, which configures the FRONTEND
 * (skipServer, Angular, jhiPrefix abf). This repository has its own
 * .yo-rc.json describing a MongoDB microservice, and `jhipster jdl`
 * applies the entities below into that existing context.
 *
 * 2. `Credential` and `CredentialRole`. Those are the gateway's Account and
 * Authority: hc-admin-gateway owns user records, this service owns
 * everything else. The link between them is `Profile.accountId`, a plain
 * String rather than a relationship, because the two live in different
 * databases and cannot be joined.
 *
 * Every entity declares `id String`. This is MongoDB — ids are ObjectIds, and
 * letting JHipster default them to Long produces a service the console cannot
 * round-trip against.
 *
 * The two files must stay in step: the Angular client was generated from the
 * same declarations, so a field added here and not there is a field the
 * console will silently never send.
 *
 * FOUR ENTITY NAMES COLLIDE with this service's earlier model and are
 * replaced by the definitions below: Address, Message, Organisation, Team.
 * The earlier shapes remain in git and in jdl/admin-db.jdl, jdl/admin-ms.jdl
 * and jdl/system.jdl.
 */
public enum Title {
    MR,
    MRS,
    MS,
    DR,
    PROF,
}
