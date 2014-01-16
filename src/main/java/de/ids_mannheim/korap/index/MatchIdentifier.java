package de.ids_mannheim.korap.index;
import java.util.*;
import java.util.regex.*;


public class MatchIdentifier {
    private String corpusID, docID;
    private int startPos, endPos = 0;

    private ArrayList<int[]> pos = new ArrayList<>(8);

    Pattern idRegex = Pattern.compile(
		        "^match-(?:([^!]+?)!)?" +
			"([^!]+)-p([0-9]+)-([0-9]+)" +
			"((?:\\(-?[0-9]+\\)-?[0-9]+--?[0-9]+)*)" +
			"(?:c.+?)?$");
    Pattern posRegex = Pattern.compile(
		        "\\(([0-9]+)\\)([0-9]+)-([0-9]+)");

    public MatchIdentifier () {};

    public MatchIdentifier (String id) {
	Matcher matcher = idRegex.matcher(id);
	if (matcher.matches()) {
	    this.setCorpusID(matcher.group(1));
	    this.setDocID(matcher.group(2));
	    this.setStartPos(Integer.parseInt(matcher.group(3)));
	    this.setEndPos(Integer.parseInt(matcher.group(4)));

	    if (matcher.group(5) != null) {
		matcher = posRegex.matcher(matcher.group(5));
		while (matcher.find()) {
		    this.addPos(
		        Integer.parseInt(matcher.group(2)),
		        Integer.parseInt(matcher.group(3)),
			Integer.parseInt(matcher.group(1))
		    );
		};
	    };
	};
    };

    public String getCorpusID () {
	return this.corpusID;
    };

    public void setCorpusID (String id) {
	if (id != null && !id.contains("!"))
	    this.corpusID = id;
    };

    public String getDocID () {
	return this.docID;
    };

    public void setDocID (String id) {
	if (!id.contains("!"))
	    this.docID = id;
    };

    public int getStartPos () {
	return this.startPos;
    };

    public void setStartPos (int pos) {
	if (pos >= 0)
	    this.startPos = pos;
    };

    public int getEndPos () {
	return this.endPos;
    };

    public void setEndPos (int pos) {
	if (pos >= 0)
	    this.endPos = pos;
    };

    public void addPos(int start, int end, int number) {
	if (start >= 0 && end >= 0 && number >= 0)
	    this.pos.add(new int[]{start, end, number});
    };

    public ArrayList<int[]> getPos () {
	return this.pos;
    };

    public String toString () {

	if (this.docID == null) return null;

	StringBuffer sb = new StringBuffer("match-");

	// Get prefix string corpus/doc
	if (this.corpusID != null) {
	    sb.append(this.corpusID).append('!');
	};
	sb.append(this.docID);

	sb.append("-p");
	sb.append(this.startPos).append('-').append(this.endPos);

	// Get Position information
	for (int[] i : this.pos) {
	    sb.append('(').append(i[2]).append(')');
	    sb.append(i[0]).append('-').append(i[1]);
	};

	/*
	if (this.processed) {
	    sb.append('c');
	    for (int[] s : this.span) {
		if (s[2] >= 256)
		    continue;
		
		if (s[2] != -1)
		    sb.append('(').append(s[2]).append(')');
		sb.append(s[0] + this.startOffsetChar);
		sb.append('-');
		sb.append(s[1] + this.startOffsetChar);
	    };
	};
	*/
	return sb.toString();
    };
};