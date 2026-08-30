import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const guide = 'https://devslab.kr/brand/open-source/';
const release = 'https://github.com/devslab-kr/oss-brand/releases/tag/v0.1.1';
const modules = [
  'ssrf-guard', 'ssrf-guard-core', 'ssrf-guard-httpclient5',
  'ssrf-guard-restclient', 'ssrf-guard-resttemplate', 'ssrf-guard-webclient',
  'ssrf-guard-feign', 'ssrf-guard-llm', 'ssrf-guard-springai',
  'ssrf-guard-langchain4j', 'ssrf-guard-jdkhttp', 'ssrf-guard-okhttp',
];
const assets = {
  '.github/assets/readme-header.png': '0410fc3688062fbc8dceb70e73cd9a11b89dae6f0f106484086f2d5000f90eb4',
  '.github/assets/social-preview.png': '2f4d27f7efc0259305275015e6126f86679c109a633c8686c93788dc29ea91f5',
  '.github/assets/project-mark.svg': '3fb2aa4ee5eb677a313790162b9f98cfb83ff618ba2128af0ad7ef9e87b9bb1f',
  'docs/assets/logo.svg': '3fb2aa4ee5eb677a313790162b9f98cfb83ff618ba2128af0ad7ef9e87b9bb1f',
  'docs/assets/favicon.svg': '3fb2aa4ee5eb677a313790162b9f98cfb83ff618ba2128af0ad7ef9e87b9bb1f',
  'docs/assets/social-preview.png': '2f4d27f7efc0259305275015e6126f86679c109a633c8686c93788dc29ea91f5',
  'docs/assets/brand/project-lockup.svg': '12621131d857d6d1787bdad3fc615594fca256a567bf8b7bfee4a57eae85ff0b',
};

let failed = false;
function fail(message) {
  console.error(`Brand check failed: ${message}`);
  failed = true;
}

async function text(relativePath) {
  return readFile(path.join(root, relativePath), 'utf8');
}

async function hash(relativePath) {
  return createHash('sha256').update(await readFile(path.join(root, relativePath))).digest('hex');
}

try {
  const manifest = JSON.parse(await text('docs/assets/brand/oss-brand.json'));
  if (manifest.registryId !== 'O02' || manifest.project !== 'ssrf-guard') fail('O02 manifest must identify ssrf-guard');
  if (manifest.release !== 'v0.1.1' || manifest.source !== release || manifest.guide !== guide) {
    fail('O02 manifest must pin oss-brand v0.1.1 and the canonical guide');
  }
} catch {
  fail('missing or invalid docs/assets/brand/oss-brand.json');
}

for (const [relativePath, expected] of Object.entries(assets)) {
  try {
    if (await hash(relativePath) !== expected) fail(`${relativePath} does not match oss-brand v0.1.1`);
  } catch {
    fail(`missing ${relativePath}`);
  }
}

for (const relativePath of ['README.md', 'README.ko.md']) {
  try {
    const content = await text(relativePath);
    if (!content.includes('.github/assets/readme-header.png') || !content.includes(guide) || !content.includes('Registry O02')) {
      fail(`${relativePath} must carry the O02 header, guide, and registry ID`);
    }
  } catch {
    fail(`missing ${relativePath}`);
  }
}

try {
  const settings = await text('settings.gradle.kts');
  const readmes = await Promise.all(['README.md', 'README.ko.md'].map(text));
  if (modules.length !== 12) fail('O02 must define exactly twelve compatible published modules');
  for (const moduleName of modules) {
    if (!settings.includes(`"${moduleName}"`)) fail(`settings.gradle.kts is missing ${moduleName}`);
    for (const readme of readmes) if (!readme.includes(`\`${moduleName}\``)) fail(`README module matrix is missing ${moduleName}`);
  }
  if (modules.includes('ssrf-guard-benchmarks')) fail('benchmarks must not count as a compatible published module');
} catch {
  fail('could not verify twelve-module compatibility');
}

try {
  const logo = await text('docs/assets/logo.svg');
  for (const geometry of [
    'data-oss-project="O02"', 'M4 16H11', 'M21 16H28',
    'M11 7H21V16C21 21 17.7 24.8 16 26C14.3 24.8 11 21 11 16Z', 'M14 16H18',
  ]) if (!logo.includes(geometry)) fail(`O02 protected-boundary geometry is missing ${geometry}`);
} catch {
  fail('could not verify O02 protected-boundary geometry');
}

try {
  const config = await text('mkdocs.yml');
  for (const marker of ['logo: assets/logo.svg', 'favicon: assets/favicon.svg', 'custom_dir: docs/overrides', 'extra.css']) {
    if (!config.includes(marker)) fail(`mkdocs.yml is missing ${marker}`);
  }
  const override = await text('docs/overrides/main.html');
  for (const marker of ['social-preview.png', 'ssrf-guard O02', guide]) {
    if (!override.includes(marker)) fail(`docs override is missing ${marker}`);
  }
  const css = await text('docs/stylesheets/extra.css');
  for (const marker of ['hero-atmosphere', 'rgb(34 211 238 / .10)', '@media (prefers-color-scheme: dark)']) {
    if (!css.includes(marker)) fail(`docs atmosphere is missing ${marker}`);
  }
} catch {
  fail('could not verify MkDocs source-only branding');
}

if (failed) process.exitCode = 1;
else console.log('Brand check passed: ssrf-guard O02 assets, geometry, modules, and MkDocs sources match oss-brand v0.1.1.');
