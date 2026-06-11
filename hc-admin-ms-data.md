# Agent Prompt: Microservice Data Generation (`hc-admin-ms`)

Act as a Senior Java Data Engineer for the `hc-admin-ms` microservice. Your task is to create a JSON file containing mock business entity data that will be used for development and testing purposes. The data should reflect the structure and relationships defined in the domain models of the `hc-admin-ms` component, and should be consistent with the user roles defined in the gateway.

## Objective

Your task is to generate a JSON file named `hc-admin-ms-data.json` containing mock business entity data for the `hc-admin-ms` (Microservice) component. This data will be used for `dev` and `test` environments and should correspond to the user roles defined in the gateway.

## Instructions

1. **Create the JSON File:**

   - Create a file named `hc-admin-ms-data.json`.

2. **Define JSON Structure:**

   - The root of the JSON file should be an object with two top-level keys: `"dev"` and `"test"`.
   - Each of these keys will contain objects for different entity types, such as `facilities`, `audits`, and `metrics`.

3. **Define Entity Schemas:**

   - **Facilities:**
     - `entityId` (string, UUID)
     - `name` (string)
     - `status` (string, e.g., "ACTIVE", "INACTIVE")
     - `createdAt` (string, ISO 8601 date-time)
     - `managedBy` (string, UUID of a user from `hc-admin-gw-data.json`)
     - `payload` (object with facility-specific details)
   - **Audits:**
     - `entityId` (string, UUID)
     - `status` (string, e.g., "COMPLETED", "FAILED")
     - `createdAt` (string, ISO 8601 date-time)
     - `managedBy` (string, UUID of a user)
     - `payload` (object with audit event details)
   - **Metrics:** - `entityId` (string, UUID) - `facilityId` (string, UUID of a facility) - `status` (string, e.g., "CURRENT", "ARCHIVED") - `createdAt` (string, ISO 8601 date-time) - `managedBy` (string, UUID of a user) - `payload` (object with metric details)
     These are not exhaustive schemas but should provide a clear structure for the data you will generate.
     Check codebase domain models for addition entities and fields to generate data for.

4. **Generate Data:**
   - Use the user IDs from the `hc-admin-gw-data.json` blueprint for the `managedBy` fields to ensure relational integrity.
     - Admin User ID: `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`
     - Operator User ID: `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12`
   - **`dev` Data:** Create data that reflects typical application usage.
     - Global records managed by the ADMIN user.
     - Operational records managed by the OPERATOR user.
     - Publicly accessible records.
   - **`test` Data:** Create data for edge cases.
     - Entities with inactive or error statuses.
     - Entities linked to deactivated or special test users.
     - Entities with null or incomplete payloads.

## Expected JSON Output (`hc-admin-ms-data.json`)

```json
{
  "dev": {
    "facilities": [
      {
        "entityId": "f1c4e567-d8b5-4a2a-9c0a-1b9e8d6c7b0a",
        "name": "Global General Hospital",
        "status": "ACTIVE",
        "createdAt": "2023-01-15T08:00:00Z",
        "managedBy": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        "payload": { "type": "General Hospital", "location": "Worldwide", "beds": 1000 }
      },
      {
        "entityId": "f1c4e567-d8b5-4a2a-9c0a-1b9e8d6c7b0b",
        "name": "Local Clinic West",
        "status": "ACTIVE",
        "createdAt": "2023-03-20T10:00:00Z",
        "managedBy": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12",
        "payload": { "type": "Clinic", "location": "West District", "beds": 50 }
      }
    ],
    "audits": [
      {
        "entityId": "a2d4e678-e9c6-4b3b-8d1b-2c0a9e7d8c1c",
        "status": "COMPLETED",
        "createdAt": "2023-05-10T14:30:00Z",
        "managedBy": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        "payload": { "event": "USER_ROLE_UPDATED", "details": "User 'operator' granted 'ROLE_DATA_IMPORTER'" }
      }
    ],
    "metrics": [
      {
        "entityId": "m3b5f789-f0d7-5c4c-9e2c-3d1b0f8e9d2d",
        "facilityId": "f1c4e567-d8b5-4a2a-9c0a-1b9e8d6c7b0b",
        "status": "CURRENT",
        "createdAt": "2023-06-01T00:00:00Z",
        "managedBy": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12",
        "payload": { "type": "PatientSatisfaction", "value": "98.5", "unit": "Percentage" }
      }
    ]
  },
  "test": {
    "facilities": [
      {
        "entityId": "t1c4e567-d8b5-4a2a-9c0a-1b9e8d6c7b0t",
        "name": "Decommissioned East Wing",
        "status": "INACTIVE",
        "createdAt": "2022-11-30T17:00:00Z",
        "managedBy": "b1eebc99-9c0b-4ef8-bb6d-6bb9bd380b11",
        "payload": { "type": "Hospital Wing", "location": "East District", "beds": 0 }
      }
    ],
    "audits": [
      {
        "entityId": "a3d4e678-e9c6-4b3b-8d1b-2c0a9e7d8c2c",
        "status": "FAILED",
        "createdAt": "2023-05-11T16:00:00Z",
        "managedBy": "b1eebc99-9c0b-4ef8-bb6d-6bb9bd380b13",
        "payload": { "event": "DATABASE_CONNECTION_FAILURE", "details": "Failed to connect to audit log database." }
      }
    ],
    "metrics": []
  }
}
```

These are just a few examples to illustrate the structure and content of the JSON file. You should expand upon this with additional records and variations as needed to fully represent the data for both `dev` and `test` environments that is derived from the domain models in the codebase.

## Execution Steps:

1. Generate the `hc-admin-ms-data.json` file with the specified structure and data.
2. Ensure that the data is consistent with the user roles and relationships defined in the gateway data blueprint.
3. Validate the JSON format to ensure it is correctly structured and can be parsed without errors.
4. Save the file and make it available for use in the `dev` and `test` environments of the `hc-admin-ms` component.
5. Document any assumptions or decisions made during the data generation process for future reference.
6. Review the generated data to ensure it meets the requirements and accurately reflects the intended use cases for both environments.
7. Write an initializer class in the `hc-admin-ms` codebase that reads the `hc-admin-ms-data.json` file and populates the database with the generated data during application startup.
8. Test the data initialization process to ensure that the data is correctly loaded into the database and that all relationships are properly established.
9. Document the data generation and initialization process in the project documentation for future reference and maintenance.

Example Initializer Class (Java):

```java
package net.jojoaddison.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
@Profile({ JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_TEST })
public class DevelopmentDataInitializer implements ApplicationRunner {

  private final Logger log = LoggerFactory.getLogger(DevelopmentDataInitializer.class);
  private final MongoTemplate template;
...
}

```

Execution of this initializer will ensure that the mock data is available in the database for both `dev` and `test` environments, allowing for effective testing and development of the `hc-admin-ms` component.
Execute the above steps to generate the required data and initialize the database accordingly.

## Conclusion

By following the above instructions, you will have successfully generated a JSON file containing mock business entity data for the `hc-admin-ms` component, and implemented an initializer to populate the database with this data during application startup. This will facilitate effective testing and development of the microservice in both `dev` and `test` environments.

DO NOT DELETE, REMOVE, OR MODIFY ANY EXISTING CLASSES, METHODS, OR FIELDS IN THE CODEBASE DURING THIS PROCESS. ANY ADDITIONS TO THE CODEBASE MUST BE DOCUMENTED AND APPROVED BY THE PROJECT MAINTAINERS.
