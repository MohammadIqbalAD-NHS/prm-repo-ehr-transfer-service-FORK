package uk.nhs.prm.repo.ehrtransferservice.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.prm.repo.ehrtransferservice.database.TransferStore;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.ParsedMessage;
import uk.nhs.prm.repo.ehrtransferservice.services.ehr_repo.EhrRepoService;
import uk.nhs.prm.repo.ehrtransferservice.services.gp2gp_messenger.Gp2gpMessengerService;

@Service
@Slf4j
@RequiredArgsConstructor
public class LargeEhrCoreMessageHandler implements MessageHandler<ParsedMessage> {

    private final EhrRepoService ehrRepoService;
    private final Gp2gpMessengerService gp2gpMessengerService;
    private final TransferStore transferStore;

    @Override
    public void handleMessage(ParsedMessage largeEhrCoreMessage) throws Exception {
        var conversationId = largeEhrCoreMessage.getConversationId();
        boolean isActive = true;

        ehrRepoService.storeMessage(largeEhrCoreMessage);

        log.info("Successfully stored large-ehr message in the ehr-repo");

        var ehrTransferData = transferStore.findTransfer(conversationId.toString());
        gp2gpMessengerService.sendContinueMessage(largeEhrCoreMessage, ehrTransferData);


        transferStore.handleEhrTransferStateUpdate(conversationId.toString(), ehrTransferData.getNemsMessageId(), "ACTION:LARGE_EHR_CONTINUE_REQUEST_SENT", isActive);
        transferStore.updateLargeEhrCoreMessageId(conversationId.toString(), largeEhrCoreMessage.getMessageId().toString());
    }
}