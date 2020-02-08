#!/bin/bash

# for Linux:
# genisoimage -output seed.iso -volid cidata -joliet -rock user-data meta-data

# for MacOS:
hdiutil makehybrid -o seed.iso -hfs -joliet -iso -default-volume-name cidata seedconfig/
