# Fase 1: compilar o projeto com Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Fase 2: imagem final, mais leve, so com o Java e o .jar compilado
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# O Render define a variavel PORT automaticamente; a aplicacao ja le
# ${PORT:8080} no application.properties, por isso nao precisa de mudar nada.
ENTRYPOINT ["java", "-jar", "app.jar"]
