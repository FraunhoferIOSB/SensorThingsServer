/*
 * Copyright (C) 2020 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
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
package de.fraunhofer.iosb.ilt.frostserver.plugin.omsmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iosb.ilt.frostserver.model.ModelRegistry;
import de.fraunhofer.iosb.ilt.frostserver.model.loader.DefModel;
import de.fraunhofer.iosb.ilt.frostserver.persistence.PersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.service.PluginModel;
import de.fraunhofer.iosb.ilt.frostserver.service.PluginRootDocument;
import de.fraunhofer.iosb.ilt.frostserver.service.Service;
import de.fraunhofer.iosb.ilt.frostserver.service.ServiceRequest;
import de.fraunhofer.iosb.ilt.frostserver.settings.ConfigDefaults;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import de.fraunhofer.iosb.ilt.frostserver.settings.Settings;
import de.fraunhofer.iosb.ilt.frostserver.settings.annotation.DefaultValueBoolean;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class PluginOMSModel2 implements PluginRootDocument, PluginModel, ConfigDefaults {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginOMSModel2.class.getName());

    @DefaultValueBoolean(false)
    public static final String TAG_ENABLE_OMS_MODEL = "OMSModel.enable";

    private static final List<String> REQUIREMENTS_OMS_MODEL = Arrays.asList(
            "http://www.opengis.net/spec/oms/3/req/datamodel");

    private CoreSettings settings;
    private boolean enabled;
    private boolean fullyInitialised;
    private DefModel modelDefinition;

    public PluginOMSModel2() {
        LOGGER.info("Creating new Core Model Plugin.");
    }

    @Override
    public void init(CoreSettings settings) {
        this.settings = settings;
        Settings pluginSettings = settings.getPluginSettings();
        enabled = pluginSettings.getBoolean(TAG_ENABLE_OMS_MODEL, getClass());
        if (enabled) {
            settings.getPluginManager().registerPlugin(this);

            InputStream stream = getClass().getClassLoader().getResourceAsStream("pluginomsmodel/omsmodel.json");
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                modelDefinition = objectMapper.readValue(stream, DefModel.class);
                modelDefinition.init();
            } catch (IOException ex) {
                LOGGER.error("Failed to load model definition", ex);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isFullyInitialised() {
        return fullyInitialised;
    }

    @Override
    public void modifyServiceDocument(ServiceRequest request, Map<String, Object> result) {
        Map<String, Object> serverSettings = (Map<String, Object>) result.get(Service.KEY_SERVER_SETTINGS);
        if (serverSettings == null) {
            // Nothing to add to.
            return;
        }
        Set<String> extensionList = (Set<String>) serverSettings.get(Service.KEY_CONFORMANCE_LIST);
        extensionList.addAll(REQUIREMENTS_OMS_MODEL);
    }

    @Override
    public void registerEntityTypes() {
        ModelRegistry modelRegistry = settings.getModelRegistry();
        modelDefinition.registerEntityTypes(modelRegistry);
    }

    @Override
    public void registerProperties() {
        ModelRegistry modelRegistry = settings.getModelRegistry();
        modelDefinition.registerProperties(modelRegistry);
    }

    @Override
    public boolean linkEntityTypes(PersistenceManager pm) {
        LOGGER.info("Initialising OMS Model Types...");
        ModelRegistry modelRegistry = settings.getModelRegistry();
        modelDefinition.linkEntityTypes(modelRegistry);
        pm.loadMapping(modelDefinition);

        // Done, release the model definition.
        modelDefinition = null;
        fullyInitialised = true;
        return true;
    }

}
