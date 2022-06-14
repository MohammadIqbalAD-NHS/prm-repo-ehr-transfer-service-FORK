package uk.nhs.prm.repo.ehrtransferservice.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import uk.nhs.prm.repo.ehrtransferservice.LocalStackAwsConfig;
import uk.nhs.prm.repo.ehrtransferservice.repo_incoming.TransferTrackerDbEntry;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest()
@ContextConfiguration(classes = {LocalStackAwsConfig.class})
public class TransferTrackerDbTest {

    @Autowired
    TransferTrackerDb transferTrackerDb;

    @Autowired
    private DynamoDbClient dbClient;

    String conversationId = "conversation Id";
    String nhsNumber = "111111111";
    String sourceGP = "source gp";
    String nemsMessageId = "Nems message Id";
    String nemsEventLastUpdated = "Last updated";
    String state = "state";
    String dateTime = "2017-11-01T15:00:33+00:00";
    String messageId = "message Id";


    @Value("${aws.transferTrackerDbTableName}")
    private String transferTrackerDbTableName;

    @BeforeEach
    public void setUp() {
        transferTrackerDb.save(new TransferTrackerDbEntry(conversationId, nhsNumber, sourceGP, nemsMessageId, nemsEventLastUpdated, state, dateTime, messageId));
    }

    @AfterEach
    void tearDown() {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("conversation_id", AttributeValue.builder().s(conversationId).build());
        dbClient.deleteItem(DeleteItemRequest.builder().tableName(transferTrackerDbTableName).key(key).build());
    }

    @Test
    void shouldReadFromDb() {
        var transferTrackerDbData = transferTrackerDb.getByConversationId(conversationId);
        assertThat(transferTrackerDbData.getNhsNumber()).isEqualTo(nhsNumber);
        assertThat(transferTrackerDbData.getConversationId()).isEqualTo(conversationId);
    }

    @Test
    void shouldDoInitialUpdateOfRecord() {
        var newTimestamp = "2018-11-01T15:00:33+00:00";
        transferTrackerDb.save(new TransferTrackerDbEntry(conversationId, nhsNumber, sourceGP, nemsMessageId, nemsEventLastUpdated, state, newTimestamp, messageId));
        var transferTrackerDbData = transferTrackerDb.getByConversationId(conversationId);
        assertThat(transferTrackerDbData.getNhsNumber()).isEqualTo(nhsNumber);
        assertThat(transferTrackerDbData.getConversationId()).isEqualTo(conversationId);
        assertThat(transferTrackerDbData.getDateTime()).isEqualTo(newTimestamp);
    }

    @Test
    void shouldHandleConversationIdThatDoesNotExistInDb() {
        var notExistingConversationId = "non-existing conversation Id";
        var lastUpdatePatientData = transferTrackerDb.getByConversationId(notExistingConversationId);
        assertThat(lastUpdatePatientData).isEqualTo(null);
    }

    @Test
    void shouldUpdateOnlyStateAndDateTime() {
        var newTimestamp = "2222-11-01T15:00:33+00:00";
        transferTrackerDb.update(conversationId, "ACTION:EHR_REQUEST_SENT", newTimestamp);

        var transferTrackerDbData = transferTrackerDb.getByConversationId(conversationId);
        assertThat(transferTrackerDbData.getState()).isEqualTo("ACTION:EHR_REQUEST_SENT");
        assertThat(transferTrackerDbData.getConversationId()).isEqualTo(conversationId);
        assertThat(transferTrackerDbData.getDateTime()).isEqualTo(newTimestamp);
    }
}