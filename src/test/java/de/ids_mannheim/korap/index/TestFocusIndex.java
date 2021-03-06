package de.ids_mannheim.korap.index;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.junit.Test;

import de.ids_mannheim.korap.KrillIndex;
import de.ids_mannheim.korap.constants.RelationDirection;
import de.ids_mannheim.korap.query.QueryBuilder;
import de.ids_mannheim.korap.query.SpanClassQuery;
import de.ids_mannheim.korap.query.SpanElementQuery;
import de.ids_mannheim.korap.query.SpanFocusQuery;
import de.ids_mannheim.korap.query.SpanNextQuery;
import de.ids_mannheim.korap.query.SpanRelationQuery;
import de.ids_mannheim.korap.query.SpanWithinQuery;
import de.ids_mannheim.korap.query.wrap.SpanQueryWrapper;
import de.ids_mannheim.korap.response.Result;
import de.ids_mannheim.korap.util.QueryException;

public class TestFocusIndex {
    private KrillIndex ki;
    private Result kr;

    public TestFocusIndex () throws IOException {
        ki = new KrillIndex();
    }

    /**
     * Check Skipto focus spans
     */
    @Test
    public void testSkipTo () throws IOException {
        ki.addDoc(TestRelationIndex.createFieldDoc0());
        ki.addDoc(TestRelationIndex.createFieldDoc1());
        ki.commit();
        SpanRelationQuery sq = new SpanRelationQuery(
                new SpanTermQuery(new Term("base", ">:xip/syntax-dep_rel")),
                true, RelationDirection.RIGHT);
        sq.setSourceClass((byte) 1);

        SpanFocusQuery sfq = new SpanFocusQuery(sq, (byte) 1);
        sfq.setSorted(false);
        SpanTermQuery stq = new SpanTermQuery(new Term("base", "s:c"));
        SpanNextQuery snq = new SpanNextQuery(stq, sfq);

        kr = ki.search(snq, (short) 20);

        assertEquals(0, kr.getMatch(0).getStartPos());
        assertEquals(2, kr.getMatch(0).getEndPos());
        assertEquals(5, kr.getMatch(1).getStartPos());
        assertEquals(9, kr.getMatch(1).getEndPos());
        // for (Match m : kr.getMatches()) {
        // System.out.println(m.getStartPos() + " " + m.getEndPos());
        // }
    }

    @Test
    public void testFocusSorting () throws IOException {
        ki = new KrillIndex();
        ki.addDoc(createFieldDoc());
        ki.commit();

        SpanElementQuery elemX = new SpanElementQuery("tokens", "x");

        assertEquals("<tokens:x />", elemX.toString());

        kr = ki.search(elemX, (short) 10);
        assertEquals("[[abc]]d", kr.getMatch(0).getSnippetBrackets());
        assertEquals("a[[bcd]]", kr.getMatch(1).getSnippetBrackets());
        assertEquals(2, kr.getTotalResults());

        SpanQuery termB = new SpanTermQuery(new Term("tokens", "s:b"));
        SpanQuery termC = new SpanTermQuery(new Term("tokens", "s:c"));

        SpanQuery classB = new SpanClassQuery(termB, (byte) 1);
        SpanQuery classC = new SpanClassQuery(termC, (byte) 1);

        SpanQuery within = new SpanWithinQuery(elemX, classB);

        kr = ki.search(within, (short) 10);
        assertEquals("[[a{1:b}c]]d", kr.getMatch(0).getSnippetBrackets());
        assertEquals("a[[{1:b}cd]]", kr.getMatch(1).getSnippetBrackets());
        assertEquals(2, kr.getTotalResults());

        SpanQuery or = new SpanOrQuery(classB, classC);
        within = new SpanWithinQuery(elemX, or);

        kr = ki.search(within, (short) 10);
        assertEquals("[[a{1:b}c]]d", kr.getMatch(0).getSnippetBrackets());
        assertEquals("[[ab{1:c}]]d", kr.getMatch(1).getSnippetBrackets());
        assertEquals("a[[{1:b}cd]]", kr.getMatch(2).getSnippetBrackets());
        assertEquals("a[[b{1:c}d]]", kr.getMatch(3).getSnippetBrackets());
        assertEquals(4, kr.getTotalResults());

        SpanFocusQuery focus = new SpanFocusQuery(within, (byte) 1);
        focus.setSorted(false);
        kr = ki.search(focus, (short) 10);
        assertEquals("focus(1: spanContain(<tokens:x />, spanOr([{1: tokens:s:b}, {1: tokens:s:c}])),sorting)", focus.toString());
        assertEquals("a[[{1:b}]]cd", kr.getMatch(0).getSnippetBrackets());
        assertEquals("a[[{1:b}]]cd", kr.getMatch(1).getSnippetBrackets());
        assertEquals("ab[[{1:c}]]d", kr.getMatch(2).getSnippetBrackets());
        assertEquals("ab[[{1:c}]]d", kr.getMatch(3).getSnippetBrackets());
        assertEquals(4, kr.getTotalResults());
        
        testFocusSortingOverWindowSize(elemX, classB, classC);
        
    }
    
    private void testFocusSortingOverWindowSize (SpanQuery elemX,
            SpanQuery classB, SpanQuery classC) throws IOException {
        ki.addDoc(createFieldDoc1());
        ki.commit();
        
        SpanQuery termD = new SpanTermQuery(new Term("tokens", "s:d"));
        SpanQuery classD = new SpanClassQuery(termD, (byte) 1);
        
        SpanQuery or = new SpanOrQuery(classB, classC, classD);
        SpanWithinQuery within = new SpanWithinQuery(elemX, or);
        kr = ki.search(within, (short) 10);
        
        assertEquals("[[abc{1:d}]]", kr.getMatch(7).getSnippetBrackets());
        assertEquals(10, kr.getTotalResults());
        
        SpanFocusQuery focus = new SpanFocusQuery(within, (byte) 1);
        focus.setSorted(false);
        focus.setWindowSize(2);
        kr = ki.search(focus, (short) 10);
        
//        for (Match m: kr.getMatches()){
//            System.out.println(m.getDocID() + " "+m.getSnippetBrackets());
//        }
        assertEquals("a[[{1:b}]]cd", kr.getMatch(0).getSnippetBrackets());
        assertEquals("a[[{1:b}]]cd", kr.getMatch(1).getSnippetBrackets());
        assertEquals("ab[[{1:c}]]d", kr.getMatch(2).getSnippetBrackets());
        assertEquals("ab[[{1:c}]]d", kr.getMatch(3).getSnippetBrackets());
        assertEquals(10, kr.getTotalResults());

    }

    @Test
    public void testFocusSortingWrapping () throws QueryException, IOException {
        ki = new KrillIndex();
        ki.addDoc(createFieldDoc());
        ki.commit();

        QueryBuilder kq = new QueryBuilder("tokens");

        SpanQueryWrapper focus = kq.focus(kq.contains(kq.tag("x"), kq.or(kq.nr(1, kq.seg("s:b")), kq.nr(1, kq.seg("s:c")))));
        assertEquals("focus(1: spanContain(<tokens:x />, spanOr([{1: tokens:s:b}, {1: tokens:s:c}])),sorting)", focus.toQuery().toString());
        
        kr = ki.search(focus.toQuery(), (short) 10);
        assertEquals("a[[{1:b}]]cd", kr.getMatch(0).getSnippetBrackets());
        assertEquals("a[[{1:b}]]cd", kr.getMatch(1).getSnippetBrackets());
        assertEquals("ab[[{1:c}]]d", kr.getMatch(2).getSnippetBrackets());
        assertEquals("ab[[{1:c}]]d", kr.getMatch(3).getSnippetBrackets());
        assertEquals(4, kr.getTotalResults());


        focus = kq.focus(kq.startswith(kq.tag("x"), kq.or(kq.nr(1, kq.seg("s:b")), kq.nr(1, kq.seg("s:c")))));
        assertEquals("focus(1: spanStartsWith(<tokens:x />, spanOr([{1: tokens:s:b}, {1: tokens:s:c}])))",
                     focus.toQuery().toString());

        kr = ki.search(focus.toQuery(), (short) 10);
        assertEquals("a[[{1:b}]]cd", kr.getMatch(0).getSnippetBrackets());
        assertEquals(1, kr.getTotalResults());
    }

    
    public static FieldDocument createFieldDoc () {
        FieldDocument fd = new FieldDocument();
        fd.addString("ID", "doc-0");
        fd.addTV("tokens", "abcd",

                "[(0-1)s:a|_0$<i>0<i>1|"
                + "<>:x$<b>64<i>0<i>3<i>3<b>0]"
                + "[(1-2)s:b|_1$<i>1<i>2|"
                + "<>:x$<b>64<i>1<i>4<i>4<b>0]"
                + "[(2-3)s:c|_2$<i>2<i>3]"
                + "[(3-4)s:d|_3$<i>3<i>4]"
                 );
        
        return fd;
    }
    
    public static FieldDocument createFieldDoc1 () {
        FieldDocument fd = new FieldDocument();
        fd.addString("ID", "doc-1");
        fd.addTV("tokens", "abcd",

                "[(0-1)s:a|_0$<i>0<i>1|"
                + "<>:x$<b>64<i>0<i>4<i>4<b>0]"
                + "[(1-2)s:b|_1$<i>1<i>2]"
                + "[(2-3)s:c|_2$<i>2<i>3|"
                + "<>:x$<b>64<i>2<i>4<i>4<b>0]"
                + "[(3-4)s:d|_3$<i>3<i>4]"
                 );
        
        return fd;
    }
}
