package de.ids_mannheim.korap.util;

public enum StatusCodes {
    // 600 - 699 - Krill server error codes
    UNABLE_TO_READ_INDEX(600), 
    UNABLE_TO_FIND_INDEX(601),
    UNABLE_TO_ADD_DOC_TO_INDEX(602),
    UNABLE_TP_COMMIT_STAGED_DATA_TO_INDEX(603),
    UNABLE_TO_CONNECT_TO_DB(604),
    MISSING_REQUEST_PARAMETER(610),
    ARBITRARY_DESERIALIZATION_ERROR(613),
    UNABLE_TO_GENERATE_JSON(620),
    UNABLE_TO_PARSE_JSON(621),
    DOCUMENT_NOT_FOUND(630),
    UNABLE_TO_EXTEND_CONTEXT(651),
    SERVER_IS_RUNNING(680),
    DOC_ADDED(681),
    RESPONSE_TIME_EXCEEDED(682),
    STAGED_DATA_COMMITTED(683),
    
//    700 - 799 - KoralQuery Deserialization errors
    NO_QUERY_GIVEN(700),
    MISSING_TYPE(701),
    INVALID_BOUNDARY(702),
    MISSING_OPERATION(703),
    MISSING_OPERAND_LIST(704),
    NUMBER_OF_OPERAND_NOT_ACCEPTABLE(705),
    UNKNOWN_FRAME_TYPE(706),
    INVALID_DISTANCE_CONSTRAINTS(707),
    MISSING_DISTANCE(708),
    VALID_CLASS_NUMBER_EXCEEDED(709),
    MISSING_CLASS_ATTRIBUTE(710),
    UNKNOWN_GROUP_OPERATION(711),
    UNKNOWN_REFERENCE_OPERATION(712),
    UNSUPPORTED_QUERY(713),
    INVALID_SPAN_REFERENCE_PARAMETER(714),
    UNSUPPORTED_ATTRIBUTE_TYPE(715),
    UNKNOWN_RELATION(716),
    MISSING_RELATION_NODE(717),
    MISSING_RELATION_TERM(718),
    INVALID_QUERY(719),
    INVALID_MATCH_ID(730),
    MISSING_KEY(740),
    UNKNOWN_MATCH_RELATION(741),
    MISSING_TERM_RELATION(743),
    UNSUPPORTED_OPERAND(744),
    UNSUPPORTED_TOKEN_TYPE(745),
    UNSUPPORTED_TERM_TYPE(746),
    NULL_ATTRIBUTE(747),
    UNKNOWN_FLAG(748),
    NON_WELL_FORMED_NOTIFICATION(750),
    UNSUPPORTED_OPERATION(760),
    UNSUPPORTED_OPERATOR(761),
    QUERY_MATCH_EVERYWHERE(780),
    IGNORE_OPTIONALITY(781),
    IGNORE_EXCLUSIVITY(782),
    QUERY_CANNOT_MATCH_ANYWHERE(783),
    UNKNOWN_QUERY_SERIALIZATION_MESSAGE(799),

//    800 - 899 - Virtual Collection Messages
    MISSING_COLLECTION(800),
    UNSUPPORTED_MATCH_TYPE(802),
    UNKNOWN_VALUE_TYPE(804),
    INVALID_VALUE(805),
    INVALID_DATE_STRING(806),
    INVALID_REGEX(807),
    UNKNOWN_REWRITE_OPERATION(814),
    MISSING_SOURCE(815),
    MISSING_VALUE(820),
    EMPTY_FILTER(830),
    UNWRAPPABLE_FILTER(831),
    INVALID_FILTER_OPERATION(832),
    UNSUPPORTED_TYPE(843),
    COLLECTIONS_UNSUPPORTED(850),
    
    
//    900 - 999 - Corpus Data errors 
    INVALID_OFFSET(952),
    INCOMPLETE_OFFSET(953),
    INVALID_FOUNDRY(970);

    private int code;
    StatusCodes (int code) {
        this.code = code;
    }
   
    public int getCode () {
        return code;
    }
}