package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.service.dto.MessageDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Message} and its DTO {@link MessageDTO}.
 */
@Mapper(componentModel = "spring")
public interface MessageMapper extends EntityMapper<MessageDTO, Message> {
    /**
     * The four references travel as ids in both directions.
     *
     * <p>Nesting them would put a vendor, a patient and a whole parent thread inside every row of
     * the desk's list, and the desk shows twelve at a time. The screens that need the record follow
     * the id; the ones that need a name have it on the message already.
     */
    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "vendorId", source = "vendor.id")
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "professionalId", source = "professional.id")
    @Override
    MessageDTO toDto(Message message);

    @Mapping(target = "parent", source = "parentId")
    @Mapping(target = "vendor", source = "vendorId")
    @Mapping(target = "patient", source = "patientId")
    @Mapping(target = "professional", source = "professionalId")
    @Override
    Message toEntity(MessageDTO messageDTO);

    /** Ids arrive as strings and the entity wants stubs; MapStruct cannot infer that on its own. */
    default Message messageFromId(String id) {
        if (id == null) {
            return null;
        }
        Message message = new Message();
        message.setId(id);
        return message;
    }

    default Vendor vendorFromId(String id) {
        if (id == null) {
            return null;
        }
        Vendor vendor = new Vendor();
        vendor.setId(id);
        return vendor;
    }

    default Patient patientFromId(String id) {
        if (id == null) {
            return null;
        }
        Patient patient = new Patient();
        patient.setId(id);
        return patient;
    }

    default Professional professionalFromId(String id) {
        if (id == null) {
            return null;
        }
        Professional professional = new Professional();
        professional.setId(id);
        return professional;
    }
}
