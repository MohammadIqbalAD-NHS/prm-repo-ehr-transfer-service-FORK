package uk.nhs.prm.repo.ehrtransferservice.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.prm.repo.ehrtransferservice.database.TransferStore;
import uk.nhs.prm.repo.ehrtransferservice.message_publishers.TransferCompleteMessagePublisher;
import uk.nhs.prm.repo.ehrtransferservice.models.EhrCompleteEvent;
import uk.nhs.prm.repo.ehrtransferservice.models.TransferCompleteEvent;
import uk.nhs.prm.repo.ehrtransferservice.repo_incoming.Transfer;
import uk.nhs.prm.repo.ehrtransferservice.services.gp2gp_messenger.Gp2gpMessengerService;

@Service
@RequiredArgsConstructor
@Slf4j
public class EhrCompleteHandler {
    private final Gp2gpMessengerService gp2gpMessengerService;
    private final TransferStore transferStore;
    private final TransferCompleteMessagePublisher transferCompleteMessagePublisher;
    private static final String TRANSFER_TO_REPO_COMPLETE = "ACTION:EHR_TRANSFER_TO_REPO_COMPLETE";

    public void handleMessage(EhrCompleteEvent ehrCompleteEvent) throws Exception {
        var conversationId = ehrCompleteEvent.getConversationId();
        boolean isActive = false;

        var ehrTransferData = transferStore.findTransfer(conversationId.toString());
        var transferCompleteEvent = createTransferCompleteEvent(ehrTransferData);

        gp2gpMessengerService.sendEhrCompletePositiveAcknowledgement(ehrCompleteEvent, ehrTransferData);

        transferStore.handleEhrTransferStateUpdate(conversationId.toString(), ehrTransferData.getNemsMessageId(), TRANSFER_TO_REPO_COMPLETE, isActive);
        transferCompleteMessagePublisher.sendMessage(transferCompleteEvent, conversationId);
    }

    private TransferCompleteEvent createTransferCompleteEvent(Transfer ehrTransferData) {
        return new TransferCompleteEvent(
                ehrTransferData.getNemsEventLastUpdated(), ehrTransferData.getSourceGP(),
                "SUSPENSION", ehrTransferData.getNemsMessageId(),
                ehrTransferData.getNhsNumber()
        );
    }
}
