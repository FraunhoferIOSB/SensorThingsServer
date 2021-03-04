/*
 * Copyright (C) 2016 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.frostserver.json.deserialize;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.fraunhofer.iosb.ilt.frostserver.json.deserialize.custom.CustomDeserializationManager;
import de.fraunhofer.iosb.ilt.frostserver.json.deserialize.custom.CustomEntityChangedMessageDeserializer;
import de.fraunhofer.iosb.ilt.frostserver.json.deserialize.custom.CustomEntityDeserializer;
import de.fraunhofer.iosb.ilt.frostserver.json.deserialize.custom.GeoJsonDeserializier;
import de.fraunhofer.iosb.ilt.frostserver.json.mixin.MixinUtils;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityChangedMessage;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.model.ModelRegistry;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Entity;
import de.fraunhofer.iosb.ilt.frostserver.model.core.EntitySet;
import de.fraunhofer.iosb.ilt.frostserver.model.core.EntitySetImpl;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Id;
import de.fraunhofer.iosb.ilt.frostserver.model.ext.TimeInstant;
import de.fraunhofer.iosb.ilt.frostserver.model.ext.TimeInterval;
import de.fraunhofer.iosb.ilt.frostserver.model.ext.TimeValue;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows parsing of STA entities from JSON. Fails on unknown properties in the
 * JSON input!
 *
 * @author jab
 */
public class JsonReader {

    private static final Map<ModelRegistry, ObjectMapper> mappers = new HashMap<>();

    /**
     * Get an object mapper for the given id Class. If the id class is the same
     * as for the first call, the cached mapper is returned.
     *
     * @param idClass The id class to use for this mapper.
     * @return The cached or created object mapper.
     */
    private static ObjectMapper getObjectMapper(ModelRegistry modelRegistry) {
        return mappers.computeIfAbsent(modelRegistry, (t) -> {
            return createObjectMapper(t);
        });
    }

    /**
     * Create a new object mapper for the given id Class.
     *
     * @param idClass The id class to use for this mapper.
     * @return The created object mapper.
     */
    private static ObjectMapper createObjectMapper(ModelRegistry modelRegistry) {
        // ToDo: Allow extensions to add deserializers

        GeoJsonDeserializier geoJsonDeserializier = new GeoJsonDeserializier();
        for (String encodingType : GeoJsonDeserializier.ENCODINGS) {
            CustomDeserializationManager.getInstance().registerDeserializer(encodingType, geoJsonDeserializier);
        }
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

        MixinUtils.addMixins(mapper);

        SimpleModule module = new SimpleModule();
        module.addAbstractTypeMapping(EntitySet.class, EntitySetImpl.class);
        module.addAbstractTypeMapping(Id.class, modelRegistry.getIdClass());
        for (EntityType entityType : modelRegistry.getEntityTypes()) {
            CustomEntityDeserializer.getInstance(modelRegistry, entityType);
        }
        module.addDeserializer(EntityChangedMessage.class, new CustomEntityChangedMessageDeserializer(modelRegistry));
        module.addDeserializer(TimeInstant.class, new TimeInstantDeserializer());
        module.addDeserializer(TimeInterval.class, new TimeIntervalDeserializer());
        module.addDeserializer(TimeValue.class, new TimeValueDeserializer());

        mapper.registerModule(module);
        return mapper;
    }

    /**
     * The objectMapper for this instance of EntityParser.
     */
    private final ObjectMapper mapper;
    private final ModelRegistry modelRegistry;

    public JsonReader(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
        mapper = getObjectMapper(modelRegistry);
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public Entity parseEntity(EntityType entityType, String value) throws IOException {
        try (final JsonParser parser = mapper.createParser(value)) {
            DefaultDeserializationContext dsc = (DefaultDeserializationContext) mapper.getDeserializationContext();
            dsc = dsc.createInstance(mapper.getDeserializationConfig(), parser, mapper.getInjectableValues());
            return CustomEntityDeserializer.getInstance(modelRegistry, entityType)
                    .deserializeFull(parser, dsc);
        }
    }

    public <T> T parseObject(Class<T> clazz, String value) throws IOException {
        return mapper.readValue(value, clazz);
    }

    public <T> T parseObject(TypeReference<T> typeReference, String value) throws IOException {
        return mapper.readValue(value, typeReference);
    }

}
