package uz.hrlab.grading.common.api;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;

import java.util.UUID;

/**
 * Global {@code String -> UUID} converter that treats a blank value as absent.
 *
 * <p>Why this exists: the SPA submits optional UUID filters (e.g.
 * {@code ?projectId=}, {@code ?clientCompanyId=}) as EMPTY STRINGS when nothing
 * is selected. Without a blank-safe converter the empty literal can travel into
 * a query and surface as a PostgreSQL {@code 22P02 invalid input syntax for type
 * uuid: ""} — which the {@link GlobalExceptionHandler} then maps to a misleading
 * 409 CONFLICT. Registering this converter on the MVC conversion service fixes
 * every {@code @RequestParam UUID} / {@code @PathVariable UUID} binding at once.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@code null} or blank input -> {@code null} (optional request params are
 *       therefore treated as "no filter"; for a <em>required</em> {@code UUID}
 *       param/path-variable Spring still rejects the resulting null with a
 *       400-style {@code MethodArgumentTypeMismatchException}).</li>
 *   <li>A valid UUID string -> the parsed {@link UUID}.</li>
 *   <li>A non-blank, malformed string -> {@link IllegalArgumentException}, which
 *       Spring wraps as a {@code MethodArgumentTypeMismatchException} -> 400 BAD
 *       REQUEST (never a DB {@code 22P02} / 409). Path-variable UUIDs thus stay
 *       strict.</li>
 * </ul>
 *
 * <p>This intentionally mirrors Spring's own {@code StringToUUIDConverter}
 * (blank -> null) but is registered explicitly so the behaviour is guaranteed
 * regardless of which conversion service backs request binding.
 */
public class BlankSafeUuidConverter implements Converter<String, UUID> {

    @Override
    @Nullable
    public UUID convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        // Throws IllegalArgumentException for a genuinely malformed (non-blank)
        // value -> Spring -> 400, instead of letting "" reach the DB as 22P02.
        return UUID.fromString(source.trim());
    }
}
