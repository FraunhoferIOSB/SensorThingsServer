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
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables;

import de.fraunhofer.iosb.ilt.frostserver.model.DefaultEntity;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityChangedMessage;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Entity;
import de.fraunhofer.iosb.ilt.frostserver.model.core.EntitySet;
import de.fraunhofer.iosb.ilt.frostserver.model.core.EntitySetImpl;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.PostgresPersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.JsonBinding;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.JsonValue;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories.EntityFactories;
import static de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories.EntityFactories.CHANGED_MULTIPLE_ROWS;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.fieldwrapper.JsonFieldFactory;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.relations.Relation;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.relations.RelationManyToMany;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.relations.RelationOneToMany;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.DataSize;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.PropertyFieldRegistry;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.PropertyFieldRegistry.PropertyFields;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.QueryState;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.Utils;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyCustomSelect;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyMain;
import de.fraunhofer.iosb.ilt.frostserver.property.NavigationPropertyMain;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.IncompleteEntityException;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.NoSuchEntityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.impl.TableImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 * @param <J> The type of the ID fields.
 * @param <T> The exact type of the implementing class.
 */
public abstract class StaTableAbstract<J extends Comparable, T extends StaMainTable<J, T>> extends TableImpl<Record> implements StaMainTable<J, T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaTableAbstract.class.getName());

    public static final String TYPE_JSONB = "\"pg_catalog\".\"jsonb\"";
    public static final String TYPE_GEOMETRY = "\"public\".\"geometry\"";

    private transient TableCollection<J> tables;
    private transient Map<String, Relation<J>> relations;
    protected PropertyFieldRegistry<J, T> pfReg;

    private final DataType<J> idType;

    protected StaTableAbstract(DataType<J> idType, Name alias, StaTableAbstract<J, T> aliased) {
        super(alias, null, aliased);
        this.idType = idType;
        if (aliased == null) {
            pfReg = new PropertyFieldRegistry<>(getThis());
        } else {
            setTables(aliased.getTables());
            pfReg = new PropertyFieldRegistry<>(getThis(), aliased.getPropertyFieldRegistry());
        }
    }

    public DataType<J> getIdType() {
        return idType;
    }

    protected void registerRelation(Relation<J> relation) {
        if (relations == null) {
            relations = new HashMap<>();
        }
        relations.put(relation.getName(), relation);
    }

    @Override
    public Relation findRelation(String name) {
        if (relations == null) {
            initRelations();
        }
        return relations.get(name);
    }

    @Override
    public PropertyFieldRegistry<J, T> getPropertyFieldRegistry() {
        if (pfReg == null) {
            pfReg = new PropertyFieldRegistry<>(getThis());
        }
        return pfReg;
    }

    @Override
    public Entity entityFromQuery(Record tuple, QueryState<J, T> state, DataSize dataSize) {
        Entity newEntity = new DefaultEntity(getEntityType());
        for (PropertyFields<T> sp : state.getSelectedProperties()) {
            sp.converter.convert(state.getMainTable(), tuple, newEntity, dataSize);
        }
        return newEntity;
    }

    @Override
    public boolean insertIntoDatabase(PostgresPersistenceManager<J> pm, Entity entity) throws NoSuchEntityException, IncompleteEntityException {
        final T thisTable = getThis();
        EntityFactories<J> entityFactories = pm.getEntityFactories();
        EntityType entityType = entity.getEntityType();
        Map<Field, Object> insertFields = new HashMap<>();

        for (NavigationPropertyMain<Entity> np : entityType.getNavigationEntities()) {
            if (entity.isSetProperty(np)) {
                Entity ne = entity.getProperty(np);
                entityFactories.entityExistsOrCreate(pm, ne);
                PropertyFields<T> registry = pfReg.getSelectFieldsForProperty(np);
                registry.converter.convert(thisTable, entity, insertFields);
            }
        }

        entityFactories.insertUserDefinedId(pm, insertFields, this.getId(), entity);

        Set<EntityPropertyMain> entityProperties = entityType.getEntityProperties();
        for (EntityPropertyMain ep : entityProperties) {
            if (ep.equals(EntityPropertyMain.ID)) {
                // ID has already been dealt with above.
                continue;
            }
            if (entity.isSetProperty(ep)) {
                pfReg.getSelectFieldsForProperty(ep).converter.convert(thisTable, entity, insertFields);
            }
        }

        DSLContext dslContext = pm.getDslContext();
        Record1<J> result = dslContext.insertInto(thisTable)
                .set(insertFields)
                .returningResult(thisTable.getId())
                .fetchOne();
        J entityId = result.component1();
        LOGGER.debug("Inserted Entity. Created id = {}.", entityId);
        entity.setId(entityFactories.idFromObject(entityId));

        for (NavigationPropertyMain<EntitySet> np : entityType.getNavigationSets()) {
            if (entity.isSetProperty(np)) {
                updateNavigationPropertySet(entity, entity.getProperty(np), pm, true);
            }
        }

        return true;
    }

    /**
     * Links the entities in the given Set to the given Entity. Optionally
     * creates the linked entities.
     *
     * @param entity The entity to link to
     * @param linkedSet The set of entities to link to the given entity
     * @param pm The PersistenceManager to use for queries
     * @param forInsert Flag indicating the update is for a newly inserted
     * entity, and new entities can be created.
     *
     * @throws IncompleteEntityException
     * @throws NoSuchEntityException
     * @throws IllegalStateException
     */
    protected void updateNavigationPropertySet(Entity entity, EntitySet linkedSet, PostgresPersistenceManager<J> pm, boolean forInsert) throws IncompleteEntityException, NoSuchEntityException {
        J entityId = (J) entity.getId().getValue();
        EntityType entityType = getEntityType();
        NavigationPropertyMain npToThis = NavigationPropertyMain.forName(entityType.entityName);
        NavigationPropertyMain npToThiss = NavigationPropertyMain.forName(entityType.plural);
        EntityFactories<J> entityFactories = pm.getEntityFactories();

        final Set<NavigationPropertyMain> linkedSetNavigationProperties = linkedSet.getEntityType().getNavigationProperties();
        Relation relation = findRelation(linkedSet.getEntityType().entityName);
        RelationManyToMany relationManyToMany = null;
        if (linkedSetNavigationProperties.contains(npToThis)) {
            // One to Many
        } else if (linkedSetNavigationProperties.contains(npToThiss)) {
            if (relation instanceof RelationManyToMany) {
                relationManyToMany = (RelationManyToMany) relation;
            } else {
                LOGGER.error("Target type ({}) and this ({}) not linked by RelationManyToMany.", linkedSet.getEntityType(), entityType);
                throw new IllegalStateException("Many-to-many relation not linked by RelationManyToMany");
            }
        } else {
            LOGGER.error("Target type ({}) does not actually link to this ({}).", linkedSet.getEntityType(), entityType);
            throw new IllegalStateException("Target type (" + linkedSet.getEntityType() + ") does not actually link to this (" + entityType + ").");
        }
        for (Entity child : linkedSet) {
            if (relationManyToMany == null) {
                if (entityFactories.entityExists(pm, child)) {
                    ((RelationOneToMany) relation).link(pm, entityId, (J) child.getId().getValue());
                } else if (forInsert) {
                    child.setProperty(npToThis, entity);
                    child.complete();
                    pm.insert(child);
                } else {
                    throw new NoSuchEntityException("Linked Entity with no id.");
                }
            } else {
                if (forInsert) {
                    entityFactories.entityExistsOrCreate(pm, child);
                } else if (!entityFactories.entityExists(pm, child)) {
                    throw new NoSuchEntityException("Linked Entity with no id.");
                }
                relationManyToMany.link(pm, entityId, (J) child.getId().getValue());
            }
        }
    }

    @Override
    public EntityChangedMessage updateInDatabase(PostgresPersistenceManager<J> pm, Entity entity, J entityId) throws NoSuchEntityException, IncompleteEntityException {
        final T thisTable = getThis();
        EntityFactories<J> entityFactories = pm.getEntityFactories();
        EntityType entityType = entity.getEntityType();
        Map<Field, Object> updateFields = new HashMap<>();
        EntityChangedMessage message = new EntityChangedMessage();

        for (NavigationPropertyMain<Entity> np : entityType.getNavigationEntities()) {
            if (entity.isSetProperty(np)) {
                Entity ne = entity.getProperty(np);
                if (!entityFactories.entityExists(pm, ne)) {
                    throw new NoSuchEntityException("Linked " + ne.getEntityType() + " not found.");
                }
                PropertyFields<T> registry = pfReg.getSelectFieldsForProperty(np);
                registry.converter.convert(thisTable, entity, updateFields, message);
            }
        }

        Set<EntityPropertyMain> entityProperties = entityType.getEntityProperties();
        for (EntityPropertyMain ep : entityProperties) {
            if (ep.equals(EntityPropertyMain.ID)) {
                // ID can not be changed.
                continue;
            }
            if (entity.isSetProperty(ep)) {
                pfReg.getSelectFieldsForProperty(ep).converter.convert(thisTable, entity, updateFields, message);
            }
        }

        DSLContext dslContext = pm.getDslContext();
        long count = 0;
        if (!updateFields.isEmpty()) {
            count = dslContext.update(thisTable)
                    .set(updateFields)
                    .where(thisTable.getId().equal(entityId))
                    .execute();
        }
        if (count > 1) {
            LOGGER.error("Updating Datastream {} caused {} rows to change!", entityId, count);
            throw new IllegalStateException(CHANGED_MULTIPLE_ROWS);
        }

        for (NavigationPropertyMain<EntitySet> np : entityType.getNavigationSets()) {
            if (entity.isSetProperty(np)) {
                updateNavigationPropertySet(entity, entity.getProperty(np), pm, false);
            }
        }
        return message;
    }

    @Override
    public void delete(PostgresPersistenceManager<J> pm, J entityId) throws NoSuchEntityException {
        final T thisTable = getThis();
        long count = pm.getDslContext()
                .delete(thisTable)
                .where(thisTable.getId().eq(entityId))
                .execute();
        if (count == 0) {
            throw new NoSuchEntityException("Entity of type " + getEntityType() + " with id " + entityId + " not found.");
        }
        LOGGER.debug("Deleted {} Entities of type {}", count, getEntityType());
    }

    @Override
    public EntitySet newSet() {
        return new EntitySetImpl(getEntityType());
    }

    @Override
    public abstract StaTableAbstract<J, T> as(Name as);

    @Override
    public abstract StaTableAbstract<J, T> as(String alias);

    public final TableCollection<J> getTables() {
        return tables;
    }

    public final void setTables(TableCollection<J> tables) {
        this.tables = tables;
    }

    @Override
    public PropertyFields<T> handleEntityPropertyCustomSelect(final EntityPropertyCustomSelect epCustomSelect) {
        final EntityPropertyMain mainEntityProperty = epCustomSelect.getMainEntityProperty();
        if (mainEntityProperty == EntityPropertyMain.PROPERTIES) {
            PropertyFields<T> mainPropertyFields = pfReg.getSelectFieldsForProperty(mainEntityProperty);

            final Field mainField = mainPropertyFields.fields.values().iterator().next().get(getThis());
            JsonFieldFactory jsonFactory = jsonFieldFromPath(mainField, epCustomSelect);

            return propertyFieldForJsonField(jsonFactory, epCustomSelect);
        }
        return null;
    }

    protected static JsonFieldFactory jsonFieldFromPath(final Field mainField, final EntityPropertyCustomSelect epCustomSelect) {
        JsonFieldFactory jsonFactory = new JsonFieldFactory(mainField);
        for (String pathItem : epCustomSelect.getSubPath()) {
            jsonFactory.addToPath(pathItem);
        }
        return jsonFactory;
    }

    protected PropertyFields<T> propertyFieldForJsonField(final JsonFieldFactory jsonFactory, final EntityPropertyCustomSelect epCustomSelect) {
        final Field deepField = jsonFactory.build().getJsonExpression();
        PropertyFields<T> pfs = new PropertyFields<>(
                epCustomSelect,
                new PropertyFieldRegistry.ConverterRecordDeflt<>(
                        (tbl, tuple, entity, dataSize) -> {
                            final JsonValue jsonValue = JsonBinding.getConverterInstance().from(tuple.get(deepField));
                            dataSize.increase(jsonValue.getStringLength());
                            Object value = jsonValue.getValue(Utils.TYPE_OBJECT);
                            epCustomSelect.setOn(entity, value);
                        }, null, null));
        pfs.addField("1", t -> deepField);
        return pfs;
    }
}
