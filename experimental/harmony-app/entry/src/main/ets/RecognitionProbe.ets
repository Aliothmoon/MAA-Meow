import frameProbe from 'libframeprobe.so';
import display from '@ohos.display';
import fs from '@ohos.file.fs';
import common from '@ohos.app.ability.common';
import { captureLog } from './CaptureLog';

interface MatchResult {
  ok: boolean;
  score?: number;
  x?: number;
  y?: number;
  frame?: number;
  reason?: string;
  settings?: boolean;
  settingsScore?: number;
}

export async function prepareRecognitionTemplates(context: common.UIAbilityContext): Promise<void> {
  const names: string[] = ['home-settings.png', 'settings-header.png'];
  for (const name of names) {
    const bytes = await context.resourceManager.getRawFileContent(`recognition/${name}`);
    const file = fs.openSync(`${context.filesDir}/${name}`, fs.OpenMode.CREATE | fs.OpenMode.WRITE_ONLY | fs.OpenMode.TRUNC);
    try { fs.writeSync(file.fd, bytes.buffer); } finally { fs.closeSync(file); }
  }
}

// A bounded image-driven navigation experiment, independent of MaaCore task execution.
export async function runRecognitionProbe(filesDir: string, update: (message: string) => void): Promise<void> {
  const wait = (ms: number): Promise<void> => new Promise<void>((resolve) => { setTimeout(resolve, ms); });
  try {
    update('请切回游戏首页；10 秒后识别设置图标。');
    await wait(10000);
    const screen = display.getDefaultDisplaySync();
    if (screen.width !== 2232 || screen.height !== 1320) { throw new Error('屏幕尺寸变化，已取消'); }
    const first = JSON.parse(frameProbe.matchSettings()) as MatchResult;
    captureLog(filesDir, `recognition before=${JSON.stringify(first)}`);
    if (!first.ok || first.x === undefined || first.y === undefined) {
      throw new Error(`首页设置图标未识别：${JSON.stringify(first)}`);
    }
    await wait(300);
    const second = JSON.parse(frameProbe.matchSettings()) as MatchResult;
    if (!second.ok || second.frame === first.frame || second.x === undefined || second.y === undefined ||
      Math.abs(second.x - first.x) > 3 || Math.abs(second.y - first.y) > 3) {
      throw new Error('两帧识别结果不稳定，已取消');
    }
    if (!await frameProbe.nativeClick(second.x, second.y, screen.width, screen.height)) {
      throw new Error('原生点击失败');
    }
    captureLog(filesDir, `recognition click x=${second.x} y=${second.y} score=${second.score}`);
    await wait(1500);
    const after = JSON.parse(frameProbe.matchSettings()) as MatchResult;
    captureLog(filesDir, `recognition after=${JSON.stringify(after)}`);
    if (after.frame === second.frame || !after.settings || after.ok) {
      throw new Error(`设置页面未确认：${JSON.stringify(after)}`);
    }
    update(`设置页识别成功（${after.settingsScore}）；正在返回首页。`);
    if (!await frameProbe.nativeClick(100, 78, screen.width, screen.height)) {
      throw new Error('原生返回点击失败');
    }
    await wait(1500);
    const restored = JSON.parse(frameProbe.matchSettings()) as MatchResult;
    captureLog(filesDir, `recognition restored=${JSON.stringify(restored)}`);
    if (restored.frame === after.frame || !restored.ok || restored.settings) {
      throw new Error(`返回首页未确认：${JSON.stringify(restored)}`);
    }
    captureLog(filesDir, 'recognition closed-loop PASS backend=native');
    update('验证通过：识别首页 → 打开设置 → 识别设置页 → 返回并识别首页。');
  } catch (error) {
    const message = (error as Error).message;
    captureLog(filesDir, `recognition failed=${message}`);
    update(`识别验证未通过：${message}`);
  }
}
