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

import de.fraunhofer.iosb.ilt.frostserver.formatter.ResultFormatter;
import de.fraunhofer.iosb.ilt.frostserver.json.serialize.JsonWriter;
import de.fraunhofer.iosb.ilt.frostserver.model.ModelRegistry;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Entity;
import de.fraunhofer.iosb.ilt.frostserver.model.core.EntitySet;
import de.fraunhofer.iosb.ilt.frostserver.path.PathElement;
import de.fraunhofer.iosb.ilt.frostserver.path.PathElementEntitySet;
import de.fraunhofer.iosb.ilt.frostserver.path.ResourcePath;
import de.fraunhofer.iosb.ilt.frostserver.plugin.coremodel.PluginCoreModel;
import de.fraunhofer.iosb.ilt.frostserver.plugin.multidatastream.PluginMultiDatastream;
import de.fraunhofer.iosb.ilt.frostserver.property.Property;
import de.fraunhofer.iosb.ilt.frostserver.query.Query;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.IncorrectRequestException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jab
 */
public class ResultFormatterDataArray implements ResultFormatter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResultFormatterDataArray.class);
    private static final String OBSERVATIONS_ONLY = "ResultFormat=dataArray is only valid for /Observations";

    private final CoreSettings settings;
    private final PluginCoreModel pluginCoreModel;
    private final PluginMultiDatastream pluginMd;
    private final ModelRegistry modelRegistry;

    public ResultFormatterDataArray(CoreSettings settings) {
        this.settings = settings;
        modelRegistry = settings.getModelRegistry();
        pluginCoreModel = settings.getPluginManager().getPlugin(PluginCoreModel.class);
        pluginMd = settings.getPluginManager().getPlugin(PluginMultiDatastream.class);
        LOGGER.debug("Creating a new ResultFormaterDataArray.");
    }

    @Override
    public void preProcessRequest(ResourcePath path, Query query) throws IncorrectRequestException {
        if (!(path.getLastElement() instanceof PathElementEntitySet)
                || path.isRef()) {
            throw new IncorrectRequestException(OBSERVATIONS_ONLY);
        }
        if (!query.getSelect().isEmpty()) {
            PathElement lastElement = path.getLastElement();
            final ModelRegistry modelRegistry = settings.getModelRegistry();
            if (lastElement instanceof PathElementEntitySet && ((PathElementEntitySet) lastElement).getEntityType() == pluginCoreModel.etObservation) {
                query.getSelect().add(pluginCoreModel.npDatastream);
                if (pluginMd != null) {
                    query.getSelect().add(pluginMd.npMultiDatastream);
                }
            }
        }
    }

    @Override
    public String format(ResourcePath path, Query query, Object result, boolean useAbsoluteNavigationLinks) {
        String entityJsonString = "";
        try {
            if (EntitySet.class.isAssignableFrom(result.getClass())) {
                EntitySet entitySet = (EntitySet) result;
                final ModelRegistry modelRegistry = settings.getModelRegistry();
                if (entitySet.getEntityType() == pluginCoreModel.etObservation) {
                    return formatDataArray(path, query, entitySet);
                }
            }
            throw new IllegalArgumentException(OBSERVATIONS_ONLY);
        } catch (IOException ex) {
            LOGGER.error("Failed to format response.", ex);
        }
        return entityJsonString;
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    public static class VisibleComponents {

        public final boolean id;
        public final boolean phenomenonTime;
        public final boolean result;
        public final boolean resultTime;
        public final boolean resultQuality;
        public final boolean validTime;
        public final boolean parameters;
        private final ModelRegistry modelRegistry;
        private final PluginCoreModel pluginCoreModel;
        private final PluginMultiDatastream pluginMultiDatastream;

        public VisibleComponents(ModelRegistry modelRegistry, PluginCoreModel pCoreModel, PluginMultiDatastream pMultiDs) {
            this(modelRegistry, pCoreModel, pMultiDs, false);
        }

        public VisibleComponents(ModelRegistry modelRegistry, PluginCoreModel pCoreModel, PluginMultiDatastream pMultiDs, boolean allValue) {
            this.modelRegistry = modelRegistry;
            this.pluginCoreModel = pCoreModel;
            this.pluginMultiDatastream = pMultiDs;
            id = allValue;
            phenomenonTime = allValue;
            result = allValue;
            resultTime = allValue;
            resultQuality = allValue;
            validTime = allValue;
            parameters = allValue;
        }

        public VisibleComponents(ModelRegistry modelRegistry, PluginCoreModel pCoreModel, PluginMultiDatastream pMultiDs, Set<Property> select) {
            this.modelRegistry = modelRegistry;
            this.pluginCoreModel = pCoreModel;
            this.pluginMultiDatastream = pMultiDs;
            id = select.contains(ModelRegistry.EP_ID);
            phenomenonTime = select.contains(pCoreModel.epPhenomenonTime);
            result = select.contains(pCoreModel.epResult);
            resultTime = select.contains(pCoreModel.epResultTime);
            resultQuality = select.contains(pCoreModel.epResultQuality);
            validTime = select.contains(pCoreModel.epValidTime);
            parameters = select.contains(pCoreModel.epParameters);
        }

        public List<String> getComponents() {
            List<String> components = new ArrayList<>();
            if (id) {
                components.add(ModelRegistry.EP_ID.name);
            }
            if (phenomenonTime) {
                components.add(pluginCoreModel.epPhenomenonTime.name);
            }
            if (result) {
                components.add(pluginCoreModel.epResult.name);
            }
            if (resultTime) {
                components.add(pluginCoreModel.epResultTime.name);
            }
            if (resultQuality) {
                components.add(pluginCoreModel.epResultQuality.name);
            }
            if (validTime) {
                components.add(pluginCoreModel.epValidTime.name);
            }
            if (parameters) {
                components.add(pluginCoreModel.epParameters.name);
            }
            return components;
        }

        public List<Object> fromObservation(Entity o) {
            List<Object> value = new ArrayList<>();
            if (id) {
                value.add(o.getId().getValue());
            }
            if (phenomenonTime) {
                value.add(o.getProperty(pluginCoreModel.epPhenomenonTime));
            }
            if (result) {
                value.add(o.getProperty(pluginCoreModel.epResult));
            }
            if (resultTime) {
                value.add(o.getProperty(pluginCoreModel.epResultTime));
            }
            if (resultQuality) {
                value.add(o.getProperty(pluginCoreModel.epResultQuality));
            }
            if (validTime) {
                value.add(o.getProperty(pluginCoreModel.epValidTime));
            }
            if (parameters) {
                value.add(o.getProperty(pluginCoreModel.epParameters));
            }
            return value;
        }
    }

    public String formatDataArray(ResourcePath path, Query query, EntitySet entitySet) throws IOException {
        VisibleComponents visComps;
        if (query == null || query.getSelect().isEmpty()) {
            visComps = new VisibleComponents(modelRegistry, pluginCoreModel, pluginMd, true);
        } else {
            visComps = new VisibleComponents(modelRegistry, pluginCoreModel, pluginMd, query.getSelect());
        }
        List<String> components = visComps.getComponents();

        Map<String, DataArrayValue> dataArraySet = new LinkedHashMap<>();
        for (Entity obs : entitySet) {
            String dataArrayId = DataArrayValue.dataArrayIdFor(obs, pluginCoreModel.npDatastream, pluginMd.npMultiDatastream);
            DataArrayValue dataArray = dataArraySet.computeIfAbsent(
                    dataArrayId,
                    k -> new DataArrayValue(path, obs, components, pluginCoreModel.npDatastream, pluginMd.npMultiDatastream)
            );
            dataArray.getDataArray().add(visComps.fromObservation(obs));
        }

        DataArrayResult result = new DataArrayResult();
        List<DataArrayValue> values = result.getValue();
        for (DataArrayValue daValue : dataArraySet.values()) {
            values.add(daValue);
        }

        result.setCount(entitySet.getCount());
        result.setNextLink(entitySet.getNextLink());

        return JsonWriter.writeObject(result);
    }

}
