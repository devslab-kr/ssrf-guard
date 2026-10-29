import assert from 'node:assert/strict';
import { test } from 'node:test';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { gzipSync } from 'node:zlib';
import { createHash } from 'node:crypto';
import { extractPackage, verifyArtifact } from './prepare.mjs';

function archive(entries) {
  const blocks = [];
  for (const [name, body = '', type = '0'] of entries) {
    const bytes = Buffer.from(body), h = Buffer.alloc(512);
    h.write(name, 0, 100);
    h.write('0000644\0', 100); h.write('0000000\0', 108); h.write('0000000\0', 116);
    h.write(bytes.length.toString(8).padStart(11, '0') + '\0', 124);
    h.write('00000000000\0', 136); h.fill(32, 148, 156); h.write(type, 156);
    h.write('ustar\0', 257); h.write('00', 263);
    const checksum = h.reduce((sum, value) => sum + value, 0);
    h.write(checksum.toString(8).padStart(6, '0') + '\0 ', 148);
    blocks.push(h, bytes, Buffer.alloc((512 - bytes.length % 512) % 512));
  }
  return gzipSync(Buffer.concat([...blocks, Buffer.alloc(1024)]));
}

function temporary(run) {
  const base = fs.realpathSync(os.tmpdir());
  const dir = fs.mkdtempSync(path.join(base, 'dds-font-contract-'));
  try { return run(dir); } finally {
    assert.equal(path.dirname(dir), base);
    assert.ok(path.basename(dir).startsWith('dds-font-contract-'));
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

test('verified npm archive preserves complete file bytes', () => temporary(dir => {
  const bytes = archive([['package/package.json', '{"name":"@devslab/family-fonts"}'], ['package/assets/', '', '5'], ['package/assets/font.woff2', 'complete-font-bytes']]);
  const pin = { sha256: createHash('sha256').update(bytes).digest('hex'), integrity: 'sha512-' + createHash('sha512').update(bytes).digest('base64') };
  verifyArtifact(bytes, pin);
  extractPackage(bytes, dir);
  assert.equal(fs.readFileSync(path.join(dir, 'assets/font.woff2'), 'utf8'), 'complete-font-bytes');
  assert.throws(() => verifyArtifact(Buffer.concat([bytes, Buffer.from('tampered')]), pin), /integrity mismatch/);
  assert.throws(() => verifyArtifact(bytes, { sha256: pin.sha256 }), /pin/);
}));

test('path traversal, links, extended entries and duplicate files are rejected', () => {
  const entries = [
    [['package/../../outside', 'blocked']],
    [['/outside', 'blocked']],
    [['package\\..\\outside', 'blocked']],
    [['other/file', 'blocked']],
    [['package/link', '', '2']],
    [['package/link', '', '1']],
    [['package/pax', '', 'x']],
    [['package/file', 'first'], ['package/file', 'second']],
  ];
  for (const entry of entries) temporary(dir => assert.throws(() => extractPackage(archive(entry), dir)));
});

test('corrupt checksum and truncated archives fail closed', () => {
  // Alter the tar header while retaining a valid gzip envelope.
  const bytes = archive([['package/file', 'data']]);
  return import('node:zlib').then(({ gunzipSync }) => {
    const raw = gunzipSync(bytes); raw[0] ^= 1;
    temporary(dir => assert.throws(() => extractPackage(gzipSync(raw), dir), /checksum/));
    temporary(dir => assert.throws(() => extractPackage(gzipSync(Buffer.alloc(511)), dir), /Incomplete/));
  });
});
