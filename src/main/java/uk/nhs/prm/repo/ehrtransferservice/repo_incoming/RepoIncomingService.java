package uk.nhs.prm.repo.ehrtransferservice.repo_incoming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.prm.repo.ehrtransferservice.database.TransferTrackerService;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.Gp2gpMessengerEhrRequestBody;
import uk.nhs.prm.repo.ehrtransferservice.services.Gp2gpMessengerClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoIncomingService {

    private static final String TRANSFER_TO_REPO_STARTED = "ACTION:TRANSFER_TO_REPO_STARTED";
    private static final String EHR_REQUEST_SENT = "ACTION:EHR_REQUEST_SENT";

    private final TransferTrackerService transferTrackerService;
    private final Gp2gpMessengerClient gp2gpMessengerClient;
    private final ConversationIdStore conversationIdStore;

    @Value("${repositoryAsid}")
    private String repositoryAsid;

    public void processIncomingEvent(RepoIncomingEvent repoIncomingEvent) throws Exception {
        transferTrackerService.recordEventInDb(repoIncomingEvent, TRANSFER_TO_REPO_STARTED);
        callGp2gpMessenger(repoIncomingEvent);
    }

    private void callGp2gpMessenger(RepoIncomingEvent repoIncomingEvent) throws Exception {
        Gp2gpMessengerEhrRequestBody requestBody = new Gp2gpMessengerEhrRequestBody(repoIncomingEvent.getDestinationGp(),
                repositoryAsid, repoIncomingEvent.getSourceGp(), conversationIdStore.getConversationId());
        try {
            gp2gpMessengerClient.sendGp2gpMessengerEhrRequest(repoIncomingEvent.getNhsNumber(), requestBody);
        } catch (Exception e) {
            log.error("Caught error during ehr-request");
            throw new Exception("Got client error", e);
        }
    }
}
