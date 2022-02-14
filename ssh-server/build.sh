#!/bin/bash

docker build -t tmp-ssh .
docker run -it --rm -d --name tmp-ssh tmp-ssh sleep 9

docker cp tmp-ssh:/tmp/dropbear/dropbear dropbear
chmod +x dropbear

docker cp tmp-ssh:/usr/bin/scp scp
chmod +x scp
