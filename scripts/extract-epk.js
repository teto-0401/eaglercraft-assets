#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

function readByteLengthString(data, offset) {
  const length = data[offset++];
  return {
    value: data.subarray(offset, offset + length).toString('utf8'),
    nextOffset: offset + length,
  };
}

function readShort(data, offset) {
  return (data[offset] << 8) | data[offset + 1];
}

function readInt(data, offset) {
  return (data[offset] << 24) | (data[offset + 1] << 16) | (data[offset + 2] << 8) | data[offset + 3];
}

function crc32(data) {
  const table = (() => {
    const t = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
      let tmp = i;
      for (let k = 0; k < 8; k++) {
        tmp = (tmp & 1) ? (0xEDB88320 ^ (tmp >>> 1)) : (tmp >>> 1);
      }
      t[i] = tmp >>> 0;
    }
    return t;
  })();
  let crc = 0xFFFFFFFF;
  for (const byte of data) {
    crc = (crc >>> 8) ^ table[(crc ^ byte) & 0xFF];
  }
  return (crc ^ 0xFFFFFFFF) >>> 0;
}

function parseEpk(inputPath, outputDir) {
  const data = fs.readFileSync(inputPath);
  if (!data.subarray(0, 8).toString('ascii').startsWith('EAGPKG$$')) {
    throw new Error('Unsupported EPK header');
  }

  let offset = 8;
  const versionInfo = readByteLengthString(data, offset);
  offset = versionInfo.nextOffset;

  const packageName = readByteLengthString(data, offset);
  offset = packageName.nextOffset;

  const commentLength = readShort(data, offset);
  offset += 2 + commentLength;

  offset += 8;

  const numFiles = readInt(data, offset);
  offset += 4;
  const compressionType = String.fromCharCode(data[offset++]);

  let payload = data.subarray(offset);
  if (compressionType === 'Z' || compressionType === 'G') {
    payload = zlib.inflateSync(payload);
  }

  const entries = [];
  let cursor = 0;
  while (cursor < payload.length) {
    const type = payload.subarray(cursor, cursor + 4).toString('ascii');
    cursor += 4;
    if (type === 'END$') {
      break;
    }

    const nameInfo = readByteLengthString(payload, cursor);
    const name = nameInfo.value;
    cursor = nameInfo.nextOffset;

    const len = readInt(payload, cursor);
    cursor += 4;

    if (type === 'HEAD') {
      cursor += len;
      entries.push({ name, type, skip: true });
      continue;
    }

    let body;
    let crc;
    if (type === 'FILE') {
      if (len < 5) {
        throw new Error(`Invalid FILE length for ${name}`);
      }
      crc = readInt(payload, cursor);
      cursor += 4;
      const bodyLength = len - 5;
      body = payload.subarray(cursor, cursor + bodyLength);
      cursor += bodyLength;
      if (payload[cursor++] !== 0x3A) {
        throw new Error(`Bad record terminator for ${name}`);
      }
      if (payload[cursor++] !== 0x3E) {
        throw new Error(`Bad record terminator for ${name}`);
      }
      if (crc !== crc32(body)) {
        throw new Error(`CRC mismatch for ${name}`);
      }
    } else {
      body = payload.subarray(cursor, cursor + len);
      cursor += len;
      if (payload[cursor++] !== 0x3A) {
        throw new Error(`Bad record terminator for ${name}`);
      }
      if (payload[cursor++] !== 0x3E) {
        throw new Error(`Bad record terminator for ${name}`);
      }
    }

    entries.push({ name, body, type });
  }

  fs.mkdirSync(outputDir, { recursive: true });
  for (const entry of entries) {
    if (entry.skip) continue;
    const targetPath = path.join(outputDir, entry.name.replace(/\\/g, '/'));
    fs.mkdirSync(path.dirname(targetPath), { recursive: true });
    fs.writeFileSync(targetPath, entry.body);
  }

  return entries.filter((entry) => !entry.skip).length;
}

const [, , inputPath, outputDir] = process.argv;
if (!inputPath || !outputDir) {
  console.error('Usage: node scripts/extract-epk.js <input.epk> <outputDir>');
  process.exit(1);
}

const resolvedInput = path.resolve(inputPath);
const resolvedOutput = path.resolve(outputDir);
const extractedCount = parseEpk(resolvedInput, resolvedOutput);
console.log(`Extracted ${extractedCount} files to ${resolvedOutput}`);
