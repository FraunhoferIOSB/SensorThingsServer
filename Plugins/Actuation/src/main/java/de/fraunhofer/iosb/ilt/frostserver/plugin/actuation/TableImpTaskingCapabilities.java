package de.fraunhofer.iosb.ilt.frostserver.plugin.actuation;

import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.persistence.IdManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.JsonBinding;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.JsonValue;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories.EntityFactories;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.fieldwrapper.JsonFieldFactory;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.relations.RelationOneToMany;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.StaTableAbstract;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableImpThings;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.PropertyFieldRegistry.PropertyFields;
import static de.fraunhofer.iosb.ilt.frostserver.plugin.actuation.PluginActuation.ACTUATOR;
import static de.fraunhofer.iosb.ilt.frostserver.plugin.actuation.PluginActuation.EP_TASKINGPARAMETERS;
import static de.fraunhofer.iosb.ilt.frostserver.plugin.actuation.PluginActuation.NP_ACTUATOR;
import static de.fraunhofer.iosb.ilt.frostserver.plugin.actuation.PluginActuation.NP_TASKINGCAPABILITIES;
import static de.fraunhofer.iosb.ilt.frostserver.plugin.actuation.PluginActuation.NP_TASKS;
import static de.fraunhofer.iosb.ilt.frostserver.plugin.actuation.PluginActuation.TASK;
import static de.fraunhofer.iosb.ilt.frostserver.plugin.actuation.PluginActuation.TASKING_CAPABILITY;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyCustomSelect;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyMain;
import de.fraunhofer.iosb.ilt.frostserver.property.NavigationPropertyMain;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.impl.SQLDataType;

public class TableImpTaskingCapabilities<J extends Comparable> extends StaTableAbstract<J, TableImpTaskingCapabilities<J>> {

    private static final long serialVersionUID = -1460005950;

    private static TableImpTaskingCapabilities INSTANCE;
    private static DataType INSTANCE_ID_TYPE;

    public static <J extends Comparable> TableImpTaskingCapabilities<J> getInstance(DataType<J> idType) {
        if (INSTANCE == null) {
            INSTANCE_ID_TYPE = idType;
            INSTANCE = new TableImpTaskingCapabilities(INSTANCE_ID_TYPE);
            return INSTANCE;
        }
        if (INSTANCE_ID_TYPE.equals(idType)) {
            return INSTANCE;
        }
        return new TableImpTaskingCapabilities<>(idType);
    }

    /**
     * The column <code>public.TASKINGCAPABILITIES.DESCRIPTION</code>.
     */
    public final TableField<Record, String> colDescription = createField(DSL.name("DESCRIPTION"), SQLDataType.CLOB, this);

    /**
     * The column <code>public.TASKINGCAPABILITIES.NAME</code>.
     */
    public final TableField<Record, String> colName = createField(DSL.name("NAME"), SQLDataType.CLOB.defaultValue(DSL.field("'no name'::text", SQLDataType.CLOB)), this);

    /**
     * The column <code>public.TASKINGCAPABILITIES.PROPERTIES</code>.
     */
    public final TableField<Record, JsonValue> colProperties = createField(DSL.name("PROPERTIES"), DefaultDataType.getDefaultDataType(TYPE_JSONB), this, "", new JsonBinding());

    /**
     * The column <code>public.TASKINGCAPABILITIES.TASKING_PARAMETERS</code>.
     */
    public final TableField<Record, JsonValue> colTaskingParameters = createField(DSL.name("TASKING_PARAMETERS"), DefaultDataType.getDefaultDataType(TYPE_JSONB), this, "", new JsonBinding());

    /**
     * The column <code>public.TASKINGCAPABILITIES.ID</code>.
     */
    public final TableField<Record, J> colId = createField(DSL.name("ID"), getIdType(), this);

    /**
     * The column <code>public.TASKINGCAPABILITIES.ACTUATOR_ID</code>.
     */
    public final TableField<Record, J> colActuatorId = createField(DSL.name("ACTUATOR_ID"), getIdType(), this);

    /**
     * The column <code>public.TASKINGCAPABILITIES.THING_ID</code>.
     */
    public final TableField<Record, J> colThingId = createField(DSL.name("THING_ID"), getIdType(), this);

    /**
     * Create a <code>public.TASKINGCAPABILITIES</code> table reference
     */
    private TableImpTaskingCapabilities(DataType<J> idType) {
        super(idType, DSL.name("TASKINGCAPABILITIES"), null);
    }

    private TableImpTaskingCapabilities(Name alias, TableImpTaskingCapabilities<J> aliased) {
        super(aliased.getIdType(), alias, aliased);
    }

    @Override
    public void initRelations() {
        registerRelation(new RelationOneToMany<>(this, TableImpThings.getInstance(getIdType()), EntityType.THING)
                .setSourceFieldAccessor(TableImpTaskingCapabilities::getThingId)
                .setTargetFieldAccessor(TableImpThings::getId)
        );

        registerRelation(new RelationOneToMany<>(this, TableImpActuators.getInstance(getIdType()), ACTUATOR)
                .setSourceFieldAccessor(TableImpTaskingCapabilities::getActuatorId)
                .setTargetFieldAccessor(TableImpActuators::getId)
        );

        registerRelation(new RelationOneToMany<>(this, TableImpTasks.getInstance(getIdType()), TASK, true)
                .setSourceFieldAccessor(TableImpTaskingCapabilities::getId)
                .setTargetFieldAccessor(TableImpTasks::getTaskingCapabilityId)
        );

        // We add the relation to us to the Things table.
        TableImpThings<J> thingsTable = TableImpThings.getInstance(getIdType());
        thingsTable.registerRelation(new RelationOneToMany<>(thingsTable, TableImpTaskingCapabilities.getInstance(getIdType()), TASKING_CAPABILITY, true)
                .setSourceFieldAccessor(TableImpThings::getId)
                .setTargetFieldAccessor(TableImpTaskingCapabilities::getThingId)
        );

    }

    @Override
    public void initProperties(final EntityFactories<J> entityFactories) {
        final IdManager idManager = entityFactories.idManager;
        pfReg.addEntryId(idManager, TableImpTaskingCapabilities::getId);
        pfReg.addEntryString(EntityPropertyMain.NAME, table -> table.colName);
        pfReg.addEntryString(EntityPropertyMain.DESCRIPTION, table -> table.colDescription);
        pfReg.addEntryMap(EntityPropertyMain.PROPERTIES, table -> table.colProperties);
        pfReg.addEntryMap(EP_TASKINGPARAMETERS, table -> table.colTaskingParameters);
        pfReg.addEntry(NP_ACTUATOR, TableImpTaskingCapabilities::getActuatorId, idManager);
        pfReg.addEntry(NavigationPropertyMain.THING, TableImpTaskingCapabilities::getThingId, idManager);
        pfReg.addEntry(NP_TASKS, TableImpTaskingCapabilities::getId, idManager);

        // We register a navigationProperty on the Things table.
        TableImpThings<J> thingsTable = TableImpThings.getInstance(getIdType());
        thingsTable.getPropertyFieldRegistry()
                .addEntry(NP_TASKINGCAPABILITIES, TableImpThings::getId, idManager);

    }

    @Override
    public EntityType getEntityType() {
        return TASKING_CAPABILITY;
    }

    @Override
    public TableField<Record, J> getId() {
        return colId;
    }

    public TableField<Record, J> getActuatorId() {
        return colActuatorId;
    }

    public TableField<Record, J> getThingId() {
        return colThingId;
    }

    @Override
    public TableImpTaskingCapabilities<J> as(Name alias) {
        return new TableImpTaskingCapabilities<>(alias, this);
    }

    @Override
    public TableImpTaskingCapabilities<J> as(String alias) {
        return new TableImpTaskingCapabilities<>(DSL.name(alias), this);
    }

    @Override
    public PropertyFields<TableImpTaskingCapabilities<J>> handleEntityPropertyCustomSelect(final EntityPropertyCustomSelect epCustomSelect) {
        final EntityPropertyMain mainEntityProperty = epCustomSelect.getMainEntityProperty();
        if (mainEntityProperty == EP_TASKINGPARAMETERS) {
            PropertyFields<TableImpTaskingCapabilities<J>> mainPropertyFields = pfReg.getSelectFieldsForProperty(mainEntityProperty);
            final Field mainField = mainPropertyFields.fields.values().iterator().next().get(getThis());

            JsonFieldFactory jsonFactory = jsonFieldFromPath(mainField, epCustomSelect);
            return propertyFieldForJsonField(jsonFactory, epCustomSelect);
        }
        return null;
    }

    @Override
    public TableImpTaskingCapabilities<J> getThis() {
        return this;
    }

}