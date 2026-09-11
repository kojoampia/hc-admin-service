/**
 * Mongock change units — one-time reshapings of stored data that must happen in <em>every</em> environment.
 *
 * <p>{@code mongock.migration-scan-package} in {@code config/application.yml} has pointed here since the
 * generator's first commit and {@code DatabaseConfiguration} has carried {@code @EnableMongock} with it;
 * the package simply held nothing until backlog item 56. {@code config/ShiftTypeMigration}'s javadoc says
 * <em>"neither repo has Liquibase or Mongock"</em> and is wrong about this one — it is an
 * {@code ApplicationRunner} because that migration is a startup sweep that reports as much as it rewrites,
 * not because there was no change-unit seam.
 *
 * <p><b>A change unit is right for reshaping and wrong for seeding.</b> It has no notion of a Spring
 * profile and runs wherever the application runs, which is what cost the gateway a production incident
 * when account seeding was put in one. Development data belongs in {@code DevelopmentDataInitializer},
 * which is profile-gated; anything here applies to production by construction.
 */
package net.jojoaddison.config.dbmigrations;
