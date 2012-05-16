package org.swip.pivotToMappings.model.patterns.mapping;

import java.util.ArrayList;
import java.util.Iterator;
import org.swip.pivotToMappings.model.KbTypeEnum;
import org.swip.pivotToMappings.model.patterns.patternElement.PatternElement;
import org.swip.pivotToMappings.model.query.queryElement.QueryElement;
import org.swip.pivotToMappings.sparql.SparqlServer;
import com.hp.hpl.jena.query.QuerySolution;
import org.apache.log4j.Logger;


public class KbElementMapping extends ElementMapping {

    private static final Logger logger = Logger.getLogger(KbElementMapping.class);

    private KbTypeEnum kbType;
    private boolean generalized;
    private ArrayList<String> generalizations;

    String firstlyMatchedOntResourceUri = null;
    String bestLabel = null;
    

    public KbElementMapping() {
        this.generalized = false;
        this.generalizations = new ArrayList<String>();
    }

    public KbElementMapping(PatternElement pe, QueryElement qe, float trustMark, String firstlyMatchedOntResource, String bestLabel, ElementMapping impliedBy, KbTypeEnum kbType) {
        super(pe, qe, trustMark, impliedBy);
        this.firstlyMatchedOntResourceUri = firstlyMatchedOntResource;
        this.bestLabel = bestLabel;
        this.kbType = kbType;

        this.generalized = false;
        this.generalizations = new ArrayList<String>();
    }
    
    public KbTypeEnum getKbType() {
        return this.kbType;
    }

    public String getBestLabel() {
        return bestLabel;
    }

    public void setBestLabel(String bestLabel) {
        this.bestLabel = bestLabel;
    }

    public String getFirstlyMatchedOntResourceUri() {
        return firstlyMatchedOntResourceUri;
    }

    public void setFirstlyMatchedOntResourceUri(String firstlyMatchedOntResourceUri) {
        this.firstlyMatchedOntResourceUri = firstlyMatchedOntResourceUri;
    }

    public ArrayList<String> getGeneralizations() {
        return this.generalizations;
    }

    @Override
    public String getStringForSentence(SparqlServer sparqlServer) {
        String ret;

        if(this.kbType  == KbTypeEnum.CLASS) {
            if(this.generalized) {
                ret = "_gen_";
            } else {
                this.generateGeneralizedLabels(sparqlServer, this.generalizeClass(sparqlServer, this.getFirstlyMatchedOntResourceUri()));
                if(this.generalizations.size() <= 0)
                    ret = "un(e) " + this.bestLabel;
                else {
                    ret = "_gen_";
                    this.generalized = true;
                }
            }
        } else if(this.kbType == KbTypeEnum.IND ) {
            if(this.generalized) {
                ret = "_gen_";
            } else {
                this.generateGeneralizedLabels(sparqlServer, this.generalizeInd(sparqlServer, this.getFirstlyMatchedOntResourceUri()));
                if(this.generalizations.size() <= 0)
                    ret = this.bestLabel;
                else {
                    ret = "_gen_";
                    this.generalized = true;
                }
            }

        } else if(this.kbType == KbTypeEnum.PROP) {
            if(this.generalized) {
                ret = "_gen_";
            } else {
                this.generateGeneralizedLabels(sparqlServer, this.generalizeProp(sparqlServer, this.getFirstlyMatchedOntResourceUri()));
                if(this.generalizations.size() <= 0)
                    ret = this.bestLabel;
                else {
                    ret = "_gen_";
                    this.generalized = true;
                }
            }
        } else {
            ret = this.bestLabel;
        }

        logger.info("KBEM : " + generalizations.toString());

        return ret;
    }

    private Iterable<QuerySolution> generalizeClass(SparqlServer sparqlServer, String c) {
        ArrayList<String> ret = new ArrayList<String>();

        String sparqlQuery = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ";
        sparqlQuery += "SELECT ?classes WHERE { <" + c + "> rdfs:subClassOf ?classes } LIMIT 5";
        
        logger.info("Generalizing " + c + "...");

        return sparqlServer.select(sparqlQuery);
    }

    private Iterable<QuerySolution> generalizeInd(SparqlServer sparqlServer, String i) {
        ArrayList<String> ret = new ArrayList<String>();

        String sparqlQuery = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ";
        sparqlQuery += "SELECT ?type WHERE { <" + i + "> rdf:type ?type } LIMIT 5";
        
        logger.info("Generalizing " + i + "...");

        return sparqlServer.select(sparqlQuery);
    }

    private Iterable<QuerySolution> generalizeProp(SparqlServer sparqlServer, String p) {
        ArrayList<String> ret = new ArrayList<String>();

        String sparqlQuery = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ";
        sparqlQuery += "SELECT ?classes WHERE { <" + p + "> rdfs:subPropertyOf ?classes } LIMIT 5";
        
        logger.info("Generalizing " + p + "...");

        return sparqlServer.select(sparqlQuery);
    }

    private void generateGeneralizedLabels(SparqlServer sparqlServer, Iterable<QuerySolution> sols) {
        String article = "";
        if(this.kbType == KbTypeEnum.CLASS || this.kbType == KbTypeEnum.IND)
            article = "un(e) ";

        this.generalizations.add("\"" + article + this.getBestLabel() + "\"");

        for(QuerySolution sol : sols) {
            Iterator<String> varNames = sol.varNames();
            while(varNames.hasNext()) {
                String varName = varNames.next();
                String labelGen = sparqlServer.getALabel(sol.get(varName).toString());

                if(labelGen != null) {
                    this.generalizations.add("\"" + article + labelGen + "\"");
                }
            }
        }
    }

    public boolean isGeneralized() {
        return this.generalized;
    }

    public void changeValues(float trustMark, String bestLabel, KbTypeEnum kbType) {
        this.trustMark = trustMark;
        this.bestLabel = bestLabel;
        this.kbType = kbType;
    }
    
    public boolean isClass() {
        return ((this.kbType == KbTypeEnum.CLASS) ? true : false);
    }
    
    public boolean isInd() {
        return ((this.kbType == KbTypeEnum.IND) ? true : false);
    }
    
    public boolean isProp() {
        return ((this.kbType == KbTypeEnum.PROP) ? true : false);
    }

    @Override
    public String toString() {
        return "[KbElementMapping]" + patternElement + " -> " + queryElement + " - trust mark=" + trustMark + " - matched = " + firstlyMatchedOntResourceUri + " - label = " + bestLabel;
    }
    
}
