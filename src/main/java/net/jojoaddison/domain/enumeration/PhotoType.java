package net.jojoaddison.domain.enumeration;

/**
 * The PhotoType enumeration.
 *
 * <p>Values are fixed by the dashboard's own enum
 * ({@code web/src/main/webapp/app/entities/enumerations/photo-type.model.ts}) and by
 * {@code web/.jhipster/Photo.json}. Both sides serialise these as names, so adding or renaming a
 * constant here without doing the same there breaks deserialisation on one side only.
 */
public enum PhotoType {
    ID_PHOTO,
    PORTRAIT,
    MUGSHOT,
    DOCUMENT_PHOTO,
    REPORT_PHOTO,
    OTHER,
}
