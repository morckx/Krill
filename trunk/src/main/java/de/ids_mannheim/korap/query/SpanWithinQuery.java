package de.ids_mannheim.korap.query;

// Based on SpanNearQuery
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.ToStringUtils;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.SpanTermQuery;

import de.ids_mannheim.korap.query.spans.WithinSpans;
import de.ids_mannheim.korap.query.SpanElementQuery;

/**
 * Matches spans that are within certain elements.
 */
public class SpanWithinQuery extends SpanQuery implements Cloneable {
    private SpanQuery wrap;
    private SpanQuery embedded;
    public String field;
    private short flag;
    private boolean collectPayloads;

    public static final short
	WITHIN = 0,
	STARTSWITH = 1,
	ENDSWITH = 2,
	MATCH = 3;

    // may support "starting" and "ending"

    // Constructor
    public SpanWithinQuery (SpanQuery wrap,
			    SpanQuery embedded,
			    short flag,
			    boolean collectPayloads) {

	this.field = embedded.getField();
	this.embedded = embedded;
	this.wrap = wrap;
	this.flag = flag;
	this.collectPayloads = collectPayloads;
    };

    // Constructor
    public SpanWithinQuery(String element,
			   SpanQuery embedded) {
	this(element, embedded, (short) 0, true);
    };

    // Constructor
    public SpanWithinQuery (String element,
			    SpanQuery embedded,
			    short flag,
			    boolean collectPayloads) {
	this(
	     (SpanQuery) new SpanElementQuery(embedded.getField(), element),
	     embedded,
	     flag,
	     collectPayloads
	);
    };


    // Constructor
    public SpanWithinQuery(String element,
			   SpanQuery embedded,
			   short flag) {
	this(element, embedded, flag, true);
    };

    // Constructor
    public SpanWithinQuery (String element,
			    SpanQuery embedded,
			    boolean collectPayloads) {
	this(element, embedded, (short) 0, collectPayloads);
    };


    // Constructor
    public SpanWithinQuery(SpanQuery wrap,
			   SpanQuery embedded,
			   short flag) {
	this(wrap, embedded, flag, true);
    };

    // Constructor
    public SpanWithinQuery(SpanQuery wrap,
			   SpanQuery embedded) {
	this(wrap, embedded, (short) 0, true);
    };


    @Override
    public String getField()    { return field;    };
    public SpanQuery wrap()     { return wrap;     };
    public SpanQuery embedded() { return embedded; };
    public short flag()         { return flag; };
  
    @Override
    public void extractTerms(Set<Term> terms) {
	embedded.extractTerms(terms);
    };
  
    @Override
    public String toString(String field) {
	StringBuilder buffer = new StringBuilder();
	buffer.append("spanWithin(");
        buffer.append(wrap.toString());
        buffer.append(", ");
	buffer.append(embedded.toString(field));
        buffer.append(")");
	buffer.append(ToStringUtils.boost(getBoost()));
	return buffer.toString();
    };

    @Override
    public Spans getSpans (final AtomicReaderContext context,
			   Bits acceptDocs,
			   Map<Term,TermContext> termContexts) throws IOException {
	return (Spans) new WithinSpans (
            this, context, acceptDocs, termContexts, this.flag
	);
    };

    @Override
    public Query rewrite (IndexReader reader) throws IOException {
	SpanWithinQuery clone = null;

	SpanQuery query = (SpanQuery) embedded.rewrite(reader);

	if (query != embedded) {
	    if (clone == null)
		clone = this.clone();
	    clone.embedded = query;
	};

	if (clone != null)
	    return clone;

	return this;
    };
  

    @Override
    public SpanWithinQuery clone () {
	SpanWithinQuery spanWithinQuery = new SpanWithinQuery(
            (SpanQuery) wrap.clone(),
	    (SpanQuery) embedded.clone(),
	    this.flag,
	    this.collectPayloads
        );
	spanWithinQuery.setBoost(getBoost());
	return spanWithinQuery;
    };


    /** Returns true iff <code>o</code> is equal to this. */
    @Override
    public boolean equals(Object o) {
	if (this == o) return true;
	if (!(o instanceof SpanWithinQuery)) return false;
	
	final SpanWithinQuery spanWithinQuery = (SpanWithinQuery) o;
	
	if (collectPayloads != spanWithinQuery.collectPayloads) return false;
	if (!wrap.equals(spanWithinQuery.wrap)) return false;
	if (!embedded.equals(spanWithinQuery.embedded)) return false;

	return getBoost() == spanWithinQuery.getBoost();
    };

    // I don't know what I am doing here
    @Override
    public int hashCode() {
	int result = 1;
	result = embedded.hashCode();
	result += wrap.hashCode();
	result ^= (result << 4) | (result >>> 29);
	result += Float.floatToRawIntBits(getBoost());
	return result;
    };
};
