## Certificate renewal
The current certificate for `localhost.localstack.cloud` and several of its subdomains are stored in `server.key`.

The file contains both certificate and private key.

### Limitations
Please make sure to conform to the [LetsEncrypt Rate Limits](https://letsencrypt.org/docs/rate-limits/).

Most notably, do not rerequest the certficate for the same set of domain names more than 5 times a week, for new domain names more than 50 times a week and no more than 100 names in total per certificate.


### Domain lists

* `certificate-domains` contains a list (newline separated) with all domains the certificate should be valid for. It allows `{region}` as placeholder, to generate domains for multiple regions.
* `certificate-regions` contains a list (newline separated) of all regions which will be substituted for the `{region}` placeholder.


### Timeline

The certificate renewal will happen every time the domain/region list are updated, or every month.