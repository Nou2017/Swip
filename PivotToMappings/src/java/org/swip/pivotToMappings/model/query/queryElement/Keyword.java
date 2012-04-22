package org.swip.pivotToMappings.model.query.queryElement;

import com.hp.hpl.jena.query.QuerySolution;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.StringTokenizer;
import org.apache.log4j.Logger;
import org.swip.pivotToMappings.model.patterns.patternElement.KbPatternElement;
import org.swip.pivotToMappings.model.patterns.patternElement.LiteralPatternElement;
import org.swip.pivotToMappings.model.patterns.patternElement.PatternElement;
import org.swip.pivotToMappings.model.patterns.patternElement.PatternElementMatching;
import org.swip.pivotToMappings.sparql.SparqlServer;
import org.swip.pivotToMappings.stemmer.SnowballStemmer;
import org.swip.pivotToMappings.stemmer.englishStemmer;

public class Keyword extends QueryElement {

    int id = 0;
    String keywordValue = null;
    
    private static final Logger logger = Logger.getLogger(QueryElement.class);

    public Keyword() {
    }

    public Keyword(boolean queried, int id, String keywordValue) {
        this.queried = queried;
        this.id = id;
        this.keywordValue = keywordValue;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getKeywordValue() {
        return keywordValue;
    }

    public void setKeywordValue(String keywordValue) {
        this.keywordValue = keywordValue;
    }

    public void match(SparqlServer serv) {
        logger.info(this.keywordValue + " matches with: ");
        long time = System.currentTimeMillis();

        HashMap<String, String> labelsMap = new HashMap<String, String>();
        HashMap<String, Float> scoresMap = new HashMap<String, Float>();

        StringTokenizer st2 = new StringTokenizer(this.keywordValue, " \t\n\r\f-_");
        String stringToMatch = "";
        while (st2.hasMoreTokens()) {
            // remove plural form if any (done manually because Lucene score doesn't handle this)
            // FIXME: is it possible to configure it in Lucene?
            String nextToken = st2.nextToken();
            if (nextToken.endsWith("ies")) {
                nextToken = nextToken.substring(0, nextToken.length()-3) + "y";
            } else if (nextToken.endsWith("s")) {
                nextToken = nextToken.substring(0, nextToken.length()-1);
            }
            stringToMatch += nextToken + " ";
        }

        String query = "  PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "  PREFIX dc:  <http://purl.org/dc/elements/1.1/>"
                + "  PREFIX pf:  <http://jena.hpl.hp.com/ARQ/property#>"
                + "SELECT ?subj ?label ?score "
                + "WHERE { "
                + "       (?label ?score ) pf:textMatch ('" + stringToMatch + "' 0.6) "
                + "       { ?subj dc:title ?label. } "
                + "                  UNION "
                + "       { ?subj rdfs:label ?label. } "
                + "      } ";

        Iterable<QuerySolution> results = serv.select(query);
        for (QuerySolution qs : results) {
            String uri = qs.get("subj").toString();

            boolean changeMaps = false;
            float score = ((com.hp.hpl.jena.rdf.model.Literal) qs.get("score")).getFloat();
            if (scoresMap.containsKey(uri)) {
                if (score > scoresMap.get(uri)) {
                    changeMaps = true;
                }
            } else {
                changeMaps = true;
            }

            if (changeMaps) {
                String label = qs.get("label").toString();
                labelsMap.put(uri, label);
                scoresMap.put(uri, score);
            }
        }

        PriorityQueue<Match> matches = new PriorityQueue<Match>();
        for (String uri : labelsMap.keySet()) {            

            float bestTrustMark = scoresMap.get(uri);
            String bestLabel = labelsMap.get(uri);
            boolean isClass = false;
            boolean isIndividual = false;
            boolean isProperty = false;

            List<String> types = serv.listTypes(uri);
            if (serv.isClass(types)) {
                isClass = true;
                logger.info(" (o) class " + uri);
            } else if (serv.isProperty(types)) {
                isProperty = true;
                logger.info(" (o) property " + uri);
            } else {
                isIndividual = true;
                logger.info(" (o) individual " + uri + "(type: ");
                for (String type : types) {
                    logger.info(type + ", ");
                }
                logger.info(")");
                if (this.queried) {
                    bestTrustMark *= trustMarkDiminutionWhenQueriedInstance;
                    logger.info(" (queried instance)");
                } else {
                    bestTrustMark *= trustMarkDiminutionWhenInstance;
                }
            }
            if (((isClass || isIndividual) && (roles.contains(QeRole.E2Q3)))
                    || ((isProperty) && (roles.contains(QeRole.E1Q1) || roles.contains(QeRole.E1Q23) || roles.contains(QeRole.E3Q3)))) {
                bestTrustMark *= trustMarkDiminutionWhenIncompatible;
                logger.info(" (incompatible type)");
            }
            
            logger.info("  --  matched label: " + bestLabel);
            logger.info("  --  trust mark = " + bestTrustMark);

            if (isIndividual) {
                for (String type : types) {
                    this.addMatch(type, bestTrustMark, uri, bestLabel, true, matches);
                }
            } else {
                this.addMatch(uri, bestTrustMark, uri, bestLabel, false, matches);
                // if query element matches a property and has not a property role in query,
                // we add a mapping from the property's range to that query element, with the same trust mark
//                if (isProperty && !this.roles.usedAsProperty()) {
//                    log.println("       matches a property but has no property role in query -> matches property's range as well", 2);
//                    List<String> ranges = serv.listRanges(uri);
//                    for (String range : ranges) {
//                        log.println("       (o) class " + range + " -- trust mark = " + bestTrustMark, 2);
//                        this.addMatch(range, bestTrustMark, range, serv.getALabel(range), false, matches);
//                    }
//                }
                // TODO: inverse de ce qu'il y a au dessus: reporter un matching de classe vers les propriétés
                // qui ont cette classe en range
            }

        }
        map(matches, serv);

        long time2 = System.currentTimeMillis();
        logger.info("time: " + (double) (time2 - time) / 1000. + "s to match keyword " + this.keywordValue + "\n");
    }
    
    public boolean isClass() {
        return false;
    }
    
    public boolean isIndividual() {
        return false;
    }
    
    public boolean isProperty() {
        return false;
    }


    void addMatch(String resourceUri, float trustMark, String firstlyMatched, String label, boolean checkMappingCondition, PriorityQueue<Match> matches) {
        final int maxNumMatches = 50;
        if (matches.size() < maxNumMatches || trustMark > matches.peek().trustMark || trustMark >= 1.0) {
            matches.add(new Match(resourceUri, trustMark, firstlyMatched, label, checkMappingCondition));
            if (matches.size() > maxNumMatches && matches.peek().trustMark < 1.0) {
                matches.poll();
            }
        }
    }

    void map(PriorityQueue<Match> matches, SparqlServer serv) {
        for (Match match : matches) {
            Collection<PatternElementMatching> concernedPEMs = PatternElement.patternElementMatchings.get(match.resourceUri);
            if (concernedPEMs != null) {
                for (PatternElementMatching pem : concernedPEMs) {
                    boolean doMapping = true;
                    if (match.checkMappingCondition && pem.getPatternElement().getMappingCondition() != null) {
                        String askQuery = pem.getPatternElement().getMappingCondition().replace("__" + pem.getPatternElement().getId() + "__", "<" + match.firstlyMatched + ">");
                        doMapping = serv.ask("ASK {" + askQuery + "}");
                    }
                    if (doMapping) {
                        float mapTrustMark = match.trustMark * pem.getTrustMarkFactor();
                        PatternElement pe = pem.getPatternElement();
                        if (pe instanceof KbPatternElement) {
                            ((KbPatternElement) pe).addKbMapping(this, mapTrustMark, match.firstlyMatched, match.label, null);
                        } else if (pe instanceof LiteralPatternElement) {
                            ((LiteralPatternElement) pe).addLiteralMapping(this, mapTrustMark, null);
                        }
                    }
                }
            }
        }
    }

    private class Match implements Comparable<Match> {

        String resourceUri = null;
        float trustMark = 0;
        String firstlyMatched = null;
        String label = null;
        boolean checkMappingCondition;

        public Match(String resourceUri, float trustMark, String firstlyMatched, String label, boolean checkMappingCondition) {
            this.resourceUri = resourceUri;
            this.trustMark = trustMark;
            this.firstlyMatched = firstlyMatched;
            this.label = label;
            this.checkMappingCondition = checkMappingCondition;
        }

        public int compareTo(Match o) {
            if (this.trustMark < o.trustMark) {
                return -1;
            }
            if (this.trustMark == o.trustMark) {
                return 0;
            }
            return 1;
        }
    }

    private String stemTerm(String term) {
        if (term == null) {
            return null;
        }
        SnowballStemmer stemmer = englishStemmer.getStemmer();
        stemmer.setCurrent(term);
        stemmer.stem();
        return stemmer.getCurrent();
    }

    @Override
    public String toString() {
        return "Keyword{\"" + keywordValue + "\" - queried=" + queried + " - id=" + id + "}";
    }

    @Override
    public String getStringRepresentation() {
        return (this.queried ? "?" : "") + (this.id > 0 ? "$" + this.id : "") + this.keywordValue;
    }
}
