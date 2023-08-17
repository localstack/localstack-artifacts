#!/usr/bin/env python3

"""
This file generates a comma-separated list of domains,
by replacing the occurences of "{region}" in the domains
in "certificate-domains" by the regions in "certificate-regions"
"""


REGION_PLACEHOLDER = "{region}"


def generate_domains(domain_list: list[str], region_list: list[str]) -> list[str]:
    result = []
    for domain in domain_list:
        if REGION_PLACEHOLDER in domain:
            result += [domain.replace(REGION_PLACEHOLDER, region) for region in region_list]
        else:
            result.append(domain)
    return result

def main():
    with open("certificate-domains", mode="rt") as f:
        domain_list = f.read().splitlines()
    with open("certificate-regions", mode="rt") as f:
        region_list = f.read().splitlines()
    expanded_list = generate_domains(domain_list, region_list)
    print(",".join(expanded_list), end="")
    

if __name__ == "__main__":
    main()
