package de.ids_mannheim.korap.search;

import java.util.*;
import java.io.*;

import static de.ids_mannheim.korap.TestSimple.*;

import de.ids_mannheim.korap.Krill;
import de.ids_mannheim.korap.KrillCollection;
import de.ids_mannheim.korap.KrillQuery;
import de.ids_mannheim.korap.KrillIndex;
import de.ids_mannheim.korap.index.FieldDocument;
import de.ids_mannheim.korap.response.Result;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestMetaFields {
    
    @Test
    public void searchMetaFields () throws IOException {

        // Construct index
        KrillIndex ki = new KrillIndex();
        // Indexing test files
        for (String i : new String[] { "00001", "00002" }) {
            ki.addDoc(
                    getClass().getResourceAsStream("/wiki/" + i + ".json.gz"),
                    true);
        };
        ki.commit();

        String jsonString = getString(getClass().getResource(
                "/queries/metas/fields.jsonld").getFile());

        Krill ks = new Krill(jsonString);

        Result kr = ks.apply(ki);
        assertEquals((long) 17, kr.getTotalResults());
        assertEquals(0, kr.getStartIndex());
        assertEquals(9, kr.getItemsPerPage());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode res = mapper.readTree(kr.toJsonString());
        assertEquals(0, res.at("/matches/0/UID").asInt());
        assertEquals("WPD", res.at("/matches/0/corpusID").asText());
        assertTrue(res.at("/matches/0/docID").isMissingNode());
        assertTrue(res.at("/matches/0/textSigle").isMissingNode());
        assertTrue(res.at("/matches/0/ID").isMissingNode());
        assertTrue(res.at("/matches/0/author").isMissingNode());
        assertTrue(res.at("/matches/0/title").isMissingNode());
        assertTrue(res.at("/matches/0/subTitle").isMissingNode());
        assertTrue(res.at("/matches/0/textClass").isMissingNode());
        assertTrue(res.at("/matches/0/pubPlace").isMissingNode());
        assertTrue(res.at("/matches/0/pubDate").isMissingNode());
        assertTrue(res.at("/matches/0/foundries").isMissingNode());
        assertTrue(res.at("/matches/0/layerInfos").isMissingNode());
        assertTrue(res.at("/matches/0/tokenization").isMissingNode());

        jsonString = getString(getClass().getResource(
                "/queries/metas/fields_2.jsonld").getFile());
        ks = new Krill(jsonString);
        kr = ks.apply(ki);
        assertEquals((long) 17, kr.getTotalResults());
        assertEquals(0, kr.getStartIndex());
        assertEquals(2, kr.getItemsPerPage());

        mapper = new ObjectMapper();
        res = mapper.readTree(kr.toJsonString());
        assertEquals(0, res.at("/matches/0/UID").asInt());
        assertTrue(res.at("/matches/0/corpusID").isMissingNode());
        assertEquals("Ruru,Jens.Ol,Aglarech", res.at("/matches/0/author")
                .asText());
        assertEquals("A", res.at("/matches/0/title").asText());
        assertEquals("WPD_AAA.00001", res.at("/matches/0/docID").asText());
        assertEquals("", res.at("/matches/0/textSigle").asText());
        assertEquals("match-WPD_AAA.00001-p6-7", res.at("/matches/0/ID")
                .asText());
        assertEquals("", res.at("/matches/0/subTitle").asText());
        assertEquals("", res.at("/matches/0/textClass").asText());
        assertEquals("", res.at("/matches/0/pubPlace").asText());
        assertEquals("", res.at("/matches/0/pubDate").asText());
        assertEquals("", res.at("/matches/0/foundries").asText());
        assertEquals("", res.at("/matches/0/layerInfo").asText());
        assertEquals("", res.at("/matches/0/tokenization").asText());
    };
};
