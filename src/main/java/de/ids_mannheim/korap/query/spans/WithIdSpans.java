package de.ids_mannheim.korap.query.spans;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.util.Bits;

import de.ids_mannheim.korap.query.SpanElementQuery;
import de.ids_mannheim.korap.query.SpanRelationQuery;

public abstract class WithIdSpans extends SimpleSpans{

	protected short spanId;
	protected boolean hasSpanId = false; // A dummy flag
	
	public WithIdSpans(SpanElementQuery spanElementQuery,
			AtomicReaderContext context, Bits acceptDocs,
			Map<Term, TermContext> termContexts) throws IOException {
		super(spanElementQuery, context, acceptDocs, termContexts);
	}
	
	public WithIdSpans(SpanRelationQuery spanRelationQuery,
			AtomicReaderContext context, Bits acceptDocs,
			Map<Term, TermContext> termContexts) throws IOException {
		super(spanRelationQuery, context, acceptDocs, termContexts);
	}

	public short getSpanId() {
		return spanId;
	}

	public void setSpanId(short spanId) {
		this.spanId = spanId;
	}
		
	
}
