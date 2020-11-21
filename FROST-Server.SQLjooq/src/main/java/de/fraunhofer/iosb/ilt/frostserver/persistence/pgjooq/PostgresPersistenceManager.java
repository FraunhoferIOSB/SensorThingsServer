/*
 * Copyright (C) 2017 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
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
package de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import de.fraunhofer.iosb.ilt.frostserver.json.deserialize.JsonReader;
import de.fraunhofer.iosb.ilt.frostserver.json.serialize.JsonWriter;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityChangedMessage;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Entity;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Id;
import de.fraunhofer.iosb.ilt.frostserver.path.PathElement;
import de.fraunhofer.iosb.ilt.frostserver.path.PathElementEntity;
import de.fraunhofer.iosb.ilt.frostserver.path.PathElementEntitySet;
import de.fraunhofer.iosb.ilt.frostserver.path.ResourcePath;
import de.fraunhofer.iosb.ilt.frostserver.persistence.AbstractPersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.IdManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories.EntityFactories;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.StaMainTable;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableCollection;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpDatastreams;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpFeatures;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpHistLocations;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpLocations;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpMultiDatastreams;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpObsProperties;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpObservations;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpSensors;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpThings;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.ConnectionUtils;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.ConnectionUtils.ConnectionWrapper;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.DataSize;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.LiquibaseHelper;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyMain;
import de.fraunhofer.iosb.ilt.frostserver.query.Query;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import de.fraunhofer.iosb.ilt.frostserver.settings.Settings;
import static de.fraunhofer.iosb.ilt.frostserver.util.Constants.UTC;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.IncompleteEntityException;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.NoSuchEntityException;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.UpgradeFailedException;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Delete;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.ResultQuery;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author scf
 * @param <J> The type of the ID fields.
 */
public abstract class PostgresPersistenceManager<J extends Comparable> extends AbstractPersistenceManager {

    public static final Instant DATETIME_MAX_INSTANT = Instant.parse("9999-12-30T23:59:59.999Z");
    // jooq fails when year field is not 4 digits long: https://github.com/jOOQ/jOOQ/issues/8178
    // TODO: Change back to -4000 when it is fixed.
    public static final Instant DATETIME_MIN_INSTANT = Instant.parse("0001-01-02T00:00:00.000Z");
    public static final OffsetDateTime DATETIME_MAX = OffsetDateTime.ofInstant(DATETIME_MAX_INSTANT, UTC);
    public static final OffsetDateTime DATETIME_MIN = OffsetDateTime.ofInstant(DATETIME_MIN_INSTANT, UTC);

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresPersistenceManager.class.getName());

    private static boolean initialised = false;

    private final IdManager idManager;
    private final TableCollection<J> tableCollection;
    private EntityFactories<J> entityFactories;

    private CoreSettings settings;
    private ConnectionWrapper connectionProvider;
    private DSLContext dslContext;

    public PostgresPersistenceManager(IdManager idManager, TableCollection<J> tableCollection) {
        this.idManager = idManager;
        this.tableCollection = tableCollection;
    }

    @Override
    public void init(CoreSettings settings) {
        this.settings = settings;
        Settings customSettings = settings.getPersistenceSettings().getCustomSettings();
        connectionProvider = new ConnectionWrapper(customSettings);
        entityFactories = new EntityFactories(idManager, tableCollection);
    }

    private void init() {
        if (initialised) {
            return;
        }
        synchronized (tableCollection) {
            if (!initialised) {
                IdGenerationHandler.setIdGenerationMode(settings.getPersistenceSettings().getIdGenerationMode());
                DataType<J> idType = tableCollection.getIdType();
                // TODO: Move to plugins
                tableCollection.registerTable(EntityType.DATASTREAM, TableImpDatastreams.getInstance(idType));
                tableCollection.registerTable(EntityType.FEATURE_OF_INTEREST, TableImpFeatures.getInstance(idType));
                tableCollection.registerTable(EntityType.HISTORICAL_LOCATION, TableImpHistLocations.getInstance(idType));
                tableCollection.registerTable(EntityType.LOCATION, TableImpLocations.getInstance(idType));
                tableCollection.registerTable(EntityType.MULTI_DATASTREAM, TableImpMultiDatastreams.getInstance(idType));
                tableCollection.registerTable(EntityType.OBSERVATION, TableImpObservations.getInstance(idType));
                tableCollection.registerTable(EntityType.OBSERVED_PROPERTY, TableImpObsProperties.getInstance(idType));
                tableCollection.registerTable(EntityType.SENSOR, TableImpSensors.getInstance(idType));
                tableCollection.registerTable(EntityType.THING, TableImpThings.getInstance(idType));
                for (StaMainTable<J, ?> table : tableCollection.getAllTables()) {
                    table.initProperties(entityFactories);
                    table.initRelations();
                }
                initialised = true;
            }
        }
    }

    @Override
    public CoreSettings getCoreSettings() {
        return settings;
    }

    @Override
    public IdManager getIdManager() {
        return idManager;
    }

    public TableCollection<J> getTableCollection() {
        return tableCollection;
    }

    public EntityFactories<J> getEntityFactories() {
        return entityFactories;
    }

    public abstract IdGenerationHandler createIdGenerationHanlder(Entity e);

    public abstract String getLiquibaseChangelogFilename();

    public DSLContext getDslContext() {
        if (dslContext == null) {
            dslContext = DSL.using(connectionProvider.get(), SQLDialect.POSTGRES);
        }
        return dslContext;
    }

    public ConnectionWrapper getConnectionProvider() {
        return connectionProvider;
    }

    @Override
    public boolean validatePath(ResourcePath path) {
        init();
        PathElement element = path.getIdentifiedElement();
        if (element == null) {
            return true;
        }
        ResourcePath tempPath = new ResourcePath();
        int idCount = 0;
        while (element != null) {
            if (element instanceof PathElementEntity) {
                PathElementEntity entityPathElement = (PathElementEntity) element;
                Id id = entityPathElement.getId();
                if (id != null) {
                    idCount++;
                    if (!getEntityFactories().entityExists(this, entityPathElement.getEntityType(), id)) {
                        return false;
                    }
                }
            }
            tempPath.addPathElement(0, element);
            element = element.getParent();
        }
        if (idCount < 2) {
            return true;
        }
        QueryBuilder<J> psb = new QueryBuilder<>(this, settings, getTableCollection());
        ResultQuery<Record1<Integer>> query = psb
                .forPath(tempPath)
                .buildCount();
        Integer count = query.fetchOne().component1();
        return count == 1;
    }

    @Override
    public Entity get(EntityType entityType, Id id) {
        return get(entityType, id, false, null);
    }

    public Entity get(EntityType entityType, Id id, Query query) {
        return get(entityType, id, false, query);
    }

    /**
     * Gets the requested entity and locks the row for update. End the
     * transaction quickly to release the lock.
     *
     * @param entityType The type of entity to fetch.
     * @param id The ID of the entity to fetch.
     * @param forUpdate if true, lock the entities row for update.
     * @return the requested entity.
     */
    private Entity get(EntityType entityType, Id id, boolean forUpdate, Query query) {
        init();
        QueryBuilder<J> psb = new QueryBuilder<>(this, settings, getTableCollection());
        ResultQuery sqlQuery = psb.forTypeAndId(entityType, id)
                .usingQuery(query)
                .forUpdate(forUpdate)
                .buildSelect();

        Record record = sqlQuery.fetchAny();
        if (record == null) {
            return null;
        }
        return psb.getQueryState().entityFromQuery(record, new DataSize());
    }

    @Override
    public Object get(ResourcePath path, Query query) {
        init();
        PathElement lastElement = path.getLastElement();
        if (!(lastElement instanceof PathElementEntity) && !(lastElement instanceof PathElementEntitySet)) {
            if (!query.getExpand().isEmpty()) {
                LOGGER.warn("Expand only allowed on Entities or EntitySets. Not on {}!", lastElement.getClass());
                query.getExpand().clear();
            }
            if (!query.getSelect().isEmpty()) {
                LOGGER.warn("Select only allowed on Entities or EntitySets. Not on {}!", lastElement.getClass());
                query.getSelect().clear();
            }
        }

        QueryBuilder<J> psb = new QueryBuilder<>(this, settings, getTableCollection())
                .forPath(path)
                .usingQuery(query);

        ResultBuilder<J> entityCreator = new ResultBuilder<>(this, path, query, psb);
        lastElement.visit(entityCreator);
        Object entity = entityCreator.getEntity();

        if (path.isEntityProperty() && entity instanceof Map) {
            Map map = (Map) entity;
            if (map.get(entityCreator.getEntityName()) == null) {
                return null;
            }
        }
        if (path.isValue() && entity instanceof Map) {
            Map map = (Map) entity;
            entity = map.get(entityCreator.getEntityName());
        }

        return entity;
    }

    @Override
    public boolean doInsert(Entity entity) throws NoSuchEntityException, IncompleteEntityException {
        init();
        StaMainTable<J, ?> table = getTableCollection().getTableForType(entity.getEntityType());
        return table.insertIntoDatabase(this, entity);
    }

    @Override
    public EntityChangedMessage doUpdate(PathElementEntity pathElement, Entity entity) throws NoSuchEntityException, IncompleteEntityException {
        init();
        EntityFactories<J> ef = getEntityFactories();

        entity.setId(pathElement.getId());
        J id = (J) pathElement.getId().getValue();
        if (!ef.entityExists(this, entity)) {
            throw new NoSuchEntityException("No entity of type " + pathElement.getEntityType() + " with id " + id);
        }

        StaMainTable<J, ?> table = getTableCollection().getTableForType(entity.getEntityType());
        return table.updateInDatabase(this, entity, id);
    }

    @Override
    public EntityChangedMessage doUpdate(PathElementEntity pathElement, JsonPatch patch) throws NoSuchEntityException, IncompleteEntityException {
        init();
        final EntityType entityType = pathElement.getEntityType();
        final Id id = pathElement.getId();

        Entity original = get(entityType, id, true, null);
        if (original == null) {
            throw new IllegalArgumentException("No Entity of type " + entityType.entityName + " with id " + id);
        }
        original.setEntityPropertiesSet(false, false);
        JsonNode originalNode = JsonWriter.getObjectMapper().valueToTree(original);
        LOGGER.trace("Old {}", originalNode);
        JsonNode newNode;
        try {
            newNode = patch.apply(originalNode);
        } catch (JsonPatchException ex) {
            throw new IllegalArgumentException("Failed to apply patch.", ex);
        }
        LOGGER.trace("New {}", newNode);
        Entity newEntity;
        try {
            JsonReader entityParser = new JsonReader(getIdManager().getIdClass());
            newEntity = entityParser.parseEntity(original.getEntityType(), newNode.toString());
            // Make sure the id is not changed by the patch.
            newEntity.setId(id);
        } catch (IOException ex) {
            LOGGER.error("Failed to parse JSON after patch.");
            throw new IllegalArgumentException("Exception", ex);
        }

        EntityChangedMessage message = new EntityChangedMessage();
        newEntity.setEntityPropertiesSet(original, message);
        if (message.getEpFields().isEmpty() && message.getNpFields().isEmpty()) {
            LOGGER.warn("Patch did not change anything.");
            throw new IllegalArgumentException("Patch did not change anything.");
        }

        StaMainTable<J, ?> table = getTableCollection().getTableForType(entityType);
        table.updateInDatabase(this, newEntity, (J) id.getValue());

        message.setEntity(newEntity);
        message.setEventType(EntityChangedMessage.Type.UPDATE);
        return message;
    }

    @Override
    public boolean doDelete(PathElementEntity pathElement) throws NoSuchEntityException {
        init();
        EntityType type = pathElement.getEntityType();
        StaMainTable<J, ?> table = getTableCollection().getTableForType(type);
        table.delete(this, (J) pathElement.getId().getValue());
        return true;
    }

    @Override
    public void doDelete(ResourcePath path, Query query) {
        init();
        query.clearSelect();
        query.addSelect(Arrays.asList(EntityPropertyMain.ID));
        QueryBuilder<J> psb = new QueryBuilder<>(this, settings, getTableCollection())
                .forPath(path)
                .usingQuery(query);

        Delete sqlDelete = psb.buildDelete((PathElementEntitySet) path.getLastElement());

        long rowCount = sqlDelete.execute();
        LOGGER.debug("Deleted {} rows using query {}", rowCount, sqlDelete);
    }

    @Override
    protected boolean doCommit() {
        return connectionProvider.doCommit();
    }

    @Override
    protected boolean doRollback() {
        return connectionProvider.doRollback();
    }

    @Override
    protected boolean doClose() {
        return connectionProvider.doClose();
    }

    @Override
    public String checkForUpgrades() {
        try {
            Settings customSettings = settings.getPersistenceSettings().getCustomSettings();
            Connection connection = ConnectionUtils.getConnection("FROST-Source", customSettings);
            String liquibaseChangelogFilename = getLiquibaseChangelogFilename();
            return LiquibaseHelper.checkForUpgrades(connection, liquibaseChangelogFilename);
        } catch (SQLException ex) {
            LOGGER.error("Could not initialise database.", ex);
            return "Failed to initialise database:\n"
                    + ex.getLocalizedMessage()
                    + "\n";
        }
    }

    @Override
    public boolean doUpgrades(Writer out) throws UpgradeFailedException, IOException {
        Settings customSettings = settings.getPersistenceSettings().getCustomSettings();
        Connection connection;
        try {
            connection = ConnectionUtils.getConnection("FROST-Source", customSettings);
        } catch (SQLException ex) {
            LOGGER.error("Could not initialise database.", ex);
            out.append("Failed to initialise database:\n");
            out.append(ex.getLocalizedMessage());
            out.append("\n");
            return false;
        }
        String liquibaseChangelogFilename = getLiquibaseChangelogFilename();
        return LiquibaseHelper.doUpgrades(connection, liquibaseChangelogFilename, out);
    }

}
