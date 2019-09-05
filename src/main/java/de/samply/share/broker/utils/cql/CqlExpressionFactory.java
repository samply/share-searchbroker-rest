package de.samply.share.broker.utils.cql;

import de.samply.config.util.FileFinderUtil;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileNotFoundException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

class CqlExpressionFactory {

    private final MultiKeyMap<String, CqlConfig.CqlAtomicExpressionEntry> mapAtomicExpressions = new MultiKeyMap<>();
    private final MultiKeyMap<String, CqlConfig.CqlEntityTypeEntry> mapPathExpressions = new MultiKeyMap<>();
    private final Map<String, String> mapCodeSystemNames = new HashMap<>();
    private final Map<String, String> mapCodeSystemUrls = new HashMap<>();
    private final Map<String, String> mapExtensions = new HashMap<>();

    private String preambleTemplate = "";

    private static final Logger logger = LogManager.getLogger(CqlExpressionFactory.class);

    CqlExpressionFactory() {
        File cqlConfigFile;
        try {
            cqlConfigFile = FileFinderUtil.findFile("samply_cql_config.xml");
        } catch (FileNotFoundException e) {
            logger.warn("No valid config file 'samply_cql_config.xml' could be found", e);
            return;
        }

        initMaps(cqlConfigFile);
    }

    CqlExpressionFactory(File cqlConfigFile) {
        initMaps(cqlConfigFile);
    }

    private void initMaps(File cqlConfigFile) {
        CqlConfig mapping;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(CqlConfig.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            mapping = (CqlConfig) unmarshaller.unmarshal(cqlConfigFile);
        } catch (JAXBException e) {
            logger.warn("Config file 'samply_cql_config.xml' could not be unmarshalled: ", e);
            return;
        }

        initMaps(mapping);
    }

    private void initMaps(CqlConfig mapping) {
        this.preambleTemplate = mapping.getPreamble();

        for (CqlConfig.CqlMdrFieldEntry mdrFieldEntry : mapping.getMdrFieldEntryList()) {
            if (!StringUtils.isBlank(mdrFieldEntry.getCodeSystemName())) {
                mapCodeSystemNames.put(mdrFieldEntry.getMdrUrn(), mdrFieldEntry.getCodeSystemName());
            }

            if (!StringUtils.isBlank(mdrFieldEntry.getCodeSystemUrl())) {
                mapCodeSystemUrls.put(mdrFieldEntry.getMdrUrn(), mdrFieldEntry.getCodeSystemUrl());
            }

            if (!StringUtils.isBlank(mdrFieldEntry.getExtensionName())) {
                mapExtensions.put(mdrFieldEntry.getMdrUrn(), mdrFieldEntry.getExtensionName());
            }
        }

        for (CqlConfig.CqlMdrFieldEntry mdrFieldEntry : mapping.getMdrFieldEntryList()) {
            for (CqlConfig.CqlEntityTypeEntry entityTypeEntry : mdrFieldEntry.getEntityTypeEntryList()) {
                for (CqlConfig.CqlAtomicExpressionEntry atomicExpressionEntry : entityTypeEntry.getAtomicExpressionList()) {
                    mapAtomicExpressions.put(mdrFieldEntry.getMdrUrn(), entityTypeEntry.getEntityType(), atomicExpressionEntry.getOperator(), atomicExpressionEntry);
                }
            }
        }

        for (CqlConfig.CqlMdrFieldEntry mdrFieldEntry : mapping.getMdrFieldEntryList()) {
            for (CqlConfig.CqlEntityTypeEntry entityTypeEntry : mdrFieldEntry.getEntityTypeEntryList()) {
                mapPathExpressions.put(mdrFieldEntry.getMdrUrn(), entityTypeEntry.getEntityType(), entityTypeEntry);
            }
        }
    }

    //TODO: Introduce parameter object
    String getAtomicExpression(String mdrUrn, String entityType, String operator, String... values) {
        CqlConfig.CqlAtomicExpressionEntry cqlAtomicExpressionEntry = mapAtomicExpressions.get(mdrUrn, entityType, operator);
        if (cqlAtomicExpressionEntry == null) {
            cqlAtomicExpressionEntry = mapAtomicExpressions.get(mdrUrn, entityType, "DEFAULT");
            if (cqlAtomicExpressionEntry == null) {
                logger.warn("No valid cql configuration found for entity type '" + entityType + "' and mdrUrn '" + mdrUrn + "' and operator '" + operator + "'");
                return "";
            }
        }

        Object[] operatorsAndValues = new Object[3 + values.length];
        operatorsAndValues[0] = operator;
        operatorsAndValues[1] = getCodesystemName(mdrUrn);
        operatorsAndValues[2] = getExtensionName(mdrUrn);
        System.arraycopy(values, 0, operatorsAndValues, 3, values.length);

        return MessageFormat.format(cqlAtomicExpressionEntry.getAtomicExpression(), operatorsAndValues);
    }

    String getPathExpression(String mdrUrn, String entityType, String atomicExpressions) {
        CqlConfig.CqlEntityTypeEntry cqlEntityTypeEntry1 = mapPathExpressions.get(mdrUrn, entityType);
        if (cqlEntityTypeEntry1 == null) {
            logger.warn("No valid cql configuration found for entity type '" + entityType + "' and mdrUrn '" + mdrUrn + "'");
            return "";
        }

        return MessageFormat.format(cqlEntityTypeEntry1.getPathExpression(), atomicExpressions);
    }

    String getPreamble(String entityType, String libraries) {
        return MessageFormat.format(preambleTemplate, entityType, libraries);
    }

    String getExtensionName(String mdrUrn) {
        return mapExtensions.getOrDefault(mdrUrn, "");
    }

    String getCodesystemName(String mdrUrn) {
        return mapCodeSystemNames.getOrDefault(mdrUrn, "");
    }

    String getCodesystemUrl(String mdrUrn) {
        return mapCodeSystemUrls.getOrDefault(mdrUrn, "");
    }
}
