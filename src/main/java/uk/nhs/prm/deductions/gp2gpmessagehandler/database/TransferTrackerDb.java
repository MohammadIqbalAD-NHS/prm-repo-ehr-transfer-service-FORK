package uk.nhs.prm.deductions.gp2gpmessagehandler.database;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import uk.nhs.prm.deductions.gp2gpmessagehandler.config.AppConfig;
import uk.nhs.prm.deductions.gp2gpmessagehandler.ehrrequesthandler.TransferTrackerDbEntry;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TransferTrackerDb {
    private final DynamoDbClient dynamoDbClient;
    private final AppConfig config;


    // Tested at integration level
    public void save(TransferTrackerDbEntry transferTrackerDbEntry, String state){
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("nhs_number", AttributeValue.builder().n(transferTrackerDbEntry.getNhsNumber()).build());
        item.put("conversation_Id", AttributeValue.builder().s(transferTrackerDbEntry.getConversationId()).build());
        item.put("source_gp", AttributeValue.builder().s(transferTrackerDbEntry.getSourceGP()).build());
        item.put("nems_message_id", AttributeValue.builder().s(transferTrackerDbEntry.getNemsMessageId()).build());
        item.put("date_time", AttributeValue.builder().s(transferTrackerDbEntry.getDateTime()).build());
        item.put("state", AttributeValue.builder().s(state).build());
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(config.transferTrackerDbTableName())
                .item(item)
                .build());
    }
    }
