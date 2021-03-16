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

import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.model.ModelRegistry;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Entity;
import de.fraunhofer.iosb.ilt.frostserver.model.ext.TimeInstant;
import de.fraunhofer.iosb.ilt.frostserver.model.ext.TimeInterval;
import de.fraunhofer.iosb.ilt.frostserver.model.ext.TimeValue;
import static de.fraunhofer.iosb.ilt.frostserver.model.ext.TypeReferencesHelper.TYPE_REFERENCE_MAP;
import static de.fraunhofer.iosb.ilt.frostserver.model.ext.TypeReferencesHelper.TYPE_REFERENCE_OBJECT;
import static de.fraunhofer.iosb.ilt.frostserver.model.ext.TypeReferencesHelper.TYPE_REFERENCE_STRING;
import static de.fraunhofer.iosb.ilt.frostserver.model.ext.TypeReferencesHelper.TYPE_REFERENCE_TIMEINSTANT;
import static de.fraunhofer.iosb.ilt.frostserver.model.ext.TypeReferencesHelper.TYPE_REFERENCE_TIMEINTERVAL;
import static de.fraunhofer.iosb.ilt.frostserver.model.ext.TypeReferencesHelper.TYPE_REFERENCE_TIMEVALUE;
import de.fraunhofer.iosb.ilt.frostserver.persistence.IdManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.PersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.PostgresPersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.JsonBinding;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.JsonValue;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.PostGisGeometryBinding;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories.EntityFactories;
import static de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.fieldwrapper.StaTimeIntervalWrapper.KEY_TIME_INTERVAL_END;
import static de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.fieldwrapper.StaTimeIntervalWrapper.KEY_TIME_INTERVAL_START;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.relations.RelationOneToMany;
import static de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.StaTableAbstract.TYPE_GEOMETRY;
import static de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.StaTableAbstract.TYPE_JSONB;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.StaTableDynamic;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableCollection;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.DataSize;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.PropertyFieldRegistry;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.ResultType;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.Utils;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyMain;
import de.fraunhofer.iosb.ilt.frostserver.property.NavigationPropertyMain.NavigationPropertyEntity;
import de.fraunhofer.iosb.ilt.frostserver.property.NavigationPropertyMain.NavigationPropertyEntitySet;
import de.fraunhofer.iosb.ilt.frostserver.service.PluginModel;
import de.fraunhofer.iosb.ilt.frostserver.service.PluginRootDocument;
import de.fraunhofer.iosb.ilt.frostserver.service.Service;
import de.fraunhofer.iosb.ilt.frostserver.service.ServiceRequest;
import de.fraunhofer.iosb.ilt.frostserver.settings.ConfigDefaults;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import de.fraunhofer.iosb.ilt.frostserver.settings.Settings;
import de.fraunhofer.iosb.ilt.frostserver.settings.annotation.DefaultValueBoolean;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class PluginOMSModel implements PluginRootDocument, PluginModel, ConfigDefaults {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginOMSModel.class.getName());

    public final EntityPropertyMain<String> epAccuracyHori = new EntityPropertyMain<>("horizAccuracy", TYPE_REFERENCE_STRING);
    public final EntityPropertyMain<String> epAccuracyVert = new EntityPropertyMain<>("vertAccuracy", TYPE_REFERENCE_STRING);
    public final EntityPropertyMain<Object> epDataQuality = new EntityPropertyMain<>("dataQuality", TYPE_REFERENCE_OBJECT, true, false);
    public final EntityPropertyMain<String> epDescription = new EntityPropertyMain<>("description", TYPE_REFERENCE_STRING);
    public final EntityPropertyMain<String> epLink = new EntityPropertyMain<>("link", TYPE_REFERENCE_STRING);
    public final EntityPropertyMain<Object> epLocation = new EntityPropertyMain<>("location", TYPE_REFERENCE_OBJECT, true, false);
    public final EntityPropertyMain<String> epMetadata = new EntityPropertyMain<>("metadata", TYPE_REFERENCE_STRING);
    public final EntityPropertyMain<String> epName = new EntityPropertyMain<>("name", TYPE_REFERENCE_STRING);
    public final EntityPropertyMain<String> epObservationType = new EntityPropertyMain<>("observationType", TYPE_REFERENCE_STRING);
    public final EntityPropertyMain<Map<String, Object>> epParameters = new EntityPropertyMain<>("Parameters", TYPE_REFERENCE_MAP, true, false);
    public final EntityPropertyMain<TimeValue> epPhenomenonTime = new EntityPropertyMain<>("PhenomenonTime", TYPE_REFERENCE_TIMEVALUE);
    public final EntityPropertyMain<String> epReason = new EntityPropertyMain<>("reason", TYPE_REFERENCE_STRING);
    public final EntityPropertyMain<Object> epResult = new EntityPropertyMain<>("Result", TYPE_REFERENCE_OBJECT, true, true);
    public final EntityPropertyMain<TimeInstant> epResultTime = new EntityPropertyMain<>("ResultTime", TYPE_REFERENCE_TIMEINSTANT, false, true);
    public final EntityPropertyMain<String> epSampleType = new EntityPropertyMain<>("sampleType", TYPE_REFERENCE_STRING);
    public final EntityPropertyMain<Object> epShape = new EntityPropertyMain<>("shape", TYPE_REFERENCE_OBJECT, true, false);
    public final EntityPropertyMain<TimeValue> epDeploymentTime = new EntityPropertyMain<>("deploymentTime", TYPE_REFERENCE_TIMEVALUE, false, false);
    public final EntityPropertyMain<TimeInterval> epValidTime = new EntityPropertyMain<>("ValidTime", TYPE_REFERENCE_TIMEINTERVAL);
    public final EntityPropertyMain<String> epValue = new EntityPropertyMain<>("value", TYPE_REFERENCE_STRING);

    public final NavigationPropertyEntity npDeployment = new NavigationPropertyEntity("Deployment");
    public final NavigationPropertyEntitySet npDeployments = new NavigationPropertyEntitySet("Deployments");
    public final NavigationPropertyEntity npFoi = new NavigationPropertyEntity("FeatureOfInterest");
    public final NavigationPropertyEntitySet npFois = new NavigationPropertyEntitySet("FeaturesOfInterest");
    public final NavigationPropertyEntity npHost = new NavigationPropertyEntity("Host");
    public final NavigationPropertyEntitySet npHosts = new NavigationPropertyEntitySet("Hosts");
    public final NavigationPropertyEntity npObservation = new NavigationPropertyEntity("Observation");
    public final NavigationPropertyEntitySet npObservations = new NavigationPropertyEntitySet("Observations");
    public final NavigationPropertyEntity npObservedProcedure = new NavigationPropertyEntity("ObservedProcedure");
    public final NavigationPropertyEntitySet npObservedProcedures = new NavigationPropertyEntitySet("ObservedProcedures");
    public final NavigationPropertyEntity npObservedProperty = new NavigationPropertyEntity("ObservedProperty");
    public final NavigationPropertyEntitySet npObservedProperties = new NavigationPropertyEntitySet("ObservedProperties");
    public final NavigationPropertyEntity npObserver = new NavigationPropertyEntity("Observer");
    public final NavigationPropertyEntitySet npObservers = new NavigationPropertyEntitySet("Observers");
    public final NavigationPropertyEntity npResult = new NavigationPropertyEntity("Result");
    public final NavigationPropertyEntitySet npResults = new NavigationPropertyEntitySet("Results");

    public final EntityType etDeployment = new EntityType("Deployment", "Deployments");
    public final EntityType etFeatureOfInterest = new EntityType("FeatureOfInterest", "FeaturesOfInterest");
    public final EntityType etHost = new EntityType("Host", "Hosts");
    public final EntityType etObservation = new EntityType("Observation", "Observations");
    public final EntityType etObservedProcedure = new EntityType("ObservedProcedure", "ObservedProcedures");
    public final EntityType etObservedProperty = new EntityType("ObservedProperty", "ObservedProperties");
    public final EntityType etObserver = new EntityType("Observer", "Observers");
    public final EntityType etResult = new EntityType("Result", "Results");

    @DefaultValueBoolean(false)
    public static final String TAG_ENABLE_OMS_MODEL = "OMSModel.enable";

    private static final List<String> REQUIREMENTS_CORE_MODEL = Arrays.asList(
            "http://www.opengis.net/spec/oms/3/req/datamodel");

    private CoreSettings settings;
    private boolean enabled;
    private boolean fullyInitialised;

    public PluginOMSModel() {
        LOGGER.info("Creating new Core Model Plugin.");
    }

    @Override
    public void init(CoreSettings settings) {
        this.settings = settings;
        Settings pluginSettings = settings.getPluginSettings();
        enabled = pluginSettings.getBoolean(TAG_ENABLE_OMS_MODEL, getClass());
        if (enabled) {
            settings.getPluginManager().registerPlugin(this);
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
        extensionList.addAll(REQUIREMENTS_CORE_MODEL);
    }

    @Override
    public void registerEntityTypes() {
        LOGGER.info("Initialising OMS Model Types...");
        ModelRegistry modelRegistry = settings.getModelRegistry();
        modelRegistry.registerEntityType(etDeployment);
        modelRegistry.registerEntityType(etFeatureOfInterest);
        modelRegistry.registerEntityType(etHost);
        modelRegistry.registerEntityType(etObservation);
        modelRegistry.registerEntityType(etObservedProcedure);
        modelRegistry.registerEntityType(etObservedProperty);
        modelRegistry.registerEntityType(etObserver);
        modelRegistry.registerEntityType(etResult);
    }

    @Override
    public void registerProperties() {
        LOGGER.info("Initialising OMS Model Properties...");
        ModelRegistry modelRegistry = settings.getModelRegistry();
        registerEps(modelRegistry);
        registerNps(modelRegistry);
    }

    private void registerEps(ModelRegistry modelRegistry) {
        modelRegistry.registerEntityProperty(ModelRegistry.EP_ID);
        modelRegistry.registerEntityProperty(ModelRegistry.EP_SELFLINK);
        modelRegistry.registerEntityProperty(ModelRegistry.EP_PROPERTIES);
        modelRegistry.registerEntityProperty(epAccuracyHori);
        modelRegistry.registerEntityProperty(epAccuracyVert);
        modelRegistry.registerEntityProperty(epDataQuality);
        modelRegistry.registerEntityProperty(epDescription);
        modelRegistry.registerEntityProperty(epLink);
        modelRegistry.registerEntityProperty(epLocation);
        modelRegistry.registerEntityProperty(epMetadata);
        modelRegistry.registerEntityProperty(epName);
        modelRegistry.registerEntityProperty(epObservationType);
        modelRegistry.registerEntityProperty(epParameters);
        modelRegistry.registerEntityProperty(epPhenomenonTime);
        modelRegistry.registerEntityProperty(epReason);
        modelRegistry.registerEntityProperty(epResult);
        modelRegistry.registerEntityProperty(epResultTime);
        modelRegistry.registerEntityProperty(epSampleType);
        modelRegistry.registerEntityProperty(epShape);
        modelRegistry.registerEntityProperty(epDeploymentTime);
        modelRegistry.registerEntityProperty(epValidTime);
        modelRegistry.registerEntityProperty(epValue);
    }

    private void registerNps(ModelRegistry modelRegistry) {
        modelRegistry.registerNavProperty(npDeployment);
        modelRegistry.registerNavProperty(npDeployments);
        modelRegistry.registerNavProperty(npFoi);
        modelRegistry.registerNavProperty(npFois);
        modelRegistry.registerNavProperty(npHost);
        modelRegistry.registerNavProperty(npHosts);
        modelRegistry.registerNavProperty(npObservation);
        modelRegistry.registerNavProperty(npObservations);
        modelRegistry.registerNavProperty(npObservedProcedure);
        modelRegistry.registerNavProperty(npObservedProcedures);
        modelRegistry.registerNavProperty(npObservedProperty);
        modelRegistry.registerNavProperty(npObservedProperties);
        modelRegistry.registerNavProperty(npObserver);
        modelRegistry.registerNavProperty(npObservers);
        modelRegistry.registerNavProperty(npResult);
        modelRegistry.registerNavProperty(npResults);
    }

    private static class TablesAndIndices<J extends Comparable> {

        public StaTableDynamic<J> tDeployment;
        public int idxDeploymentId;
        public int idxDepHost;
        public int idxDepObserver;
        public StaTableDynamic<J> tFeatureOfInterest;
        public int idxFeatureId;
        public StaTableDynamic<J> tHost;
        public int idxHostId;
        public StaTableDynamic<J> tObservation;
        public int idxObservationId;
        public int idxObsObsPropertyId;
        public int idxObsObsProcedureId;
        public int idxObsObserverId;
        public int idxObsHostId;
        public int idxObsFoiId;
        public StaTableDynamic<J> tObservedProcedure;
        public int idxOProcedureId;
        public StaTableDynamic<J> tObservedProperty;
        public int idxOPropertyId;
        public StaTableDynamic<J> tObserver;
        public int idxObserverId;
        public StaTableDynamic<J> tResult;
        public int idxResultId;
        public int idxResObservation;
    }

    @Override
    public boolean linkEntityTypes(PersistenceManager pm) {
        LOGGER.info("Linking OMS Model Types...");
        etDeployment
                .registerProperty(ModelRegistry.EP_ID, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(epName, true)
                .registerProperty(epDescription, false)
                .registerProperty(epMetadata, false)
                .registerProperty(epLink, false)
                .registerProperty(epReason, false)
                .registerProperty(epDeploymentTime, false)
                .registerProperty(npObserver, true)
                .registerProperty(npHost, true);
        etFeatureOfInterest
                .registerProperty(ModelRegistry.EP_ID, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(epName, true)
                .registerProperty(epDescription, false)
                .registerProperty(epMetadata, false)
                .registerProperty(epLink, false)
                .registerProperty(epAccuracyHori, false)
                .registerProperty(epAccuracyVert, false)
                .registerProperty(epSampleType, false)
                .registerProperty(epParameters, false)
                .registerProperty(epShape, false)
                .registerProperty(npObservations, false);
        etHost
                .registerProperty(ModelRegistry.EP_ID, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(epName, true)
                .registerProperty(epDescription, false)
                .registerProperty(epMetadata, false)
                .registerProperty(epLink, false)
                .registerProperty(epLocation, false)
                .registerProperty(npDeployments, false)
                .registerProperty(npObservations, false);
        etObservation
                .registerProperty(ModelRegistry.EP_ID, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(epName, false)
                .registerProperty(epDescription, false)
                .registerProperty(epMetadata, false)
                .registerProperty(epObservationType, false)
                .registerProperty(epPhenomenonTime, false)
                .registerProperty(epResultTime, false)
                .registerProperty(epValidTime, false)
                .registerProperty(epDataQuality, false)
                .registerProperty(epParameters, false)
                .registerProperty(npFoi, true)
                .registerProperty(npHost, true)
                .registerProperty(npResults, false)
                .registerProperty(npObservedProcedure, true)
                .registerProperty(npObservedProperty, true)
                .registerProperty(npObserver, true);
        etObservedProcedure
                .registerProperty(ModelRegistry.EP_ID, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(epName, true)
                .registerProperty(epDescription, false)
                .registerProperty(epMetadata, false)
                .registerProperty(epLink, false)
                .registerProperty(npObservations, false);
        etObservedProperty
                .registerProperty(ModelRegistry.EP_ID, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(epName, true)
                .registerProperty(epDescription, false)
                .registerProperty(epMetadata, false)
                .registerProperty(epLink, false)
                .registerProperty(npObservations, false);
        etObserver
                .registerProperty(ModelRegistry.EP_ID, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(epName, true)
                .registerProperty(epDescription, false)
                .registerProperty(epMetadata, false)
                .registerProperty(epLink, false)
                .registerProperty(epLocation, false)
                .registerProperty(npDeployments, false)
                .registerProperty(npObservations, false);
        etResult
                .registerProperty(ModelRegistry.EP_ID, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(epName, true)
                .registerProperty(epDescription, false)
                .registerProperty(epMetadata, false)
                .registerProperty(epPhenomenonTime, false)
                .registerProperty(epResultTime, false)
                .registerProperty(epResult, true)
                .registerProperty(epDataQuality, false)
                .registerProperty(epValidTime, false)
                .registerProperty(npObservation, true);

        if (pm instanceof PostgresPersistenceManager) {
            PostgresPersistenceManager ppm = (PostgresPersistenceManager) pm;
            TableCollection tableCollection = ppm.getTableCollection();
            IdManager idManager = ppm.getIdManager();

            registerTables(tableCollection, idManager, new TablesAndIndices<>());
        }
        fullyInitialised = true;
        return true;
    }

    private static <J extends Comparable> void registerRelationOneToMany(final StaTableDynamic<J> tOne, final StaTableDynamic<J> tMany, final int fieldIdxMany) {
        tOne.registerRelation(new RelationOneToMany<>(tOne, tMany, tMany.getEntityType(), true)
                .setSourceFieldAccessor(StaTableDynamic<J>::getId)
                .setTargetFieldAccessor(t -> (TableField<Record, J>) t.field(fieldIdxMany))
        );
        tMany.registerRelation(new RelationOneToMany<>(tMany, tOne, tOne.getEntityType(), false)
                .setSourceFieldAccessor(t -> (TableField<Record, J>) t.field(fieldIdxMany))
                .setTargetFieldAccessor(StaTableDynamic<J>::getId)
        );

    }

    private <J extends Comparable> void registerTables(TableCollection<J> tableCollection, IdManager idManager, TablesAndIndices<J> tai) {
        DataType<J> idType = tableCollection.getIdType();

        registerTableDeployment(idType, tableCollection, idManager, tai);
        registerTableFoi(idType, tableCollection, idManager, tai);
        registerTableHost(idType, tableCollection, idManager, tai);
        registerTableObservation(idType, tableCollection, idManager, tai);
        registerTableProcedure(idType, tableCollection, idManager, tai);
        registerTableObsProperty(idType, tableCollection, idManager, tai);
        registerTableObserver(idType, tableCollection, idManager, tai);
        registerTableResult(idType, tableCollection, idManager, tai);

        registerRelationOneToMany(tai.tHost, tai.tDeployment, tai.idxDepHost);
        registerRelationOneToMany(tai.tObserver, tai.tDeployment, tai.idxDepObserver);
        registerRelationOneToMany(tai.tObservedProperty, tai.tObservation, tai.idxObsObsPropertyId);
        registerRelationOneToMany(tai.tObservedProcedure, tai.tObservation, tai.idxObsObsProcedureId);
        registerRelationOneToMany(tai.tObserver, tai.tObservation, tai.idxObsObserverId);
        registerRelationOneToMany(tai.tHost, tai.tObservation, tai.idxObsHostId);
        registerRelationOneToMany(tai.tFeatureOfInterest, tai.tObservation, tai.idxObsFoiId);
        registerRelationOneToMany(tai.tObservation, tai.tResult, tai.idxResObservation);

    }

    private <J extends Comparable> void registerTableDeployment(DataType<J> idType, TableCollection<J> tableCollection, IdManager idManager, TablesAndIndices<J> tai) {
        tai.tDeployment = new StaTableDynamic<>(DSL.name("deployment"), etDeployment, idType);
        tableCollection.registerTable(tai.tDeployment.getEntityType(), tai.tDeployment);
        tai.idxDeploymentId = tai.tDeployment.registerIdField("id", idType);
        final int idxName = tai.tDeployment.registerField("name", SQLDataType.VARCHAR(100));
        final int idxDesc = tai.tDeployment.registerField("description", SQLDataType.CLOB);
        final int idxMeta = tai.tDeployment.registerField("metadata", SQLDataType.CLOB);
        final int idxLink = tai.tDeployment.registerField("link", SQLDataType.CLOB);
        final int idxReas = tai.tDeployment.registerField("reason", SQLDataType.VARCHAR(100));
        final int idxTiSt = tai.tDeployment.registerField("start_time", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxTiEn = tai.tDeployment.registerField("end_time", SQLDataType.TIMESTAMPWITHTIMEZONE);
        tai.idxDepObserver = tai.tDeployment.registerField("observer", idType);
        tai.idxDepHost = tai.tDeployment.registerField("host", idType);

        PropertyFieldRegistry<J, StaTableDynamic<J>> pfReg = tai.tDeployment.getPropertyFieldRegistry();
        pfReg.addEntryId(idManager, StaTableDynamic::getId);
        pfReg.addEntryString(epName, t -> t.field(idxName));
        pfReg.addEntryString(epDescription, t -> t.field(idxDesc));
        pfReg.addEntryString(epMetadata, t -> t.field(idxMeta));
        pfReg.addEntryString(epLink, t -> t.field(idxLink));
        pfReg.addEntryString(epReason, t -> t.field(idxReas));
        pfReg.addEntry(epDeploymentTime,
                new PropertyFieldRegistry.ConverterTimeValue<>(epDeploymentTime, t -> t.field(idxTiSt), t -> t.field(idxTiEn)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_START, t -> t.field(idxTiSt)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_END, t -> t.field(idxTiEn)));
        pfReg.addEntry(npObserver, t -> t.field(tai.idxDepObserver), idManager);
        pfReg.addEntry(npHost, t -> t.field(tai.idxDepHost), idManager);
    }

    private <J extends Comparable> void registerTableFoi(DataType<J> idType, TableCollection<J> tableCollection, IdManager idManager, TablesAndIndices<J> tai) {
        tai.tFeatureOfInterest = new StaTableDynamic<>(DSL.name("foi"), etFeatureOfInterest, idType);
        tableCollection.registerTable(tai.tFeatureOfInterest.getEntityType(), tai.tFeatureOfInterest);
        tai.idxFeatureId = tai.tFeatureOfInterest.registerIdField("id", idType);
        final int idxName = tai.tFeatureOfInterest.registerField("name", SQLDataType.VARCHAR(100));
        final int idxDesc = tai.tFeatureOfInterest.registerField("description", SQLDataType.CLOB);
        final int idxMeta = tai.tFeatureOfInterest.registerField("metadata", SQLDataType.CLOB);
        final int idxLink = tai.tFeatureOfInterest.registerField("link", SQLDataType.CLOB);
        final int idxAcHo = tai.tFeatureOfInterest.registerField("horiz_accuracy", SQLDataType.VARCHAR(100));
        final int idxAcVe = tai.tFeatureOfInterest.registerField("vert_accuracy", SQLDataType.VARCHAR(100));
        final int idxSaTy = tai.tFeatureOfInterest.registerField("sample_type", SQLDataType.VARCHAR(100));
        final int idxPara = tai.tFeatureOfInterest.registerField("parameters", DefaultDataType.getDefaultDataType(TYPE_JSONB), new JsonBinding());
        final int idxShap = tai.tFeatureOfInterest.registerField("shape", SQLDataType.CLOB);
        final int idxGeom = tai.tFeatureOfInterest.registerField("geom", DefaultDataType.getDefaultDataType(TYPE_GEOMETRY), new PostGisGeometryBinding());

        PropertyFieldRegistry<J, StaTableDynamic<J>> pfReg = tai.tFeatureOfInterest.getPropertyFieldRegistry();
        pfReg.addEntryId(idManager, StaTableDynamic::getId);
        pfReg.addEntryString(epName, t -> t.field(idxName));
        pfReg.addEntryString(epDescription, t -> t.field(idxDesc));
        pfReg.addEntryString(epMetadata, t -> t.field(idxMeta));
        pfReg.addEntryString(epLink, t -> t.field(idxLink));
        pfReg.addEntryString(epAccuracyHori, t -> t.field(idxAcHo));
        pfReg.addEntryString(epAccuracyVert, t -> t.field(idxAcVe));
        pfReg.addEntryString(epSampleType, t -> t.field(idxSaTy));
        pfReg.addEntryMap(epParameters, t -> t.field(idxPara));
        pfReg.addEntry(epShape,
                new PropertyFieldRegistry.ConverterRecordDeflt<>(
                        (StaTableDynamic<J> t, Record tuple, Entity entity, DataSize dataSize) -> {
                            String locationString = tuple.get(t.field(idxShap, SQLDataType.CLOB));
                            dataSize.increase(locationString == null ? 0 : locationString.length());
                            entity.setProperty(epLocation, Utils.locationUnknownEncoding(locationString));
                        },
                        (t, entity, insertFields) -> {
                            Object feature = entity.getProperty(epLocation);
                            EntityFactories.insertGeometry(insertFields, t.field(idxShap, SQLDataType.CLOB), t.field(idxGeom), null, feature);
                        },
                        (t, entity, updateFields, message) -> {
                            Object feature = entity.getProperty(epLocation);
                            EntityFactories.insertGeometry(updateFields, t.field(idxShap, SQLDataType.CLOB), t.field(idxGeom), null, feature);
                            message.addField(epLocation);
                        }),
                new PropertyFieldRegistry.NFP<>("j", t -> t.field(idxShap)));
        pfReg.addEntryNoSelect(epLocation, "g", t -> t.field(idxGeom));
    }

    private <J extends Comparable> void registerTableHost(DataType<J> idType, TableCollection<J> tableCollection, IdManager idManager, TablesAndIndices<J> tai) {
        tai.tHost = new StaTableDynamic<>(DSL.name("host"), etHost, idType);
        tableCollection.registerTable(tai.tHost.getEntityType(), tai.tHost);
        tai.idxHostId = tai.tHost.registerIdField("id", idType);
        final int idxName = tai.tHost.registerField("name", SQLDataType.VARCHAR(100));
        final int idxDesc = tai.tHost.registerField("description", SQLDataType.CLOB);
        final int idxMeta = tai.tHost.registerField("metadata", SQLDataType.CLOB);
        final int idxLink = tai.tHost.registerField("link", SQLDataType.CLOB);
        final int idxLoca = tai.tHost.registerField("location", SQLDataType.CLOB);
        final int idxGeom = tai.tHost.registerField("geom", DefaultDataType.getDefaultDataType(TYPE_GEOMETRY), new PostGisGeometryBinding());

        PropertyFieldRegistry<J, StaTableDynamic<J>> pfReg = tai.tHost.getPropertyFieldRegistry();
        pfReg.addEntryId(idManager, StaTableDynamic::getId);
        pfReg.addEntryString(epName, t -> t.field(idxName));
        pfReg.addEntryString(epDescription, t -> t.field(idxDesc));
        pfReg.addEntryString(epMetadata, t -> t.field(idxMeta));
        pfReg.addEntryString(epLink, t -> t.field(idxLink));
        pfReg.addEntry(epLocation,
                new PropertyFieldRegistry.ConverterRecordDeflt<>(
                        (StaTableDynamic<J> t, Record tuple, Entity entity, DataSize dataSize) -> {
                            String locationString = tuple.get(t.field(idxLoca, SQLDataType.CLOB));
                            dataSize.increase(locationString == null ? 0 : locationString.length());
                            entity.setProperty(epLocation, Utils.locationUnknownEncoding(locationString));
                        },
                        (t, entity, insertFields) -> {
                            Object feature = entity.getProperty(epLocation);
                            EntityFactories.insertGeometry(insertFields, t.field(idxLoca, SQLDataType.CLOB), t.field(idxGeom), null, feature);
                        },
                        (t, entity, updateFields, message) -> {
                            Object feature = entity.getProperty(epLocation);
                            EntityFactories.insertGeometry(updateFields, t.field(idxLoca, SQLDataType.CLOB), t.field(idxGeom), null, feature);
                            message.addField(epLocation);
                        }),
                new PropertyFieldRegistry.NFP<>("j", t -> t.field(idxLoca)));
        pfReg.addEntryNoSelect(epLocation, "g", t -> t.field(idxGeom));
        pfReg.addEntry(npDeployments, t -> t.field(tai.idxHostId), idManager);
        pfReg.addEntry(npObservations, t -> t.field(tai.idxHostId), idManager);
    }

    private <J extends Comparable> void registerTableObservation(DataType<J> idType, TableCollection<J> tableCollection, IdManager idManager, TablesAndIndices<J> tai) {
        tai.tObservation = new StaTableDynamic<>(DSL.name("observation"), etObservation, idType);
        tableCollection.registerTable(tai.tObservation.getEntityType(), tai.tObservation);
        tai.idxObservationId = tai.tObservation.registerIdField("id", idType);
        final int idxName = tai.tObservation.registerField("name", SQLDataType.VARCHAR(100));
        final int idxDesc = tai.tObservation.registerField("description", SQLDataType.CLOB);
        final int idxMeta = tai.tObservation.registerField("metadata", SQLDataType.CLOB);
        final int idxObTy = tai.tObservation.registerField("observation_type", SQLDataType.VARCHAR(100));
        final int idxPhSt = tai.tObservation.registerField("phenomenon_time_start", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxPhEn = tai.tObservation.registerField("phenomenon_time_end", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxReTi = tai.tObservation.registerField("result_time", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxVaSt = tai.tObservation.registerField("valid_time_start", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxVaEn = tai.tObservation.registerField("valid_time_end", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxDaQu = tai.tObservation.registerField("data_quality", DefaultDataType.getDefaultDataType(TYPE_JSONB), new JsonBinding());
        final int idxPara = tai.tObservation.registerField("parameters", DefaultDataType.getDefaultDataType(TYPE_JSONB), new JsonBinding());
        tai.idxObsObsPropertyId = tai.tObservation.registerField("obs_prop", idType);
        tai.idxObsObsProcedureId = tai.tObservation.registerField("obs_procedure", idType);
        tai.idxObsObserverId = tai.tObservation.registerField("observer", idType);
        tai.idxObsHostId = tai.tObservation.registerField("host", idType);
        tai.idxObsFoiId = tai.tObservation.registerField("foi", idType);

        PropertyFieldRegistry<J, StaTableDynamic<J>> pfReg = tai.tObservation.getPropertyFieldRegistry();
        pfReg.addEntryId(idManager, StaTableDynamic::getId);
        pfReg.addEntryString(epName, t -> t.field(idxName));
        pfReg.addEntryString(epDescription, t -> t.field(idxDesc));
        pfReg.addEntryString(epMetadata, t -> t.field(idxMeta));
        pfReg.addEntryString(epObservationType, t -> t.field(idxObTy));
        pfReg.addEntry(epPhenomenonTime,
                new PropertyFieldRegistry.ConverterTimeValue<>(epPhenomenonTime, t -> t.field(idxPhSt), t -> t.field(idxPhEn)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_START, t -> t.field(idxPhSt)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_END, t -> t.field(idxPhEn)));
        pfReg.addEntry(epResultTime, t -> t.field(idxReTi),
                new PropertyFieldRegistry.ConverterTimeInstant<>(epResultTime, t -> t.field(idxReTi)));
        pfReg.addEntry(epValidTime,
                new PropertyFieldRegistry.ConverterTimeInterval<>(epValidTime, t -> t.field(idxVaSt), t -> t.field(idxVaEn)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_START, t -> t.field(idxVaSt)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_END, t -> t.field(idxVaEn)));
        pfReg.addEntry(epDataQuality, t -> t.field(idxDaQu),
                new PropertyFieldRegistry.ConverterRecordDeflt<>(
                        (StaTableDynamic<J> t, Record tuple, Entity entity, DataSize dataSize) -> {
                            JsonValue resultQuality = Utils.getFieldJsonValue(tuple, (Field<JsonValue>) t.field(idxDaQu));
                            dataSize.increase(resultQuality.getStringLength());
                            entity.setProperty(epDataQuality, resultQuality.getValue());
                        },
                        (table, entity, insertFields) -> {
                            insertFields.put(table.field(idxDaQu), EntityFactories.objectToJson(entity.getProperty(epDataQuality)));
                        },
                        (table, entity, updateFields, message) -> {
                            updateFields.put(table.field(idxDaQu), EntityFactories.objectToJson(entity.getProperty(epDataQuality)));
                            message.addField(epDataQuality);
                        }));
        pfReg.addEntryMap(epParameters, t -> t.field(idxPara));

        pfReg.addEntry(npObservedProperty, t -> t.field(tai.idxObsObsPropertyId), idManager);
        pfReg.addEntry(npObservedProcedure, t -> t.field(tai.idxObsObsProcedureId), idManager);
        pfReg.addEntry(npObserver, t -> t.field(tai.idxObsObserverId), idManager);
        pfReg.addEntry(npHost, t -> t.field(tai.idxObsHostId), idManager);
        pfReg.addEntry(npFoi, t -> t.field(tai.idxObsFoiId), idManager);
    }

    private <J extends Comparable> void registerTableObsProperty(DataType<J> idType, TableCollection<J> tableCollection, IdManager idManager, TablesAndIndices<J> tai) {
        tai.tObservedProperty = new StaTableDynamic<>(DSL.name("obs_prop"), etObservedProperty, idType);
        tableCollection.registerTable(tai.tObservedProperty.getEntityType(), tai.tObservedProperty);
        tai.idxOPropertyId = tai.tObservedProperty.registerIdField("id", idType);
        final int idxName = tai.tObservedProperty.registerField("name", SQLDataType.VARCHAR(100));
        final int idxDesc = tai.tObservedProperty.registerField("description", SQLDataType.CLOB);
        final int idxMeta = tai.tObservedProperty.registerField("metadata", SQLDataType.CLOB);
        final int idxLink = tai.tObservedProperty.registerField("link", SQLDataType.CLOB);

        PropertyFieldRegistry<J, StaTableDynamic<J>> pfReg = tai.tObservedProperty.getPropertyFieldRegistry();
        pfReg.addEntryId(idManager, StaTableDynamic::getId);
        pfReg.addEntryString(epName, t -> t.field(idxName));
        pfReg.addEntryString(epDescription, t -> t.field(idxDesc));
        pfReg.addEntryString(epMetadata, t -> t.field(idxMeta));
        pfReg.addEntryString(epLink, t -> t.field(idxLink));
        pfReg.addEntry(npObservations, t -> t.field(tai.idxOPropertyId), idManager);
    }

    private <J extends Comparable> void registerTableObserver(DataType<J> idType, TableCollection<J> tableCollection, IdManager idManager, TablesAndIndices<J> tai) {
        tai.tObserver = new StaTableDynamic<>(DSL.name("observer"), etObserver, idType);
        tableCollection.registerTable(tai.tObserver.getEntityType(), tai.tObserver);
        tai.idxObserverId = tai.tObserver.registerIdField("id", idType);
        final int idxName = tai.tObserver.registerField("name", SQLDataType.VARCHAR(100));
        final int idxDesc = tai.tObserver.registerField("description", SQLDataType.CLOB);
        final int idxMeta = tai.tObserver.registerField("metadata", SQLDataType.CLOB);
        final int idxLink = tai.tObserver.registerField("link", SQLDataType.CLOB);
        final int idxLoca = tai.tObserver.registerField("location", SQLDataType.CLOB);
        final int idxGeom = tai.tObserver.registerField("geom", DefaultDataType.getDefaultDataType(TYPE_GEOMETRY), new PostGisGeometryBinding());

        PropertyFieldRegistry<J, StaTableDynamic<J>> pfReg = tai.tObserver.getPropertyFieldRegistry();
        pfReg.addEntryId(idManager, StaTableDynamic::getId);
        pfReg.addEntryString(epName, t -> t.field(idxName));
        pfReg.addEntryString(epDescription, t -> t.field(idxDesc));
        pfReg.addEntryString(epMetadata, t -> t.field(idxMeta));
        pfReg.addEntryString(epLink, t -> t.field(idxLink));
        pfReg.addEntry(epLocation,
                new PropertyFieldRegistry.ConverterRecordDeflt<>(
                        (StaTableDynamic<J> t, Record tuple, Entity entity, DataSize dataSize) -> {
                            String locationString = tuple.get(t.field(idxLoca, SQLDataType.CLOB));
                            dataSize.increase(locationString == null ? 0 : locationString.length());
                            entity.setProperty(epLocation, Utils.locationUnknownEncoding(locationString));
                        },
                        (t, entity, insertFields) -> {
                            Object feature = entity.getProperty(epLocation);
                            EntityFactories.insertGeometry(insertFields, t.field(idxLoca, SQLDataType.CLOB), t.field(idxGeom), null, feature);
                        },
                        (t, entity, updateFields, message) -> {
                            Object feature = entity.getProperty(epLocation);
                            EntityFactories.insertGeometry(updateFields, t.field(idxLoca, SQLDataType.CLOB), t.field(idxGeom), null, feature);
                            message.addField(epLocation);
                        }),
                new PropertyFieldRegistry.NFP<>("j", t -> t.field(idxLoca)));
        pfReg.addEntryNoSelect(epLocation, "g", t -> t.field(idxGeom));
        pfReg.addEntry(npDeployments, t -> t.field(tai.idxObserverId), idManager);
        pfReg.addEntry(npObservations, t -> t.field(tai.idxObserverId), idManager);
    }

    private <J extends Comparable> void registerTableProcedure(DataType<J> idType, TableCollection<J> tableCollection, IdManager idManager, TablesAndIndices<J> tai) {
        tai.tObservedProcedure = new StaTableDynamic<>(DSL.name("obs_procedure"), etObservedProcedure, idType);
        tableCollection.registerTable(tai.tObservedProcedure.getEntityType(), tai.tObservedProcedure);
        tai.idxOProcedureId = tai.tObservedProcedure.registerIdField("id", idType);
        final int idxName = tai.tObservedProcedure.registerField("name", SQLDataType.VARCHAR(100));
        final int idxDesc = tai.tObservedProcedure.registerField("description", SQLDataType.CLOB);
        final int idxMeta = tai.tObservedProcedure.registerField("metadata", SQLDataType.CLOB);
        final int idxLink = tai.tObservedProcedure.registerField("link", SQLDataType.CLOB);

        PropertyFieldRegistry<J, StaTableDynamic<J>> pfReg = tai.tObservedProcedure.getPropertyFieldRegistry();
        pfReg.addEntryId(idManager, StaTableDynamic::getId);
        pfReg.addEntryString(epName, t -> t.field(idxName));
        pfReg.addEntryString(epDescription, t -> t.field(idxDesc));
        pfReg.addEntryString(epMetadata, t -> t.field(idxMeta));
        pfReg.addEntryString(epLink, t -> t.field(idxLink));
        pfReg.addEntry(npObservations, t -> t.field(tai.idxOProcedureId), idManager);
    }

    private <J extends Comparable> void registerTableResult(DataType<J> idType, TableCollection<J> tableCollection, IdManager idManager, TablesAndIndices<J> tai) {
        tai.tResult = new StaTableDynamic<>(DSL.name("result_val"), etResult, idType);
        tableCollection.registerTable(tai.tResult.getEntityType(), tai.tResult);
        tai.idxResultId = tai.tResult.registerIdField("id", idType);
        final int idxName = tai.tResult.registerField("name", SQLDataType.VARCHAR(100));
        final int idxDesc = tai.tResult.registerField("description", SQLDataType.CLOB);
        final int idxMeta = tai.tResult.registerField("metadata", SQLDataType.CLOB);
        final int idxPhSt = tai.tResult.registerField("phenomenon_time_start", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxPhEn = tai.tResult.registerField("phenomenon_time_end", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxReTi = tai.tResult.registerField("result_time", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxReTy = tai.tResult.registerField("result_type", SQLDataType.SMALLINT);
        final int idxReNu = tai.tResult.registerField("result_number", SQLDataType.DOUBLE);
        final int idxReSt = tai.tResult.registerField("result_string", SQLDataType.CLOB);
        final int idxReJs = tai.tResult.registerField("result_json", DefaultDataType.getDefaultDataType(TYPE_JSONB), new JsonBinding());
        final int idxReBo = tai.tResult.registerField("result_boolean", SQLDataType.BOOLEAN);
        final int idxDaQu = tai.tResult.registerField("data_quality", DefaultDataType.getDefaultDataType(TYPE_JSONB), new JsonBinding());
        final int idxVaSt = tai.tResult.registerField("valid_time_start", SQLDataType.TIMESTAMPWITHTIMEZONE);
        final int idxVaEn = tai.tResult.registerField("valid_time_end", SQLDataType.TIMESTAMPWITHTIMEZONE);
        tai.idxResObservation = tai.tResult.registerField("obs", idType);

        PropertyFieldRegistry<J, StaTableDynamic<J>> pfReg = tai.tResult.getPropertyFieldRegistry();
        pfReg.addEntryId(idManager, StaTableDynamic::getId);
        pfReg.addEntryString(epName, t -> t.field(idxName));
        pfReg.addEntryString(epDescription, t -> t.field(idxDesc));
        pfReg.addEntryString(epMetadata, t -> t.field(idxMeta));
        pfReg.addEntry(epPhenomenonTime,
                new PropertyFieldRegistry.ConverterTimeValue<>(epPhenomenonTime, t -> t.field(idxPhSt), t -> t.field(idxPhEn)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_START, t -> t.field(idxPhSt)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_END, t -> t.field(idxPhEn)));
        pfReg.addEntry(epResultTime, t -> t.field(idxReTi),
                new PropertyFieldRegistry.ConverterTimeInstant<>(epResultTime, t -> t.field(idxReTi)));
        pfReg.addEntry(epResult,
                new PropertyFieldRegistry.ConverterRecordDeflt<>(
                        (StaTableDynamic<J> table, Record tuple, Entity entity, DataSize dataSize) -> {
                            readResultFromDb(table, tuple, entity, dataSize, idxReTy, idxReSt, idxReNu, idxReBo, idxReJs);
                        },
                        (table, entity, insertFields) -> {
                            handleResult(table, insertFields, entity, idxReTy, idxReSt, idxReNu, idxReBo, idxReJs);
                        },
                        (table, entity, updateFields, message) -> {
                            handleResult(table, updateFields, entity, idxReTy, idxReSt, idxReNu, idxReBo, idxReJs);
                            message.addField(epResult);
                        }),
                new PropertyFieldRegistry.NFP<>("n", t -> t.field(idxReNu)),
                new PropertyFieldRegistry.NFP<>("b", t -> t.field(idxReBo)),
                new PropertyFieldRegistry.NFP<>("s", t -> t.field(idxReSt)),
                new PropertyFieldRegistry.NFP<>("j", t -> t.field(idxReJs)),
                new PropertyFieldRegistry.NFP<>("t", t -> t.field(idxReTy)));
        pfReg.addEntry(epDataQuality, t -> t.field(idxDaQu),
                new PropertyFieldRegistry.ConverterRecordDeflt<>(
                        (StaTableDynamic<J> t, Record tuple, Entity entity, DataSize dataSize) -> {
                            JsonValue resultQuality = Utils.getFieldJsonValue(tuple, (Field<JsonValue>) t.field(idxDaQu));
                            dataSize.increase(resultQuality.getStringLength());
                            entity.setProperty(epDataQuality, resultQuality.getValue());
                        },
                        (table, entity, insertFields) -> {
                            insertFields.put(table.field(idxDaQu), EntityFactories.objectToJson(entity.getProperty(epDataQuality)));
                        },
                        (table, entity, updateFields, message) -> {
                            updateFields.put(table.field(idxDaQu), EntityFactories.objectToJson(entity.getProperty(epDataQuality)));
                            message.addField(epDataQuality);
                        }));
        pfReg.addEntry(epValidTime,
                new PropertyFieldRegistry.ConverterTimeInterval<>(epValidTime, t -> t.field(idxVaSt), t -> t.field(idxVaEn)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_START, t -> t.field(idxVaSt)),
                new PropertyFieldRegistry.NFP<>(KEY_TIME_INTERVAL_END, t -> t.field(idxVaEn)));

    }

    public <J extends Comparable<J>> void handleResult(StaTableDynamic<J> table, Map<Field, Object> record, Entity entity,
            int idxReTy, int idxReSt, int idxReNu, int idxReBo, int idxReJs) {
        Object result = entity.getProperty(epResult);
        if (result instanceof Number) {
            record.put(table.field(idxReTy), ResultType.NUMBER.sqlValue());
            record.put(table.field(idxReSt), result.toString());
            record.put(table.field(idxReNu), ((Number) result).doubleValue());
            record.put(table.field(idxReBo), null);
            record.put(table.field(idxReJs), null);
        } else if (result instanceof Boolean) {
            record.put(table.field(idxReTy), ResultType.BOOLEAN.sqlValue());
            record.put(table.field(idxReSt), result.toString());
            record.put(table.field(idxReBo), result);
            record.put(table.field(idxReNu), null);
            record.put(table.field(idxReJs), null);
        } else if (result instanceof String) {
            record.put(table.field(idxReTy), ResultType.STRING.sqlValue());
            record.put(table.field(idxReSt), result.toString());
            record.put(table.field(idxReNu), null);
            record.put(table.field(idxReBo), null);
            record.put(table.field(idxReJs), null);
        } else {
            record.put(table.field(idxReTy), ResultType.OBJECT_ARRAY.sqlValue());
            record.put(table.field(idxReJs), EntityFactories.objectToJson(result));
            record.put(table.field(idxReSt), null);
            record.put(table.field(idxReNu), null);
            record.put(table.field(idxReBo), null);
        }
    }

    public <J extends Comparable<J>> void readResultFromDb(
            StaTableDynamic<J> table, Record tuple, Entity entity, DataSize dataSize,
            int idxReTy, int idxReSt, int idxReNu, int idxReBo, int idxReJs) {
        Short resultTypeOrd = Utils.getFieldOrNull(tuple, (Field<Short>) table.field(idxReTy));
        if (resultTypeOrd != null) {
            ResultType resultType = ResultType.fromSqlValue(resultTypeOrd);
            switch (resultType) {
                case BOOLEAN:
                    entity.setProperty(epResult, Utils.getFieldOrNull(tuple, table.field(idxReBo)));
                    break;

                case NUMBER:
                    handleNumber(table, tuple, entity, idxReSt, idxReNu);
                    break;

                case OBJECT_ARRAY:
                    JsonValue jsonData = Utils.getFieldJsonValue(tuple, (Field<JsonValue>) table.field(idxReJs));
                    dataSize.increase(jsonData.getStringLength());
                    entity.setProperty(epResult, jsonData.getValue());
                    break;

                case STRING:
                    String stringData = Utils.getFieldOrNull(tuple, (Field<String>) table.field(idxReSt));
                    dataSize.increase(stringData == null ? 0 : stringData.length());
                    entity.setProperty(epResult, stringData);
                    break;

                default:
                    throw new IllegalStateException("Unhandled resultType: " + resultType);
            }
        }
    }

    private <J extends Comparable> void handleNumber(StaTableDynamic<J> table, Record tuple, Entity entity, int idxReSt, int idxReNu) {
        try {
            entity.setProperty(epResult, new BigDecimal(Utils.getFieldOrNull(tuple, (Field<String>) table.field(idxReSt))));
        } catch (NumberFormatException | NullPointerException e) {
            // It was not a Number? Use the double value.
            entity.setProperty(epResult, Utils.getFieldOrNull(tuple, table.field(idxReNu)));
        }
    }

}
