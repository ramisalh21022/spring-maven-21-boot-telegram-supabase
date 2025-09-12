# ----------------------
# مرحلة البناء (Build Stage)
# ----------------------
FROM eclipse-temurin:21-jdk-jammy AS build

# تثبيت Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# تعيين مجلد العمل
WORKDIR /app

# نسخ ملفات المشروع
COPY pom.xml mvnw ./
COPY src ./src

# بناء المشروع وإنشاء الـ JAR
RUN mvn clean package -DskipTests

# ----------------------
# مرحلة التشغيل (Run Stage)
# ----------------------
FROM eclipse-temurin:21-jdk-jammy

# تعيين مجلد العمل
WORKDIR /app

# نسخ الـ JAR الناتج من مرحلة البناء
COPY --from=build /app/target/*.jar app.jar

# تعيين نقطة البداية
ENTRYPOINT ["java", "-jar", "app.jar"]

