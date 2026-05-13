package net.jojoaddison.domain;

import java.util.UUID;

public class ProfileTestSamples {

    public static Profile getProfileSample1() {
        return new Profile()
            .id("id1")
            .personId("personId1")
            .photoId("photoId1")
            .contactId("contactId1")
            .addressList("addressList1")
            .roles("roles1")
            .organisationId("organisationId1")
            .teamId("teamId1")
            .documentItems("documentItems1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Profile getProfileSample2() {
        return new Profile()
            .id("id2")
            .personId("personId2")
            .photoId("photoId2")
            .contactId("contactId2")
            .addressList("addressList2")
            .roles("roles2")
            .organisationId("organisationId2")
            .teamId("teamId2")
            .documentItems("documentItems2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Profile getProfileRandomSampleGenerator() {
        return new Profile()
            .id(UUID.randomUUID().toString())
            .personId(UUID.randomUUID().toString())
            .photoId(UUID.randomUUID().toString())
            .contactId(UUID.randomUUID().toString())
            .addressList(UUID.randomUUID().toString())
            .roles(UUID.randomUUID().toString())
            .organisationId(UUID.randomUUID().toString())
            .teamId(UUID.randomUUID().toString())
            .documentItems(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
