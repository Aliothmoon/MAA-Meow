import socket from '@ohos.net.socket';
import util from '@ohos.util';

// Initial HDC session handshake only. No key import, authentication or shell command.
export function varint(value: number): number[] {
  const result: number[] = [];
  let rest = value;
  while (rest >= 128) { result.push((rest % 128) + 128); rest = Math.floor(rest / 128); }
  result.push(rest);
  return result;
}
export function textField(field: number, value: string): number[] {
  if (value.length === 0) { return []; }
  const data = new util.TextEncoder().encodeInto(value);
  return [(field * 8) + 2].concat(varint(data.length)).concat(Array.from(data));
}
function handshake(address: string, port: number): ArrayBuffer {
  const payload = textField(1, 'OHOS HDC').concat([16, 0, 24])
    .concat(varint((Date.now() % 1000000000) + 1))
    .concat(textField(4, `${address}:${port}`)).concat(textField(6, 'Ver: 3.2.0f'));
  const protect: number[] = [16, 1, 32, 9];
  const frame = new Uint8Array(11 + protect.length + payload.length);
  frame.set([72, 87, 0, 0, 1], 0);
  const view = new DataView(frame.buffer);
  view.setUint16(5, protect.length, false);
  view.setUint32(7, payload.length, false);
  frame.set(protect, 11);
  frame.set(payload, 11 + protect.length);
  return frame.buffer;
}

// Bounded parser: report only banner and auth type, never log the challenge token.
export class ProtoReader {
  offset: number = 0;
  constructor() {}
  read(data: Uint8Array): number {
    let value: number = 0;
    let scale: number = 1;
    for (let i = 0; i < 5; i++) {
      if (this.offset >= data.length) { throw new Error('不完整 varint'); }
      const byte = data[this.offset++];
      value += (byte & 127) * scale;
      if (byte < 128) { return value; }
      scale *= 128;
    }
    throw new Error('varint 超出范围');
  }
}
function describe(frame: Uint8Array): string {
  const view = new DataView(frame.buffer);
  const headSize = view.getUint16(5, false);
  const dataSize = view.getUint32(7, false);
  const data = frame.slice(11 + headSize, 11 + headSize + dataSize);
  const reader = new ProtoReader();
  let banner: string = '';
  let authType: number = -1;
  while (reader.offset < data.length) {
    const tag = reader.read(data);
    const field = Math.floor(tag / 8);
    if (tag % 8 === 0) {
      const value = reader.read(data);
      if (field === 2) { authType = value; }
    } else if (tag % 8 === 2) {
      const size = reader.read(data);
      if (size > data.length - reader.offset) { throw new Error('不完整字段'); }
      if (field === 1) {
        banner = util.TextDecoder.create('utf-8').decodeWithStream(data.slice(reader.offset, reader.offset + size));
      }
      reader.offset += size;
    } else { throw new Error('暂不支持的字段类型'); }
  }
  if (banner !== 'OHOS HDC' || authType < 0) { throw new Error('响应不是有效 HDC 握手'); }
  return `HDC 本机握手成功，authType=${authType}；尚未认证、未执行命令。`;
}

export async function probeHdc(address: string, port: number): Promise<string> {
  if (!/^(127\.0\.0\.1|(?:\d{1,3}\.){3}\d{1,3})$/.test(address) ||
    !Number.isInteger(port) || port < 1 || port > 65535) { throw new Error('请输入手机自身 IPv4 地址和有效端口'); }
  const client = socket.constructTCPSocketInstance();
  let receive: number[] = [];
  let timer: number = -1;
  let resolveReply: ((value: string) => void) | undefined = undefined;
  let rejectReply: ((error: Error) => void) | undefined = undefined;
  const reply = new Promise<string>((resolve, reject) => {
    resolveReply = resolve;
    rejectReply = reject;
  });
  // Handle rejection immediately, even if the connect operation has not finished yet.
  reply.catch(() => {});
  client.on('message', (message: socket.SocketMessageInfo) => {
    try {
      receive = receive.concat(Array.from(new Uint8Array(message.message)));
      if (receive.length > 65536) { throw new Error('响应过大'); }
      if (receive.length < 11) { return; }
      const bytes = new Uint8Array(receive);
      if (bytes[0] !== 72 || bytes[1] !== 87) { throw new Error('端口返回的不是 HDC 数据'); }
      const view = new DataView(bytes.buffer);
      const size = 11 + view.getUint16(5, false) + view.getUint32(7, false);
      if (size > 65536) { throw new Error('HDC 帧超出限制'); }
      if (bytes.length < size) { return; }
      if (resolveReply) { resolveReply(describe(bytes)); }
    } catch (error) { if (rejectReply) { rejectReply(error as Error); } }
  });
  client.on('error', (error: Error) => { if (rejectReply) { rejectReply(error); } });
  client.on('close', () => { if (rejectReply) { rejectReply(new Error('HDC 对端关闭连接')); } });
  try {
    await client.bind({ address: '0.0.0.0', port: 0, family: 1 });
    await client.connect({ address: { address: address, port: port, family: 1 }, timeout: 5000 });
    timer = setTimeout(() => { if (rejectReply) { rejectReply(new Error('已连接，但 HDC 握手响应超时')); } }, 5000);
    await client.send({ data: handshake(address, port) });
    return await reply;
  } finally {
    if (timer >= 0) { clearTimeout(timer); }
    resolveReply = undefined;
    rejectReply = undefined;
    try { await client.close(); } catch { /* Keep the original error if connect failed. */ }
  }
}
