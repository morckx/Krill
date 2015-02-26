package de.ids_mannheim.korap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.lucene.util.automaton.RegExp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.ids_mannheim.korap.query.SpanWithinQuery;
import de.ids_mannheim.korap.query.QueryBuilder;
import de.ids_mannheim.korap.query.wrap.*;
import de.ids_mannheim.korap.response.Notifications;
import de.ids_mannheim.korap.util.QueryException;

/**
 * KrillQuery provides deserialization methods
 * for KoralQuery query objects.
 *
 * <blockquote><pre>
 *   SpanQueryWrapper sqw = new KrillQuery("tokens").fromJson("{... JsonString ...}");
 * </pre></blockquote>
 *
 * @author diewald
 */
/*
  TODO: Merge this with SpanQueryWrapper

  TODO: Use full-blown jsonld processor

  TODO: All queries with a final right expansion
  e.g. der alte []
  should be wrapped in a contains(<base/s=t>) to ensure
  they are not outside the text.

  TODO: Create Pre-filter while preparing a Query.
  The pre-filter will contain a boolena query with all
  necessary terms, supporting boolean OR, ignoring
  negation terms (and negation subqueries), like
  [base=Der]([base=alte]|[base=junge])[base=Mann & p!=ADJA]![base=war | base=lag]
  Search for all documents containing "s:Der" and ("s:alte" or "s:junge") and "s:Mann"
*/
public class KrillQuery extends Notifications {
    private String field;
    private QueryBuilder builder;
    private JsonNode json;

    // Logger
    private final static Logger log = LoggerFactory.getLogger(KrillQuery.class);

    // This advices the java compiler to ignore all loggings
    public static final boolean DEBUG = false;

    // <legacy>
    public static final byte
        OVERLAP      = SpanWithinQuery.OVERLAP,
        REAL_OVERLAP = SpanWithinQuery.REAL_OVERLAP,
        WITHIN       = SpanWithinQuery.WITHIN,
        REAL_WITHIN  = SpanWithinQuery.REAL_WITHIN,
        ENDSWITH     = SpanWithinQuery.ENDSWITH,
        STARTSWITH   = SpanWithinQuery.STARTSWITH,
        MATCH        = SpanWithinQuery.MATCH;
    // </legacy>

    private static final int MAX_CLASS_NUM = 255; // 127;

    /**
     * Constructs a new object for query deserialization
     * and building. Expects the name of an index field
     * to apply the query on (this should normally be
     * a token stream field).
     *
     * @param field The specific index field for the query.
     */
    public KrillQuery (String field) {
        this.field = field;
    };


    // Private class for koral:boundary objects
    private class Boundary {
        public int min, max;

        // Constructor for boundaries
        public Boundary (JsonNode json, int defaultMin, int defaultMax)
            throws QueryException {
            
            // No @type defined
            if (!json.has("@type")) {
                throw new QueryException(
                    701,
                    "JSON-LD group has no @type attribute"
                );
            };

            // Wrong @type defined
            if (!json.get("@type").asText().equals("koral:boundary"))
                throw new QueryException(702, "Boundary definition is invalid");

            // Set min boundary
            this.min = json.has("min") ?
                json.get("min").asInt(defaultMin) :
                defaultMin;

            // Set max boundary
            this.max = json.has("max") ?
                json.get("max").asInt(defaultMax) :
                defaultMax;
            
            if (DEBUG)
                log.trace("Found koral:boundary with {}:{}", min, max);
        };
    };


    /**
     * Deserialize JSON-LD query to a {@link SpanQueryWrapper} object.
     *
     * <p>
     * <blockquote><pre>
     *   KrillQuery kq = new KrillQuery("tokens");
     *   SpanQueryWrapper sqw = kq.fromJson('{"@type":"koral:token","wrap":{' +
     *      '"@type":"koral:term","foundry":"opennlp",' +
     *      '"key":"tree","layer":"orth",' +
     *      '"match":"match:eq"}}'
     *   );
     * </pre></blockquote>
     *
     * @param json String representing the JSON query string.
     * @return {@link SpanQueryWrapper} object. 
     * @throws QueryException
     */
    public SpanQueryWrapper fromJson (String json) throws QueryException {
        JsonNode jsonN;
        try {
            // Read Json string
            jsonN = new ObjectMapper().readValue(json, JsonNode.class);
        }

        // Something went wrong
        catch (IOException e) {
            String msg = e.getMessage();
            log.warn("Unable to parse JSON: " + msg.split("\n")[0]);
            throw new QueryException(621, "Unable to parse JSON");
        };

        // The query is nested in a parent query
        if (!jsonN.has("@type") && jsonN.has("query"))
            jsonN = jsonN.get("query");

        // Deserialize from node
        return this.fromJson(jsonN);
    };


    /**
     * Deserialize JSON-LD query as a {@link JsonNode} object
     * to a {@link SpanQueryWrapper} object.
     *
     * @param json {@link JsonNode} representing the JSON query string.
     * @return {@link SpanQueryWrapper} object. 
     * @throws QueryException
     */
    // TODO: Exception messages are horrible!
    // TODO: Use the shortcuts implemented in this class instead of the wrapper constructors
    // TODO: Rename this span context!
    public SpanQueryWrapper fromJson (JsonNode json) throws QueryException {
        int number = 0;
        if (!json.has("@type"))
            throw new QueryException(701, "JSON-LD group has no @type attribute");

        // Set this for reserialization
        // This may be changed later on
        this.json = json;

        // Get @type for branching
        String type = json.get("@type").asText();

        switch (type) {
        case "koral:group":
            return this._groupFromJson(json);

        case "koral:reference":
            if (json.has("operation") &&
                !json.get("operation").asText().equals("operation:focus"))
                throw new QueryException(712, "Unknown reference operation");

            if (!json.has("operands")) {
                throw new QueryException(
                    766,
                    "Peripheral references are currently not supported"
                );
            };

            JsonNode operands = json.get("operands");

            if (!operands.isArray())
                throw new QueryException(704, "Operation needs operand list");

            if (operands.size() == 0)
                throw new QueryException(704, "Operation needs operand list");

            if (operands.size() != 1)
                throw new QueryException(705, "Number of operands is not acceptable");

            // Reference based on classes
            if (json.has("classRef")) {
                if (json.has("classRefOp")) {
                    throw new QueryException(
                        761,
                        "Class reference operators are currently not supported"
                    );
                };

                number = json.get("classRef").get(0).asInt();

                if (number > MAX_CLASS_NUM)
                    throw new QueryException(
                        709,
                        "Valid class numbers exceeded"
                    );
            }

            // Reference based on spans
            else if (json.has("spanRef")) {
                JsonNode spanRef = json.get("spanRef");
                int length = 0;
                int startOffset = 0;
                if (!spanRef.isArray() || spanRef.size() == 0) {
                    throw new QueryException(
                        714,
                        "Span references expect a start position" +
                        " and a length parameter"
                    );
                };
                
                if (spanRef.size() > 1)
                    length = spanRef.get(1).asInt(0);
	            
                startOffset = spanRef.get(0).asInt(0);

                if (DEBUG) log.trace("Wrap span reference {},{}", startOffset, length);

                SpanQueryWrapper sqw = this.fromJson(operands.get(0));
				SpanSubspanQueryWrapper ssqw = new SpanSubspanQueryWrapper(
						sqw, startOffset, length);
				return ssqw;
            };

            if (DEBUG) log.trace("Wrap class reference {}", number);

            return new SpanFocusQueryWrapper(
                this.fromJson(operands.get(0)), number
            );

        case "koral:token":
            // The token is empty and should be treated like []
            if (!json.has("wrap"))
                return new SpanRepetitionQueryWrapper();

            // Get wrapped token
            return this._segFromJson(json.get("wrap"));

        case "koral:span":
            return this._termFromJson(json);
        };

        // Unknown query type
        throw new QueryException(713, "Query type is not supported");
    };


    /**
     * Get the associated {@link QueryBuilder} object
     * for query building.
     */
    public QueryBuilder builder () {
        if (this.builder == null)
            this.builder = new QueryBuilder(this.field);
        return this.builder;
    };


    /**
     * Return the associated KoralQuery query object
     * as a {@link JsonNode}. This won't work,
     * if the object was build using a {@link QueryBuilder},
     * therefore it is limited to mirror a deserialized KoralQuery
     * object.
     *
     * @return The {@link JsonNode} representing the query object
     *         of a deserialized KoralQuery object.
     */
    public JsonNode toJsonNode () {
        return this.json;
    };


    /**
     * Return the associated KoralQuery query object
     * as a JSON string. This won't work,
     * if the object was build using a {@link QueryBuilder},
     * therefore it is limited to mirror a deserialized KoralQuery
     * object.
     *
     * @return A JSON string representing the query object
     *         of a deserialized KoralQuery object.
     */
    public String toJsonString () {
        if (this.json == null)
            return "{}";
        return this.json.toString();
    };


    // Deserialize koral:group
    private SpanQueryWrapper _groupFromJson (JsonNode json) throws QueryException {

        // No operation
        if (!json.has("operation"))
            throw new QueryException(703, "Group expects operation");

        // Get operation
        String operation = json.get("operation").asText();

        if (DEBUG) log.trace("Found {} group", operation);

        if (!json.has("operands"))
            throw new QueryException(704, "Operation needs operand list");

        // Get all operands
        JsonNode operands = json.get("operands");

        if (operands == null || !operands.isArray())
            throw new QueryException(704, "Operation needs operand list");

        if (DEBUG) log.trace("Operands are {}", operands);

        // Branch on operation
        switch (operation) {
        case "operation:junction":
            return this._operationJunctionFromJson(operands);
            
        case "operation:position":
            return this._operationPositionFromJson(json, operands);

        case "operation:sequence":
            return this._operationSequenceFromJson(json, operands);

        case "operation:class":
            return this._operationClassFromJson(json, operands);

        case "operation:repetition":
            return this._operationRepetitionFromJson(json, operands);

        case "operation:relation":
            throw new QueryException(765, "Relations are currently not supported");

        case "operation:or": // Deprecated in favor of operation:junction
            return this._operationJunctionFromJson(operands);
            /*
        case "operation:submatch": // Deprecated in favor of koral:reference
            return this._operationSubmatchFromJson(json, operands);
            */
        };

        // Unknown
        throw new QueryException(711, "Unknown group operation");
    };


    // Deserialize operation:junction
    private SpanQueryWrapper _operationJunctionFromJson (JsonNode operands)
        throws QueryException {
        SpanAlterQueryWrapper ssaq = new SpanAlterQueryWrapper(this.field);
        for (JsonNode operand : operands) {
            ssaq.or(this.fromJson(operand));
        };
        return ssaq;
    };


    // Deserialize operation:position
    private SpanQueryWrapper _operationPositionFromJson (JsonNode json, JsonNode operands)
        throws QueryException {
        if (operands.size() != 2)
            throw new QueryException(705, "Number of operands is not acceptable");

        String frame = "isAround";
        // Temporary workaround for wrongly set overlaps
        if (json.has("frames")) {
            JsonNode frameN = json.get("frames");
            if (frameN.isArray()) {
                frameN = json.get("frames").get(0);
                if (frameN != null && frameN.isValueNode())
                    frame = frameN.asText().substring(7);
            };
        }
        // <legacyCode>
        else if (json.has("frame")) {
            this.addMessage(0, "Frame is deprecated");

            JsonNode frameN = json.get("frame");
            if (frameN != null && frameN.isValueNode())
                frame = frameN.asText().substring(6);
        };
        // </legacyCode>

        if (DEBUG) log.trace("Position frame is '{}'", frame);

        // Byte flag - should cover all 13 cases, i.e. two bytes long
        byte flag = WITHIN;
        switch (frame) {
        case "isAround":
            break;
        case "strictlyContains":
            flag = REAL_WITHIN;
            break;
        case "isWithin":
            break;
        case "startsWith":
            flag = STARTSWITH;
            break;
        case "endsWith":
            flag = ENDSWITH;
            break;
        case "matches":
            flag = MATCH;
            break;
        case "overlaps":
            flag = OVERLAP;
            this.addWarning(
                769,
                "Overlap variant currently interpreted as overlap"
            );
            break;
        case "overlapsLeft":
            // Temporary workaround
            this.addWarning(
                769,
                "Overlap variant currently interpreted as overlap"
            );
            flag = OVERLAP;
            break;
        case "overlapsRight":
            // Temporary workaround
            this.addWarning(
                769,
                "Overlap variant currently interpreted as overlap"
            );
            flag = OVERLAP;
            break;
        case "strictlyOverlaps":
            flag = REAL_OVERLAP;
            break;

            // alignsLeft

        default:
            throw new QueryException(706, "Frame type is unknown");
        };
        
        // The exclusion operator is no longer relevant
        // <legacyCode>
        Boolean exclude;
        if (json.has("exclude") && json.get("exclude").asBoolean()) {
            throw new QueryException(
                760,
                "Exclusion is currently not supported in position operations"
            );
        };
        // </legacyCode>

        // Create SpanWithin Query
        return new SpanWithinQueryWrapper(
            this.fromJson(operands.get(0)),
            this.fromJson(operands.get(1)),
            flag
        );
    };


    // Deserialize operation:repetition
    private SpanQueryWrapper _operationRepetitionFromJson (JsonNode json, JsonNode operands)
        throws QueryException {

        if (operands.size() != 1)
            throw new QueryException(705, "Number of operands is not acceptable");

        int min = 0, max = 100;

        if (json.has("boundary")) {
            Boundary b = new Boundary(json.get("boundary"), 0, 100);
            min = b.min;
            max = b.max;
        }
        // <legacyCode>
        else {
            this.addMessage(0, "Setting boundary by min and max is deprecated");

            // Set minimum value
            if (json.has("min"))
                min = json.get("min").asInt(0);

            // Set maximum value
            if (json.has("max"))
                max = json.get("max").asInt(100);
        };
        // </legacyCode>

        // Sanitize max
        if (max < 0)
            max = 100;
        else if (max > 100)
            max = 100;

        // Sanitize min
        if (min < 0)
            min = 0;
        else if (min > 100)
            min = 100;
                
        // Check relation between min and max
        if (min > max)
            max = max;

        SpanQueryWrapper sqw = this.fromJson(operands.get(0));
                
        if (sqw.maybeExtension())
            return sqw.setMin(min).setMax(max);

        return new SpanRepetitionQueryWrapper(sqw, min, max);
    };


    // Deserialize operation:submatch
    @Deprecated
    private SpanQueryWrapper _operationSubmatchFromJson (JsonNode json, JsonNode operands)
        throws QueryException {

        int number = 1;

        this.addMessage(0, "operation:submatch is deprecated");

        if (operands.size() != 1)
            throw new QueryException(705, "Number of operands is not acceptable");

        // Use class reference
        if (json.has("classRef")) {
            if (json.has("classRefOp")) {
                throw new QueryException(
                    761,
                    "Class reference operators are currently not supported"
                );
            };

            number = json.get("classRef").get(0).asInt();
        }

        // Use span reference
        else if (json.has("spanRef")) {
            throw new QueryException(
                762,
                "Span references are currently not supported"
            );
        }; 

        return new SpanFocusQueryWrapper(
            this.fromJson(operands.get(0)), number
        );
    };


    // Deserialize operation:class
    private SpanQueryWrapper _operationClassFromJson (JsonNode json, JsonNode operands)
        throws QueryException {
        int number = 1;

        // Too many operands
        if (operands.size() != 1)
            throw new QueryException(705, "Number of operands is not acceptable");

        // Get class number
        if (json.has("classOut")) {
            number = json.get("classOut").asInt(0);
        }
        // <legacyCode>
        else if (json.has("class")) {
            this.addMessage(0, "Class is deprecated");
            number = json.get("class").asInt(0);
        };
        // </legacyCode>

        // Class reference check
        if (json.has("classRefCheck")) {
            this.addWarning(
                764,
                "Class reference checks are currently " +
                "not supported - results may not be correct"
            );
        };

        // Class reference operation
        // This has to be done after class ref check
        if (json.has("classRefOp")) {
            throw new QueryException(
                761,
                "Class reference operators are currently not supported"
            );
        };

        // Number is set
        if (number > 0) {
            if (operands.size() != 1) {
                throw new QueryException(
                    705,
                    "Number of operands is not acceptable"
                );
            };
            
            if (DEBUG) log.trace("Found Class definition for {}", number);

            if (number > MAX_CLASS_NUM) {
                throw new QueryException(
                    709,
                    "Valid class numbers exceeded"
                );
            };

            // Serialize operand
            SpanQueryWrapper sqw = this.fromJson(operands.get(0));

            // Problematic
			if (sqw.maybeExtension())
			return sqw.setClassNumber(number);

            return new SpanClassQueryWrapper(sqw, number);
        };

        throw new QueryException(710, "Class attribute missing");
    };


    // Deserialize operation:sequence
    private SpanQueryWrapper _operationSequenceFromJson (JsonNode json, JsonNode operands)
        throws QueryException {

        // Sequence with only one operand
        if (operands.size() == 1)
            return this.fromJson(operands.get(0));

        SpanSequenceQueryWrapper sseqqw = this.builder().seq();

        // Say if the operand order is important
        if (json.has("inOrder"))
            sseqqw.setInOrder(json.get("inOrder").asBoolean());

        // Introduce distance constraints
        // ATTENTION: Distances have to be set before segments are added
        if (json.has("distances")) {

            // THIS IS NO LONGER NECESSARY, AS IT IS COVERED BY FRAMES
            if (json.has("exclude") && json.get("exclude").asBoolean()) {
                throw new QueryException(
                    763,
                    "Excluding distance constraints are currently not supported"
                );
            };

            if (!json.get("distances").isArray()) {
                throw new QueryException(
                    707,
                    "Distance Constraints have to be defined as arrays"
                );
            };

            // TEMPORARY: Workaround for group distances
            JsonNode firstDistance = json.get("distances").get(0);
            
            if (!firstDistance.has("@type")) {
                throw new QueryException(
                    701,
                    "JSON-LD group has no @type attribute"
                );
            };

            JsonNode distances;
            if (firstDistance.get("@type").asText().equals("koral:group")) {
                if (!firstDistance.has("operands") ||
                    !firstDistance.get("operands").isArray())
                    throw new QueryException(704, "Operation needs operand list");

                distances = firstDistance.get("operands");
            }

            // Support korap distances
            // Support cosmas distances
            else if (firstDistance.get("@type").asText().equals("koral:distance")
                     ||
                     firstDistance.get("@type").asText().equals("cosmas:distance")) {
                distances = json.get("distances");
            }

            else
                throw new QueryException(708, "No valid distances defined");

            // Add all distance constraint to query
            for (JsonNode constraint : distances) {
                String unit = "w";
                if (constraint.has("key"))
                    unit = constraint.get("key").asText();
                
                // There is a maximum of 100 fix
                int min = 0, max = 100;
                if (constraint.has("boundary")) {
                    Boundary b = new Boundary(constraint.get("boundary"), 0,100);
                    min = b.min;
                    max = b.max;
                }
                else {
                    if (constraint.has("min"))
                        min = constraint.get("min").asInt(0);
                    if (constraint.has("max"))
                        max = constraint.get("max").asInt(100);
                };
                        
                // Add foundry and layer to the unit for new indices
                if (constraint.has("foundry") &&
                    constraint.has("layer") &&
                    constraint.get("foundry").asText().length() > 0 &&
                    constraint.get("layer").asText().length() > 0) {
                            
                    StringBuilder value = new StringBuilder();
                    value.append(constraint.get("foundry").asText());
                    value.append('/');
                    value.append(constraint.get("layer").asText());
                    value.append(':').append(unit);
                    unit = value.toString();
                };
                
                // Sanitize boundary
                if (max < min) max = min;
                        
                if (DEBUG)
                    log.trace("Add distance constraint of '{}': {}-{}",
                              unit, min, max);
                        
                sseqqw.withConstraint(min, max, unit);
            };
        };

        // Add segments to sequence
        for (JsonNode operand : operands) {
            sseqqw.append(this.fromJson(operand));
        };

        // inOrder was set to false without a distance constraint
        if (!sseqqw.isInOrder() && !sseqqw.hasConstraints()) {
            sseqqw.withConstraint(1, 1, "w");
        };

        return sseqqw;
    };


    // Deserialize koral:token
    private SpanQueryWrapper _segFromJson (JsonNode json) throws QueryException {
        if (!json.has("@type"))
            throw new QueryException(701, "JSON-LD group has no @type attribute");

        String type = json.get("@type").asText();

        if (DEBUG)
            log.trace("Wrap new token definition by {}", type);

        // Branch on type
        switch (type) {
        case "koral:term":
//            String match = "match:eq";
//            if (json.has("match"))
//                match = json.get("match").asText();
//            
//            switch (match) {
//
//            case "match:ne":
//                if (DEBUG)
//                    log.trace("Term is negated");
//
//                SpanSegmentQueryWrapper ssqw =
//                    (SpanSegmentQueryWrapper) this._termFromJson(json);
//
//                ssqw.makeNegative();
//
//                return this.seg().without(ssqw);
//
//            case "match:eq":
                return this._termFromJson(json);
//            };
//
//            throw new QueryException(741, "Match relation unknown");

        case "koral:termGroup":

            if (!json.has("operands"))
                throw new QueryException(742, "Term group needs operand list");

            // Get operands
            JsonNode operands = json.get("operands");

            SpanSegmentQueryWrapper ssegqw = this.builder().seg();
            
            if (!json.has("relation"))
                throw new QueryException(743, "Term group expects a relation");

            switch (json.get("relation").asText()) {
            case "relation:and":

                for (JsonNode operand : operands) {
                    SpanQueryWrapper part = this._segFromJson(operand);
                    if (part instanceof SpanAlterQueryWrapper) {
                        ssegqw.with((SpanAlterQueryWrapper) part);			
                    }
                    else if (part instanceof SpanRegexQueryWrapper) {
                        ssegqw.with((SpanRegexQueryWrapper) part);
                    }
                    else if (part instanceof SpanSegmentQueryWrapper) {
                        ssegqw.with((SpanSegmentQueryWrapper) part);
                    }
                    else {
                        throw new QueryException(
                            744,
                            "Operand not supported in term group"
                        );
                    };
                };
                return ssegqw;

            case "relation:or":

                SpanAlterQueryWrapper ssaq = new SpanAlterQueryWrapper(this.field);
                for (JsonNode operand : operands) {
                    ssaq.or(this._segFromJson(operand));
                };
                return ssaq;
            };
        };
        throw new QueryException(745, "Token type is not supported");    
    };


    // Deserialize koral:term
    private SpanQueryWrapper _termFromJson (JsonNode json)
        throws QueryException {
    	
        if (!json.has("key") || json.get("key").asText().length() < 1) {
			if (!json.has("attr"))
				throw new QueryException(740,
						"Key definition is missing in term or span");
        };
	    
        if (!json.has("@type")) {
            throw new QueryException(
                701,
                "JSON-LD group has no @type attribute"
            );
        };

        Boolean isTerm = json.get("@type").asText().equals("koral:term") ? true : false;
        Boolean isCaseInsensitive = false;

        if (json.has("caseInsensitive") && json.get("caseInsensitive").asBoolean())
            isCaseInsensitive = true;

        StringBuilder value = new StringBuilder();
        
        // expect orth? expect lemma? 
        // s:den | i:den | cnx/l:die | mate/m:mood:ind | cnx/syn:@PREMOD |
        // mate/m:number:sg | opennlp/p:ART

        if (json.has("foundry") && json.get("foundry").asText().length() > 0)
            value.append(json.get("foundry").asText()).append('/');

        // No default foundry defined
        if (json.has("layer") && json.get("layer").asText().length() > 0) {
            String layer = json.get("layer").asText();
            switch (layer) {

            case "lemma":
                layer = "l";
                break;

            case "pos":
                layer = "p";
                break;

            case "orth":
                // TODO: THIS IS A BUG! AND SHOULD BE NAMED "SURFACE" or .
                layer = "s";
                break;

            case "struct":
                layer = "s";
                break;

            case "const":
                layer = "c";
                break;
            };

            if (isCaseInsensitive && isTerm) {
                if (layer.equals("s"))
                    layer = "i";
                else {
                    this.addWarning(
                        767,
                        "Case insensitivity is currently not supported for this layer"
                    );
                };
            };

            // Ignore foundry for orth layer
            if (layer.equals("s") || layer.equals("i"))
                value.setLength(0);

            value.append(layer).append(':');
        };

        if (json.has("key") && json.get("key").asText().length() > 0) {
            String key = json.get("key").asText();
            value.append(isCaseInsensitive ? key.toLowerCase() : key);
        };

        if (json.has("value") && json.get("value").asText().length() > 0)
            value.append(':').append(json.get("value").asText());

        // Regular expression or wildcard
        if (isTerm && json.has("type")) {

            QueryBuilder qb = this.builder();

            // Branch on type
            switch (json.get("type").asText()) {
            case "type:regex":
                return qb.seg(qb.re(value.toString(), isCaseInsensitive));

            case "type:wildcard":
                return qb.seq(qb.wc(value.toString(), isCaseInsensitive));

            case "type:string":
                break;

            default:
                this.addWarning(746, "Term type is not supported - treated as a string");
            };
        };

        if (isTerm){

        	String match = "match:eq";
			if (json.has("match")) {
				match = json.get("match").asText();
			}

			SpanSegmentQueryWrapper ssqw = this.builder().seg(value.toString());			
			if (match.equals("match:ne")) {
				if (DEBUG) log.trace("Term is negated");
				ssqw.makeNegative();
				return this.builder().seg().without(ssqw);
			} 
			else if (match.equals("match:eq")) {
				return ssqw;
			} 
			else {
				throw new QueryException(741, "Match relation unknown");
			}
        }

        if (json.has("attr")) {
			JsonNode attrNode = json.get("attr");
			if (!attrNode.has("@type")) {
				throw new QueryException(701,
						"JSON-LD group has no @type attribute");
			}

			if (value.toString().isEmpty()) {
				return _createElementAttrFromJson(null, json, attrNode);
				// this.addWarning(771,
				// "Arbitraty elements with attributes are currently not supported.");
			}
			else{
				SpanQueryWrapper elementWithIdWrapper = this.builder().tag(value.toString());
				if (elementWithIdWrapper == null){ return null; }
				return _createElementAttrFromJson(elementWithIdWrapper, json,
						attrNode);
			}
        };
        return this.builder().tag(value.toString());
    };


    // Deserialize elements with attributes
	private SpanQueryWrapper _createElementAttrFromJson (
                SpanQueryWrapper elementWithIdWrapper,
                JsonNode json,
                JsonNode attrNode) throws QueryException {

		if (attrNode.get("@type").asText().equals("koral:term")) {
			SpanQueryWrapper attrWrapper = _attrFromJson(json.get("attr"));
			if (attrWrapper != null) {
				if (elementWithIdWrapper != null){
					return new SpanWithAttributeQueryWrapper(elementWithIdWrapper,
                                                             attrWrapper);
				}
				else {
					return new SpanWithAttributeQueryWrapper(attrWrapper);
				}
			} 
			else {
				throw new QueryException(747, "Attribute is null");
			}
		} 
        else if (attrNode.get("@type").asText().equals("koral:termGroup")) {
			return _handleAttrGroup(elementWithIdWrapper, attrNode);
		}
		else {
			this.addWarning(715, "Attribute type is not supported");
		}
		return elementWithIdWrapper;
	}


    // Deserialize attribute groups
	private SpanQueryWrapper _handleAttrGroup (
                SpanQueryWrapper elementWithIdWrapper,
                JsonNode attrNode) throws QueryException {
		if (!attrNode.has("relation")) {
			throw new QueryException(743, "Term group expects a relation");
		}
		if (!attrNode.has("operands")) {
			throw new QueryException(742, "Term group needs operand list");
		}

		String relation = attrNode.get("relation").asText();
		JsonNode operands = attrNode.get("operands");

		SpanQueryWrapper attrWrapper;
		if ("relation:and".equals(relation)) {
			List<SpanQueryWrapper> wrapperList = new ArrayList<SpanQueryWrapper>();
			for (JsonNode operand : operands) {
				attrWrapper = _termFromJson(operand);
				if (attrWrapper == null) {
					throw new QueryException(747, "Attribute is null");
				}
				wrapperList.add(attrWrapper);
			}

			if (elementWithIdWrapper != null){
				return new SpanWithAttributeQueryWrapper(elementWithIdWrapper,
                                                         wrapperList);
			}
			else {
				return new SpanWithAttributeQueryWrapper(wrapperList);
			}
		} 
		else if ("relation:or".equals(relation)) {
			SpanAlterQueryWrapper saq = new SpanAlterQueryWrapper(field);
			SpanWithAttributeQueryWrapper saqw;
			for (JsonNode operand : operands) {
				attrWrapper = _termFromJson(operand);
				if (attrWrapper == null) {
					throw new QueryException(747, "Attribute is null");
				}
				if (elementWithIdWrapper != null) {
					saqw = new SpanWithAttributeQueryWrapper(
                        elementWithIdWrapper,
                        attrWrapper
                    );
				} else {
					saqw = new SpanWithAttributeQueryWrapper(attrWrapper);
				}
				saq.or(saqw);
			}
			return saq;
		}
		else {
			throw new QueryException(716, "Unknown relation");
		}
	}

    // Get attributes from a json termgroup
    private SpanQueryWrapper _attrFromJson (JsonNode attrNode)
        throws QueryException {

		if (attrNode.has("key")) {
			return _termFromJson(attrNode);
		} 
		else if (attrNode.has("tokenarity") || attrNode.has("arity")) {
			this.addWarning(770, "Arity attributes are currently not supported"
					+ " - results may not be correct"
            );
		} 
		else if (attrNode.has("root")) {
			String rootValue = attrNode.get("root").asText();
			if (rootValue.equals("true") || rootValue.equals("false")) {
				return new SpanAttributeQueryWrapper(
						new SpanSimpleQueryWrapper("tokens", "@root",
								Boolean.valueOf(rootValue))
                );
            }
        }
		return null;
    };
};