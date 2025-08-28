FROM debian:bookworm

ARG TARGETARCH

ARG NGINX_VERSION="1.28.0"

RUN apt-get update && apt-get install -y gcc make curl xz-utils libssl-dev

RUN curl https://nginx.org/download/nginx-${NGINX_VERSION}.tar.gz -JLO

RUN tar xvf nginx-1.*.tar.gz && ls -lah

ARG CFLAGS="-O2 -Werror=implicit-function-declaration -ffile-prefix-map=/build/reproducible-path/nginx-1.26.3=. -fstack-protector-strong -fstack-clash-protection -Wformat -Werror=format-security -fPIC -Wdate-time -D_FORTIFY_SOURCE=2"

RUN cd nginx-1.*/ && \
./configure \
    --sbin-path=/nginx \
    --pid-path="/tmp/nginx.pid" \
    --lock-path="/tmp/nginx.lock" \
    --error-log-path="/dev/stdout" \
    --http-log-path="/dev/stdout" \
    --conf-path=/conf/nginx.conf \
    --prefix="/" \
    --with-cc-opt="$CFLAGS" \
    --without-pcre \
    --with-threads \
    --with-stream \
    --with-stream_ssl_module \
    --without-stream_limit_conn_module \
    --without-stream_access_module \
    --without-stream_geo_module \
    --without-stream_map_module \
    --without-stream_split_clients_module \
    --without-stream_return_module \
    --without-stream_set_module \
    --without-stream_upstream_hash_module \
    --without-stream_upstream_least_conn_module \
    --without-stream_upstream_random_module \
    --without-stream_upstream_zone_module \
    --without-select_module \
    --without-poll_module \
    --without-http && \
    make && \
    strip --strip-all objs/nginx && \
    cp objs/nginx ../nginx
