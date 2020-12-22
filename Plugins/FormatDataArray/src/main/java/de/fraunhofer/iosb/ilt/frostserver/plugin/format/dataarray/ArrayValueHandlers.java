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
package de.fraunhofer.iosb.ilt.frostserver.plugin.format.dataarray;

import de.fraunhofer.iosb.ilt.frostserver.model.DefaultEntity;
import de.fraunhofer.iosb.ilt.frostserver.model.ModelRegistry;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Entity;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Id;
import de.fraunhofer.iosb.ilt.frostserver.model.ext.TimeInstant;
import de.fraunhofer.iosb.ilt.frostserver.model.ext.TimeInterval;
import de.fraunhofer.iosb.ilt.frostserver.persistence.IdManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.PersistenceManagerFactory;
import de.fraunhofer.iosb.ilt.frostserver.plugin.coremodel.PluginCoreModel;
import static de.fraunhofer.iosb.ilt.frostserver.property.SpecialNames.AT_IOT_ID;
import de.fraunhofer.iosb.ilt.frostserver.service.PluginManager;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class ArrayValueHandlers {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayValueHandlers.class);

    /**
     * Our default handlers.
     */
    private final Map<String, ArrayValueHandler> HANDLERS = new HashMap<>();

    public ArrayValueHandler getHandler(CoreSettings settings, String component) {
        if (HANDLERS.isEmpty()) {
            createDefaults(settings);
        }
        return HANDLERS.get(component);
    }

    private synchronized void createDefaults(CoreSettings settings) {
        PluginManager pluginManager = settings.getPluginManager();
        PluginCoreModel pluginCoreModel = pluginManager.getPlugin(PluginCoreModel.class);
        ModelRegistry modelRegistry = settings.getModelRegistry();
        if (!HANDLERS.isEmpty()) {
            return;
        }

        final IdManager idManager = PersistenceManagerFactory.getInstance(settings).getIdManager();
        ArrayValueHandler idHandler = (Object value, Entity target) -> target.setId(idManager.parseId(value.toString()));
        HANDLERS.put("id", idHandler);
        HANDLERS.put(AT_IOT_ID, idHandler);
        HANDLERS.put(
                "result",
                (Object value, Entity target) -> target.setProperty(pluginCoreModel.EP_RESULT, value)
        );
        HANDLERS.put(
                "resultQuality",
                (Object value, Entity target) -> target.setProperty(pluginCoreModel.EP_RESULTQUALITY, value)
        );
        HANDLERS.put("parameters", (Object value, Entity target) -> {
            if (value instanceof Map) {
                target.setProperty(pluginCoreModel.EP_PARAMETERS, (Map) value);
                return;
            }
            throw new IllegalArgumentException("parameters has to be a map.");
        });
        HANDLERS.put("phenomenonTime", (Object value, Entity target) -> {
            try {
                TimeInstant time = TimeInstant.parse(value.toString());
                target.setProperty(pluginCoreModel.EP_PHENOMENONTIME, time);
                return;
            } catch (Exception e) {
                LOGGER.trace("Not a time instant: {}.", value);
            }
            try {
                TimeInterval time = TimeInterval.parse(value.toString());
                target.setProperty(pluginCoreModel.EP_PHENOMENONTIME, time);
                return;
            } catch (Exception e) {
                LOGGER.trace("Not a time interval: {}.", value);
            }
            throw new IllegalArgumentException("phenomenonTime could not be parsed as time instant or time interval.");
        });
        HANDLERS.put("resultTime", (Object value, Entity target) -> {
            try {
                TimeInstant time = TimeInstant.parse(value.toString());
                target.setProperty(pluginCoreModel.EP_RESULTTIME, time);
            } catch (Exception e) {
                throw new IllegalArgumentException("resultTime could not be parsed as time instant or time interval.", e);
            }
        });
        HANDLERS.put("validTime", (Object value, Entity target) -> {
            try {
                TimeInterval time = TimeInterval.parse(value.toString());
                target.setProperty(pluginCoreModel.EP_VALIDTIME, time);
            } catch (Exception e) {
                throw new IllegalArgumentException("resultTime could not be parsed as time instant or time interval.", e);
            }
        });
        HANDLERS.put("FeatureOfInterest/id", (Object value, Entity target) -> {
            Id foiId = idManager.parseId(value.toString());
            target.setProperty(pluginCoreModel.NP_FEATUREOFINTEREST, new DefaultEntity(pluginCoreModel.FEATURE_OF_INTEREST, foiId));
        });

    }

    public interface ArrayValueHandler {

        public void handle(Object value, Entity target);
    }
}
