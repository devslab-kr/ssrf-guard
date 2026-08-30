import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const guide = 'https://devslab.kr/brand/open-source/';
const release = 'https://github.com/devslab-kr/oss-brand/releases/tag/v0.2.0';
const modules = [
  'ssrf-guard', 'ssrf-guard-core', 'ssrf-guard-httpclient5',
  'ssrf-guard-restclient', 'ssrf-guard-resttemplate', 'ssrf-guard-webclient',
  'ssrf-guard-feign', 'ssrf-guard-llm', 'ssrf-guard-springai',
  'ssrf-guard-langchain4j', 'ssrf-guard-jdkhttp', 'ssrf-guard-okhttp',
];
const assets = {
  '.github/assets/readme-header.png': 'b04fca4d521c405c9a2440ff35d4180cfff16a56c7cb7550ec3dde3b723f5840',
  '.github/assets/social-preview.png': '2b1e72babbf82f209bc5c5b841bac6ec3f4fb2d8e71bf588e5a0c01ddbdb3a36',
  '.github/assets/project-mark.svg': '857816541c044b09a8ba15306eea84fb0ec4e4803bfbcccb3ff3b88760dfd2b0',
  'docs/assets/logo.svg': '857816541c044b09a8ba15306eea84fb0ec4e4803bfbcccb3ff3b88760dfd2b0',
  'docs/assets/favicon.svg': '857816541c044b09a8ba15306eea84fb0ec4e4803bfbcccb3ff3b88760dfd2b0',
  'docs/assets/social-preview.png': '2b1e72babbf82f209bc5c5b841bac6ec3f4fb2d8e71bf588e5a0c01ddbdb3a36',
  'docs/assets/brand/project-lockup.svg': '0cdac2cfb8c17ed63da4da917ce8eed473e6d52b12bcef2f0c4faccdb6f4aa01',
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
  if (manifest.release !== 'v0.2.0' || manifest.source !== release || manifest.guide !== guide) {
    fail('O02 manifest must pin oss-brand v0.2.0 and the canonical guide');
  }
} catch {
  fail('missing or invalid docs/assets/brand/oss-brand.json');
}

for (const [relativePath, expected] of Object.entries(assets)) {
  try {
    if (await hash(relativePath) !== expected) fail(`${relativePath} does not match oss-brand v0.2.0`);
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
    'data-oss-project="O02"', 'data-layer="q-frame"',
    '<rect x="5" y="5" width="16" height="16" rx="2"',
    '<rect x="11" y="11" width="16" height="16" rx="2"',
    'data-layer="product-route"', 'M13 18H17', 'M21 18H25', 'M19 14V22',
  ]) if (!logo.includes(geometry)) fail(`O02 Q-line geometry is missing ${geometry}`);
} catch {
  fail('could not verify O02 Q-line geometry');
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
  const slateGlow = /body\[data-md-color-scheme="slate"\]\s+\.hero-atmosphere > \[aria-hidden="true"\]\.hero-atmosphere__glow\s*\{([\s\S]*?)\n\}/.exec(css)?.[1] ?? '';
  if (!css.includes('body[data-md-color-scheme="slate"]')) {
    fail('docs atmosphere must respond to Material slate color scheme on its body DOM target');
  }
  if (!slateGlow.includes('rgb(34 211 238 / .10)')) {
    fail('Material slate hero glow must use cyan capped at 10% alpha');
  }
  if (/rgb\(34 211 238 \/ \.(?:1[1-9]|[2-9]\d)\)/.test(slateGlow)) {
    fail('Material slate hero glow must not exceed 10% cyan alpha');
  }
} catch {
  fail('could not verify MkDocs source-only branding');
}

if (failed) process.exitCode = 1;
else console.log('Brand check passed: ssrf-guard O02 assets, geometry, modules, and MkDocs sources match oss-brand v0.2.0.');
