# ssrf-guard

> SSRF (Server-Side Request Forgery) protection for Spring Boot — whitelist-based outbound HTTP guard with private-network blocking, redirect validation, and TOCTOU mitigation.

## Why

A Spring Boot service that lets users supply URLs — webhook callbacks, image-fetch helpers, "import from URL," anything taking a string and turning it into an HTTP call — is one fetch away from leaking AWS credentials via `http://169.254.169.254/latest/meta-data/`, scanning an internal subnet, or invoking an admin endpoint exposed only on the loopback. SSRF was [#7 on the OWASP Top 10 2021](https://owasp.org/Top10/A10_2021-Server-Side_Request_Forgery_%28SSRF%29/) for exactly this kind of "default trust your own server" failure.

ssrf-guard puts a four-layer filter around every outbound call, runs on plain Spring Boot 3.5+, and needs nothing more than putting it on the classpath plus a whitelist in `application.yml`.

## The defenses

1. **Front-line interceptor** — string-level scheme, host, and port check on every URL before DNS is touched.
2. **DNS-time whitelist re-check** — same host policy applied a second time when the hostname is actually resolved, closing the gap a forged URL might slip through.
3. **Private-network IP filter** — loopback, RFC-1918, link-local (including AWS metadata at `169.254.169.254`), CGNAT, IPv6 ULA all blocked.
4. **Redirect re-validation** — every 3xx hop is run through the same scheme/host/IP rules. An attacker can't whitelist `example.com` and then 302 you to `169.254.169.254`.

The `InetAddress` array our DNS resolver validates is the exact same array Apache HttpClient hands to `Socket.connect()` — there's no second DNS lookup between validation and connection. That closes the TOCTOU window a naïve whitelist would otherwise leave open.

## Quick install

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>ssrf-guard</artifactId>
    <version>2.0.0</version>
</dependency>
```

```yaml
ssrf:
  guard:
    suffixes:
      - api.partner.com
      - example.org
```

Spring Boot auto-configures the rest. Continue to the [Installation guide](getting-started/installation.md) for the full reference, or jump to the [Quickstart](getting-started/quickstart.md) for a 5-minute walkthrough.

## Where it lives

- **GitHub**: <https://github.com/devslab-kr/ssrf-guard>
- **Maven Central**: <https://central.sonatype.com/artifact/kr.devslab/ssrf-guard>
- **Docs**: <https://ssrf-guard.devslab.kr/>

## License

Apache License 2.0. Part of the [DevsLab open-source toolkit](https://github.com/devslab-kr).
