package uk.gov.hmcts.reform.hmc.helper.hmi;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.hmc.client.HearingCode;
import uk.gov.hmcts.reform.hmc.data.CaseHearingRequestEntity;
import uk.gov.hmcts.reform.hmc.data.HearingDayPanelEntity;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingResponseEntity;
import uk.gov.hmcts.reform.hmc.model.HmcHearingResponse;
import uk.gov.hmcts.reform.hmc.model.HmcHearingUpdate;

import java.time.Clock;
import java.time.LocalDateTime;

import static uk.gov.hmcts.reform.hmc.constants.Constants.EXCEPTION;

@Component
public class HmiHearingResponseMapper {

    public HmcHearingResponse mapEntityToHmcModel(HearingResponseEntity hearingResponseEntity, HearingEntity hearing) {
        HmcHearingResponse hmcHearingResponse = new HmcHearingResponse();
        hmcHearingResponse.setHearingID(hearing.getId().toString());
        CaseHearingRequestEntity matchingCaseHearingRequestEntity = hearing
            .getCaseHearingRequest(hearingResponseEntity.getRequestVersion());
        hmcHearingResponse.setCaseRef(matchingCaseHearingRequestEntity.getCaseReference());
        hmcHearingResponse.setHmctsServiceCode(matchingCaseHearingRequestEntity.getHmctsServiceCode());

        //There is currently only support for one hearingDayDetail to be provided in HearingResponse From ListAssist
        HmcHearingUpdate hmcHearingUpdate = new HmcHearingUpdate();
        hmcHearingUpdate.setHmcStatus(hearing.getStatus());
        if (hearing.getStatus() != EXCEPTION) {
            hmcHearingUpdate.setHearingResponseReceivedDateTime(hearingResponseEntity.getRequestTimeStamp());
            hmcHearingUpdate.setHearingEventBroadcastDateTime(LocalDateTime.now(Clock.systemUTC()));
            if (hearingResponseEntity.getListingStatus() != null) {
                hmcHearingUpdate.setHearingListingStatus(hearingResponseEntity.getListingStatus());
            }
            hmcHearingUpdate.setNextHearingDate(hearingResponseEntity.getHearingDayDetails().get(0).getStartDateTime());
            hmcHearingUpdate.setHearingVenueId(hearingResponseEntity.getHearingDayDetails().get(0).getVenueId());
            for (HearingDayPanelEntity hearingDayPanelEntity :
                hearingResponseEntity.getHearingDayDetails().get(0).getHearingDayPanel()) {
                if (Boolean.TRUE.equals(hearingDayPanelEntity.getIsPresiding())) {
                    hmcHearingUpdate.setHearingJudgeId(hearingDayPanelEntity.getPanelUserId());
                }
            }
            hmcHearingUpdate.setListAssistCaseStatus(HearingCode.getByLabel(hearingResponseEntity
                                                                                .getListingCaseStatus()).name());
            hmcHearingUpdate.setHearingRoomId(hearingResponseEntity.getHearingDayDetails().get(0).getRoomId());
        }
        hmcHearingResponse.setHearingUpdate(hmcHearingUpdate);
        return hmcHearingResponse;
    }

    public HmcHearingResponse mapEntityToHmcModel(HearingEntity hearing) {
        HmcHearingResponse hmcHearingResponse = new HmcHearingResponse();
        hmcHearingResponse.setHearingID(hearing.getId().toString());
        CaseHearingRequestEntity matchingCaseHearingRequestEntity = hearing.getLatestCaseHearingRequest();
        hmcHearingResponse.setCaseRef(matchingCaseHearingRequestEntity.getCaseReference());
        hmcHearingResponse.setHmctsServiceCode(matchingCaseHearingRequestEntity.getHmctsServiceCode());

        HmcHearingUpdate hmcHearingUpdate = new HmcHearingUpdate();
        hmcHearingUpdate.setHmcStatus(hearing.getStatus());
        if (hearing.getStatus() != EXCEPTION) {
            hmcHearingUpdate.setHearingEventBroadcastDateTime(LocalDateTime.now(Clock.systemUTC()));
        }
        hmcHearingResponse.setHearingUpdate(hmcHearingUpdate);
        return hmcHearingResponse;
    }
}
