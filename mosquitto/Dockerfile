ARG DEB_RELEASE

FROM debian:${DEB_RELEASE}-slim

ARG VERSION

RUN apt-get update
RUN apt-get install -y gcc g++ libssl-dev libcjson-dev git make

RUN cd /tmp && \
    git clone https://github.com/eclipse/mosquitto --branch $VERSION && \
    cd mosquitto && \
    make WITH_STRIP=yes WITH_STATIC_LIBRARIES=yes WITH_SHARED_LIBRARIES=no WITH_DOCS=no && \
    strip src/mosquitto
