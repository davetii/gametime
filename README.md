# Gametime SpringBoot App
Example Spring boot app used to learn technologies.
Its meant to be a basketball simimulator

### External Dependencies
* jdk17
* Docker installed on system
* docker-compose available on command line

### Whats in the POM
* Springboot 3 mvc version
* openapi
* liquibase
* Db support: postgres, h2

### Installing
* mvn install

### Executing program
* mvn spring-boot:run

### API
http://localhost:8080/swagger-ui/index.html

### H2 DB
http://127.0.0.1:8080/h2-console

### postgres
see application-test.properties



### todo
* Expand Cucumber tests
* Implement Advice pattern
* Create branch for mongo
* Add game simulator logic
* Add kafka sample
* Add RabbitMQ sample



