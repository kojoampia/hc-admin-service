package net.jojoaddison.domain.enumeration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The generator inputs in {@code .jhipster/} and {@code jdl/} must declare exactly the enum values
 * the code has.
 *
 * <p>Ported from hc-professional's test of the same name on 2026-09-04, and it found three
 * disagreements on its first run — see the list at the bottom of this comment. That repo's copy
 * exists because {@code .jhipster/DutyRoster.json} went on declaring the retired {@code MORNING} and
 * {@code AFTERNOON} for a fortnight with nothing failing to build; hc-admin had no equivalent in
 * {@code api/} or in {@code app/}, and the superset-enum change of 2026-09-04 left three inputs on
 * the four-value shape for exactly that reason.
 *
 * <p><b>These files are generator inputs, not documentation.</b> The next {@code jhipster entity}
 * run emits the enum from {@code fieldValues}, so a stale value here is not merely wrong — it comes
 * back. {@code ShiftAssignment.json} on the old four values rewrites {@code shift-type.model.ts}
 * <em>and</em> {@code operations-shiftType.json} together, so the two agree at four and
 * {@code enum-coverage.spec.ts} in {@code app/} stays green while {@code FLEXIBLE} disappears from
 * the console.
 *
 * <p><b>Two sources, and hc-admin needs both.</b> hc-professional keeps only {@code .jhipster};
 * this repo also carries hand-written {@code jdl/*.jdl}, which is what a regeneration is actually
 * driven from, and the two can disagree with each other as well as with the code. Every
 * {@code enum X { … }} declaration in any {@code .jdl} is checked the same way as every
 * {@code fieldValues} string.
 *
 * <p><b>Nothing here is a list of names.</b> The files are found by walking the directories, the
 * enums by the {@code fieldType} or the declaration name, and the expectation by
 * {@link Class#getEnumConstants()}. Adding a value to any enum a generator input names fails this
 * test until that input is updated, with nobody having edited this file. A test whose coverage has
 * to be extended by hand silently stops covering things: {@code PaginationIT} asserted a literal
 * list of twenty-three paths and eight endpoints went unpaginated behind it for a fortnight.
 *
 * <p><b>"Any enum a generator input names" is narrower than "any enum in this package."</b>
 * {@code EarningsGranularity}, {@code MessageType}, {@code RoleType} and
 * {@code UnavailabilityReason} are named by no {@code .jhipster} file and no JDL enum declaration,
 * so adding a value to one of them fails nothing here and nothing else covers them either. That is
 * the honest state rather than a gap to paper over — they belong to hand-written DTOs and
 * projections with no generator input to drift from.
 *
 * <p><b>Order is part of the contract</b>, not only membership: the generator emits the constants in
 * the order the input lists them, so a set comparison would pass on an input that regenerates a
 * differently-ordered enum.
 *
 * <p>What it caught when it was written, all three of them silent:
 *
 * <ul>
 *   <li>{@code jdl/hc-admin-console.jdl} — {@code enum ShiftType} was correct, but
 *       {@code entity WageRate} declared no {@code shiftType} field at all. That one is
 *       {@link net.jojoaddison.domain.JdlEntityFieldsTest}'s, not this test's.
 *   <li>{@code .jhipster/Professional.json} — {@code ProfessionalRole} without {@code THERAPIST}.
 *   <li>{@code .jhipster/Professional.json} — {@code VerificationStatus} without {@code REVOKED}
 *       and {@code EXPIRED}, which arrived with the verification history.
 * </ul>
 */
class JhipsterEnumFieldValuesTest {

    /** Where the generated Java enums live; {@code fieldType} names a class in here. */
    private static final String ENUMERATION_PACKAGE = "net.jojoaddison.domain.enumeration";

    /** Both relative to the module directory, which is surefire's working directory. */
    private static final Path DEFINITIONS = Path.of(".jhipster");

    private static final Path JDL = Path.of("jdl");

    /** {@code enum ShiftType { DAY, EVENING, NIGHT, OFF, FLEXIBLE }}, on one line as JDL writes it. */
    private static final Pattern JDL_ENUM = Pattern.compile("\\benum\\s+(\\w+)\\s*\\{([^}]*)}");

    /** One enum declaration: which file it came from, which enum it names, and what it says. */
    private record EnumDeclaration(String file, String where, String enumType, List<String> declaredValues) {}

    @Test
    void everyEnumFieldInAGeneratorInputDeclaresExactlyItsEnumsValues() {
        assertAgree(jhipsterEnumFields(), DEFINITIONS);
    }

    @Test
    void everyEnumDeclaredInTheJdlDeclaresExactlyItsEnumsValues() {
        assertAgree(jdlEnums(), JDL);
    }

    @Test
    void theSweepFindsGeneratorInputsAndEnumsToCheck() {
        // A sweep that silently matches nothing passes forever. These are floors, not totals:
        // asserting the exact numbers would be the list of names this test exists to avoid.
        assertThat(definitionFiles()).as("entity definitions under %s", DEFINITIONS).isNotEmpty();
        assertThat(jdlFiles()).as("JDL models under %s", JDL).isNotEmpty();
        assertThat(jhipsterEnumFields()).as("fields declaring fieldValues in %s", DEFINITIONS).isNotEmpty();
        assertThat(jdlEnums()).as("enum declarations in %s", JDL).isNotEmpty();
    }

    private static void assertAgree(List<EnumDeclaration> declarations, Path source) {
        List<String> disagreements = new ArrayList<>();

        for (EnumDeclaration declaration : declarations) {
            List<String> actual = enumValues(declaration.enumType());
            if (actual == null) {
                disagreements.add(
                    "%s %s has type %s, which is not an enum in %s".formatted(
                        declaration.file(),
                        declaration.where(),
                        declaration.enumType(),
                        ENUMERATION_PACKAGE
                    )
                );
            } else if (!actual.equals(declaration.declaredValues())) {
                disagreements.add(
                    "%s %s declares %s but %s has %s".formatted(
                        declaration.file(),
                        declaration.where(),
                        declaration.declaredValues(),
                        declaration.enumType(),
                        actual
                    )
                );
            }
        }

        assertThat(disagreements)
            .as(
                "Generator inputs under %s must declare exactly the values of the enum they name — " +
                    "regenerating the entity emits the enum from these, so a disagreement here " +
                    "reintroduces or drops values in code",
                source
            )
            .isEmpty();
    }

    private static List<Path> definitionFiles() {
        return filesUnder(DEFINITIONS, ".json");
    }

    private static List<Path> jdlFiles() {
        return filesUnder(JDL, ".jdl");
    }

    private static List<Path> filesUnder(Path directory, String suffix) {
        assertThat(directory).as("run from the module directory: %s holds generator inputs", directory.toAbsolutePath()).isDirectory();
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .sorted(Comparator.naturalOrder())
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + directory.toAbsolutePath(), e);
        }
    }

    private static List<EnumDeclaration> jhipsterEnumFields() {
        ObjectMapper mapper = new ObjectMapper();
        List<EnumDeclaration> declarations = new ArrayList<>();

        for (Path definition : definitionFiles()) {
            JsonNode entity;
            try {
                entity = mapper.readTree(definition.toFile());
            } catch (IOException e) {
                throw new IllegalStateException("Could not parse " + definition, e);
            }
            for (JsonNode field : entity.path("fields")) {
                // fieldValues is present exactly on enum-typed fields, whatever the enum is called.
                if (field.hasNonNull("fieldValues")) {
                    declarations.add(
                        new EnumDeclaration(
                            definition.toString(),
                            "field '" + field.path("fieldName").asText() + "'",
                            field.path("fieldType").asText(),
                            split(field.path("fieldValues").asText())
                        )
                    );
                }
            }
        }
        return declarations;
    }

    private static List<EnumDeclaration> jdlEnums() {
        List<EnumDeclaration> declarations = new ArrayList<>();

        for (Path model : jdlFiles()) {
            String source;
            try {
                source = Files.readString(model);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + model, e);
            }
            Matcher matcher = JDL_ENUM.matcher(stripComments(source));
            while (matcher.find()) {
                declarations.add(
                    new EnumDeclaration(model.toString(), "enum " + matcher.group(1), matcher.group(1), split(matcher.group(2)))
                );
            }
        }
        return declarations;
    }

    /**
     * Block and line comments removed before matching, so a commented-out enum is not read as a
     * declaration. Several JDL blocks here carry {@code /** … *}{@code /} documentation immediately
     * above them.
     */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//.*", "");
    }

    private static List<String> split(String values) {
        return Arrays.stream(values.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }

    /** The enum's constants in declaration order, or {@code null} if the name is no enum. */
    private static List<String> enumValues(String enumType) {
        Class<?> type;
        try {
            type = Class.forName(ENUMERATION_PACKAGE + "." + enumType);
        } catch (ClassNotFoundException e) {
            return null;
        }
        Object[] constants = type.getEnumConstants();
        return constants == null
            ? null
            : Arrays.stream(constants)
                  .map(constant -> ((Enum<?>) constant).name())
                  .toList();
    }
}
