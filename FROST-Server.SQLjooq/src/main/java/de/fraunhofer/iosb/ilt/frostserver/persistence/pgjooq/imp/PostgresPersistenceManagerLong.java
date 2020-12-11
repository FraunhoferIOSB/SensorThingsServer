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
package de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.imp;

import de.fraunhofer.iosb.ilt.frostserver.model.core.Id;
import de.fraunhofer.iosb.ilt.frostserver.model.core.IdLong;
import de.fraunhofer.iosb.ilt.frostserver.persistence.IdManagerLong;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.PostgresPersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableCollection;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import java.util.HashMap;
import java.util.Map;
import org.jooq.impl.SQLDataType;

/**
 *
 * @author jab
 * @author scf
 */
public class PostgresPersistenceManagerLong extends PostgresPersistenceManager<Long> {

    private static final String LIQUIBASE_CHANGELOG_FILENAME = "liquibase/tables.xml";

    private static final IdManagerLong ID_MANAGER = new IdManagerLong();
    private static final Map<CoreSettings, TableCollection<Long>> tableCollections = new HashMap<>();

    private static TableCollection<Long> getTableCollection(CoreSettings settings) {
        return tableCollections.computeIfAbsent(
                settings,
                (t) -> new TableCollection<>(IdLong.PERSISTENCE_TYPE_INTEGER, SQLDataType.BIGINT)
        );
    }

    public PostgresPersistenceManagerLong() {
        super(ID_MANAGER);
    }

    @Override
    public void init(CoreSettings settings) {
        super.init(settings, getTableCollection(settings));
    }

    @Override
    public String getLiquibaseChangelogFilename() {
        return LIQUIBASE_CHANGELOG_FILENAME;
    }

    @Override
    protected boolean validateClientSuppliedId(Id entityId) {
        return entityId.getValue() != null;
    }

}
