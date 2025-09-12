# ----------------------
# مرحلة البناء
# ----------------------
FROM maven:3.9.3-eclipse-temurin-21-jdk-focal AS build

WORKDIR /app

# نسخ ملفات المشروع
COPY pom.xml .
COPY src ./src

# بناء المشروع وتوليد JAR
RUN mvn clean package -DskipTests

# ----------------------
# مرحلة التشغيل
# ----------------------
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# نسخ الـ JAR الناتج
COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]




