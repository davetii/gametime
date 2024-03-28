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
* docker-compose

### Installing
* mvn install
  * local profile uses h2
  * test profile uses docker postgres

### Executing program
* mvn spring-boot:run

### API
http://localhost:8080/swagger-ui/index.html

### H2 DB (under local profile)
http://127.0.0.1:8080/h2-console

### Adminer for postgres (under test profile)
http://localhost:8083/

### postgres
see application-test.properties

### Docker Run Time test
* build the image _mvn spring-boot:build-image_
* Run the image from docker folder _docker-compose up_
* note: a different docker-compose is run during software build process

### Profiles
* local = h2, set in application.properties and ensure test profile in pom.xml is not active
* test = postgres docker image, set application.properties to test and ensure maven profile is set to active


### todo
* Expand Cucumber tests to intgration test suite
* Implement @ControllerAdvice for error handling
* Resilience4j supported with tests
* Create branch for mongo
* Add game simulator logic
* Add kafka sample
* Add RabbitMQ sample



