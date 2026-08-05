package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.PhotoAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Photo;
import net.jojoaddison.domain.enumeration.PhotoType;
import net.jojoaddison.repository.PhotoRepository;
import net.jojoaddison.service.dto.PhotoDTO;
import net.jojoaddison.service.mapper.PhotoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link PhotoResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class PhotoResourceIT {

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_ALT_TEXT = "AAAAAAAAAA";
    private static final String UPDATED_ALT_TEXT = "BBBBBBBBBB";

    private static final String DEFAULT_URL = "AAAAAAAAAA";
    private static final String UPDATED_URL = "BBBBBBBBBB";

    private static final String DEFAULT_PROFILE_ID = "AAAAAAAAAA";
    private static final String UPDATED_PROFILE_ID = "BBBBBBBBBB";

    private static final PhotoType DEFAULT_PHOTO_TYPE = PhotoType.ID_PHOTO;
    private static final PhotoType UPDATED_PHOTO_TYPE = PhotoType.PORTRAIT;

    private static final byte[] DEFAULT_DATA = TestUtil.createByteArray(1, "0");
    private static final byte[] UPDATED_DATA = TestUtil.createByteArray(1, "1");
    private static final String DEFAULT_DATA_CONTENT_TYPE = "image/jpg";
    private static final String UPDATED_DATA_CONTENT_TYPE = "image/png";

    private static final String DEFAULT_PHOTO_METADATA = "AAAAAAAAAA";
    private static final String UPDATED_PHOTO_METADATA = "BBBBBBBBBB";

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final Instant DEFAULT_MODIFIED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_MODIFIED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    /**
     * The path the dashboard already calls. It resolves through
     * {@code getEndpointFor('api/photos', 'hcadminservice')} in
     * {@code web/…/entities/photo/service/photo.service.ts} — before this resource existed, every
     * request to it returned 404 in production.
     */
    private static final String ENTITY_API_URL = "/api/photos";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private final ObjectMapper om = TestUtil.createObjectMapper();

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private PhotoMapper photoMapper;

    @Autowired
    private MockMvc restPhotoMockMvc;

    private Photo photo;

    private Photo insertedPhoto;

    public static Photo createEntity() {
        return new Photo()
            .description(DEFAULT_DESCRIPTION)
            .altText(DEFAULT_ALT_TEXT)
            .url(DEFAULT_URL)
            .profileId(DEFAULT_PROFILE_ID)
            .photoType(DEFAULT_PHOTO_TYPE)
            .data(DEFAULT_DATA)
            .dataContentType(DEFAULT_DATA_CONTENT_TYPE)
            .photoMetadata(DEFAULT_PHOTO_METADATA)
            .createdBy(DEFAULT_CREATED_BY)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedBy(DEFAULT_MODIFIED_BY)
            .modifiedDate(DEFAULT_MODIFIED_DATE);
    }

    public static Photo createUpdatedEntity() {
        return new Photo()
            .description(UPDATED_DESCRIPTION)
            .altText(UPDATED_ALT_TEXT)
            .url(UPDATED_URL)
            .profileId(UPDATED_PROFILE_ID)
            .photoType(UPDATED_PHOTO_TYPE)
            .data(UPDATED_DATA)
            .dataContentType(UPDATED_DATA_CONTENT_TYPE)
            .photoMetadata(UPDATED_PHOTO_METADATA)
            .createdBy(UPDATED_CREATED_BY)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE);
    }

    @BeforeEach
    void initTest() {
        photo = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedPhoto != null) {
            photoRepository.delete(insertedPhoto);
            insertedPhoto = null;
        }
    }

    @Test
    void createPhoto() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        PhotoDTO photoDTO = photoMapper.toDto(photo);
        var returnedPhotoDTO = om.readValue(
            restPhotoMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            PhotoDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedPhoto = photoMapper.toEntity(returnedPhotoDTO);
        assertPhotoUpdatableFieldsEquals(returnedPhoto, getPersistedPhoto(returnedPhoto));

        insertedPhoto = returnedPhoto;
    }

    @Test
    void createPhotoWithExistingId() throws Exception {
        photo.setId("existing_id");
        PhotoDTO photoDTO = photoMapper.toDto(photo);

        long databaseSizeBeforeCreate = getRepositoryCount();

        restPhotoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkDescriptionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        photo.setDescription(null);

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkUrlIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        photo.setUrl(null);

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkProfileIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        photo.setProfileId(null);

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPhotoTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        photo.setPhotoType(null);

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkDataIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        photo.setData(null);

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkCreatedDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        photo.setCreatedDate(null);

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkModifiedByIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        photo.setModifiedBy(null);

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkModifiedDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        photo.setModifiedDate(null);

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    /**
     * The blob is serialised as base64 in JSON. Asserted explicitly because this is the field the
     * Angular client round-trips through {@code data}/{@code dataContentType}, and a mismatch here
     * is invisible until someone uploads an actual image.
     */
    @Test
    void getAllPhotos() throws Exception {
        insertedPhoto = photoRepository.save(photo);

        restPhotoMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(photo.getId())))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].altText").value(hasItem(DEFAULT_ALT_TEXT)))
            .andExpect(jsonPath("$.[*].url").value(hasItem(DEFAULT_URL)))
            .andExpect(jsonPath("$.[*].profileId").value(hasItem(DEFAULT_PROFILE_ID)))
            .andExpect(jsonPath("$.[*].photoType").value(hasItem(DEFAULT_PHOTO_TYPE.toString())))
            .andExpect(jsonPath("$.[*].data").value(hasItem(Base64.getEncoder().encodeToString(DEFAULT_DATA))))
            .andExpect(jsonPath("$.[*].dataContentType").value(hasItem(DEFAULT_DATA_CONTENT_TYPE)))
            .andExpect(jsonPath("$.[*].photoMetadata").value(hasItem(DEFAULT_PHOTO_METADATA)));
    }

    /**
     * The list endpoint is paginated, matching {@code web/.jhipster/Photo.json}'s
     * {@code "pagination": "pagination"} — the client reads X-Total-Count to render its pager.
     */
    @Test
    void getAllPhotosReturnsPaginationHeaders() throws Exception {
        insertedPhoto = photoRepository.save(photo);

        restPhotoMockMvc
            .perform(get(ENTITY_API_URL + "?page=0&size=1&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(header().exists("Link"));
    }

    @Test
    void getPhoto() throws Exception {
        insertedPhoto = photoRepository.save(photo);

        restPhotoMockMvc
            .perform(get(ENTITY_API_URL_ID, photo.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(photo.getId()))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.altText").value(DEFAULT_ALT_TEXT))
            .andExpect(jsonPath("$.url").value(DEFAULT_URL))
            .andExpect(jsonPath("$.profileId").value(DEFAULT_PROFILE_ID))
            .andExpect(jsonPath("$.photoType").value(DEFAULT_PHOTO_TYPE.toString()))
            .andExpect(jsonPath("$.data").value(Base64.getEncoder().encodeToString(DEFAULT_DATA)))
            .andExpect(jsonPath("$.dataContentType").value(DEFAULT_DATA_CONTENT_TYPE))
            .andExpect(jsonPath("$.photoMetadata").value(DEFAULT_PHOTO_METADATA));
    }

    @Test
    void getNonExistingPhoto() throws Exception {
        restPhotoMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingPhoto() throws Exception {
        insertedPhoto = photoRepository.save(photo);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        Photo updatedPhoto = photoRepository.findById(photo.getId()).orElseThrow();
        updatedPhoto
            .description(UPDATED_DESCRIPTION)
            .altText(UPDATED_ALT_TEXT)
            .url(UPDATED_URL)
            .profileId(UPDATED_PROFILE_ID)
            .photoType(UPDATED_PHOTO_TYPE)
            .data(UPDATED_DATA)
            .dataContentType(UPDATED_DATA_CONTENT_TYPE)
            .photoMetadata(UPDATED_PHOTO_METADATA)
            .createdBy(UPDATED_CREATED_BY)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE);
        PhotoDTO photoDTO = photoMapper.toDto(updatedPhoto);

        restPhotoMockMvc
            .perform(
                put(ENTITY_API_URL_ID, photoDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO))
            )
            .andExpect(status().isOk());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPhotoToMatchAllProperties(updatedPhoto);
    }

    @Test
    void putNonExistingPhoto() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        photo.setId(UUID.randomUUID().toString());

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(
                put(ENTITY_API_URL_ID, photoDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPhoto() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        photo.setId(UUID.randomUUID().toString());

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(photoDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPhoto() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        photo.setId(UUID.randomUUID().toString());

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isMethodNotAllowed());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePhotoWithPatch() throws Exception {
        insertedPhoto = photoRepository.save(photo);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        Photo partialUpdatedPhoto = new Photo();
        partialUpdatedPhoto.setId(photo.getId());

        partialUpdatedPhoto.description(UPDATED_DESCRIPTION).url(UPDATED_URL).photoType(UPDATED_PHOTO_TYPE).modifiedBy(UPDATED_MODIFIED_BY);

        restPhotoMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPhoto.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPhoto))
            )
            .andExpect(status().isOk());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPhotoUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedPhoto, photo), getPersistedPhoto(photo));
    }

    @Test
    void fullUpdatePhotoWithPatch() throws Exception {
        insertedPhoto = photoRepository.save(photo);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        Photo partialUpdatedPhoto = new Photo();
        partialUpdatedPhoto.setId(photo.getId());

        partialUpdatedPhoto
            .description(UPDATED_DESCRIPTION)
            .altText(UPDATED_ALT_TEXT)
            .url(UPDATED_URL)
            .profileId(UPDATED_PROFILE_ID)
            .photoType(UPDATED_PHOTO_TYPE)
            .data(UPDATED_DATA)
            .dataContentType(UPDATED_DATA_CONTENT_TYPE)
            .photoMetadata(UPDATED_PHOTO_METADATA)
            .createdBy(UPDATED_CREATED_BY)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE);

        restPhotoMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPhoto.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPhoto))
            )
            .andExpect(status().isOk());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPhotoUpdatableFieldsEquals(partialUpdatedPhoto, getPersistedPhoto(partialUpdatedPhoto));
    }

    @Test
    void patchNonExistingPhoto() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        photo.setId(UUID.randomUUID().toString());

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, photoDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(photoDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPhoto() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        photo.setId(UUID.randomUUID().toString());

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(photoDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPhoto() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        photo.setId(UUID.randomUUID().toString());

        PhotoDTO photoDTO = photoMapper.toDto(photo);

        restPhotoMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(photoDTO)))
            .andExpect(status().isMethodNotAllowed());

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePhoto() throws Exception {
        insertedPhoto = photoRepository.save(photo);

        long databaseSizeBeforeDelete = getRepositoryCount();

        restPhotoMockMvc
            .perform(delete(ENTITY_API_URL_ID, photo.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return photoRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Photo getPersistedPhoto(Photo photo) {
        return photoRepository.findById(photo.getId()).orElseThrow();
    }

    protected void assertPersistedPhotoToMatchAllProperties(Photo expectedPhoto) {
        assertPhotoAllPropertiesEquals(expectedPhoto, getPersistedPhoto(expectedPhoto));
    }
}
