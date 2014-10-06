package de.ids_mannheim.korap.query.wrap;

import java.util.*;
import de.ids_mannheim.korap.query.DistanceConstraint;
import de.ids_mannheim.korap.query.SpanElementQuery;
import de.ids_mannheim.korap.query.SpanNextQuery;
import de.ids_mannheim.korap.query.SpanDistanceQuery;
import de.ids_mannheim.korap.query.SpanExpansionQuery;
import de.ids_mannheim.korap.query.SpanMultipleDistanceQuery;

import de.ids_mannheim.korap.query.wrap.SpanQueryWrapper;
import de.ids_mannheim.korap.query.wrap.SpanSegmentQueryWrapper;
import de.ids_mannheim.korap.query.wrap.SpanAlterQueryWrapper;
import de.ids_mannheim.korap.query.wrap.SpanRegexQueryWrapper;
import de.ids_mannheim.korap.query.wrap.SpanWildcardQueryWrapper;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
TODO: Make isNegative work!
*/


/**
 * Deserialize complexe sequence queries to Lucene SpanQueries.
 *
 * @author Nils Diewald
 */
public class SpanSequenceQueryWrapper extends SpanQueryWrapper {
    private String field;
    private ArrayList<SpanQuery> segments;
    private ArrayList<DistanceConstraint> constraints;

    // Logger
    private final static Logger log = LoggerFactory.getLogger(SpanSequenceQueryWrapper.class);

    // This advices the java compiler to ignore all loggings
    public static final boolean DEBUG = true;
    
    private boolean
	isInOrder = true,
	isOptional = true,
	lastIsOptional = false,
	firstIsOptional = false;

    public SpanSequenceQueryWrapper (String field) {
	this.field = field;
	this.segments = new ArrayList<SpanQuery>(2);
    };

    public SpanSequenceQueryWrapper (String field, String ... terms) {
	this(field);
	for (int i = 0; i < terms.length; i++) {
	    this.segments.add((SpanQuery) new SpanTermQuery(new Term(field, terms[i])));
	    this.isOptional = false;
	};
	this.isNull = false;
    };

    public SpanSequenceQueryWrapper (String field, SpanQuery sq) {
	this(field);
	if (DEBUG)
	    log.trace("New spanquery sequence " + sq.toString());
	this.segments.add((SpanQuery) sq);
	this.isOptional = false;
	this.isNull = false;
    };

    public SpanSequenceQueryWrapper (String field, SpanQueryWrapper sswq) {
	this(field);

	if (!sswq.isNull()) {

	    if (sswq.isNegative())
		this.isNegative = true;
	    
	    this.segments.add((SpanQuery) sswq.toQuery());
	    this.isNull = false;
	    if (sswq.isOptional()) {
		if (DEBUG)
		    log.trace("New optional query sequence " +
			      sswq.toQuery().toString());
		this.isOptional = true;
		this.lastIsOptional = true;
		this.firstIsOptional = true;
	    }
	    else {
		this.isOptional = false;
		if (DEBUG)
		    log.trace("New non-optional query sequence " +
			      sswq.toQuery().toString());
	    };
	};
    };

    public SpanSequenceQueryWrapper (String field, SpanRegexQueryWrapper re) {
	this(field);
	if (!re.isNull()) {
	    this.segments.add((SpanQuery) re.toQuery());
	    this.isNull = false;
	    this.isOptional = false;
	};
    };

    public SpanSequenceQueryWrapper (String field, SpanWildcardQueryWrapper wc) {
	this(field);
	if (!wc.isNull()) {
	    this.segments.add((SpanQuery) wc.toQuery());
	    this.isOptional = false;
	    this.isNull = false;
	};
    };

    public SpanQuery get (int index) {
	return this.segments.get(index);
    };

    public void set (int index, SpanQuery sq) {
	this.segments.set(index, sq);
    };

    public SpanSequenceQueryWrapper append (String term) {
	return this.append(
	    (SpanQuery) new SpanTermQuery(new Term(field, term))
        );
    };
    
    public SpanSequenceQueryWrapper append (SpanQuery query) {
	this.isNull     = false;
	this.isOptional = false;

	// Check if there has to be alternation magic in action
	if (this.lastIsOptional) {
	    if (DEBUG)
		log.trace("Append non-opt query to opt query " +
			  query.toString());

	    SpanAlterQueryWrapper saqw = new SpanAlterQueryWrapper(field, query);
	    SpanSequenceQueryWrapper ssqw = new SpanSequenceQueryWrapper(field, query);

	    // Remove last element of the list and prepend it
	    ssqw.prepend(this.segments.remove(this.segments.size() - 1));
	    saqw.or(ssqw);

	    // Update boundary optionality
	    if (this.firstIsOptional && this.segments.size() == 0)
		this.firstIsOptional = false;
	    
	    this.lastIsOptional = false;

	    this.segments.add((SpanQuery) saqw.toQuery());
	}
	else {
	    if (DEBUG)
		log.trace("Append non-opt query to non-opt query " +
			  query.toString());

	    this.segments.add(query);
	};
	
	return this;
    };

    public SpanSequenceQueryWrapper append (SpanQueryWrapper ssq) {

	if (DEBUG)
	    log.trace("Try to append query {}", ssq.toString());

	if (!ssq.isNull()) {

	    if (DEBUG)
		log.trace("THe query {} is not null", ssq.toString());

	    if (ssq.isNegative())
		this.isNegative = true;
	    
	    SpanQuery appendQuery = ssq.toQuery();
	    if (!ssq.isOptional()) {
		if (DEBUG)
		    log.trace("Append non-opt query to non-opt query {}",
			      appendQuery.toString());
		return this.append(appendQuery);
	    };
	    
	    // Situation is ab? or a?b?
	    if (this.segments.size() != 0) {

		// Remove last element of the list and prepend it
		SpanQuery lastQuery = this.segments.remove(this.segments.size() - 1);
		SpanAlterQueryWrapper saqw = new SpanAlterQueryWrapper(field, lastQuery);
		SpanSequenceQueryWrapper ssqw = new SpanSequenceQueryWrapper(field, appendQuery);
		ssqw.prepend(lastQuery);

		// Situation is a?b?
		if (this.lastIsOptional) {
		    saqw.or(appendQuery);
		    // last stays optional
		    if (DEBUG)
			log.trace("Append opt query to opt query {}",
				  appendQuery.toString());

		}
		else if (DEBUG) {
		    log.trace("Append opt query to non-opt query {}",
			      appendQuery.toString());
		};

		saqw.or(ssqw);
		this.segments.add((SpanQuery) saqw.toQuery());
	    }
	    
	    // Situation is b?
	    else {
		this.segments.add(appendQuery);

		if (DEBUG)
		    log.trace("Append opt query {}",
			      appendQuery.toString());

		// Update boundary optionality
		this.firstIsOptional = true;
		this.isOptional      = true;
		this.lastIsOptional  = true;
	    };
	    this.isNull = false;
	};
	return this;
    };

    public SpanSequenceQueryWrapper append (SpanRegexQueryWrapper srqw) {
	if (!srqw.isNull()) {
	    return this.append((SpanQuery) srqw.toQuery());
	};
	return this;
    };
    
    public SpanSequenceQueryWrapper append (SpanWildcardQueryWrapper swqw) {
	if (!swqw.isNull()) {
	    return this.append((SpanQuery) swqw.toQuery());
	};
	return this;
    };

    public SpanSequenceQueryWrapper prepend (String term) {
	return this.prepend(new SpanTermQuery(new Term(field, term)));
    };

    public SpanSequenceQueryWrapper prepend (SpanQuery query) {
	this.isNull     = false;
	this.isOptional = false;
	
	// Check if there has to be alternation magic in action
	if (this.firstIsOptional) {
	    SpanAlterQueryWrapper saqw = new SpanAlterQueryWrapper(field, query);
	    SpanSequenceQueryWrapper ssqw = new SpanSequenceQueryWrapper(field, query);
	    // Remove last element of the list and prepend it
	    ssqw.append(this.segments.remove(0));
	    saqw.or(ssqw);

	    // Update boundary optionality
	    if (this.lastIsOptional && this.segments.size() == 0)
		this.lastIsOptional = false;
	    this.firstIsOptional = false;

	    this.segments.add(0, (SpanQuery) saqw.toQuery());
	}
	else {
	    this.segments.add(0,query);
	};
	return this;
    };

    public SpanSequenceQueryWrapper prepend (SpanQueryWrapper ssq) {
	if (!ssq.isNull()) {

	    if (ssq.isNegative())
		this.isNegative = true;
	    
	    SpanQuery prependQuery = ssq.toQuery();
	    if (!ssq.isOptional()) {
		return this.prepend(prependQuery);
	    };
	    
	    // Situation is b?a or b?a?
	    if (this.segments.size() != 0) {
		// Remove first element of the list and append it
		SpanQuery firstQuery = this.segments.remove(0);
		SpanAlterQueryWrapper saqw = new SpanAlterQueryWrapper(field, firstQuery);
		SpanSequenceQueryWrapper ssqw = new SpanSequenceQueryWrapper(field, prependQuery);
		ssqw.append(firstQuery);

		// Situation is b?a?
		if (this.firstIsOptional)
		    saqw.or(prependQuery);
		// first stays optional

		saqw.or(ssqw);
		this.segments.add(0, (SpanQuery) saqw.toQuery());
	    }
	    
	    // Situation is b?
	    else {
		this.segments.add(prependQuery);

		// Update boundary optionality
		this.firstIsOptional = true;
		this.isOptional      = true;
		this.lastIsOptional  = true;
	    };
	    this.isNull = false;
	};
	return this;
    };

    public SpanSequenceQueryWrapper prepend (SpanRegexQueryWrapper re) {
	if (!re.isNull()) {
	    return this.prepend(re.toQuery());
	};
	return this;
    };

    public SpanSequenceQueryWrapper prepend (SpanWildcardQueryWrapper swqw) {
	if (!swqw.isNull()) {
	    return this.prepend(swqw.toQuery());
	};
	return this;
    };

    public SpanSequenceQueryWrapper withConstraint (int min, int max) {
	return this.withConstraint(min, max, false);
    };

    public SpanSequenceQueryWrapper withConstraint (int min, int max, boolean exclusion) {
	if (this.constraints == null)
	    this.constraints = new ArrayList<DistanceConstraint>(1);
	this.constraints.add(new DistanceConstraint(min, max, isInOrder, exclusion));
	return this;
    };

    public SpanSequenceQueryWrapper withConstraint (int min, int max, String unit) {
	return this.withConstraint(min, max, unit, false);
    };
    
    public SpanSequenceQueryWrapper withConstraint (int min,
						    int max,
						    String unit,
						    boolean exclusion) {
	if (this.constraints == null)
	    this.constraints = new ArrayList<DistanceConstraint>(1);
	if (unit.equals("w"))
	    this.constraints.add(new DistanceConstraint(min, max, isInOrder, exclusion));
	else
	    this.constraints.add(
		 new DistanceConstraint(
                     new SpanElementQuery(this.field, unit), min, max, isInOrder, exclusion)
	    );
	return this;
    };

    
    public SpanQuery toQuery () {
	if (this.segments.size() == 0 || this.isNull) {
	    return (SpanQuery) null;
	};

	SpanQuery query = this.segments.get(0);

	// NextQueries:
	if (this.constraints == null || this.constraints.size() == 0 ||
	    (this.constraints.size() == 1 &&
	     (this.constraints.get(0).getMinDistance() == 1 &&
	      this.constraints.get(0).getMaxDistance() == 1))) {
	    for (int i = 1; i < this.segments.size(); i++) {
		query = new SpanNextQuery(
	            query,
		    this.segments.get(i) // Todo: Maybe payloads are not necessary
		);
	    };
	    return (SpanQuery) query;
	};

	// DistanceQueries
	if (this.constraints.size() == 1) {
	    DistanceConstraint constraint = this.constraints.get(0);

	    // Create spanElementDistance query
	    if (!constraint.getUnit().equals("w")) {
		for (int i = 1; i < this.segments.size(); i++) {
		    SpanDistanceQuery sdquery = new SpanDistanceQuery(
			    query,
				this.segments.get(i),
				constraint,
				true
		    );
		    query = (SpanQuery) sdquery;
		};
	    }

	    // Create spanDistance query
	    else {
		for (int i = 1; i < this.segments.size(); i++) {
		    SpanDistanceQuery sdquery = new SpanDistanceQuery(
	                query,
			this.segments.get(i),
			constraint,
			true
		    );
		    query = (SpanQuery) sdquery;
		};
	    };

	    return (SpanQuery) query;
	};

	// MultipleDistanceQueries
	for (int i = 1; i < this.segments.size(); i++) {
	    query = new SpanMultipleDistanceQuery(
	        query,
		this.segments.get(i),
		this.constraints,
		isInOrder,
		true
	    );
	};

	return (SpanQuery) query;
    };

    public void setInOrder (boolean isInOrder) {
	this.isInOrder = isInOrder;
    };

    public boolean isInOrder () {
	return this.isInOrder;
    };

    public boolean hasConstraints () {
	if (this.constraints == null)
	    return false;
	if (this.constraints.size() <= 0)
	    return false;
	return true;
    };
};
