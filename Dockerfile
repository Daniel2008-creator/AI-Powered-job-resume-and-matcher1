FROM maven: 3.9.6-eclipse-temurin-21 AS build
COPY・・
RUN mn clean package -DskipTests

FROM eclipse-temurin:21-jdk
COPY --from-build /target/*. jar app. Jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app. jar"]
