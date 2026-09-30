FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /MiniPay

COPY build.gradle ./
COPY settings.gradle ./
COPY gradlew ./
COPY gradle ./gradle
COPY src ./src

RUN sh ./gradlew bootJar

FROM eclipse-temurin:21-jre-noble

WORKDIR /MiniPay

COPY --from=build /MiniPay/build/libs/minipay-0.0.1-SNAPSHOT.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

EXPOSE 8080