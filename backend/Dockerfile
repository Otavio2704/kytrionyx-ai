# ──────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
# Usa a imagem oficial do Maven com JDK 17 para compilar o projeto.
# O truque de copiar o pom.xml antes do src/ aproveita o cache do Docker:
# se só o código mudar (não as dependências), o mvn dependency:go-offline
# não é reexecutado, tornando rebuilds muito mais rápidos.
# ──────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copia só o pom.xml primeiro para cachear o download das dependências
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copia o código-fonte e compila (sem rodar os testes)
COPY src ./src
RUN mvn package -DskipTests -q

# ──────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime
# Usa apenas o JRE (bem menor que o JDK) para rodar o .jar gerado.
# A imagem final não contém Maven nem código-fonte.
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

# Usuário não-root para segurança
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Copia apenas o jar do stage anterior
COPY --from=build /app/target/*.jar app.jar

# Porta exposta pelo Spring Boot
EXPOSE 8080

# Flags JVM otimizadas para container:
#   -XX:+UseContainerSupport         — respeita os limites de CPU/memória do Docker
#   -XX:MaxRAMPercentage=75.0        — usa até 75% da RAM disponível para o heap
#   -Djava.security.egd=...urandom   — gerador de entropia mais rápido no Linux
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
