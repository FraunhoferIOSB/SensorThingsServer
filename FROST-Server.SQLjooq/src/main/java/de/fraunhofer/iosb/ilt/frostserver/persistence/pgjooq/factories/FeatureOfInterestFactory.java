/*
 * Copyright (C) 2018 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
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
package de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories;

import de.fraunhofer.iosb.ilt.frostserver.model.EntityChangedMessage;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.model.FeatureOfInterest;
import de.fraunhofer.iosb.ilt.frostserver.model.Observation;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.PostgresPersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.JsonValue;
import static de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories.EntityFactories.CAN_NOT_BE_NULL;
import static de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories.EntityFactories.CHANGED_MULTIPLE_ROWS;
import static de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories.EntityFactories.NO_ID_OR_NOT_FOUND;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.AbstractTableFeatures;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.AbstractTableLocations;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.AbstractTableObservations;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyMain;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.IncompleteEntityException;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.NoSuchEntityException;
import java.util.HashMap;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hylke van der Schaaf
 * @param <J> The type of the ID fields.
 */
public class FeatureOfInterestFactory<J extends Comparable> implements EntityFactory<FeatureOfInterest, J> {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureOfInterestFactory.class);

    private final EntityFactories<J> entityFactories;
    private final AbstractTableFeatures<J> table;

    public FeatureOfInterestFactory(EntityFactories<J> factories, AbstractTableFeatures<J> table) {
        this.entityFactories = factories;
        this.table = table;
    }

    @Override
    public boolean insert(PostgresPersistenceManager<J> pm, FeatureOfInterest foi) throws IncompleteEntityException {
        // No linked entities to check first.
        Map<Field, Object> insert = new HashMap<>();
        insert.put(table.colName, foi.getName());
        insert.put(table.colDescription, foi.getDescription());
        insert.put(table.colProperties, new JsonValue(foi.getProperties()));

        String encodingType = foi.getEncodingType();
        insert.put(table.colEncodingType, encodingType);
        EntityFactories.insertGeometry(insert, table.colFeature, table.colGeom, encodingType, foi.getFeature());

        entityFactories.insertUserDefinedId(pm, insert, table.getId(), foi);

        DSLContext dslContext = pm.getDslContext();
        Record1<J> result = dslContext.insertInto(table)
                .set(insert)
                .returningResult(table.getId())
                .fetchOne();
        J generatedId = result.component1();
        LOGGER.debug("Inserted FeatureOfInterest. Created id = {}.", generatedId);
        foi.setId(entityFactories.idFromObject(generatedId));
        return true;
    }

    @Override
    public EntityChangedMessage update(PostgresPersistenceManager<J> pm, FeatureOfInterest foi, J foiId) throws NoSuchEntityException, IncompleteEntityException {
        DSLContext dslContext = pm.getDslContext();
        Map<Field, Object> update = new HashMap<>();
        EntityChangedMessage message = new EntityChangedMessage();

        updateName(foi, update, message);
        updateDescription(foi, update, message);
        updateProperties(foi, update, message);
        updateFeatureAndEncoding(foi, update, message, dslContext, foiId);

        long count = 0;
        if (!update.isEmpty()) {
            count = dslContext.update(table)
                    .set(update)
                    .where(table.getId().equal(foiId))
                    .execute();
        }
        if (count > 1) {
            LOGGER.error("Updating FeatureOfInterest {} caused {} rows to change!", foiId, count);
            throw new IllegalStateException(CHANGED_MULTIPLE_ROWS);
        }

        linkExistingObservations(foi, pm, dslContext, foiId);

        LOGGER.debug("Updated FeatureOfInterest {}", foiId);
        return message;
    }

    private void updateName(FeatureOfInterest foi, Map<Field, Object> update, EntityChangedMessage message) throws IncompleteEntityException {
        if (foi.isSetName()) {
            if (foi.getName() == null) {
                throw new IncompleteEntityException("name" + CAN_NOT_BE_NULL);
            }
            update.put(table.colName, foi.getName());
            message.addField(EntityPropertyMain.NAME);
        }
    }

    private void updateDescription(FeatureOfInterest foi, Map<Field, Object> update, EntityChangedMessage message) throws IncompleteEntityException {
        if (foi.isSetDescription()) {
            if (foi.getDescription() == null) {
                throw new IncompleteEntityException(EntityPropertyMain.DESCRIPTION.jsonName + CAN_NOT_BE_NULL);
            }
            update.put(table.colDescription, foi.getDescription());
            message.addField(EntityPropertyMain.DESCRIPTION);
        }
    }

    private void updateProperties(FeatureOfInterest foi, Map<Field, Object> update, EntityChangedMessage message) {
        if (foi.isSetProperties()) {
            update.put(table.colProperties, new JsonValue(foi.getProperties()));
            message.addField(EntityPropertyMain.PROPERTIES);
        }
    }

    private void updateFeatureAndEncoding(FeatureOfInterest foi, Map<Field, Object> update, EntityChangedMessage message, DSLContext dslContext, J foiId) throws IncompleteEntityException {
        if (foi.isSetEncodingType() && foi.getEncodingType() == null) {
            throw new IncompleteEntityException("encodingType" + CAN_NOT_BE_NULL);
        }
        if (foi.isSetFeature() && foi.getFeature() == null) {
            throw new IncompleteEntityException("feature" + CAN_NOT_BE_NULL);
        }
        if (foi.isSetEncodingType() && foi.getEncodingType() != null && foi.isSetFeature() && foi.getFeature() != null) {
            String encodingType = foi.getEncodingType();
            update.put(table.colEncodingType, encodingType);
            EntityFactories.insertGeometry(update, table.colFeature, table.colGeom, encodingType, foi.getFeature());
            message.addField(EntityPropertyMain.ENCODINGTYPE);
            message.addField(EntityPropertyMain.FEATURE);
        } else if (foi.isSetEncodingType() && foi.getEncodingType() != null) {
            String encodingType = foi.getEncodingType();
            update.put(table.colEncodingType, encodingType);
            message.addField(EntityPropertyMain.ENCODINGTYPE);
        } else if (foi.isSetFeature() && foi.getFeature() != null) {
            String encodingType = dslContext.select(table.colEncodingType)
                    .from(table)
                    .where(table.getId().eq(foiId))
                    .fetchOne(table.colEncodingType);
            Object parsedObject = EntityFactories.reParseGeometry(encodingType, foi.getFeature());
            EntityFactories.insertGeometry(update, table.colFeature, table.colGeom, encodingType, parsedObject);
            message.addField(EntityPropertyMain.FEATURE);
        }
    }

    private void linkExistingObservations(FeatureOfInterest foi, PostgresPersistenceManager<J> pm, DSLContext dslContext, J foiId) throws NoSuchEntityException {
        // Link existing Observations to the FeatureOfInterest.
        for (Observation o : foi.getObservations()) {
            if (o.getId() == null || !entityFactories.entityExists(pm, o)) {
                throw new NoSuchEntityException(EntityType.OBSERVATION.entityName + NO_ID_OR_NOT_FOUND);
            }
            J obsId = (J) o.getId().getValue();
            AbstractTableObservations<J> obsTable = entityFactories.tableCollection.getTableObservations();
            long oCount = dslContext.update(obsTable)
                    .set(obsTable.getFeatureId(), foiId)
                    .where(obsTable.getId().eq(obsId))
                    .execute();
            if (oCount > 0) {
                LOGGER.debug("Assigned FeatureOfInterest {} to Observation {}.", foiId, obsId);
            }
        }
    }

    @Override
    public void delete(PostgresPersistenceManager<J> pm, J entityId) throws NoSuchEntityException {
        long count = pm.getDslContext()
                .delete(table)
                .where(table.getId().eq(entityId))
                .execute();
        if (count == 0) {
            throw new NoSuchEntityException("FeatureOfInterest " + entityId + " not found.");
        }
        // Delete references to the FoI in the Locations table.
        AbstractTableLocations<J> tLoc = entityFactories.tableCollection.getTableLocations();
        pm.getDslContext()
                .update(tLoc)
                .set(tLoc.getGenFoiId(), (J) null)
                .where(tLoc.getGenFoiId().eq(entityId))
                .execute();
    }

}
