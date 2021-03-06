---
layout: default
title: Creating Entities
category: STA
order: 10
---

# Creating New Entities

The OGC SensorThings API does not just allow you to read data, it is possible to create, update and delete all data too.

## Creating basic entities

Creating is done by making a http POST to a collection.
For instance, to create a new Thing, the JSON description of the Thing is posted to `v1.1/Things`:

```
POST https://example.org/FROST-Server/v1.1/Things

{
  "name" : "lantern",
  "description" : "camping lantern",
  "properties" : {
    "property1" : "it’s waterproof",
    "property2" : "it glows in the dark"
  }
}
```

## Creating entities with (required) relations

All entities can link to other entities. In some cases, these relations are mandatory.
For instance, a Datastream must link to a Thing, a Sensor and an ObservedProperty.
When these related entities already exist, they can be specified in the JSON of the Entitiy.
The following example crates a Datastream, and links to an existing Thing (with id 2), Sensor (with id 5) and ObservedProperty (with id 3):

```
POST https://example.org/FROST-Server/v1.1/Datastreams

{
  "name" : "Temperature in the Kitchen",
  "description" : "The temperature in the kitchen, measured by the sensor next to the window",
  "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
  "unitOfMeasurement": {
    "name": "Degree Celsius",
    "symbol": "°C",
    "definition": "ucum:Cel"
  },
  "Thing": {"@iot.id": 2},
  "Sensor": {"@iot.id": 5},
  "ObservedProperty": {"@iot.id": 3}
}
```

It is also possible to specify one relation in the URL of the POST instead of in the JSON.
The following two POSTs both create a new Observation in Datastream 14:

```
POST https://example.org/FROST-Server/v1.1/Observations

{
  "result" : 21,
  "Datastream": {"@iot.id": 14}
}
```

In the following example, the post is made to `v1.1/Datastreams(14)/Observations`, i.e. to the collection of Observations belonging to Datastream 14.
This automatically links the new Observation to Datastream 14.
```
POST https://example.org/FROST-Server/v1.1/Datastreams(14)/Observations

{
  "result" : 21
}
```

## Creating multiple related entities in one POST

It is possible to create an entities, and its relations, in one POST, by giving the full related
entity in the JSON instead of only the entitiy id. The following POST creates a Thing, a Location, two Datastreams
(linked to the same, existing Sensor) each with an ObservedProperty, in one POST.

```
POST https://example.org/FROST-Server/v1.1/Things

{
  "name" : "Kitchen",
  "description" : "The Kitchen in my house",
  "properties" : {
    "oven" : true,
    "heatingPlates" : 4
  },
  "Locations": [
    {
      "name": "Location of the kitchen",
      "description": "This is where the kitchen is",
      "encodingType": "application/geo+json",
      "location": {
        "type": "Point",
        "coordinates": [8.438889, 44.27253]
      }
    }
  ],
  "Datastreams": [
    {
      "name": "Temperature in the Kitchen",
      "description" : "The temperature in the kitchen, measured by the sensor next to the window",
      "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
      "unitOfMeasurement": {
        "name": "Degree Celsius",
        "symbol": "°C",
        "definition": "ucum:Cel"
      },
      "Sensor": {"@iot.id": 5},
      "ObservedProperty": {
        "name": "Temperature",
        "description": "Temperature",
        "definition": "http://dd.eionet.europa.eu/vocabularyconcept/aq/meteoparameter/54"
      }
    },{
      "name": "Humidity in the Kitchen",
      "description" : "The relative humidity in the kitchen, measured by the sensor next to the window",
      "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
      "unitOfMeasurement": {
        "name": "Percent",
        "symbol": "%",
        "definition": "ucum:%"
      },
      "Sensor": {"@iot.id": 5},
      "ObservedProperty": {
        "name": "Relative humidity",
        "description": "Relative humidity",
        "definition": "http://dd.eionet.europa.eu/vocabularyconcept/aq/meteoparameter/58"
      }
    }
  ]
}
```

## Example Entities

Here are example entities, with their required fields:

### Datastream

```JSON
{
  "name" : "Temperature in the Kitchen",
  "description" : "The temperature in the kitchen, measured by the sensor next to the window",
  "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
  "unitOfMeasurement": {
    "name": "Degree Celsius",
    "symbol": "°C",
    "definition": "ucum:Cel"
  },
  "Thing": {"@iot.id": 999},
  "Sensor": {"@iot.id": 999},
  "ObservedProperty": {"@iot.id": 999}
}
```

### FeatureOfInterest

The FeatureOfInterest is often autogenerated from the Location of the Thing.
```JSON
{
  "name": "0113330020070312",
  "description": "Water Sample from L'HALLUE À DAOURS (80) taken on 2007-03-12 at 00:00:00",
  "properties": {},
  "encodingType": "application/geo+json",
  "feature": {
    "type": "Point",
    "coordinates": [
        8.10,
        50.00
    ]
  }
}
```

### Location

```JSON
{
  "name": "Location of the kitchen",
  "description": "This is where the kitchen is",
  "properties": {},
  "encodingType": "application/geo+json",
  "location": {
    "type": "Point",
    "coordinates": [8.10, 50.00]
  }
}
```

### Observation

If the phenomenonTime is not given, the server will use the current time as phenomenonTime.
If the featureOfInterest is not given, the server will use a FeatureOfInterest generated from the Location of the Thing of the Datasteam.
If the resultTime is not given, the value `null` is used.

```JSON
{
  "result": 49,
  "phenomenonTime": "2020-10-11T12:13:14+02:00"
}
```

### ObservedProperty

```JSON
{
  "name": "Temperature",
  "description": "Temperature",
  "properties": {},
  "definition": "http://dd.eionet.europa.eu/vocabularyconcept/aq/meteoparameter/54"
}
```

### Sensor

```JSON
{
  "name": "HDT22",
  "description": "A cheap sensor that measures Temperature and Humidity",
  "properties": {},
  "encodingType": "application/pdf",
  "metadata": "https://www.sparkfun.com/datasheets/Sensors/Temperature/DHT22.pdf"
}
```

### Thing

```JSON
{
  "name" : "Kitchen",
  "description" : "The Kitchen in my house",
  "properties" : {
    "oven" : true,
    "heatingPlates" : 4
  }
}
```



