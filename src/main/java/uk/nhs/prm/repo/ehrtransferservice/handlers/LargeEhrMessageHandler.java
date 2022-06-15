package uk.nhs.prm.repo.ehrtransferservice.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.prm.repo.ehrtransferservice.database.TransferTrackerService;
import uk.nhs.prm.repo.ehrtransferservice.models.LargeSqsMessage;
import uk.nhs.prm.repo.ehrtransferservice.services.ehr_repo.EhrRepoService;
import uk.nhs.prm.repo.ehrtransferservice.services.gp2gp_messenger.Gp2gpMessengerService;

@Service
@Slf4j
public class LargeEhrMessageHandler implements MessageHandler<LargeSqsMessage> {

    private final EhrRepoService ehrRepoService;
    private final Gp2gpMessengerService gp2gpMessengerService;
    private final TransferTrackerService transferTrackerService;

    public LargeEhrMessageHandler(EhrRepoService ehrRepoService, Gp2gpMessengerService gp2gpMessengerService, TransferTrackerService transferTrackerService) {
        this.ehrRepoService = ehrRepoService;
        this.gp2gpMessengerService = gp2gpMessengerService;
        this.transferTrackerService = transferTrackerService;
    }

    @Override
    public String getInteractionId() {
        return null;
    }

    @Override
    public void handleMessage(LargeSqsMessage largeSqsMessage) throws Exception {
        var conversationId = largeSqsMessage.getConversationId();

        ehrRepoService.storeMessage(largeSqsMessage);
        log.info("Successfully stored large-ehr message in the ehr-repo");

        var ehrTransferData = transferTrackerService.getEhrTransferData(conversationId.toString());
        gp2gpMessengerService.sendContinueMessage(largeSqsMessage, ehrTransferData);

        transferTrackerService.updateStateOfEhrTransfer(conversationId.toString(), "ACTION:LARGE_EHR_CONTINUE_REQUEST_SENT");
        transferTrackerService.updateLargeEhrCoreMessageId(conversationId.toString(), largeSqsMessage.getMessageId().toString());
    }
}