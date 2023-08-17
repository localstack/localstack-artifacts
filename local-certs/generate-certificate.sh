#!/bin/bash
set -euo pipefail

# generate comma separated list of all domains to request a cert for
certificate_domains=$(python generate-domains.py)
echo "Generating certificate for domains ${certificate_domains}"

# create credentials file
echo "dns_gandi_api_key=${DNS_API_KEY}" > gandi.ini
chmod 600 gandi.ini

# request certificate
set -x
# TODO replace with `certbot` after trial run
certbot -n --agree-tos --email ${CERTBOT_EMAIL} ${CERTBOT_ARGS} --authenticator dns-gandi --dns-gandi-credentials gandi.ini --work-dir=$PWD/work --config-dir=$PWD/config --logs-dir=$PWD/logs -d $certificate_domains certonly
set +x

# remove credentials to avoid accidental leakage
rm gandi.ini

# concatinate private key + cert into single file to match current structure
echo "Concatinating certificate and key into single file"
cat config/live/localhost.localstack.cloud/privkey.pem config/live/localhost.localstack.cloud/fullchain.pem > server.key

# display certificate information
echo "Certificate information"
openssl x509 -in server.key -text -noout
