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
 * <p><b>Only the live console model is checked, and the other three JDL files are deliberately out
 * of scope.</b> {@code admin-db.jdl}, {@code admin-ms.jdl} and {@code system.jdl} predate the console
 * model and are known to disagree with the code — {@code hc-admin-console.jdl}'s own comment on
 * {@code FacilityType} records that {@code admin-db.jdl} "still declares {@code Facility.type} as a
 * String and has been wrong since the enum was introduced". Sweeping them would produce a long red
 * list nobody intends to act on, which is the fastest way to have a test disabled. Naming one file
 * is a real limit, and it is stated rather than hidden.
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
        return Arrays
            .stream(type.getDeclaredFields())
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
        assertThat(MODEL).as("run from the module directory: %s is the live JDL model", MODEL.toAbsolutePath()).isRegularFile();
        try {
            return Files.readString(MODEL);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + MODEL.toAbsolutePath(), e);
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
