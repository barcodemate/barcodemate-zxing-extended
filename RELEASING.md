# Releasing

What it takes to publish this to Maven Central, and what has to be decided
first. Nothing here is automated, and deliberately so: a Central release cannot
be withdrawn once published.

## Before the first release

### 1. Claim the `com.barcodemate` namespace

Central verifies a group id by proving you control the matching domain. For
`com.barcodemate` that is `barcodemate.com`, which we own.

1. Sign in at <https://central.sonatype.com> with the GitHub account that owns
   this repository.
2. **View Namespaces** → **Add Namespace** → `com.barcodemate`.
3. It gives a verification key. Add it as a DNS TXT record on
   `barcodemate.com` (Cloudflare → DNS → TXT, name `@`, content the key).
4. Back in the portal, **Verify Namespace**. Propagation is usually minutes.

Once verified the record can be removed, but leaving it costs nothing.

### 2. Create a signing key

Central requires every artifact to carry a detached GPG signature, and the
public key to be on a public keyserver.

```bash
gpg --full-generate-key          # RSA 4096, no expiry or a long one
gpg --list-secret-keys --keyid-format=long
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

Keep the private key and its passphrase somewhere they will survive this
laptop. Losing them does not break published artifacts but does mean a new key
for the next release.

### 3. Store credentials

Generate a user token in the portal (**Account** → **Generate User Token**) and
put it in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>gpg</id>
      <properties>
        <gpg.keyname>KEY_ID</gpg.keyname>
      </properties>
    </profile>
  </profiles>
</settings>
```

That file holds secrets. It belongs outside this repository, which is why it is
not in it.

## Each release

1. **Decide the version.** This project follows the usual meaning of
   semantic versioning, with one addition: the baselines it derives from are
   part of the contract. A release that moves to a newer ZXing or zxing-cpp
   baseline is at least a minor version, and says so in its notes, because the
   behaviour of the inherited formats can change with it.

2. **Run everything, including what is normally skipped.**

   ```bash
   mvn test -Dzxing.blackbox.base=upstream-zxing-java/core \
            -Dzxing.cpp.samples.base=upstream-zxing-cpp/test/samples
   ```

   The sample-image tests skip silently without those paths. A release built
   without them is a release whose format support was never checked against a
   real image.

3. **Set the version and tag.**

   ```bash
   mvn versions:set -DnewVersion=0.1.0
   git commit -am "release: 0.1.0"
   git tag -a v0.1.0 -m "0.1.0"
   ```

4. **Build, sign and upload.**

   ```bash
   mvn clean deploy -Prelease
   ```

   This uploads a *bundle* and stops. Nothing is public yet.

   If the build fails *after* reporting `Uploaded bundle successfully`, the
   upload still happened; the failure is downstream of it. Check the real state
   before rebuilding, or you will end up with two deployments of the same
   version:

   ```bash
   curl -s -X POST -H "Authorization: Bearer $(printf '%s:%s' USER PASS | base64)" \
     "https://central.sonatype.com/api/v1/publisher/status?id=DEPLOYMENT_ID"
   ```

   A `deploymentState` of `VALIDATED` means it is staged and waiting.

5. **Check it in the portal, then publish.** Look at the file list: the jar,
   the sources jar, the javadoc jar, and a `.asc` for each. Confirm the version
   is what you meant. Only then click **Publish**.

   This step is manual on purpose. `autoPublish` is off in the pom for the same
   reason.

6. **Push the tag**, and open the version back up:

   ```bash
   git push origin main --tags
   mvn versions:set -DnewVersion=0.2.0-SNAPSHOT
   ```

## What a release note has to say

This project's honesty about what is verified is worth as much as its code, and
release notes are where that is easiest to lose. Each one should state:

- the two upstream baselines, by tag and commit;
- which formats changed, and which are still marked experimental;
- for any newly supported format, what it was verified against -- ported
  upstream tests, sample images, or independently generated symbols -- and how
  many.

"MicroPDF417 support" and "MicroPDF417 support, upright symbols only, verified
against three of upstream's ten sample images with no misreads" are the same
code and very different claims.
