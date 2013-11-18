package de.ids_mannheim.korap.query.spans;

import java.lang.StringBuilder;

public abstract class KorapSpan implements Comparable<KorapSpan>, Cloneable {
    public int start, end, doc;

    public KorapSpan () {
	this.start = -1;
	this.end = -1;
	initPayload();
    };

    public void clear () {
	this.start = -1;
	this.end = -1;
	this.doc = -1;
	clearPayload();
    };

    public KorapSpan copyFrom (KorapSpan o) {
	this.start = o.start;
	this.end = o.end;
	this.doc = o.doc;
	clearPayload();
	return this;
    };

    public abstract void clearPayload ();
    public abstract void initPayload ();

    @Override
    public int compareTo (KorapSpan o) {
	/* optimizable for short numbers to return o.end - this.end */
	if (this.doc < o.doc) {
	    return -1;
	}
	else if (this.doc == o.doc) {
	    if (this.start < o.start) {
		return -1;
	    }
	    else if (this.start == o.start) {
		if (this.end < o.end)
		    return -1;
	    };
	};
	return 1;
    };

    public String toString () {
	StringBuilder sb = new StringBuilder("[");
	return sb.append(this.start).append('-')
	    .append(this.end)
	    .append('(').append(this.doc).append(')')
	    .append(']')
	    .toString();
    };

    /*
equals und hashcode implementieren
     */
};
