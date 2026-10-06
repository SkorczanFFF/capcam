#!/usr/bin/env node
// Listens for CapCam phone packets and prints rate, jitter, loss and the latest angles once per second.
// Understands both output formats: opentrack (48 bytes) and CapCam v1 (36 bytes, magic "CCAM").
//
// Usage: node tools/udp-monitor.mjs [port] [--minutes N]
//   port defaults to 4242 (opentrack); use 4243 for the CapCam format.
//   --minutes N stops after N minutes and prints a summary.

import dgram from 'node:dgram';

const args = process.argv.slice(2);
const port = Number(args.find((a) => /^\d+$/.test(a)) ?? 4242);
const minutesIdx = args.indexOf('--minutes');
const minutes = minutesIdx >= 0 ? Number(args[minutesIdx + 1]) : 0;

const OPENTRACK_SIZE = 48;
const CAPCAM_SIZE = 36;

let windowGapsMs = [];
let windowPackets = 0;
let lastArrival = 0n;
let lastSeq = null;
let latest = null;
let format = '?';

const total = { packets: 0, lost: 0, bad: 0, gapsMs: [], secondsBelow100: 0, seconds: 0, start: Date.now() };

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  return sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];
}

function capcamQuatToAngles(x, y, z, w) {
  // Same convention as the phone: R = Rz(yaw) · Rx(pitch) · Ry(roll); head x right, y forward, z up.
  const m01 = 2 * (x * y - z * w);
  const m11 = 1 - 2 * (x * x + z * z);
  const m20 = 2 * (x * z - y * w);
  const m21 = 2 * (y * z + x * w);
  const m22 = 1 - 2 * (x * x + y * y);
  const deg = 180 / Math.PI;
  return {
    yaw: Math.atan2(-m01, m11) * deg,
    pitch: Math.asin(Math.max(-1, Math.min(1, m21))) * deg,
    roll: Math.atan2(-m20, m22) * deg,
  };
}

function parse(buf) {
  if (buf.length === CAPCAM_SIZE && buf.toString('ascii', 0, 4) === 'CCAM') {
    if (buf.readUInt8(4) !== 1) return null;
    const seq = buf.readUInt32LE(8);
    const q = [20, 24, 28, 32].map((o) => buf.readFloatLE(o));
    return { format: 'CapCam v1', seq, ...capcamQuatToAngles(...q) };
  }
  if (buf.length === OPENTRACK_SIZE) {
    return {
      format: 'opentrack',
      seq: null,
      yaw: buf.readDoubleLE(24),
      pitch: buf.readDoubleLE(32),
      roll: buf.readDoubleLE(40),
    };
  }
  return null;
}

const socket = dgram.createSocket('udp4');

socket.on('message', (buf, rinfo) => {
  const now = process.hrtime.bigint();
  const p = parse(buf);
  if (!p) {
    total.bad++;
    return;
  }
  format = `${p.format} from ${rinfo.address}`;
  if (lastArrival !== 0n) {
    const gap = Number(now - lastArrival) / 1e6;
    windowGapsMs.push(gap);
    total.gapsMs.push(gap);
  }
  lastArrival = now;
  if (p.seq !== null) {
    if (lastSeq !== null) {
      const delta = (p.seq - lastSeq) >>> 0;
      if (delta > 1 && delta < 0x80000000) total.lost += delta - 1;
    }
    lastSeq = p.seq;
  }
  windowPackets++;
  total.packets++;
  latest = p;
});

const fmt = (n, w = 6, d = 1) => n.toFixed(d).padStart(w);

const ticker = setInterval(() => {
  const hz = windowPackets;
  const gaps = windowGapsMs.sort((a, b) => a - b);
  total.seconds++;
  if (hz < 100) total.secondsBelow100++;
  const angles = latest
    ? `yaw ${fmt(latest.yaw)}  pitch ${fmt(latest.pitch)}  roll ${fmt(latest.roll)}`
    : 'waiting for packets…';
  console.log(
    `${fmt(hz, 4, 0)} Hz  gap p50 ${fmt(percentile(gaps, 50), 5, 2)} ms  p99 ${fmt(percentile(gaps, 99), 6, 2)} ms  ` +
      `max ${fmt(gaps.at(-1) ?? 0, 6, 1)} ms  lost ${total.lost}  | ${angles}  [${format}]`,
  );
  windowGapsMs = [];
  windowPackets = 0;
}, 1000);

function summary() {
  const gaps = total.gapsMs.sort((a, b) => a - b);
  const secs = (Date.now() - total.start) / 1000;
  console.log('\n--- summary ---');
  console.log(`duration ${secs.toFixed(0)} s, packets ${total.packets}, avg ${(total.packets / secs).toFixed(1)} Hz`);
  console.log(`seconds below 100 Hz: ${total.secondsBelow100} of ${total.seconds}`);
  console.log(
    `gap p50 ${percentile(gaps, 50).toFixed(2)} ms, p99 ${percentile(gaps, 99).toFixed(2)} ms, ` +
      `max ${(gaps.at(-1) ?? 0).toFixed(1)} ms`,
  );
  console.log(`lost (CapCam only) ${total.lost}, unrecognized packets ${total.bad}`);
}

function stop() {
  clearInterval(ticker);
  socket.close();
  summary();
  process.exit(0);
}

process.on('SIGINT', stop);
if (minutes > 0) setTimeout(stop, minutes * 60_000);

socket.bind(port, () => {
  console.log(`Listening on UDP ${port}${minutes ? ` for ${minutes} min` : ''} (Ctrl+C to stop)`);
});
