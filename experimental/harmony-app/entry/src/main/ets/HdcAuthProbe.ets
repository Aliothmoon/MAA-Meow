import fs from '@ohos.file.fs';
import socket from '@ohos.net.socket';
import util from '@ohos.util';
import crypto from '@ohos.security.cryptoFramework';
import { varint, textField, ProtoReader } from './HdcProbe';

function packet(channel: number, command: number, payload: number[]): ArrayBuffer {
  const protect = [8].concat(varint(channel)).concat([16]).concat(varint(command)).concat([32, 9]);
  const frame = new Uint8Array(11 + protect.length + payload.length);
  frame.set([72, 87, 0, 0, 1]);
  const view = new DataView(frame.buffer);
  view.setUint16(5, protect.length); view.setUint32(7, payload.length);
  frame.set(protect, 11); frame.set(payload, 11 + protect.length);
  return frame.buffer;
}
function frameCommand(frame: Uint8Array): number {
  const view = new DataView(frame.buffer);
  const protect = frame.slice(11, 11 + view.getUint16(5));
  const reader = new ProtoReader();
  let command: number = -1;
  while (reader.offset < protect.length) {
    const tag = reader.read(protect);
    if (tag % 8 !== 0) { throw new Error('HDC 保护头字段类型异常'); }
    const value = reader.read(protect);
    if (tag === 16) { command = value; }
  }
  return command;
}
interface AuthReply { type: number; session: number; content: string; }
function parseAuth(data: Uint8Array): AuthReply {
  const reader = new ProtoReader();
  const reply: AuthReply = { type: -1, session: 0, content: '' };
  while (reader.offset < data.length) {
    const tag = reader.read(data); const field = Math.floor(tag / 8);
    if (tag % 8 === 0) {
      const value = reader.read(data);
      if (field === 2) { reply.type = value; }
      if (field === 3) { reply.session = value; }
    } else if (tag % 8 === 2) {
      const size = reader.read(data);
      if (size > data.length - reader.offset) { throw new Error('字段越界'); }
      if (field === 5) { reply.content = util.TextDecoder.create('utf-8').decodeWithStream(data.slice(reader.offset, reader.offset + size)); }
      reader.offset += size;
    } else if (tag % 8 === 1 || tag % 8 === 5) {
      const size = tag % 8 === 1 ? 8 : 4;
      if (size > data.length - reader.offset) { throw new Error('固定字段越界'); }
      reader.offset += size;
    } else { throw new Error(`认证字段类型异常 field=${field} wire=${tag % 8}`); }
  }
  return reply;
}

interface StoredIdentity { publicKey: string; privateKey: string; }
let cachedIdentity: crypto.KeyPair | undefined = undefined;
async function loadIdentity(filesDir: string): Promise<crypto.KeyPair> {
  if (cachedIdentity) { return cachedIdentity; }
  const path = `${filesDir}/hdc-identity.json`;
  const generator = crypto.createAsyKeyGenerator('RSA3072');
  const base64 = new util.Base64Helper();
  let keys: crypto.KeyPair;
  if (fs.accessSync(path)) {
    const stored = JSON.parse(fs.readTextSync(path)) as StoredIdentity;
    keys = await generator.convertKey({ data: base64.decodeSync(stored.publicKey) },
      { data: base64.decodeSync(stored.privateKey) });
  } else {
    keys = await generator.generateKeyPair();
    const stored: StoredIdentity = {
      publicKey: base64.encodeToStringSync(keys.pubKey.getEncoded().data),
      privateKey: base64.encodeToStringSync(keys.priKey.getEncoded().data)
    };
    const file = fs.openSync(path, fs.OpenMode.CREATE | fs.OpenMode.WRITE_ONLY | fs.OpenMode.TRUNC);
    try { fs.writeSync(file.fd, JSON.stringify(stored)); } finally { fs.closeSync(file.fd); }
  }
  cachedIdentity = keys;
  return keys;
}

// Fresh app-owned identity for this experiment. Never imports the computer's HDC key.
export async function authenticateHdc(address: string, port: number, filesDir: string, update: (message: string) => void, restartService: boolean = false, startOnly: boolean = false): Promise<string> {
  if (!/^(?:\d{1,3}\.){3}\d{1,3}$/.test(address) || !Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('地址或端口无效');
  }
  update('正在读取应用固定 RSA 身份');
  const keys = await loadIdentity(filesDir);
  update('固定 RSA 身份已就绪，正在导出公钥');
  const publicData = keys.pubKey.getEncoded();
  if (!publicData || !publicData.data) { throw new Error('公钥导出结果为空'); }
  const encoded = new util.Base64Helper().encodeToStringSync(publicData.data);
  update('公钥已编码，正在连接 HDC');
  let pem: string = '-----BEGIN PUBLIC KEY-----\n';
  for (let i = 0; i < encoded.length; i += 64) { pem += encoded.slice(i, i + 64) + '\n'; }
  pem += '-----END PUBLIC KEY-----\n';
  const client = socket.constructTCPSocketInstance();
  let session: number = (Date.now() % 1000000000) + 1;
  const sendAuth = async (type: number, content: string): Promise<void> => {
    const payload = textField(1, 'OHOS HDC').concat([16, type, 24]).concat(varint(session))
      .concat(textField(4, `${address}:${port}`)).concat(textField(5, content)).concat(textField(6, 'Ver: 3.2.0f73c487f8b34a2ba5'));
    await client.send({ data: packet(0, 1, payload) });
  };
  let pendingResolve: ((value: Uint8Array) => void) | undefined = undefined;
  let pendingReject: ((error: Error) => void) | undefined = undefined;
  let buffered: number[] = [];
  const queue: Uint8Array[] = [];
  client.on('message', (message: socket.SocketMessageInfo) => {
    buffered = buffered.concat(Array.from(new Uint8Array(message.message)));
    while (buffered.length >= 11) {
      const bytes = new Uint8Array(buffered); const view = new DataView(bytes.buffer);
      const size = 11 + view.getUint16(5) + view.getUint32(7);
      if (bytes[0] !== 72 || bytes[1] !== 87 || size > 65536) {
        if (pendingReject) { pendingReject(new Error('无效 HDC 帧')); } return;
      }
      if (bytes.length < size) { return; }
      const frame = bytes.slice(0, size); buffered = buffered.slice(size);
      if (pendingResolve) { const resolve = pendingResolve; pendingResolve = undefined; resolve(frame); }
      else { queue.push(frame); }
    }
  });
  client.on('error', (error: Error) => { if (pendingReject) { pendingReject(error); } });
  client.on('close', () => { if (pendingReject) { pendingReject(new Error('调试连接已关闭')); } });
  const receive = async (): Promise<Uint8Array> => {
    if (queue.length > 0) { return queue.shift()!; }
    return new Promise<Uint8Array>((resolve, reject) => {
      const timer = setTimeout(() => { pendingResolve = undefined; pendingReject = undefined; reject(new Error('等待认证响应超时')); }, 60000);
      pendingResolve = (value: Uint8Array) => { clearTimeout(timer); pendingReject = undefined; resolve(value); };
      pendingReject = (error: Error) => { clearTimeout(timer); pendingResolve = undefined; reject(error); };
    });
  };
  try {
    await client.bind({ address: '0.0.0.0', port: 0, family: 1 });
    await client.connect({ address: { address: address, port: port, family: 1 }, timeout: 5000 });
    update('HDC TCP 已连接，发送初始握手');
    await sendAuth(0, 'authtype'.padEnd(16, ' ') + '1'.padEnd(16, ' ') + '1');
    let authenticated: boolean = false;
    for (let i = 0; i < 10; i++) {
      const frame = await receive(); const view = new DataView(frame.buffer);
      const command = frameCommand(frame);
      if (command !== 1) {
        update(`认证期间收到 HDC 消息 command=${command}`);
        if (command === 2) { throw new Error('调试服务关闭了认证连接，请确认系统信任后重试'); }
        continue;
      }
      const auth = parseAuth(frame.slice(11 + view.getUint16(5)));
      if (auth.session !== 0) { session = auth.session; }
      update(`认证阶段 ${auth.type}；如弹出调试授权，请确认 MAA-HDC-Probe。`);
      if (auth.type === 3) { await sendAuth(3, 'MAA-HDC-Probe\f' + pem); }
      else if (auth.type === 2) {
        const sign = crypto.createSign('RSA3072|PSS|SHA512|MGF1_SHA512');
        await sign.init(keys.priKey);
        sign.setSignSpec(crypto.SignSpecItem.PSS_SALT_LEN_NUM, 64);
        const signature = await sign.sign({ data: new util.TextEncoder().encodeInto(auth.content) });
        await sendAuth(2, new util.Base64Helper().encodeToStringSync(signature.data));
      } else if (auth.type === 4) {
        if (auth.content.includes('DAEMON_UNAUTH')) {
          update('等待系统信任授权，请在弹窗选择信任；本次保持连接等待。');
          continue;
        }
        authenticated = true; break;
      }
      else { throw new Error(`暂不支持的认证阶段 ${auth.type}`); }
    }
    if (!authenticated) { throw new Error('认证轮次超限'); }
    update(restartService ? 'HDC 认证成功，正在重启验证触控服务' : 'HDC 认证成功，正在执行只读 id');
    // Fixed command only. Never accepts arbitrary shell text from the UI.
    const restartCommand = restartService ?
      "find_service() { ps -eo pid,args | sed -n 's/^ *\\([0-9][0-9]*\\)  *uitest start-daemon singleness --extension-name maa-hypium-tcp.so *$/\\1/p'; }; old=$(find_service); for n in $old; do kill $n || { echo MAA_RESTART_FAILED_KILL; exit 1; }; done; sleep 1; if [ -n \"$(find_service)\" ]; then echo MAA_RESTART_FAILED_OLD_ALIVE; exit 1; fi; uitest start-daemon singleness --extension-name maa-hypium-tcp.so; sleep 1; new=$(find_service); if [ -z \"$new\" ]; then echo MAA_RESTART_FAILED_NO_NEW; exit 1; fi; for n in $new; do for o in $old; do if [ \"$n\" = \"$o\" ]; then echo MAA_RESTART_FAILED_SAME_PID; exit 1; fi; done; done; echo MAA_RESTART_OK old=$old new=$new" : 'id';
    const shellCommand = startOnly ? "find_service() { ps -eo pid,args | sed -n 's/^ *\\([0-9][0-9]*\\)  *uitest start-daemon singleness --extension-name maa-hypium-tcp.so *$/\\1/p'; }; old=$(find_service); if [ -n \"$old\" ]; then echo MAA_START_FAILED_EXISTING pid=$old; exit 1; fi; if [ ! -r /data/local/tmp/maa-hypium-tcp.so ]; then echo MAA_START_FAILED_AGENT_MISSING; exit 1; fi; uitest start-daemon singleness --extension-name maa-hypium-tcp.so; sleep 1; new=$(find_service); if [ -z \"$new\" ]; then echo MAA_START_FAILED_NO_PROCESS; exit 1; fi; echo MAA_START_OK new=$new" : restartCommand;
    await client.send({ data: packet(1, 1001, Array.from(new util.TextEncoder().encodeInto(shellCommand))) });
    let output: string = '';
    for (let i = 0; i < 20; i++) {
      const frame = await receive(); const view = new DataView(frame.buffer);
      const headSize = view.getUint16(5); const protect = frame.slice(11, 11 + headSize);
      const reader = new ProtoReader(); let command: number = 0; let channel: number = 0;
      while (reader.offset < protect.length) {
        const tag = reader.read(protect); const value = reader.read(protect);
        if (tag === 16) { command = value; }
        if (tag === 8) { channel = value; }
      }
      update(`命令响应 channel=${channel} command=${command} bytes=${frame.length - 11 - headSize}`);
      if (channel !== 1) { continue; }
      if (command === 10 || command === 9 || command === 2001) {
        const payload = frame.slice(11 + headSize + (command === 9 ? 1 : 0));
        output += util.TextDecoder.create('utf-8').decodeWithStream(payload);
        if (output.length > 4096) { throw new Error('命令响应过长'); }
      }
      if (command === 2) { break; }
    }
    if (startOnly) {
      if (!output.includes('MAA_START_OK new=') || output.includes('MAA_START_FAILED')) {
        throw new Error(`首次启动未成功：${output}`);
      }
      return `本机新服务已启动，请验证点击：${output.trim()}`;
    }
    if (restartService) {
      if (!output.includes('MAA_RESTART_OK old=') || output.includes('MAA_RESTART_FAILED') || output.includes('Permission denied')) {
        throw new Error(`重启服务未证实成功：${output}`);
      }
      return `本机服务启动命令已完成，请验证点击：${output.trim()}`;
    }
    if (!output.includes('uid=')) { throw new Error(`认证已成功，但 id 返回异常：${output}`); }
    return `手机自连接认证成功：${output.trim()}`;
  } finally {
    pendingResolve = undefined; pendingReject = undefined;
    try { await client.close(); } catch { /* Preserve original failure. */ }
  }
}
