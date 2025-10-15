package uk.gov.hmcts.reform.hmc.errorhandling;

public final class ValidationError {

    private ValidationError() {
    }

    public static final String HMCTS_SERVICE_CODE_EMPTY_INVALID = "Hmcts service code is invalid";
    public static final String CASE_REF_EMPTY = "Case ref can not be empty";
    public static final String CASE_REF_INVALID = "Case ref details is invalid";
    public static final String HEARING_RESPONSE_DATETIME_NULL = "Hearing response received date "
        + "time can not be null or empty";
    public static final String HEARING_BROADCAST_DATETIME_NULL = "Hearing response broadcast date "
        + "time can not be null or empty";
    public static final String HMCTS_STATUS_NULL = "HMCTS status can not be null or empty";
    private static final String CHARACTERS_LONG = "characters long";
    public static final String HMCTS_STATUS_LENGTH = "HMCTS status must not be more than 100 " + CHARACTERS_LONG;

    public static final String LISTING_STATUS_NULL = "Listing status can not be null or empty";
    public static final String HEARING_LISTING_STATUS_CODE_LENGTH = "Hearing Listing status code must not be more "
        + "than 30 " + CHARACTERS_LONG;
    public static final String LIST_ASSIST_CASE_STATUS_NULL = "List assist case status can not be null or empty";

}
