package net.jojoaddison.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The live JDL model declares the same <b>value</b> fields the domain classes carry.
 *
 * <p><b>{@link net.jojoaddison.domain.enumeration.JhipsterEnumFieldValuesTest} cannot see this class
 * of drift, and it is the one that bit on 2026-09-04.</b> That test checks the values of an enum a
 * generator input names; this checks that the input names the field at all. {@code WageRate} gained
 * a required {@code shiftType} in the superset-enum change and {@code jdl/hc-admin-console.jdl} was
 * not updated, so regenerating that entity would have dropped a required field from the domain, the
 * DTO and the mapper — and an enum sweep sees nothing, because the enum it would check is not
 * mentioned. It also found {@code entity Facility} declaring no {@code id}, alone among its
 * siblings.
 *
 * <p><b>Associations are out of scope, and the filter is the field's type rather than its name.</b>
 * JDL declares a relationship in a separate {@code relationship} block, not in the entity body, so a
 * {@code Patient.profile} is correctly absent from {@code entity Patient}. Matching those by name
 * would not work anyway: JHipster names the collection side singular in JDL and plural in Java —
 * {@code Category{activity}} is {@code Set<ServiceActivity> activities} — so this excludes any field
 * whose type is a class in this package, or a collection of one. Nothing has to be listed and the
 * pluralisation rule never has to be reimplemented here.
 *
 * <p><b>Only the live console model is checked, and the other JDL files are deliberately out of
 * scope — but the exclusion is now checked rather than merely stated</b> (backlog item 21, decided
 * 2026-09-06). {@code admin-db.jdl} and {@code system.jdl} predate the console model and are known
 * to disagree with the code: all ten entities of the first and both of the second, measured
 * 2026-09-04 under this test's own rules. {@code admin-db.jdl}'s {@code Profile} declares
 * {@code personId}, {@code photo}, {@code contact}, {@code addressList}, {@code roles},
 * {@code status}, {@code organizationId} and {@code teamId}, and the class carries none of them —
 * a <em>different model</em>, not a stale copy of this one. Sweeping them would produce a long red
 * list nobody intends to act on, which is the fastest way to have a test disabled.
 *
 * <p>What {@link #everyJdlBesideTheLiveModelDeclaresItselfHistorical} does instead is require every
 * {@code .jdl} in the directory to say which it is, so the answer to "which of these is real" lives
 * in the files rather than in this javadoc. That is the half a stated limit could not give: naming
 * the excluded files here is a list, and a list stops covering things — a fifth {@code .jdl} added
 * tomorrow would have been silently unswept and indistinguishable from the live one to any reader
 * opening the directory. {@code admin-ms.jdl} was a third excluded name here until 2026-09-06 and
 * had been <b>zero bytes since the day it was added</b>, so its clean result was vacuous; it was
 * deleted rather than marked, because a generator input that nothing can generate from is a trap and
 * an empty file cannot even be a record.
 *
 * <p><b>Fields are read by reflection, not from the source.</b> {@code getDeclaredFields()} returns
 * exactly what the class itself declares, so the auditing fields inherited from
 * {@link AbstractAuditingEntity} — which JDL never declares — are excluded for free rather than by a
 * name list, and so is {@code serialVersionUID}, being static. {@code Facility} is the one entity
 * here that predates that base class and declares its own four, which is what {@link #AUDITING} is
 * for.
 *
 * <p>Checked in <b>both</b> directions. A field in the code and not in the JDL is lost on the next
 * regeneration; a field in the JDL and not in the code is silently added by it. Neither is a state
 * anybody chose.
 */
class JdlEntityFieldsTest {

    /** The live model. Relative to the module directory, which is surefire's working directory. */
    private static final Path MODEL = Path.of("jdl", "hc-admin-console.jdl");

    /**
     * The two things a {@code .jdl} in that directory may declare itself, on a line of its own.
     *
     * <p>A marker rather than a filename list, for the reason this whole class exists: a list here
     * would be the hand-maintained enumeration that stops covering things, and the fact belongs to
     * the file a reader has open rather than to a test they have not found.
     */
    private static final String LIVE_MARKER = "// JDL STATUS: LIVE";

    private static final String HISTORICAL_MARKER = "// JDL STATUS: HISTORICAL";

    private static final String DOMAIN_PACKAGE = "net.jojoaddison.domain";

    private static final Pattern ENTITY = Pattern.compile("\\bentity\\s+(\\w+)\\s*(?:\\([^)]*\\))?\\s*\\{([^}]*)}");

    /** A JDL field line: {@code digitalAddress String required maxlength(20)}. */
    private static final Pattern FIELD = Pattern.compile("^(\\w+)\\s+\\w+");

    /**
     * The auditing fields, under both spellings this codebase uses.
     *
     * <p><b>JDL declares auditing for no entity anywhere</b> — it is stamped server-side by
     * {@code AuditingEntityCallback} and carried on no DTO — so these are never a disagreement.
     * Most classes inherit them from {@link AbstractAuditingEntity} and are excluded for free by
     * {@code getDeclaredFields()}; {@code Facility} is the one entity in this model that predates
     * that class and declares its own, which is why the list has to exist at all. Both spellings are
     * named because the two differ: the base class says {@code lastModified*}, the older classes say
     * {@code modified*}.
     */
    private static final List<String> AUDITING = List.of(
        "createdBy",
        "createdDate",
        "modifiedBy",
        "modifiedDate",
        "lastModifiedBy",
        "lastModifiedDate"
    );

    @Test
    void everyEntityInTheLiveJdlDeclaresExactlyTheValueFieldsItsDomainClassCarries() {
        List<String> disagreements = new ArrayList<>();

        for (Map.Entry<String, List<String>> entity : entities().entrySet()) {
            String name = entity.getKey();
            List<String> declared = entity.getValue();
            Class<?> type;
            try {
                type = Class.forName(DOMAIN_PACKAGE + "." + name);
            } catch (ClassNotFoundException e) {
                disagreements.add("%s declares entity %s, which is no class in %s".formatted(MODEL, name, DOMAIN_PACKAGE));
                continue;
            }

            List<String> carried = valueFields(type);
            for (String field : carried) {
                if (!declared.contains(field)) {
                    disagreements.add(
                        "%s.%s is a value field of the domain class and %s does not declare it — regenerating %s drops it".formatted(
                            name,
                            field,
                            MODEL,
                            name
                        )
                    );
                }
            }
            for (String field : declared) {
                if (!carried.contains(field)) {
                    disagreements.add(
                        "%s declares %s.%s and the domain class has no such value field — regenerating %s adds it".formatted(
                            MODEL,
                            name,
                            field,
                            name
                        )
                    );
                }
            }
        }

        assertThat(disagreements)
            .as("%s is a generator input: it must declare exactly the value fields the domain classes carry", MODEL)
            .isEmpty();
    }

    /**
     * Every {@code .jdl} in the directory says whether it is the live model or a historical record.
     *
     * <p>The classification is the point, not the marker. Four files sat in {@code jdl/} with
     * nothing distinguishing the one a regeneration would run from the two that describe a model
     * this service has not had since 2026-08 and the one that was empty — so a reader had no way to
     * tell, and the honest answer was recoverable only from this test's javadoc, which is the wrong
     * place for it. Now the file says it, and this fails on a file that says neither.
     *
     * <p><b>The live marker is asserted too, not just the historical ones.</b> Marking every file
     * historical would satisfy a check that only looked for the word and would leave the model this
     * test sweeps claiming it is not one.
     */
    @Test
    void everyJdlBesideTheLiveModelDeclaresItselfHistorical() {
        List<Path> models;
        try (var files = Files.list(MODEL.getParent())) {
            models = files
                .filter(path -> path.getFileName().toString().endsWith(".jdl"))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list " + MODEL.getParent().toAbsolutePath(), e);
        }

        // A directory listing that found nothing would pass every assertion below.
        assertThat(models).as("%s holds the JDL models", MODEL.getParent()).contains(MODEL);

        List<String> unclassified = new ArrayList<>();
        for (Path model : models) {
            String source = read(model);
            boolean live = source.contains(LIVE_MARKER);
            boolean historical = source.contains(HISTORICAL_MARKER);
            if (model.equals(MODEL)) {
                if (!live) {
                    unclassified.add("%s is the live model and does not carry `%s`".formatted(model, LIVE_MARKER));
                }
            } else if (!historical) {
                unclassified.add(
                    (
                        "%s carries neither `%s` nor `%s` — a .jdl in this directory must say which it is, " +
                        "because nothing else distinguishes a generator input from a record of one"
                    ).formatted(model, LIVE_MARKER, HISTORICAL_MARKER)
                );
            } else if (live) {
                unclassified.add("%s claims to be both live and historical".formatted(model));
            }
            // An empty file can be neither: it is not a model and it is not a record of one, and its
            // clean sweep result reads as agreement. admin-ms.jdl was exactly that for five weeks.
            if (source.isBlank()) {
                unclassified.add("%s is empty — delete it rather than classifying it".formatted(model));
            }
        }

        assertThat(unclassified)
            .as("every .jdl beside %s must declare whether a regeneration should read it", MODEL.getFileName())
            .isEmpty();
    }

    @Test
    void theSweepFindsEntitiesAndFieldsToCheck() {
        // A parser that silently matches nothing passes forever. Floors, not totals — an exact count
        // would be the hand-maintained list this test exists to avoid.
        Map<String, List<String>> entities = entities();
        assertThat(entities).as("entity blocks in %s", MODEL).hasSizeGreaterThan(20);
        assertThat(entities.values().stream().mapToInt(List::size).sum()).as("declared fields in %s", MODEL).isGreaterThan(100);
    }

    /** Entity name to its declared field names, in declaration order. */
    private static Map<String, List<String>> entities() {
        Map<String, List<String>> entities = new LinkedHashMap<>();
        Matcher matcher = ENTITY.matcher(normalise(read()));
        while (matcher.find()) {
            List<String> fields = new ArrayList<>();
            for (String line : matcher.group(2).split("\n")) {
                Matcher field = FIELD.matcher(line.strip());
                if (field.find()) {
                    fields.add(field.group(1));
                }
            }
            entities.put(matcher.group(1), fields);
        }
        return entities;
    }

    /**
     * The class's own non-static fields that are not associations: everything JDL declares inside an
     * {@code entity} block, and nothing it declares in a {@code relationship} block.
     */
    private static List<String> valueFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
            .filter(field -> !isAssociation(field))
            .map(Field::getName)
            .filter(name -> !AUDITING.contains(name))
            .toList();
    }

    private static boolean isAssociation(Field field) {
        if (isDomainClass(field.getType())) {
            return true;
        }
        if (Collection.class.isAssignableFrom(field.getType()) && field.getGenericType() instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            return arguments.length == 1 && arguments[0] instanceof Class<?> element && isDomainClass(element);
        }
        return false;
    }

    /**
     * A class in {@code net.jojoaddison.domain} itself — not in {@code .enumeration} beneath it,
     * which is where the enum-typed value fields point and which JDL does declare in the entity.
     */
    private static boolean isDomainClass(Class<?> type) {
        return type.getPackageName().equals(DOMAIN_PACKAGE);
    }

    private static String read() {
        return read(MODEL);
    }

    private static String read(Path model) {
        assertThat(model).as("run from the module directory: %s is a JDL model", model.toAbsolutePath()).isRegularFile();
        try {
            return Files.readString(model);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + model.toAbsolutePath(), e);
        }
    }

    /**
     * Comments and validation regexes removed, in that order.
     *
     * <p>JDL carries {@code /** … *}{@code /} documentation above most blocks and {@code //} lines
     * between them. <b>The {@code pattern(…)} strip is the less obvious half and it is not
     * cosmetic:</b> {@code Address.digitalAddress} is validated by
     * {@code pattern(/^[A-Z]{2}-[0-9]{3}-[0-9]{4}$/)}, whose quantifiers contain closing braces, so
     * an entity body matched up to the first {@code &#125;} stops in the middle of that line and the
     * five fields after it read as undeclared. The first run of this test reported exactly that and
     * it was the parser, not the model.
     */
    private static String normalise(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//.*", "").replaceAll("pattern\\(/(?:[^/\\\\]|\\\\.)*/\\)", "");
    }
}
