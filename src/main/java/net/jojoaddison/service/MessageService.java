package net.jojoaddison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import net.jojoaddison.domain.Message;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.service.dto.MessageDTO;
import net.jojoaddison.service.dto.MessageSentEvent;
import net.jojoaddison.service.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Message}.
 */
@Service
public class MessageService {

    private static final Logger LOG = LoggerFactory.getLogger(MessageService.class);

    /** Matches the binding HcAdminServiceKafkaResource publishes on. */
    private static final String PRODUCER_BINDING_NAME = "binding-out-0";

    private final MessageRepository messageRepository;

    private final MessageMapper messageMapper;

    private final StreamBridge streamBridge;

    private final ObjectMapper objectMapper;

    public MessageService(
        MessageRepository messageRepository,
        MessageMapper messageMapper,
        StreamBridge streamBridge,
        ObjectMapper objectMapper
    ) {
        this.messageRepository = messageRepository;
        this.messageMapper = messageMapper;
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
    }

    /**
     * Save a message.
     *
     * @param messageDTO the entity to save.
     * @return the persisted entity.
     */
    /**
     * Persist an outbound message, then announce it.
     *
     * <p>In that order, and the order is the point: the event carries an id, and a recipient that
     * acts on it fetches the message by that id. Publishing first means a notification can arrive
     * for a row that does not exist yet, which reads to the recipient as a message that vanished.
     *
     * <p>A failure to publish does not fail the send. The message is saved and readable on the desk;
     * losing the notification costs the recipient a live nudge, and unwinding a persisted message
     * because the bus was briefly down would cost them the message itself.
     */
    public MessageDTO send(MessageDTO messageDTO) {
        MessageDTO saved = save(messageDTO);
        publishSent(saved);
        return saved;
    }

    private void publishSent(MessageDTO saved) {
        try {
            streamBridge.send(PRODUCER_BINDING_NAME, objectMapper.writeValueAsString(MessageSentEvent.of(saved)));
        } catch (JsonProcessingException | RuntimeException e) {
            LOG.warn("Message {} was saved but its sent event could not be published", saved.getId(), e);
        }
    }

    public MessageDTO save(MessageDTO messageDTO) {
        LOG.debug("Request to save Message : {}", messageDTO);
        Message message = messageMapper.toEntity(messageDTO);
        message = messageRepository.save(message);
        return messageMapper.toDto(message);
    }

    /**
     * Update a message.
     *
     * @param messageDTO the entity to save.
     * @return the persisted entity.
     */
    public MessageDTO update(MessageDTO messageDTO) {
        LOG.debug("Request to update Message : {}", messageDTO);
        Message message = messageMapper.toEntity(messageDTO);
        message = messageRepository.save(message);
        return messageMapper.toDto(message);
    }

    /**
     * Partially update a message.
     *
     * @param messageDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<MessageDTO> partialUpdate(MessageDTO messageDTO) {
        LOG.debug("Request to partially update Message : {}", messageDTO);

        return messageRepository
            .findById(messageDTO.getId())
            .map(existingMessage -> {
                messageMapper.partialUpdate(existingMessage, messageDTO);

                return existingMessage;
            })
            .map(messageRepository::save)
            .map(messageMapper::toDto);
    }

    /**
     * Get all the messages.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<MessageDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all Messages");
        return messageRepository.findAll(pageable).map(messageMapper::toDto);
    }

    /**
     * Get one message by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<MessageDTO> findOne(String id) {
        LOG.debug("Request to get Message : {}", id);
        return messageRepository.findById(id).map(messageMapper::toDto);
    }

    /**
     * Delete the message by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete Message : {}", id);
        messageRepository.deleteById(id);
    }
}
