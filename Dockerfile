# المرحلة الأولى: البناء
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# نسخ ملفات المشروع
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src

# تنزيل الـ dependencies وبناء الـ JAR
RUN ./mvnw clean package -DskipTests

# المرحلة الثانية: تشغيل التطبيق
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# نسخ الـ JAR النهائي من مرحلة البناء
COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar

# تعيين المتغيرات البيئية (يمكن تعديلها على Render)
ENV SPRING_PROFILES_ACTIVE=prod
ENV TELEGRAM_TOKEN=""
ENV RENDER_EXTERNAL_URL=""
ENV SUPABASE_URL=""
ENV SUPABASE_KEY=""
ENV SUPABASE_BUCKET=food-stor

# فتح البورت
EXPOSE 8080

# تشغيل التطبيق
ENTRYPOINT ["java", "-jar", "app.jar"]
