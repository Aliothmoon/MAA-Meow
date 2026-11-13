import frameProbe from 'libframeprobe.so';
import common from '@ohos.app.ability.common';
import display from '@ohos.display';
import wantAgent from '@ohos.app.ability.wantAgent';
import backgroundTaskManager from '@ohos.resourceschedule.backgroundTaskManager';
import { BusinessError } from '@ohos.base';
import { captureLog } from './CaptureLog';

interface FrameStatus {
  stage: string;
  state: number;
  error: number;
  started: boolean;
  ended: boolean;
  done: boolean;
  frames: number;
  copied: number;
  width: number;
  height: number;
  fps: number;
  mapErrors: number;
  formatErrors: number;
  imageSaved: boolean;
}

export class NativeFrameProbe {
  private onReady: (() => void) | undefined = undefined;
  private update: (message: string, busy: boolean) => void;
  private context: common.UIAbilityContext | undefined = undefined;
  private timer: number = -1;
  private taskId: number = -1;
  private busy: boolean = false;
  private finishing: boolean = false;
  private backgroundRequested: boolean = false;

  constructor(update: (message: string, busy: boolean) => void) {
    this.update = update;
  }

  start(context: common.UIAbilityContext, onReady?: () => void): void {
    if (this.busy) {
      return;
    }
    this.context = context;
    this.onReady = onReady;
    this.busy = true;
    this.backgroundRequested = false;
    try {
      const screen = display.getDefaultDisplaySync();
      captureLog(context.filesDir, 'native frame start requested');
      const result = frameProbe.start(screen.width, screen.height, context.filesDir);
      if (result !== 0) {
        this.busy = false;
        const raw = frameProbe.status();
        const status: FrameStatus = JSON.parse(raw) as FrameStatus;
        captureLog(context.filesDir, `native frame start failed ${raw}`);
        this.update(`实时取帧启动失败：${status.stage} / ${result}`, false);
        return;
      }
      this.update('请在系统弹窗授权实时取帧', true);
      this.timer = setInterval(() => { this.poll(); }, 250);
    } catch (error) {
      const failure = error as BusinessError;
      this.stop(`实时取帧异常：${failure.message}`);
    }
  }

  private poll(): void {
    if (this.finishing || !this.context) {
      return;
    }
    try {
      const status: FrameStatus = JSON.parse(frameProbe.status()) as FrameStatus;
      if (status.done || status.error !== 0) {
        this.stop(status.error !== 0 ? `实时取帧错误 ${status.stage} / ${status.error}` : '实时取帧完成');
        return;
      }
      if (status.started) {
        if (!this.backgroundRequested) {
          this.backgroundRequested = true;
          this.beginBackground(this.context);
        }
        this.update(`${status.width} × ${status.height}，收到 ${status.frames} 帧，` +
          `复制 ${status.copied} 帧，${status.fps.toFixed(1)} 帧/秒；请切到游戏`, true);
      }
    } catch (error) {
      const failure = error as BusinessError;
      this.stop(`取帧状态错误：${failure.message}`);
    }
  }

  private async beginBackground(context: common.UIAbilityContext): Promise<void> {
    try {
      const agent = await wantAgent.getWantAgent({
        wants: [{ bundleName: 'com.maameow.hmosprobe', abilityName: 'EntryAbility' }],
        operationType: wantAgent.OperationType.START_ABILITY,
        requestCode: 1,
        wantAgentFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
      });
      const request = new backgroundTaskManager.ContinuousTaskRequest();
      request.backgroundTaskModes = [backgroundTaskManager.BackgroundTaskMode.MODE_AV_PLAYBACK_AND_RECORD];
      request.backgroundTaskSubmodes = [
        backgroundTaskManager.BackgroundTaskSubmode.SUBMODE_SCREEN_RECORD_NORMAL_NOTIFICATION
      ];
      request.wantAgent = agent;
      const task = await backgroundTaskManager.startBackgroundRunning(context, request);
      const id = task.continuousTaskId;
      if (id === undefined) {
        await backgroundTaskManager.stopBackgroundRunning(context);
        throw new Error('No continuous task ID returned');
      }
      if (!this.busy || this.finishing) {
        await backgroundTaskManager.stopBackgroundRunning(context, id);
        return;
      }
      this.taskId = id;
      const ready = this.onReady;
      this.onReady = undefined;
      if (ready) { ready(); }
      captureLog(context.filesDir, `native frame background task started ${id}`);
    } catch (error) {
      const failure = error as BusinessError;
      captureLog(context.filesDir, `native frame background error ${failure.code} ${failure.message}`);
      await this.stop(`实时取帧后台任务失败：${failure.code}`);
    }
  }

  async stop(message: string = '实时取帧手动停止'): Promise<void> {
    if (this.finishing || !this.busy) {
      return;
    }
    this.finishing = true;
    this.onReady = undefined;
    if (this.timer !== -1) {
      clearInterval(this.timer);
      this.timer = -1;
    }
    try {
      frameProbe.stop();
      const raw = frameProbe.status();
      const status: FrameStatus = JSON.parse(raw) as FrameStatus;
      if (this.context) {
        captureLog(this.context.filesDir, `native frame finished ${raw}`);
      }
      this.update(`${message}：${status.width} × ${status.height}，` +
        `${status.frames} 帧，复制 ${status.copied} 帧，原始帧${status.imageSaved ? '已保存' : '未保存'}`, false);
    } catch (error) {
      const failure = error as BusinessError;
      this.update(`停止实时取帧失败：${failure.message}`, false);
    } finally {
      if (this.context && this.taskId !== -1) {
        try {
          await backgroundTaskManager.stopBackgroundRunning(this.context, this.taskId);
          captureLog(this.context.filesDir, 'native frame background task stopped');
        } catch (error) {
          const failure = error as BusinessError;
          captureLog(this.context.filesDir, `native frame background stop error ${failure.code}`);
        }
        this.taskId = -1;
      }
      this.busy = false;
      this.finishing = false;
    }
  }
}
