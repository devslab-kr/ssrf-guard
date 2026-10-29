import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { gunzipSync } from 'node:zlib';
import { execFileSync } from 'node:child_process';

const MAX_TAR_BYTES = 32 * 1024 * 1024;
const inside = (parent, child) => {
  const rel = path.relative(parent, child);
  return rel !== '' && !rel.startsWith('..' + path.sep) && rel !== '..' && !path.isAbsolute(rel);
};

export function verifyArtifact(bytes, pin) {
  if (!/^[a-f0-9]{64}$/.test(pin.sha256 ?? '') ||
      !/^sha512-[A-Za-z0-9+/]+={0,2}$/.test(pin.integrity ?? '')) {
    throw new Error('DDS artifact pin must contain SHA256 and sha512 integrity');
  }
  const sha256 = createHash('sha256').update(bytes).digest('hex');
  const integrity = 'sha512-' + createHash('sha512').update(bytes).digest('base64');
  if (sha256 !== pin.sha256 || integrity !== pin.integrity) {
    throw new Error('DDS artifact integrity mismatch');
  }
}

export function extractPackage(bytes, destination) {
  const tar = gunzipSync(bytes, { maxOutputLength: MAX_TAR_BYTES });
  const seen = new Set();
  let offset = 0, count = 0, ended = false;
  const string = (block, start, length) => block.subarray(start, start + length).toString('utf8').replace(/\0.*$/s, '');
  const octal = value => {
    const text = value.replace(/\0.*$/s, '').trim();
    if (!/^[0-7]+$/.test(text)) throw new Error('Unsupported tar numeric field');
    return Number.parseInt(text, 8);
  };
  while (offset + 512 <= tar.length) {
    const header = tar.subarray(offset, offset + 512);
    if (header.every(byte => byte === 0)) {
      if (tar.subarray(offset).some(byte => byte !== 0)) throw new Error('Unexpected data after tar trailer');
      ended = true;
      break;
    }
    if (++count > 512) throw new Error('DDS archive has too many entries');
    const checksum = octal(string(header, 148, 8));
    const actual = header.reduce((sum, byte, i) => sum + (i >= 148 && i < 156 ? 32 : byte), 0);
    if (checksum !== actual) throw new Error('Invalid tar header checksum');
    const name = [string(header, 345, 155), string(header, 0, 100)].filter(Boolean).join('/');
    const type = String.fromCharCode(header[156]);
    const size = octal(string(header, 124, 12));
    if (!Number.isSafeInteger(size) || size < 0 || size > MAX_TAR_BYTES ||
        offset + 512 + size > tar.length) throw new Error('Invalid tar entry size');
    if (!['0', '\0', '5'].includes(type)) throw new Error('DDS archive links and extended entry types are forbidden');
    if (name.includes('\\') || name.startsWith('/') || /^[A-Za-z]:/.test(name) ||
        name.split('/').some(part => part === '..' || part === '.')) {
      throw new Error('Unsafe DDS archive path');
    }
    if (name !== 'package/' && name !== 'package' && !name.startsWith('package/')) {
      throw new Error('DDS archive entries must use the npm package/ prefix');
    }
    const relative = name.replace(/^package\/?/, '').replace(/\/$/, '');
    if (relative) {
      const target = path.resolve(destination, ...relative.split('/'));
      if (!inside(path.resolve(destination), target) || seen.has(relative)) throw new Error('Unsafe or duplicate DDS archive path');
      seen.add(relative);
      if (type === '5') {
        if (size !== 0) throw new Error('Invalid tar directory size');
        fs.mkdirSync(target, { recursive: true });
      } else {
        fs.mkdirSync(path.dirname(target), { recursive: true });
        fs.writeFileSync(target, tar.subarray(offset + 512, offset + 512 + size), { flag: 'wx' });
      }
    } else if (type !== '5') {
      throw new Error('DDS archive package root must be a directory');
    }
    offset += 512 + Math.ceil(size / 512) * 512;
  }
  if (!ended || !count) throw new Error('Incomplete DDS tar archive');
}

export function prepare(root = fileURLToPath(new URL('../../', import.meta.url))) {
  const tooling = path.join(root, 'tools', 'fonts');
  const pin = JSON.parse(fs.readFileSync(path.join(tooling, 'artifact.json'), 'utf8'));
  const archive = path.resolve(tooling, pin.file ?? '');
  const vendor = path.join(tooling, 'vendor');
  if (!inside(vendor, archive) || path.extname(archive) !== '.tgz') throw new Error('DDS artifact must be vendored under tools/fonts/vendor');
  const bytes = fs.readFileSync(archive);
  verifyArtifact(bytes, pin);
  const cache = path.join(root, '.cache', 'dds-fonts');
  fs.mkdirSync(cache, { recursive: true });
  const temporary = fs.mkdtempSync(path.join(cache, 'package-'));
  try {
    extractPackage(bytes, temporary);
    const pkg = JSON.parse(fs.readFileSync(path.join(temporary, 'package.json'), 'utf8'));
    if (pkg.name !== '@devslab/family-fonts' || pkg.private !== true || pkg.version !== pin.version) throw new Error('Unexpected DDS font package identity');
    execFileSync(process.execPath, [
      path.join(temporary, 'export.mjs'),
      '--out-dir', 'docs/assets/dds-fonts',
      '--base-url', '/assets/dds-fonts',
      '--head-output', 'docs/overrides/partials/dds-fonts.html',
      '--monospace',
    ], { cwd: root, stdio: 'inherit' });
  } finally {
    if (!inside(cache, temporary) || !path.basename(temporary).startsWith('package-')) throw new Error('Unsafe DDS temporary cleanup target');
    fs.rmSync(temporary, { recursive: true, force: true });
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { prepare(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}
