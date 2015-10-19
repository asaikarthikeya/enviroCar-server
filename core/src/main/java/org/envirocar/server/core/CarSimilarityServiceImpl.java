package org.envirocar.server.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.envirocar.server.core.dao.SensorDao;
import org.envirocar.server.core.entities.Sensor;
import org.envirocar.server.core.entities.Sensors;
import org.envirocar.server.core.exception.ResourceNotFoundException;
import org.envirocar.server.core.filter.PropertyFilter;
import org.envirocar.server.core.filter.SensorFilter;
import org.envirocar.server.core.util.pagination.PageBasedPagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class CarSimilarityServiceImpl implements CarSimilarityService {
    
    private static final Logger log = LoggerFactory.getLogger(CarSimilarityServiceImpl.class);
    
    private static final String FUEL_TYPE = "fuelType";
    private static final String CONSTRUCTION_YEAR = "constructionYear";
    private static final String ENGINE_DISPLACEMENT = "engineDisplacement";
    private static final String MANUFACTURER = "manufacturer";
    private static final String MODEL = "model";
    
    private final SensorDao sensorDao;
    private Map<String, Set<String>> similarManufactures = new HashMap<>();

    @Inject
    public CarSimilarityServiceImpl(SensorDao sensorDao) {
        this.sensorDao = sensorDao;
        
        loadSimilarityDefinition("/car-similarity.json");
    }

    protected void loadSimilarityDefinition(String carSimilarityResourcePath) {
        InputStream res = getClass().getResourceAsStream(carSimilarityResourcePath);
        
        if (res != null) {
            final ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode json = mapper.reader().readTree(res);
                JsonNode mans = json.path("manufacturers");
                
                if (!mans.isMissingNode()) {
                    mans.forEach((JsonNode t) -> {
                        Set<String> matches = mapper.convertValue(t.path("matches"), Set.class);
                        String name = t.path("name").asText();
                        this.similarManufactures.put(name, matches);
                    });
                }
            } catch (IOException ex) {
                log.warn("Could not read Similarity Definition", ex);
            }
        }
        
    }

    @Override
    public Sensor resolveEquivalent(final Sensor s) throws ResourceNotFoundException {
        Sensors candidates = this.sensorDao.get(new SensorFilter("car", createFilter(s), new PageBasedPagination(PageBasedPagination.MAX_PAGE_SIZE, 0)));
        
        final Holder h = new Holder();

        candidates.forEach((Sensor t) -> {
            if (h.get() == null && isEquivalent(s, t)) {
                h.set(t);
            }
        });
        
        if (h.get() == null) {
            throw new ResourceNotFoundException("No equivalent sensor available");
        }
        
        return h.get();
    }
    
    protected boolean isEquivalent(Sensor a, Sensor b) {
        Map<String, Object> aProps = a.getProperties();
        Map<String, Object> bProps = b.getProperties();
       
        if (!isModelEquivalent(aProps.get(MODEL), bProps.get(MODEL))) {
            return false;
        }
        
        return isManufacturerEquivalent(aProps.get(MANUFACTURER), bProps.get(MANUFACTURER));
    }

    private Set<PropertyFilter> createFilter(Sensor s) {
        Set<PropertyFilter> filterSet = new HashSet<>();
        
        if (s.getProperties().containsKey(FUEL_TYPE)) {
            filterSet.add(new PropertyFilter(FUEL_TYPE, s.getProperties().get(FUEL_TYPE).toString()));
        }
        
        if (s.getProperties().containsKey(ENGINE_DISPLACEMENT)) {
            filterSet.add(new PropertyFilter(ENGINE_DISPLACEMENT, s.getProperties().get(ENGINE_DISPLACEMENT).toString()));
        }
        
        if (s.getProperties().containsKey(CONSTRUCTION_YEAR)) {
            filterSet.add(new PropertyFilter(CONSTRUCTION_YEAR, s.getProperties().get(CONSTRUCTION_YEAR).toString()));
        }
        
        return filterSet;
    }

    protected boolean isModelEquivalent(Object s1, Object s2) {
        if (s1 == null || s2 == null) {
            return false;
        }
        
        return s1.toString().toLowerCase().trim().equals(s2.toString().toLowerCase().trim());
    }
    
    protected boolean isManufacturerEquivalent(Object s1, Object s2) {
        if (s1 == null || s2 == null) {
            return false;
        }
        
        String s1Optimized = s1.toString().toLowerCase().trim();
        String s2Optimized = s2.toString().toLowerCase().trim();
        
        if (s1Optimized.equals(s2Optimized)) {
            return true;
        }
        else {
            return isManufacturerSimilar(s1Optimized, s2Optimized);
        }
    }

    protected boolean isManufacturerSimilar(String s1Optimized, String s2Optimized) {
        String matched1 = matchToName(s1Optimized, this.similarManufactures);
        String matched2 = matchToName(s2Optimized, this.similarManufactures);
        
        return matched1.equals(matched2);
    }

    private String matchToName(String s, Map<String, Set<String>> similarityDef) {
        for (String name : similarityDef.keySet()) {
            if (similarityDef.get(name).contains(s)) {
                return name;
            }
        }
        
        return s;
    }
    
    private class Holder {
        
        private Sensor sensor;

        public void set(Sensor s) {
            this.sensor = s;
        }
        
        public Sensor get() {
            return this.sensor;
        }
        
    }
    
    
}
