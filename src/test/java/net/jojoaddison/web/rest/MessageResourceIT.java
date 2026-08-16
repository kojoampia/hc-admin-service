package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.MessageAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.enumeration.MessageChannel;
import net.jojoaddison.domain.enumeration.MessageStatus;
import net.jojoaddison.domain.enumeration.Priority;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.service.dto.MessageDTO;
import net.jojoaddison.service.mapper.MessageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link MessageResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class MessageResourceIT {

    private static final Instant DEFAULT_SENT_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_SENT_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_FROM_ADDRESS = "AAAAAAAAAA";
    private static final String UPDATED_FROM_ADDRESS = "BBBBBBBBBB";

    private static final String DEFAULT_SENDER_NAME = "AAAAAAAAAA";
    private static final String UPDATED_SENDER_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_SUBJECT = "AAAAAAAAAA";
    private static final String UPDATED_SUBJECT = "BBBBBBBBBB";

    private static final String DEFAULT_BODY = "AAAAAAAAAA";
    private static final String UPDATED_BODY = "BBBBBBBBBB";

    private static final MessageChannel DEFAULT_CHANNEL = MessageChannel.EMAIL;
    private static final MessageChannel UPDATED_CHANNEL = MessageChannel.PATIENT_APP;

    private static final MessageStatus DEFAULT_STATUS = MessageStatus.NEW;
    private static final MessageStatus UPDATED_STATUS = MessageStatus.READ;

    private static final Priority DEFAULT_PRIORITY = Priority.LOW;
    private static final Priority UPDATED_PRIORITY = Priority.NORMAL;
    private static final String ENTITY_API_URL = "/api/messages";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private MockMvc restMessageMockMvc;

    private Message message;

    private Message insertedMessage;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Message createEntity() {
        return new Message()
            .sentAt(DEFAULT_SENT_AT)
            .fromAddress(DEFAULT_FROM_ADDRESS)
            .senderName(DEFAULT_SENDER_NAME)
            .subject(DEFAULT_SUBJECT)
            .body(DEFAULT_BODY)
            .channel(DEFAULT_CHANNEL)
            .status(DEFAULT_STATUS)
            .priority(DEFAULT_PRIORITY);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Message createUpdatedEntity() {
        return new Message()
            .sentAt(UPDATED_SENT_AT)
            .fromAddress(UPDATED_FROM_ADDRESS)
            .senderName(UPDATED_SENDER_NAME)
            .subject(UPDATED_SUBJECT)
            .body(UPDATED_BODY)
            .channel(UPDATED_CHANNEL)
            .status(UPDATED_STATUS)
            .priority(UPDATED_PRIORITY);
    }

    @BeforeEach
    void initTest() {
        message = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedMessage != null) {
            messageRepository.delete(insertedMessage);
            insertedMessage = null;
        }
    }

    @Test
    void createMessage() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Message
        MessageDTO messageDTO = messageMapper.toDto(message);
        var returnedMessageDTO = om.readValue(
            restMessageMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            MessageDTO.class
        );

        // Validate the Message in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedMessage = messageMapper.toEntity(returnedMessageDTO);
        assertMessageUpdatableFieldsEquals(returnedMessage, getPersistedMessage(returnedMessage));

        insertedMessage = returnedMessage;
    }

    /**
     * Sending persists, and it is a separate act from creating.
     *
     * <p>`POST /messages` records something that arrived; `POST /messages/send` records something
     * going out and announces it. Keeping them apart is what stops every create firing a
     * notification at somebody.
     */
    @Test
    void sendMessagePersistsIt() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        message.setToAddress("orders@kaneshiemed.gh");
        message.setRecipientName("Kaneshie Medical Supplies");
        MessageDTO messageDTO = messageMapper.toDto(message);

        var returnedMessageDTO = om.readValue(
            restMessageMockMvc
                .perform(post(ENTITY_API_URL + "/send").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            MessageDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedMessageDTO.getToAddress()).isEqualTo("orders@kaneshiemed.gh");
        assertThat(returnedMessageDTO.getRecipientName()).isEqualTo("Kaneshie Medical Supplies");
        insertedMessage = messageMapper.toEntity(returnedMessageDTO);
    }

    /**
     * Refused rather than broadcast.
     *
     * <p>The recipient is the event's routing key. Without it the message saves, the event
     * publishes with a null key, and the consumer falls back to broadcast — delivering a private
     * reply to every connected operator. That failure is silent, which is why this is a 400.
     */
    @Test
    void sendMessageRequiresARecipient() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        message.setToAddress(null);
        MessageDTO messageDTO = messageMapper.toDto(message);

        restMessageMockMvc
            .perform(post(ENTITY_API_URL + "/send").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void createMessageWithExistingId() throws Exception {
        // Create the Message with an existing ID
        message.setId("existing_id");
        MessageDTO messageDTO = messageMapper.toDto(message);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restMessageMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Message in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkSentAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        message.setSentAt(null);

        // Create the Message, which fails.
        MessageDTO messageDTO = messageMapper.toDto(message);

        restMessageMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkFromAddressIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        message.setFromAddress(null);

        // Create the Message, which fails.
        MessageDTO messageDTO = messageMapper.toDto(message);

        restMessageMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkSenderNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        message.setSenderName(null);

        // Create the Message, which fails.
        MessageDTO messageDTO = messageMapper.toDto(message);

        restMessageMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkSubjectIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        message.setSubject(null);

        // Create the Message, which fails.
        MessageDTO messageDTO = messageMapper.toDto(message);

        restMessageMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkChannelIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        message.setChannel(null);

        // Create the Message, which fails.
        MessageDTO messageDTO = messageMapper.toDto(message);

        restMessageMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        message.setStatus(null);

        // Create the Message, which fails.
        MessageDTO messageDTO = messageMapper.toDto(message);

        restMessageMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPriorityIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        message.setPriority(null);

        // Create the Message, which fails.
        MessageDTO messageDTO = messageMapper.toDto(message);

        restMessageMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllMessages() throws Exception {
        // Initialize the database
        insertedMessage = messageRepository.save(message);

        // Get all the messageList
        restMessageMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(message.getId())))
            .andExpect(jsonPath("$.[*].sentAt").value(hasItem(DEFAULT_SENT_AT.toString())))
            .andExpect(jsonPath("$.[*].fromAddress").value(hasItem(DEFAULT_FROM_ADDRESS)))
            .andExpect(jsonPath("$.[*].senderName").value(hasItem(DEFAULT_SENDER_NAME)))
            .andExpect(jsonPath("$.[*].subject").value(hasItem(DEFAULT_SUBJECT)))
            .andExpect(jsonPath("$.[*].body").value(hasItem(DEFAULT_BODY)))
            .andExpect(jsonPath("$.[*].channel").value(hasItem(DEFAULT_CHANNEL.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].priority").value(hasItem(DEFAULT_PRIORITY.toString())));
    }

    @Test
    void getMessage() throws Exception {
        // Initialize the database
        insertedMessage = messageRepository.save(message);

        // Get the message
        restMessageMockMvc
            .perform(get(ENTITY_API_URL_ID, message.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(message.getId()))
            .andExpect(jsonPath("$.sentAt").value(DEFAULT_SENT_AT.toString()))
            .andExpect(jsonPath("$.fromAddress").value(DEFAULT_FROM_ADDRESS))
            .andExpect(jsonPath("$.senderName").value(DEFAULT_SENDER_NAME))
            .andExpect(jsonPath("$.subject").value(DEFAULT_SUBJECT))
            .andExpect(jsonPath("$.body").value(DEFAULT_BODY))
            .andExpect(jsonPath("$.channel").value(DEFAULT_CHANNEL.toString()))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.priority").value(DEFAULT_PRIORITY.toString()));
    }

    @Test
    void getNonExistingMessage() throws Exception {
        // Get the message
        restMessageMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingMessage() throws Exception {
        // Initialize the database
        insertedMessage = messageRepository.save(message);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the message
        Message updatedMessage = messageRepository.findById(message.getId()).orElseThrow();
        updatedMessage
            .sentAt(UPDATED_SENT_AT)
            .fromAddress(UPDATED_FROM_ADDRESS)
            .senderName(UPDATED_SENDER_NAME)
            .subject(UPDATED_SUBJECT)
            .body(UPDATED_BODY)
            .channel(UPDATED_CHANNEL)
            .status(UPDATED_STATUS)
            .priority(UPDATED_PRIORITY);
        MessageDTO messageDTO = messageMapper.toDto(updatedMessage);

        restMessageMockMvc
            .perform(
                put(ENTITY_API_URL_ID, messageDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO))
            )
            .andExpect(status().isOk());

        // Validate the Message in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedMessageToMatchAllProperties(updatedMessage);
    }

    @Test
    void putNonExistingMessage() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        message.setId(UUID.randomUUID().toString());

        // Create the Message
        MessageDTO messageDTO = messageMapper.toDto(message);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMessageMockMvc
            .perform(
                put(ENTITY_API_URL_ID, message.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Message in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchMessage() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        message.setId(UUID.randomUUID().toString());

        // Create the Message
        MessageDTO messageDTO = messageMapper.toDto(message);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMessageMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(messageDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Message in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamMessage() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        message.setId(UUID.randomUUID().toString());

        // Create the Message
        MessageDTO messageDTO = messageMapper.toDto(message);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMessageMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Message in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateMessageWithPatch() throws Exception {
        // Initialize the database
        insertedMessage = messageRepository.save(message);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the message using partial update
        Message partialUpdatedMessage = new Message();
        partialUpdatedMessage.setId(message.getId());

        partialUpdatedMessage
            .sentAt(UPDATED_SENT_AT)
            .fromAddress(UPDATED_FROM_ADDRESS)
            .senderName(UPDATED_SENDER_NAME)
            .subject(UPDATED_SUBJECT);

        restMessageMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMessage.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedMessage))
            )
            .andExpect(status().isOk());

        // Validate the Message in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMessageUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedMessage, message), getPersistedMessage(message));
    }

    @Test
    void fullUpdateMessageWithPatch() throws Exception {
        // Initialize the database
        insertedMessage = messageRepository.save(message);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the message using partial update
        Message partialUpdatedMessage = new Message();
        partialUpdatedMessage.setId(message.getId());

        partialUpdatedMessage
            .sentAt(UPDATED_SENT_AT)
            .fromAddress(UPDATED_FROM_ADDRESS)
            .senderName(UPDATED_SENDER_NAME)
            .subject(UPDATED_SUBJECT)
            .body(UPDATED_BODY)
            .channel(UPDATED_CHANNEL)
            .status(UPDATED_STATUS)
            .priority(UPDATED_PRIORITY);

        restMessageMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMessage.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedMessage))
            )
            .andExpect(status().isOk());

        // Validate the Message in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMessageUpdatableFieldsEquals(partialUpdatedMessage, getPersistedMessage(partialUpdatedMessage));
    }

    @Test
    void patchNonExistingMessage() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        message.setId(UUID.randomUUID().toString());

        // Create the Message
        MessageDTO messageDTO = messageMapper.toDto(message);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMessageMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, message.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(messageDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Message in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchMessage() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        message.setId(UUID.randomUUID().toString());

        // Create the Message
        MessageDTO messageDTO = messageMapper.toDto(message);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMessageMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(messageDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Message in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamMessage() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        message.setId(UUID.randomUUID().toString());

        // Create the Message
        MessageDTO messageDTO = messageMapper.toDto(message);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMessageMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(messageDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Message in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteMessage() throws Exception {
        // Initialize the database
        insertedMessage = messageRepository.save(message);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the message
        restMessageMockMvc
            .perform(delete(ENTITY_API_URL_ID, message.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return messageRepository.count();
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

    protected Message getPersistedMessage(Message message) {
        return messageRepository.findById(message.getId()).orElseThrow();
    }

    protected void assertPersistedMessageToMatchAllProperties(Message expectedMessage) {
        assertMessageAllPropertiesEquals(expectedMessage, getPersistedMessage(expectedMessage));
    }

    protected void assertPersistedMessageToMatchUpdatableProperties(Message expectedMessage) {
        assertMessageAllUpdatablePropertiesEquals(expectedMessage, getPersistedMessage(expectedMessage));
    }
}
