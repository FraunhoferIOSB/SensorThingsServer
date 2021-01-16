package de.fraunhofer.iosb.ilt.frostserver.plugin.actuation;

import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.model.ModelRegistry;
import de.fraunhofer.iosb.ilt.frostserver.persistence.IdManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.JsonBinding;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.JsonValue;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.factories.EntityFactories;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.fieldwrapper.JsonFieldFactory;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.relations.RelationOneToMany;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.StaTableAbstract;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableCollection;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.PropertyFieldRegistry.PropertyFields;
import de.fraunhofer.iosb.ilt.frostserver.plugin.coremodel.PluginCoreModel;
import de.fraunhofer.iosb.ilt.frostserver.plugin.coremodel.TableImpThings;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyCustomSelect;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyMain;
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

    /**
     * The column <code>public.TASKINGCAPABILITIES.EP_DESCRIPTION</code>.
     */
    public final TableField<Record, String> colDescription = createField(DSL.name("DESCRIPTION"), SQLDataType.CLOB, this);

    /**
     * The column <code>public.TASKINGCAPABILITIES.EP_NAME</code>.
     */
    public final TableField<Record, String> colName = createField(DSL.name("NAME"), SQLDataType.CLOB.defaultValue(DSL.field("'no name'::text", SQLDataType.CLOB)), this);

    /**
     * The column <code>public.TASKINGCAPABILITIES.EP_PROPERTIES</code>.
     */
    public final TableField<Record, JsonValue> colProperties = createField(DSL.name("PROPERTIES"), DefaultDataType.getDefaultDataType(TYPE_JSONB), this, "", new JsonBinding());

    /**
     * The column <code>public.TASKINGCAPABILITIES.TASKING_PARAMETERS</code>.
     */
    public final TableField<Record, JsonValue> colTaskingParameters = createField(DSL.name("TASKING_PARAMETERS"), DefaultDataType.getDefaultDataType(TYPE_JSONB), this, "", new JsonBinding());

    /**
     * The column <code>public.TASKINGCAPABILITIES.EP_ID</code>.
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

    private final PluginActuation pluginActuation;
    private final PluginCoreModel pluginCoreModel;

    /**
     * Create a <code>public.TASKINGCAPABILITIES</code> table reference.
     *
     * @param idType The (SQL)DataType of the Id columns used in the actual
     * database.
     * @param pluginActuation the actuation plugin this table belongs to.
     * @param pluginCoreModel the coreModel plugin that this data model links
     * to.
     */
    public TableImpTaskingCapabilities(DataType<J> idType, PluginActuation pluginActuation, PluginCoreModel pluginCoreModel) {
        super(idType, DSL.name("TASKINGCAPABILITIES"), null);
        this.pluginActuation = pluginActuation;
        this.pluginCoreModel = pluginCoreModel;
    }

    private TableImpTaskingCapabilities(Name alias, TableImpTaskingCapabilities<J> aliased, PluginActuation pluginActuation, PluginCoreModel pluginCoreModel) {
        super(aliased.getIdType(), alias, aliased);
        this.pluginActuation = pluginActuation;
        this.pluginCoreModel = pluginCoreModel;
    }

    @Override
    public void initRelations() {
        final TableCollection<J> tables = getTables();
        final ModelRegistry modelRegistry = getModelRegistry();
        TableImpThings<J> tableThings = tables.getTableForClass(TableImpThings.class);
        registerRelation(new RelationOneToMany<>(this, tableThings, pluginCoreModel.etThing)
                .setSourceFieldAccessor(TableImpTaskingCapabilities::getThingId)
                .setTargetFieldAccessor(TableImpThings::getId)
        );
        TableImpActuators<J> tableActuators = tables.getTableForClass(TableImpActuators.class);
        registerRelation(new RelationOneToMany<>(this, tableActuators, pluginActuation.etActuator)
                .setSourceFieldAccessor(TableImpTaskingCapabilities::getActuatorId)
                .setTargetFieldAccessor(TableImpActuators::getId)
        );
        final TableImpTasks<J> tableTasks = tables.getTableForClass(TableImpTasks.class);
        registerRelation(new RelationOneToMany<>(this, tableTasks, pluginActuation.etTask, true)
                .setSourceFieldAccessor(TableImpTaskingCapabilities::getId)
                .setTargetFieldAccessor(TableImpTasks::getTaskingCapabilityId)
        );

        // We add the relation to us to the Things table.
        final TableImpThings<J> thingsTable = tables.getTableForClass(TableImpThings.class);
        final TableImpTaskingCapabilities<J> tableTaskingCaps = tables.getTableForClass(TableImpTaskingCapabilities.class);
        thingsTable.registerRelation(new RelationOneToMany<>(thingsTable, tableTaskingCaps, pluginActuation.etTaskingCapability, true)
                .setSourceFieldAccessor(TableImpThings::getId)
                .setTargetFieldAccessor(TableImpTaskingCapabilities::getThingId)
        );

    }

    @Override
    public void initProperties(final EntityFactories<J> entityFactories) {
        final TableCollection<J> tables = getTables();
        final ModelRegistry modelRegistry = getModelRegistry();
        final IdManager idManager = entityFactories.getIdManager();
        pfReg.addEntryId(idManager, TableImpTaskingCapabilities::getId);
        pfReg.addEntryString(pluginCoreModel.epName, table -> table.colName);
        pfReg.addEntryString(pluginCoreModel.epDescription, table -> table.colDescription);
        pfReg.addEntryMap(ModelRegistry.EP_PROPERTIES, table -> table.colProperties);
        pfReg.addEntryMap(pluginActuation.epTaskingParameters, table -> table.colTaskingParameters);
        pfReg.addEntry(pluginActuation.npActuator, TableImpTaskingCapabilities::getActuatorId, idManager);
        pfReg.addEntry(pluginCoreModel.npThing, TableImpTaskingCapabilities::getThingId, idManager);
        pfReg.addEntry(pluginActuation.npTasks, TableImpTaskingCapabilities::getId, idManager);

        // We register a navigationProperty on the Things table.
        TableImpThings<J> thingsTable = tables.getTableForClass(TableImpThings.class);
        thingsTable.getPropertyFieldRegistry()
                .addEntry(pluginActuation.npTaskingCapabilities, TableImpThings::getId, idManager);
    }

    @Override
    public EntityType getEntityType() {
        return pluginActuation.etTaskingCapability;
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
        return new TableImpTaskingCapabilities<>(alias, this, pluginActuation, pluginCoreModel).initCustomFields();
    }

    @Override
    public TableImpTaskingCapabilities<J> as(String alias) {
        return new TableImpTaskingCapabilities<>(DSL.name(alias), this, pluginActuation, pluginCoreModel).initCustomFields();
    }

    @Override
    public PropertyFields<TableImpTaskingCapabilities<J>> handleEntityPropertyCustomSelect(final EntityPropertyCustomSelect epCustomSelect) {
        final EntityPropertyMain mainEntityProperty = epCustomSelect.getMainEntityProperty();
        if (mainEntityProperty == pluginActuation.epTaskingParameters) {
            PropertyFields<TableImpTaskingCapabilities<J>> mainPropertyFields = pfReg.getSelectFieldsForProperty(mainEntityProperty);
            final Field mainField = mainPropertyFields.fields.values().iterator().next().get(getThis());

            JsonFieldFactory jsonFactory = jsonFieldFromPath(mainField, epCustomSelect);
            return propertyFieldForJsonField(jsonFactory, epCustomSelect);
        }
        return super.handleEntityPropertyCustomSelect(epCustomSelect);
    }

    @Override
    public TableImpTaskingCapabilities<J> getThis() {
        return this;
    }

}
