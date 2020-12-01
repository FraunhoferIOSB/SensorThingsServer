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
package de.fraunhofer.iosb.ilt.frostserver.model;

import de.fraunhofer.iosb.ilt.frostserver.model.core.Entity;
import de.fraunhofer.iosb.ilt.frostserver.model.core.EntitySet;
import de.fraunhofer.iosb.ilt.frostserver.model.core.EntityValidator;
import de.fraunhofer.iosb.ilt.frostserver.model.core.Id;
import de.fraunhofer.iosb.ilt.frostserver.path.PathElement;
import de.fraunhofer.iosb.ilt.frostserver.path.PathElementEntity;
import de.fraunhofer.iosb.ilt.frostserver.path.PathElementEntitySet;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyMain;
import de.fraunhofer.iosb.ilt.frostserver.property.NavigationPropertyMain;
import de.fraunhofer.iosb.ilt.frostserver.property.Property;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.IncompleteEntityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The types of entities in STA.
 *
 * @author jab, scf
 */
public class EntityType implements Comparable<EntityType> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityType.class.getName());

    /**
     * The entitiyName of this entity type as used in URLs.
     */
    public final String entityName;
    /**
     * The entitiyName of collections of this entity type as used in URLs.
     */
    public final String plural;

    private boolean initialised = false;
    /**
     * The Set of PROPERTIES that Entities of this type have, mapped to the flag
     * indicating if they are required.
     */
    private final Map<Property, Boolean> propertyMap = new LinkedHashMap<>();
    /**
     * The set of Entity properties.
     */
    private final Set<EntityPropertyMain> entityProperties = new LinkedHashSet<>();
    /**
     * The set of Navigation properties.
     */
    private final Set<NavigationPropertyMain> navigationProperties = new LinkedHashSet<>();
    /**
     * The set of Navigation properties pointing to single entities.
     */
    private final Set<NavigationPropertyMain<Entity>> navigationEntities = new LinkedHashSet<>();
    /**
     * The set of Navigation properties pointing to entity sets.
     */
    private final Set<NavigationPropertyMain<EntitySet>> navigationSets = new LinkedHashSet<>();
    /**
     * The map of NavigationProperties by their target EntityTypes.
     */
    private final Map<EntityType, NavigationPropertyMain> navigationPropertiesByTarget = new HashMap<>();

    private final List<EntityValidator> validatorsNewEntity = new ArrayList<>();
    private final List<EntityValidator> validatorsUpdateEntity = new ArrayList<>();

    public EntityType(String singular, String plural) {
        this.entityName = singular;
        this.plural = plural;
    }

    public void clear() {
        initialised = false;
        propertyMap.clear();
        entityProperties.clear();
        navigationEntities.clear();
        navigationProperties.clear();
        navigationPropertiesByTarget.clear();
        navigationSets.clear();
        validatorsNewEntity.clear();
        validatorsUpdateEntity.clear();
    }

    public EntityType registerProperty(Property property, boolean required) {
        propertyMap.put(property, required);
        return this;
    }

    public void init() {
        if (initialised) {
            LOGGER.error("Re-Init of EntityType!");
        }
        initialised = true;
        for (Property property : propertyMap.keySet()) {
            if (property instanceof EntityPropertyMain) {
                entityProperties.add((EntityPropertyMain) property);
            }
            if (property instanceof NavigationPropertyMain) {
                NavigationPropertyMain np = (NavigationPropertyMain) property;
                navigationProperties.add(np);
                if (np.isEntitySet()) {
                    navigationSets.add(np);
                } else {
                    navigationEntities.add(np);
                }
                navigationPropertiesByTarget.put(np.getEntityType(), np);
            }
        }
    }

    public EntityType addValidator(EntityValidator validator) {
        validatorsNewEntity.add(validator);
        return this;
    }

    /**
     * The Map of PROPERTIES that Entities of this type have, with their
     * required status.
     *
     * @return The Set of PROPERTIES that Entities of this type have.
     */
    public Map<Property, Boolean> getPropertyMap() {
        return propertyMap;
    }

    /**
     * The Set of PROPERTIES that Entities of this type have.
     *
     * @return The Set of PROPERTIES that Entities of this type have.
     */
    public Set<Property> getPropertySet() {
        return propertyMap.keySet();
    }

    /**
     * Get the set of Entity properties.
     *
     * @return The set of Entity properties.
     */
    public Set<EntityPropertyMain> getEntityProperties() {
        return entityProperties;
    }

    /**
     * Get the set of Navigation properties.
     *
     * @return The set of Navigation properties.
     */
    public Set<NavigationPropertyMain> getNavigationProperties() {
        return navigationProperties;
    }

    /**
     * Get the set of Navigation properties pointing to single entities.
     *
     * @return The set of Navigation properties pointing to single entities.
     */
    public Set<NavigationPropertyMain<Entity>> getNavigationEntities() {
        return navigationEntities;
    }

    /**
     * Get the set of Navigation properties pointing to entity sets.
     *
     * @return The set of Navigation properties pointing to entity sets.
     */
    public Set<NavigationPropertyMain<EntitySet>> getNavigationSets() {
        return navigationSets;
    }

    public NavigationPropertyMain getNavigationProperty(EntityType to) {
        return navigationPropertiesByTarget.get(to);
    }

    /**
     * @param property The property to check the required state for.
     * @return True when the property is required, false otherwise.
     */
    public boolean isRequired(Property property) {
        return propertyMap.getOrDefault(property, false);
    }

    /**
     * Check if all required properties are non-null on the given Entity.
     *
     * @param entity the Entity to check.
     * @param entityPropertiesOnly flag indicating only the EntityProperties
     * should be checked.
     * @throws IncompleteEntityException If any of the required properties are
     * null.
     * @throws IllegalStateException If any of the required properties are
     * incorrect (i.e. Observation with both a Datastream and a MultiDatastream.
     */
    public void complete(Entity entity, boolean entityPropertiesOnly) throws IncompleteEntityException {
        for (Property property : getPropertySet()) {
            if (entityPropertiesOnly && !(property instanceof EntityPropertyMain)) {
                continue;
            }
            if (isRequired(property) && !entity.isSetProperty(property)) {
                throw new IncompleteEntityException("Missing required property '" + property.getJsonName() + "'");
            }
        }
        for (EntityValidator validator : validatorsNewEntity) {
            validator.validate(entity, entityPropertiesOnly);
        }
    }

    public void complete(Entity entity, PathElementEntitySet containingSet) throws IncompleteEntityException {
        EntityType type = containingSet.getEntityType();
        if (type != entity.getEntityType()) {
            throw new IllegalArgumentException("Set of type " + type + " can not contain a " + entity.getEntityType());
        }

        checkParent(containingSet, entity);

        complete(entity, false);
    }

    public void validateUpdate(Entity entity) throws IncompleteEntityException {
        for (EntityValidator validator : validatorsUpdateEntity) {
            validator.validate(entity, false);
        }
    }

    private void checkParent(PathElementEntitySet containingSet, Entity entity) throws IncompleteEntityException {
        PathElement parent = containingSet.getParent();
        if (parent instanceof PathElementEntity) {
            PathElementEntity parentEntity = (PathElementEntity) parent;
            Id parentId = parentEntity.getId();
            if (parentId == null) {
                return;
            }
            checkParent(entity, parentEntity, parentId);
        }
    }

    private void checkParent(Entity entity, PathElementEntity parentEntity, Id parentId) throws IncompleteEntityException {
        EntityType parentType = parentEntity.getEntityType();
        final NavigationPropertyMain parentNavProperty = navigationPropertiesByTarget.get(parentType);
        if (parentNavProperty == null || parentNavProperty.isEntitySet()) {
            LOGGER.error("Incorrect 'parent' entity type for {}: {}", entityName, parentType);
            throw new IncompleteEntityException("Incorrect 'parent' entity type for " + entityName + ": " + parentType);
        }
        entity.setProperty(parentNavProperty, new DefaultEntity(parentType).setId(parentId));
    }

    @Override
    public String toString() {
        return entityName;
    }

    @Override
    public int compareTo(EntityType o) {
        return entityName.compareTo(o.entityName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EntityType)) {
            return false;
        }
        EntityType other = (EntityType) obj;
        if (entityName.equals(other.entityName)) {
            LOGGER.error("Found other instance of {}", entityName);
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.entityName);
        return hash;
    }

}
