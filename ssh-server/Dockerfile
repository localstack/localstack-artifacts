FROM debian:buster-slim

RUN apt-get update
RUN apt-get install -y autoconf automake gcc git gzip libssl-dev libz-dev make openssl

RUN cd /tmp; \
    git clone https://github.com/mkj/dropbear; \
    cd dropbear; \
    ./configure --enable-static; \
    make

RUN apt-get install openssh-client
RUN which scp
