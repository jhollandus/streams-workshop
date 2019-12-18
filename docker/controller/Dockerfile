FROM confluentinc/cp-ksql-cli:5.3.1 AS ksql

FROM confluentinc/cp-base:5.3.1

ARG VERSION="5.3.1-1"
ARG SCALA_VERSION="2.12"

RUN apt-get update \
    && apt-get install -y confluent-kafka-${SCALA_VERSION}=${VERSION} \
    && apt-get install -y confluent-schema-registry=${VERSION} \
    && apt-get install -y ansible 


RUN apt-get dist-upgrade -y \
    && apt-get install apt-transport-https -y \
    && apt-key adv --keyserver hkp://pgp.mit.edu:80 --recv-keys 58118E89F3A912897C070ADBF76221572C52609D \
    && echo "deb https://apt.dockerproject.org/repo debian-jessie main" | \ tee /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install docker-engine -y \
    && systemctl enable docker

COPY --from=ksql /usr/share/java/ksql-cli/* /usr/share/java/ksql-cli/
COPY --from=ksql /usr/bin/ksql* /usr/bin/
COPY --from=ksql /etc/ksql/* /etc/ksql/
COPY --from=ksql /usr/share/doc/cp-ksql-cli/* /usr/share/doc/ksql-cli/

WORKDIR /opt/work
ENV PATH "$PATH:/opt/work/bin"
ENTRYPOINT ["/bin/bash"]
