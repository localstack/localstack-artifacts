FROM debian:bookworm-slim

RUN apt-get update
RUN apt-get install -y autoconf automake gcc git gzip libssl-dev libz-dev make openssl

RUN cd /tmp; \
    git clone https://github.com/mkj/dropbear -b DROPBEAR_2024.85; \
    cd dropbear; \
    ./configure --enable-static; \
    make PROGRAMS="dropbear scp" SCPPROGRESS=1
