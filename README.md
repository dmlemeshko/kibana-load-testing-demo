# Kibana APIs load testing project

**Pre-conditions**

Start Kibana with basic license

**How to run simulation against local instance**

You can update configuration in local.conf resource file
```
mvn install
mvn gatling:test
```

**How to run simulation against cloud instance**

You need to add cloud.conf resource file with valid configuration
```
mvn install
export env=cloud && mvn gatling:test
```
