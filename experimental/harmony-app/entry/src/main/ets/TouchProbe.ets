import socket from '@ohos.net.socket';
import util from '@ohos.util';

interface HypiumParams {
  api: string;
  this: string;
  args: number[];
  message_type: string;
}
interface HypiumRequest {
  module: string;
  method: string;
  params: HypiumParams;
  request_id: string;
}
interface HypiumReply {
  result?: string;
  exception?: string;
}

// One short-lived localhost connection. Targets come from our own test controls.
export async function testLocalTouch(x: number, y: number): Promise<string> {
  return testLocalAction('Driver.click', [Math.round(x), Math.round(y)]);
}

export async function testLocalSwipe(x1: number, y: number, x2: number): Promise<string> {
  return testLocalAction('Driver.swipe', [Math.round(x1), Math.round(y), Math.round(x2), Math.round(y), 600]);
}

async function testLocalAction(api: string, args: number[]): Promise<string> {
  const client = socket.constructTCPSocketInstance();
  let pendingResolve: ((value: string) => void) | undefined = undefined;
  let pendingReject: ((reason: Error) => void) | undefined = undefined;
  let incoming: string = '';
  const decoder = util.TextDecoder.create('utf-8');
  client.on('message', (message: socket.SocketMessageInfo) => {
    incoming += decoder.decodeWithStream(new Uint8Array(message.message));
    if (incoming.length > 8192) {
      if (pendingReject) { pendingReject(new Error('响应超过限制')); }
      return;
    }
    try {
      const response = JSON.parse(incoming) as HypiumReply;
      if (response.exception) {
        if (pendingReject) { pendingReject(new Error(JSON.stringify(response.exception))); }
      } else if (pendingResolve) {
        pendingResolve(incoming);
      }
    } catch { /* A TCP message may contain only part of the JSON response. */ }
  });
  client.on('error', (error: Error) => {
    if (pendingReject) { pendingReject(error); }
  });
  const invoke = async (api: string, driver: string, args: number[]): Promise<string> => {
    incoming = '';
    const request: HypiumRequest = {
      module: 'com.ohos.devicetest.hypiumApiHelper', method: 'callHypiumApi',
      params: { api: api, this: driver, args: args, message_type: 'hypium' },
      request_id: `maa-${Date.now()}`
    };
    return new Promise<string>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('服务响应超时')), 5000);
      pendingResolve = (value: string) => { clearTimeout(timer); resolve(value); };
      pendingReject = (error: Error) => { clearTimeout(timer); reject(error); };
      client.send({ data: JSON.stringify(request) + '\n' }).catch((error: Error) => {
        clearTimeout(timer); reject(error);
      });
    });
  }
  try {
    await client.bind({ address: '127.0.0.1', port: 0, family: 1 });
    await client.connect({ address: { address: '127.0.0.1', port: 8012, family: 1 }, timeout: 5000 });
    const created = JSON.parse(await invoke('Driver.create', 'Driver#0', [])) as HypiumReply;
    if (!created.result || !created.result.startsWith('Driver#')) { throw new Error('驱动创建失败'); }
    await invoke(api, created.result, args);
    return `本机服务已接受 ${api}；请核对界面变化。`;
  } finally {
    pendingResolve = undefined;
    pendingReject = undefined;
    await client.close();
  }
}
