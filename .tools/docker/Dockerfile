# Projekt Android (Kotlin). Aplikacji nie da sie uruchomic w kontenerze —
# wymaga urzadzenia z Wi-Fi Direct. Obraz sluzy do powtarzalnego budowania
# pliku APK w CI i lokalnie, bez instalowania Android SDK na hoscie.
FROM eclipse-temurin:17-jdk

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        unzip \
        git \
    && rm -rf /var/lib/apt/lists/*

# Command line tools instalujemy do podkatalogu "latest" — takiej struktury
# oczekuje sdkmanager, inaczej konczy sie bledem o braku repozytorium.
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && curl -fsSL -o /tmp/tools.zip \
        https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q /tmp/tools.zip -d /tmp \
    && mv /tmp/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm /tmp/tools.zip

ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

RUN yes | sdkmanager --licenses > /dev/null \
    && # Modul deklaruje compileSdk = 35, wiec ta platforma musi byc dostepna.
    && sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" > /dev/null

WORKDIR /workspace

CMD ["./gradlew", "assembleDebug"]
