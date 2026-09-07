FROM jenkins/jenkins:lts-jdk17

USER root

# Install OpenJDK 21 alongside built-in JDK 17
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    wget \
    git \
    openjdk-21-jdk \
    && rm -rf /var/lib/apt/lists/*

# Install Eclipse Temurin JDK 8 for legacy Minecraft branches (1.16.5 and 1.12.2)
RUN mkdir -p /opt/java/openjdk-8 && \
    curl -fsSL "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse" | \
    tar -xzf - -C /opt/java/openjdk-8 --strip-components=1

# Symlink JDK paths into consistent locations for JCasC
RUN ln -s /opt/java/openjdk /opt/java/openjdk-17 && \
    ln -s /usr/lib/jvm/java-21-openjdk-amd64 /opt/java/openjdk-21

USER jenkins

# Pre-install plugins via official CLI
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli -f /usr/share/jenkins/ref/plugins.txt

# Disable interactive setup wizard and load JCasC config
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG="/usr/share/jenkins/ref/jenkins.yaml"

# Copy declarative configuration
COPY --chown=jenkins:jenkins jenkins.yaml /usr/share/jenkins/ref/jenkins.yaml
