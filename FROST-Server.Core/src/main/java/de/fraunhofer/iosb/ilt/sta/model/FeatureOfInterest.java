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
package de.fraunhofer.iosb.ilt.sta.model;

import de.fraunhofer.iosb.ilt.sta.model.core.EntitySet;
import de.fraunhofer.iosb.ilt.sta.model.core.EntitySetImpl;
import de.fraunhofer.iosb.ilt.sta.model.core.Id;
import de.fraunhofer.iosb.ilt.sta.model.core.NamedEntity;
import de.fraunhofer.iosb.ilt.sta.path.EntityType;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author jab, scf
 */
public class FeatureOfInterest extends NamedEntity {

    private String encodingType;
    private Object feature;
    private EntitySet<Observation> observations;

    private boolean setEncodingType;
    private boolean setFeature;

    public FeatureOfInterest() {
        this.observations = new EntitySetImpl<>(EntityType.OBSERVATION);
    }

    public FeatureOfInterest(
            Id id,
            String selfLink,
            String navigationLink,
            String name,
            String description,
            String encodingType,
            Object feature,
            Map<String, Object> properties,
            EntitySet<Observation> observations) {
        super(id, selfLink, navigationLink, name, description, properties);
        this.encodingType = encodingType;
        this.feature = feature;
        this.observations = observations;
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.FEATUREOFINTEREST;
    }

    @Override
    public void setEntityPropertiesSet() {
        super.setEntityPropertiesSet();
        setEncodingType = true;
        setFeature = true;
    }

    public String getEncodingType() {
        return encodingType;
    }

    public void setEncodingType(String encodingType) {
        this.encodingType = encodingType;
        setEncodingType = encodingType != null;
    }

    public boolean isSetEncodingType() {
        return setEncodingType;
    }

    public Object getFeature() {
        return feature;
    }

    public void setFeature(Object feature) {
        setFeature = feature != null;
        this.feature = feature;
    }

    public boolean isSetFeature() {
        return setFeature;
    }

    public EntitySet<Observation> getObservations() {
        return observations;
    }

    public void setObservations(EntitySet<Observation> observations) {
        this.observations = observations;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), encodingType, feature, observations);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FeatureOfInterest other = (FeatureOfInterest) obj;
        return super.equals(other)
                && Objects.equals(encodingType, other.encodingType)
                && Objects.equals(feature, other.feature)
                && Objects.equals(observations, other.observations);
    }

}
