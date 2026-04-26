package com.bone.tpa.claim.infrastructure.messaging.event;

import com.bone.core.event.EntityEventHandler;
import com.bone.tpa.sdk.claim.model.Claim;
import org.springframework.stereotype.Component;

@Component
public class ClaimEventHandler implements EntityEventHandler<Claim> {

    @Override
    public void handleEvent(Claim claimCase) {
//        switch (claimCase.getEventType()) {
//            case CREATED:
//                // todo  claimCaseService.createClaimCase(claimCase);
//                break;
//            case UPDATED:
//                // todo  claimCaseService.updateClaimCase(claimCase);
//                break;
//            case DELETED:
//                // todo  claimCaseService.deleteClaimCase(claimCase);
//                break;
//            default:
//                throw new IllegalArgumentException("Unknown event type: " + claimCase.getEventType());
//        }
    }
}
