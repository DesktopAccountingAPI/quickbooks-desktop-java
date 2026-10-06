#!/usr/bin/env node
// Maven Central release for this SDK through the Central Portal Publisher API.
//
//   node scripts/publish.mjs --dry-run [--ephemeral-key]   build the signed bundle locally, upload nothing
//   node scripts/publish.mjs --publish [--tag vX.Y.Z]      upload, validate, verify, publish, wait for repo1
//   node scripts/publish.mjs --verify-consumer             compile and run a smoke program against the published artifact
//
// Environment (publish only; never printed, never passed on a command line):
//   MAVEN_CENTRAL_USERNAME, MAVEN_CENTRAL_PASSWORD  Central Portal user token
//   GPG_PRIVATE_KEY                                 base64 of the ASCII-armored private signing key
//   GPG_PASSPHRASE                                  its passphrase (fed to gpg on stdin)
//
// The bundle follows the Central layout: <group path>/<artifact>/<version>/ with the jar, sources jar,
// javadoc jar and pom, each with .asc, .md5 and .sha1 (plus .sha256/.sha512).

import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const FINGERPRINT = "E8BA0F958F4F222C90B03A7565C74895CD6F4C1E";
const CENTRAL = "https://central.sonatype.com/api/v1/publisher";
const REPO1 = "https://repo1.maven.org/maven2";
const IS_WINDOWS = process.platform === "win32";

const args = process.argv.slice(2);
const flag = (name) => args.includes(`--${name}`);
const opt = (name) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : undefined;
};

function log(msg) {
  console.log(`[publish] ${msg}`);
}

function fail(msg) {
  console.error(`[publish] ERROR: ${msg}`);
  process.exit(1);
}

function run(cmd, cmdArgs, options = {}) {
  const r = spawnSync(cmd, cmdArgs, { cwd: ROOT, stdio: options.input !== undefined ? ["pipe", "pipe", "pipe"] : ["ignore", "pipe", "pipe"], encoding: "utf8", shell: IS_WINDOWS && !cmd.includes("gpg"), ...options });
  if (r.error) throw r.error;
  if (r.status !== 0 && !options.allowFailure) {
    throw new Error(`${cmd} ${cmdArgs.filter((a) => !a.startsWith("-----")).join(" ")} failed (${r.status}): ${(r.stderr || r.stdout || "").trim().slice(0, 2000)}`);
  }
  return r;
}

function coordinates() {
  const pom = readFileSync(join(ROOT, "pom.xml"), "utf8").replace(/<parent>[\s\S]*?<\/parent>/, "");
  const head = pom.slice(0, pom.search(/<(dependencies|build|properties)>/));
  const tag = (t) => {
    const m = new RegExp(`<${t}>([^<]+)</${t}>`).exec(head);
    if (!m) fail(`pom.xml has no project ${t}`);
    return m[1].trim();
  };
  return { group: tag("groupId"), artifact: tag("artifactId"), version: tag("version") };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function digest(algo, file) {
  return createHash(algo).update(readFileSync(file)).digest("hex");
}

// ---------------------------------------------------------------------------- signing

/** A private, temporary GNUPGHOME holding only the release key. Deleted by cleanup(). */
function createKeyring({ ephemeral }) {
  const home = mkdtempSync(join(tmpdir(), "daapi-gpg-"));
  if (!IS_WINDOWS) chmodSync(home, 0o700);
  const env = { ...process.env, GNUPGHOME: home };
  let passphrase;
  let fingerprint;
  try {
  if (ephemeral) {
    // Local test of the signing path with a throwaway key (never uploaded).
    passphrase = createHash("sha256").update(String(Math.random()) + Date.now()).digest("hex");
    run("gpg", ["--batch", "--pinentry-mode", "loopback", "--passphrase-fd", "0", "--quick-gen-key", "Ephemeral Test Key <test@example.invalid>", "ed25519", "sign", "1d"], { env, input: passphrase });
    const list = run("gpg", ["--batch", "--with-colons", "--list-secret-keys"], { env }).stdout;
    fingerprint = /^fpr:+([0-9A-F]{40}):/m.exec(list)[1];
  } else {
    const encoded = process.env.GPG_PRIVATE_KEY;
    passphrase = process.env.GPG_PASSPHRASE;
    if (!encoded) throw new Error("GPG_PRIVATE_KEY is not set");
    if (passphrase === undefined) throw new Error("GPG_PASSPHRASE is not set");
    const keyFile = join(home, "release-key.asc");
    writeFileSync(keyFile, Buffer.from(encoded.replace(/\s+/g, ""), "base64"), { mode: 0o600 });
    run("gpg", ["--batch", "--import", keyFile], { env });
    rmSync(keyFile, { force: true });
    fingerprint = FINGERPRINT;
    const list = run("gpg", ["--batch", "--with-colons", "--list-secret-keys"], { env }).stdout;
    if (!list.includes(`fpr:::::::::${FINGERPRINT}:`)) throw new Error(`the imported key is not ${FINGERPRINT}`);
  }
  } catch (e) {
    spawnSync("gpgconf", ["--kill", "gpg-agent"], { env, stdio: "ignore" });
    rmSync(home, { recursive: true, force: true });
    throw e;
  }
  return {
    fingerprint,
    sign(file) {
      run("gpg", ["--batch", "--yes", "--pinentry-mode", "loopback", "--passphrase-fd", "0", "--local-user", fingerprint, "--armor", "--detach-sign", "--output", `${file}.asc`, file], { env, input: passphrase });
    },
    verify(file) {
      const r = run("gpg", ["--batch", "--status-fd", "1", "--verify", `${file}.asc`, file], { env, allowFailure: true });
      // VALIDSIG <signing key fpr> ... <primary key fpr>
      const m = /^\[GNUPG:\] VALIDSIG (\S+) .* (\S+)$/m.exec(r.stdout);
      if (r.status !== 0 || !m || (m[1] !== fingerprint && m[2] !== fingerprint)) throw new Error(`signature check failed for ${file}`);
    },
    cleanup() {
      spawnSync("gpgconf", ["--kill", "gpg-agent"], { env, stdio: "ignore" });
      rmSync(home, { recursive: true, force: true });
    },
  };
}

// ---------------------------------------------------------------------------- bundle

function buildBundle(c, keyring) {
  const target = join(ROOT, "target");
  const base = `${c.artifact}-${c.version}`;
  const sources = { [`${base}.jar`]: join(target, `${base}.jar`), [`${base}-sources.jar`]: join(target, `${base}-sources.jar`), [`${base}-javadoc.jar`]: join(target, `${base}-javadoc.jar`) };
  if (Object.values(sources).some((f) => !existsSync(f))) {
    log("building jars (mvn package -DskipTests)");
    run("mvn", ["-B", "-ntp", "-q", "-DskipTests", "package"]);
  }
  const staging = join(target, "central-bundle");
  rmSync(staging, { recursive: true, force: true });
  const dir = join(staging, ...c.group.split("."), c.artifact, c.version);
  mkdirSync(dir, { recursive: true });
  for (const [name, from] of Object.entries(sources)) copyFileSync(from, join(dir, name));
  copyFileSync(join(ROOT, "pom.xml"), join(dir, `${base}.pom`));
  const artifacts = readdirSync(dir).sort();
  for (const name of artifacts) {
    const file = join(dir, name);
    if (keyring) {
      keyring.sign(file);
      keyring.verify(file);
    }
    for (const algo of ["md5", "sha1", "sha256", "sha512"]) writeFileSync(`${file}.${algo}`, digest(algo, file));
  }
  const zip = join(target, `${base}-bundle.zip`);
  rmSync(zip, { force: true });
  run("jar", ["--create", "--no-manifest", "--file", zip, "-C", staging, "."]);
  return { zip, dir, files: readdirSync(dir).sort() };
}

// ---------------------------------------------------------------------------- Central Portal

function authHeader() {
  const user = process.env.MAVEN_CENTRAL_USERNAME;
  const pass = process.env.MAVEN_CENTRAL_PASSWORD;
  if (!user || !pass) fail("MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD must be set");
  return `Bearer ${Buffer.from(`${user}:${pass}`).toString("base64")}`;
}

async function http(method, url, { auth, body, expect = [200] } = {}) {
  const headers = auth ? { Authorization: auth } : {};
  const res = await fetch(url, { method, headers, body });
  const text = await res.text();
  if (!expect.includes(res.status)) throw new Error(`${method} ${url.replace(/\?.*/, "")} -> HTTP ${res.status}: ${text.slice(0, 2000)}`);
  return { status: res.status, text };
}

async function onRepo1(c) {
  const url = `${REPO1}/${c.group.replace(/\./g, "/")}/${c.artifact}/${c.version}/${c.artifact}-${c.version}.pom`;
  const res = await fetch(url, { method: "HEAD" });
  return res.status === 200;
}

async function publishedOnCentral(c, auth) {
  const q = new URLSearchParams({ namespace: c.group, name: c.artifact, version: c.version });
  const r = await http("GET", `${CENTRAL}/published?${q}`, { auth, expect: [200, 404] });
  if (r.status !== 200) return false;
  try {
    return JSON.parse(r.text).published === true;
  } catch {
    return false;
  }
}

async function waitForState(id, auth, wanted, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let last = "";
  while (Date.now() < deadline) {
    const r = await http("POST", `${CENTRAL}/status?id=${encodeURIComponent(id)}`, { auth });
    const s = JSON.parse(r.text);
    if (s.deploymentState !== last) log(`deployment ${id}: ${s.deploymentState}`);
    last = s.deploymentState;
    if (s.deploymentState === "FAILED") throw new Error(`deployment failed: ${JSON.stringify(s.errors ?? s, null, 2)}`);
    if (wanted.includes(s.deploymentState)) return s;
    await sleep(10_000);
  }
  throw new Error(`timed out waiting for ${wanted.join("/")} (last state ${last})`);
}

async function verifyStaged(c, auth, bundle) {
  // Download the staged jar through the deployment download API and compare it with the local build.
  const rel = `${c.group.replace(/\./g, "/")}/${c.artifact}/${c.version}/${c.artifact}-${c.version}.jar`;
  try {
    const res = await fetch(`${CENTRAL}/deployments/download/${rel}`, { headers: { Authorization: auth } });
    if (res.status !== 200) {
      log(`staged artifact download returned HTTP ${res.status}; relying on the local signature and checksum checks`);
      return;
    }
    const remote = createHash("sha1").update(Buffer.from(await res.arrayBuffer())).digest("hex");
    const local = readFileSync(join(bundle.dir, `${c.artifact}-${c.version}.jar.sha1`), "utf8").trim();
    if (remote !== local) throw new Error(`staged jar sha1 ${remote} differs from the local build ${local}`);
    log("staged jar matches the local build");
  } catch (e) {
    if (String(e.message).startsWith("staged jar")) throw e;
    log(`could not download the staged jar (${e.message}); relying on the local signature and checksum checks`);
  }
}

async function waitForRepo1(c, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await onRepo1(c)) {
      log(`${c.group}:${c.artifact}:${c.version} is on repo1.maven.org`);
      return;
    }
    await sleep(30_000);
  }
  throw new Error(`${c.group}:${c.artifact}:${c.version} did not appear on repo1.maven.org within ${Math.round(timeoutMs / 60000)} minutes`);
}

// ---------------------------------------------------------------------------- consumer smoke test

function verifyConsumer(c) {
  const dir = mkdtempSync(join(tmpdir(), "daapi-consumer-"));
  try {
    const src = join(dir, "src", "main", "java", "smoke");
    mkdirSync(src, { recursive: true });
    writeFileSync(join(dir, "pom.xml"), `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>smoke</groupId>
  <artifactId>consumer</artifactId>
  <version>1</version>
  <properties>
    <maven.compiler.release>11</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>${c.group}</groupId>
      <artifactId>${c.artifact}</artifactId>
      <version>${c.version}</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.6.4</version>
        <configuration>
          <mainClass>smoke.Main</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
`);
    writeFileSync(join(src, "Main.java"), `package smoke;

import com.desktopaccountingapi.quickbooksdesktop.DesktopAccountingApiClient;
import com.desktopaccountingapi.quickbooksdesktop.core.SdkInfo;
import com.desktopaccountingapi.quickbooksdesktop.webhooks.WebhookVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        if (!"${c.version}".equals(SdkInfo.VERSION)) throw new IllegalStateException("version " + SdkInfo.VERSION);
        DesktopAccountingApiClient client = DesktopAccountingApiClient.builder()
            .apiKey("sk_test_Conformance0Key0For0SDK0Tests000010nQFLR").baseUrl("http://127.0.0.1:9").build();
        WebhookVerifier.builder().clock(Clock.fixed(Instant.ofEpochSecond(1614265330L), ZoneOffset.UTC)).build().verifySignature(
            "{\\"test\\": 2432232314}",
            Map.of("webhook-id", "msg_p5jXN8AQM9LWM0D4loKWxJek", "webhook-timestamp", "1614265330",
                "webhook-signature", "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE="),
            "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw");
        System.out.println("consumer smoke ok: " + SdkInfo.USER_AGENT + " " + client.options().baseUrl());
    }
}
`);
    const r = run("mvn", ["-B", "-ntp", "-q", "-U", "-f", join(dir, "pom.xml"), "compile", "exec:java"], { cwd: dir });
    process.stdout.write(r.stdout);
    if (!r.stdout.includes("consumer smoke ok")) throw new Error("consumer smoke program printed no success line");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

// ---------------------------------------------------------------------------- main

async function main() {
  const c = coordinates();
  log(`${c.group}:${c.artifact}:${c.version}`);
  const tag = opt("tag") ?? process.env.RELEASE_TAG;
  if (tag && tag !== `v${c.version}`) fail(`tag ${tag} does not match the pom version ${c.version} (expected v${c.version})`);

  if (flag("verify-consumer")) {
    if (!(await onRepo1(c))) fail(`${c.version} is not on repo1.maven.org yet`);
    verifyConsumer(c);
    return;
  }

  if (flag("dry-run")) {
    const haveKey = Boolean(process.env.GPG_PRIVATE_KEY);
    const ephemeral = flag("ephemeral-key");
    let keyring;
    try {
      if (ephemeral || haveKey) keyring = createKeyring({ ephemeral });
      else log("no GPG_PRIVATE_KEY: building an unsigned bundle (signing skipped)");
      const bundle = buildBundle(c, keyring);
      log(`bundle ${bundle.zip}`);
      for (const f of bundle.files) log(`  ${c.group.replace(/\./g, "/")}/${c.artifact}/${c.version}/${f}`);
      log(`dry run: would upload with publishingType=USER_MANAGED to ${CENTRAL}/upload, then validate, publish and wait for repo1`);
    } finally {
      keyring?.cleanup();
    }
    return;
  }

  if (!flag("publish")) fail("usage: node scripts/publish.mjs --dry-run | --publish [--tag vX.Y.Z] | --verify-consumer");

  if (await onRepo1(c)) {
    log(`${c.version} is already on repo1.maven.org; nothing to upload`);
    return;
  }
  const auth = authHeader();
  if (await publishedOnCentral(c, auth)) {
    log(`${c.version} is already published on Central; waiting for repo1`);
    await waitForRepo1(c, 60 * 60_000);
    return;
  }
  const keyring = createKeyring({ ephemeral: false });
  let bundle;
  try {
    bundle = buildBundle(c, keyring);
  } finally {
    keyring.cleanup();
  }
  log(`uploading ${bundle.files.length} files`);
  const form = new FormData();
  form.append("bundle", new Blob([readFileSync(bundle.zip)]), `${c.artifact}-${c.version}-bundle.zip`);
  const name = encodeURIComponent(`${c.artifact}-${c.version}`);
  const up = await http("POST", `${CENTRAL}/upload?publishingType=USER_MANAGED&name=${name}`, { auth, body: form, expect: [200, 201] });
  const id = up.text.trim();
  log(`deployment ${id} uploaded`);
  await waitForState(id, auth, ["VALIDATED"], 30 * 60_000);
  await verifyStaged(c, auth, bundle);
  await http("POST", `${CENTRAL}/deployment/${encodeURIComponent(id)}`, { auth, expect: [200, 204] });
  log(`deployment ${id} published; waiting for PUBLISHED`);
  await waitForState(id, auth, ["PUBLISHED"], 60 * 60_000);
  await waitForRepo1(c, 60 * 60_000);
}

main().catch((e) => fail(e.message));
