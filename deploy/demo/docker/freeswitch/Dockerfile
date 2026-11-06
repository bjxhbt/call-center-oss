FROM debian:bookworm-slim AS builder
ARG BUILD_JOBS=2
ARG DEBIAN_MIRROR=http://mirrors.cloud.tencent.com
RUN sed -i "s|http://deb.debian.org|${DEBIAN_MIRROR}|g" /etc/apt/sources.list.d/debian.sources \
    && apt-get -o Acquire::Retries=3 -o Acquire::http::Timeout=30 update && apt-get install -y --no-install-recommends \
    ca-certificates curl build-essential autoconf automake libtool-bin pkg-config \
    cmake nasm yasm python3 libssl-dev zlib1g-dev libjpeg-dev libtiff-dev \
    libsqlite3-dev libcurl4-openssl-dev libpcre2-dev libedit-dev libspeex-dev \
    libspeexdsp-dev libopus-dev libsndfile1-dev libldns-dev uuid-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY upstream/ ./upstream/
# Upstream sources are pinned and checked; no registry token or site secrets.
RUN if [ -f upstream/sofia-sip.tar.gz ]; then cp upstream/sofia-sip.tar.gz sofia.tar.gz; \
    else curl -fL --connect-timeout 15 --max-time 300 --retry 3 https://codeload.github.com/freeswitch/sofia-sip/tar.gz/ad36ac8f755308e8b87f98a505e83d4e408e5cc3 -o sofia.tar.gz; fi \
    && echo 'a63ba972e4b7839b50bfd1277e150ebdb70ecd9261894bc65a74a7d3e995d533  sofia.tar.gz' | sha256sum -c - \
    && mkdir sofia && tar xzf sofia.tar.gz -C sofia --strip-components=1 \
    && cd sofia && ./bootstrap.sh && ./configure --with-pic --with-glib=no --without-doxygen --disable-stun \
    && make -j${BUILD_JOBS} && make install
RUN if [ -f upstream/spandsp.tar.gz ]; then cp upstream/spandsp.tar.gz spandsp.tar.gz; \
    else curl -fL --connect-timeout 15 --max-time 300 --retry 3 https://codeload.github.com/freeswitch/spandsp/tar.gz/8f1e1646bdec99eac5fd2cd92c35563f736b9b89 -o spandsp.tar.gz; fi \
    && echo '1cb9a0ee3a68a20e84db9702cb82ac643b27e15820e74eb5a69511952dfa27b2  spandsp.tar.gz' | sha256sum -c - \
    && mkdir spandsp && tar xzf spandsp.tar.gz -C spandsp --strip-components=1 \
    && cd spandsp && ./bootstrap.sh && ./configure --with-pic \
    && make -j${BUILD_JOBS} && make install && ldconfig
RUN if [ -f upstream/freeswitch.tar.gz ]; then cp upstream/freeswitch.tar.gz freeswitch.tar.gz; \
    else curl -fL --connect-timeout 15 --max-time 300 --retry 3 https://codeload.github.com/signalwire/freeswitch/tar.gz/v1.11.3 -o freeswitch.tar.gz; fi \
    && echo '6ab49ae26f45e35d3b88ae12b5d915a10aeabafcc0fd24a427c3bab4012c9e55  freeswitch.tar.gz' | sha256sum -c - \
    && mkdir freeswitch && tar xzf freeswitch.tar.gz -C freeswitch --strip-components=1 \
    && cd freeswitch && ./bootstrap.sh -j
COPY modules.conf /build/freeswitch/modules.conf
RUN cd freeswitch && ./configure --prefix=/usr/local/freeswitch --disable-dependency-tracking \
    && make -j${BUILD_JOBS} && make install

FROM debian:bookworm-slim
ARG DEBIAN_MIRROR=http://mirrors.cloud.tencent.com
RUN sed -i "s|http://deb.debian.org|${DEBIAN_MIRROR}|g" /etc/apt/sources.list.d/debian.sources \
    && apt-get -o Acquire::Retries=3 -o Acquire::http::Timeout=30 update && apt-get install -y --no-install-recommends \
    ca-certificates python3 libssl3 zlib1g libjpeg62-turbo libtiff6 libsqlite3-0 \
    libcurl4 libpcre2-8-0 libedit2 libspeex1 libspeexdsp1 libopus0 libsndfile1 \
    libldns3 libuuid1 libatomic1 libstdc++6 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 xhbt && useradd --uid 10001 --gid 10001 --no-create-home xhbt
COPY --from=builder /usr/local/ /usr/local/
RUN ldconfig && rm -rf /usr/local/freeswitch/conf /usr/local/freeswitch/etc/freeswitch \
    && mkdir -p /usr/local/freeswitch/conf /usr/local/freeswitch/db /usr/local/freeswitch/log /usr/local/freeswitch/run /app/runtime/public/recordings /app/runtime/public/files \
    && chown -R 10001:10001 /usr/local/freeswitch /app \
    && if find /usr/local/freeswitch/bin /usr/local/freeswitch/mod -type f -exec ldd {} \; 2>/dev/null | grep 'not found'; then exit 1; fi
COPY --chown=10001:10001 entrypoint.py /opt/xhbt/entrypoint.py
ENV LD_LIBRARY_PATH=/usr/local/lib:/usr/local/freeswitch/lib
USER 10001:10001
WORKDIR /usr/local/freeswitch
EXPOSE 5060/tcp 5060/udp 5066/tcp 8021/tcp 20000-20199/udp
ENTRYPOINT ["python3", "/opt/xhbt/entrypoint.py"]
