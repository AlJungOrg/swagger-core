package io.swagger.v3.core.converter;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ModelConverterContextImpl implements ModelConverterContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelConverterContextImpl.class);

    private final List<ModelConverter> converters;
    private final Map<String, Schema> modelByName;
    private final HashMap<AnnotatedType, Schema> modelByType;
    private final Set<AnnotatedType> processedTypes;

    public ModelConverterContextImpl(List<ModelConverter> converters) {
        this.converters = converters;
        modelByName = new TreeMap<>();
        modelByType = new HashMap<>();
        processedTypes = new HashSet<>();
    }

    public ModelConverterContextImpl(ModelConverter converter) {
        this(new ArrayList<ModelConverter>());
        converters.add(converter);
    }

    public Iterator<ModelConverter> getConverters() {
        return converters.iterator();
    }

    @Override
    public void defineModel(String name, Schema model) {
        AnnotatedType aType = null;
        defineModel(name, model, aType, null);
    }

    @Override
    public void defineModel(String name, Schema model, Type type, String prevName) {
        defineModel(name, model, new AnnotatedType().type(type), prevName);
    }
    @Override
    public void defineModel(String name, Schema model, AnnotatedType type, String prevName) {
        Schema oldSchema = modelByName.get(name);
         if (null != oldSchema && oldSchema instanceof ComposedSchema
                 && !(model instanceof ComposedSchema)) {
             // If the schema already exists and is a composed schema and the new one is not, simply
             // return ignoring the new schema.
             return;
         }
         
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(String.format("defineModel %s %s", name, model));
        }
        modelByName.put(name, model);

        if (StringUtils.isNotBlank(prevName) && !prevName.equals(name)) {
            modelByName.remove(prevName);
        }

        if (type != null && type.getType() != null) {
            modelByType.put(type, model);
        }
    }

    @Override
    public Map<String, Schema> getDefinedModels() {
        return Collections.unmodifiableMap(modelByName);
    }

    @Override
    public Schema resolve(AnnotatedType type) {

        if (processedTypes.contains(type)) {
            return modelByType.get(type);
        } else {
            processedTypes.add(type);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("resolve %s", type.getType()));
        }
        Iterator<ModelConverter> converters = this.getConverters();
        Schema resolved = null;
        if (converters.hasNext()) {
            ModelConverter converter = converters.next();
            LOGGER.trace("trying extension " + converter);
            resolved = converter.resolve(type, this, converters);
        }
        if (resolved != null) {
            String name = resolved.getName();
            // If the type is already defined, use the defined model instead of the re-resolved model.
            if (null != name) {
                if (modelByName.containsKey(name)) {
                    resolved = modelByName.get(name);
                } else {
                    modelByName.put(name, resolved);
                }
            }
            modelByType.put(type, resolved);
        } else {
            processedTypes.remove(type);
        }

        return resolved;
    }
}
