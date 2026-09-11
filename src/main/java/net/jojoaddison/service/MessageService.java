package net.jojoaddison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import net.jojoaddison.broker.OutboundEventPublisher;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.enumeration.MessageChannel;
import net.jojoaddison.domain.enumeration.MessageStatus;
import net.jojoaddison.domain.enumeration.Priority;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.support.NamedFilters;
import net.jojoaddison.service.dto.MessageDTO;
import net.jojoaddison.service.dto.MessageSentEvent;
import net.jojoaddison.service.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
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

    private final OutboundEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    private final MongoTemplate mongoTemplate;

    public MessageService(
        MessageRepository messageRepository,
        MessageMapper messageMapper,
        OutboundEventPublisher eventPublisher,
        ObjectMapper objectMapper,
        MongoTemplate mongoTemplate
    ) {
        this.messageRepository = messageRepository;
        this.messageMapper = messageMapper;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.mongoTemplate = mongoTemplate;
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
     *
     * <p><b>Nor does it cost the caller any time.</b> It did until 2026-09-06: the publish ran here,
     * on the request thread, and the first send after a start against an unreachable broker took
     * <b>sixty seconds</b> — measured, with a 201 at the end of it and nothing failing (backlog item
     * 39a). {@link OutboundEventPublisher} owns that now and the reasoning is on it.
     */
    public MessageDTO send(MessageDTO messageDTO) {
        MessageDTO saved = save(messageDTO);
        publishSent(saved);
        return saved;
    }

    /**
     * Serialises here and publishes elsewhere, and the split is deliberate.
     *
     * <p>A payload that cannot be written is a defect in this service rather than a broker outage, so
     * it is caught while the message is still in hand and logged against its id. Everything past that
     * point is the broker's problem and leaves this thread.
     */
    private void publishSent(MessageDTO saved) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(MessageSentEvent.of(saved));
        } catch (JsonProcessingException | RuntimeException e) {
            LOG.warn("Message {} was saved but its sent event could not be serialised", saved.getId(), e);
            return;
        }
        eventPublisher.publish(PRODUCER_BINDING_NAME, payload, "Message " + saved.getId());
    }

    /**
     * The desk's list, filtered by the four things it can filter on.
     *
     * <p>Three of them were already being sent and silently dropped: the desk's status chips, its
     * priority chips and its search box. The status counters above the table read X-Total-Count
     * from a one-row query per status, so with the filter ignored every tile showed the collection
     * total — New 12, Read 12, Replied 12, against twelve messages.
     *
     * <p>{@code channel} joined them for item 19, where the demo's Filter control offers channel and
     * status and the console offered priority alone. It is one more named parameter rather than a
     * query language, which is the line {@link NamedFilters} draws.
     */
    public Page<MessageDTO> findAll(
        Pageable pageable,
        MessageStatus status,
        Priority priority,
        String subjectContains,
        MessageChannel channel
    ) {
        NamedFilters.Builder filters = NamedFilters.builder()
            .equals("status", status)
            .equals("priority", priority)
            .equals("channel", channel)
            .contains("subject", subjectContains);
        return NamedFilters.page(mongoTemplate, Message.class, filters, pageable).map(messageMapper::toDto);
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
