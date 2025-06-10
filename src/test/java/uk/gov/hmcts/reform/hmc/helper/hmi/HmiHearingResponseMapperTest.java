package uk.gov.hmcts.reform.hmc.helper.hmi;


import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.hmc.client.HearingCode;
import uk.gov.hmcts.reform.hmc.data.CaseHearingRequestEntity;
import uk.gov.hmcts.reform.hmc.data.HearingDayDetailsEntity;
import uk.gov.hmcts.reform.hmc.data.HearingDayPanelEntity;
import uk.gov.hmcts.reform.hmc.data.HearingEntity;
import uk.gov.hmcts.reform.hmc.data.HearingResponseEntity;
import uk.gov.hmcts.reform.hmc.model.HmcHearingResponse;
import uk.gov.hmcts.reform.hmc.utils.TestingUtil;

import java.time.LocalDateTime;
import java.util.List;

import static java.time.LocalDateTime.of;
import static java.time.LocalDateTime.parse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;

class HmiHearingResponseMapperTest {

    private static HmiHearingResponseMapper hmiHearingResponseMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        hmiHearingResponseMapper = new HmiHearingResponseMapper();
    }

    @Test
    void mapHmiHearingToEntityToHmcModel() {
        HmcHearingResponse response = hmiHearingResponseMapper.mapEntityToHmcModel(
            generateHearingResponseEntity(
                1,
                of(2019, 1, 10, 11, 20, 00),
                "Draft",
                of(2019, 1, 10, 11, 20, 00),
                "12", true, "11", HearingCode.LISTED.name()
            ),
            generateHearingEntity("AWAITING_LISTING", 1, 1L)
        );
        assertAll(
            () -> assertThat(response.getHearingID(), is("1")),
            () -> assertThat(
                response.getHearingUpdate().getHearingResponseReceivedDateTime(),
                is(parse("2019-01-10T11:20"))
            ),
            () -> assertThat(response.getHearingUpdate().getHmcStatus(), is("AWAITING_LISTING")),
            () -> assertThat(response.getHearingUpdate().getHearingListingStatus(), is("Draft")),
            () -> assertThat(
                response.getHearingUpdate().getNextHearingDate(),
                is(parse("2019-01-10T11:20"))
            ),
            () -> assertThat(response.getHearingUpdate().getHearingVenueId(), is("12")),
            () -> assertThat(response.getHearingUpdate().getHearingJudgeId(), is("11")),
            () -> assertThat(response.getHearingUpdate().getListAssistCaseStatus(), is(HearingCode.LISTED.name()))
        );
    }

    @Test
    void mapHmiHearingToEntityToHmcModelWhenStatusIsException() {
        HmcHearingResponse response = hmiHearingResponseMapper.mapEntityToHmcModel(
            generateHearingResponseEntity(
                1,
                of(2019, 1, 10, 11, 20, 00),
                "Draft",
                of(2019, 1, 10, 11, 20, 00),
                "12", false, "11", HearingCode.LISTED.name()
            ),
            generateHearingEntity("EXCEPTION", 1, 1L)
        );
        assertAll(
            () -> assertThat(response.getHearingID(), is("1")),
            () -> assertThat(
                response.getHearingUpdate().getHearingResponseReceivedDateTime(), is(nullValue())),
            () -> assertThat(response.getHearingUpdate().getHearingEventBroadcastDateTime(), is(nullValue())),
            () -> assertThat(response.getHearingUpdate().getHmcStatus(), is("EXCEPTION")),
            () -> assertThat(response.getHearingUpdate().getHearingListingStatus(), is(nullValue())),
            () -> assertThat(response.getHearingUpdate().getNextHearingDate(), is(nullValue())),
            () -> assertThat(response.getHearingUpdate().getHearingVenueId(), is(nullValue())),
            () -> assertThat(response.getHearingUpdate().getHearingJudgeId(), is(nullValue()))
        );
    }

    @Test
    void mapHmiHearingToEntityToHmcModelForHearingEntity() {
        HmcHearingResponse response = hmiHearingResponseMapper.mapEntityToHmcModel(
            generateHearingEntity("AWAITING_LISTING", 1, 1L)
        );
        assertAll(
            () -> assertThat(response.getHearingID(), is("1")),
            () -> assertThat(response.getCaseRef(), is("1111222233334444")),
            () -> assertThat(response.getHmctsServiceCode(), is("Test")),
            () -> assertThat(response.getHearingUpdate().getHearingResponseReceivedDateTime(), is(nullValue())),
            () -> assertThat(response.getHearingUpdate().getHmcStatus(), is("AWAITING_LISTING")),
            () -> assertThat(response.getHearingUpdate().getHearingEventBroadcastDateTime(), is(notNullValue()))
        );
    }

    @Test
    void mapHmiHearingToEntityToHmcModelForHearingEntityWhenStatusIsException() {
        HmcHearingResponse response = hmiHearingResponseMapper.mapEntityToHmcModel(
            generateHearingEntity("EXCEPTION", 1, 1L)
        );
        assertAll(
            () -> assertThat(response.getHearingID(), is("1")),
            () -> assertThat(response.getCaseRef(), is("1111222233334444")),
            () -> assertThat(response.getHmctsServiceCode(), is("Test")),
            () -> assertThat(response.getHearingUpdate().getHearingResponseReceivedDateTime(), is(nullValue())),
            () -> assertThat(response.getHearingUpdate().getHmcStatus(), is("EXCEPTION")),
            () -> assertThat(response.getHearingUpdate().getHearingEventBroadcastDateTime(), is(nullValue()))
        );
    }


    private HearingResponseEntity generateHearingResponseEntity(int requestVersion, LocalDateTime dateTime,
                                                                String listingStatus,
                                                                LocalDateTime startTime,
                                                                String venueId, Boolean isPresiding,
                                                                String panelId, String listingCaseStatus) {
        HearingResponseEntity hearingResponseEntity = new HearingResponseEntity();
        hearingResponseEntity.setRequestVersion(requestVersion);
        hearingResponseEntity.setRequestTimeStamp(dateTime);
        hearingResponseEntity.setListingStatus(listingStatus);
        hearingResponseEntity.setListingCaseStatus(listingCaseStatus);

        HearingDayDetailsEntity hearingDayDetailsEntity = new HearingDayDetailsEntity();
        hearingDayDetailsEntity.setStartDateTime(startTime);
        hearingDayDetailsEntity.setVenueId(venueId);
        HearingDayPanelEntity hearingDayPanelEntity = new HearingDayPanelEntity();
        hearingDayPanelEntity.setIsPresiding(isPresiding);
        hearingDayPanelEntity.setPanelUserId(panelId);
        hearingDayDetailsEntity.setHearingDayPanel(List.of(hearingDayPanelEntity));
        hearingResponseEntity.setHearingDayDetails(List.of(hearingDayDetailsEntity));

        return hearingResponseEntity;
    }

    private HearingEntity generateHearingEntity(String status, int version, Long id) {
        HearingEntity hearingEntity = new HearingEntity();
        hearingEntity.setId(id);
        CaseHearingRequestEntity caseHearingRequestEntity = TestingUtil.caseHearingRequestEntity();
        caseHearingRequestEntity.setVersionNumber(version);
        hearingEntity.setCaseHearingRequests(Lists.newArrayList(caseHearingRequestEntity));

        HearingResponseEntity hearingResponseEntity = new HearingResponseEntity();
        hearingEntity.setHearingResponses(Lists.newArrayList(hearingResponseEntity));
        hearingEntity.setStatus(status);

        return hearingEntity;
    }
}
