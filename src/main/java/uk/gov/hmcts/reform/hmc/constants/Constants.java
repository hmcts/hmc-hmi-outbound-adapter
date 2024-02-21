package uk.gov.hmcts.reform.hmc.constants;

public final class Constants {

    private Constants() {
    }

    public static final String MESSAGE_TYPE = "message_type";
    public static final String HEARING_ID = "hearing_id";
    public static final String  HMC_HMI_OUTBOUND_ADAPTER = "<Hmc hmi outbound adapter>";
    public static final String WRITE = "<WRITE>";
    public static final String READ = "<READ>";

    public static final String ERROR_PROCESSING_MESSAGE = "Error occurred during service bus processing. "
        + "Service:{} .Entity:{}. Method:{}. Hearing ID: {}.";
    public static final String HMC_TO_HMI = "hmc-to-hmi";
    public static final String HMC_FROM_HMI = "hmc-from-hmi";
    public static final String NOT_DEFINED = "NOT_DEFINED";
    public static final String MESSAGE_ERROR = "Error for message with id ";
    public static final String WITH_ERROR = " with error ";
    public static final String UPDATE_HEARING_REQUEST = "update-hearing-request";
    public static final String CREATE_HEARING_REQUEST = "create-hearing-request";
    public static final String DELETE_HEARING_REQUEST = "delete-hearing-request";
    public static final String HMI_TARGET = "hmi";
    public static final String FH_TARGET = "fh";
    public static final String HMC_TARGET = "hmc";
}
