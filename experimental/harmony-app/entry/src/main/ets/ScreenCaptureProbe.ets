import media from '@ohos.multimedia.media';
import fs from '@ohos.file.fs';
import { BusinessError } from '@ohos.base';
import common from '@ohos.app.ability.common';
import wantAgent from '@ohos.app.ability.wantAgent';
import backgroundTaskManager from '@ohos.resourceschedule.backgroundTaskManager';
import { captureLog } from './CaptureLog';

// A short recording proves app-level capture permissions, not real-time frame access.
export class ScreenCaptureProbe {
  private recorder: media.AVScreenCaptureRecorder | undefined = undefined;
  private file: fs.File | undefined = undefined;
  private timer: number = -1;
  private active: boolean = false;
  private closing: boolean = false;
  private output: string = '';
  private filesDir: string = '';
  private context: common.UIAbilityContext | undefined = undefined;
  private backgroundTaskId: number = -1;
  private update: (message: string, busy: boolean) => void;

  constructor(update: (message: string, busy: boolean) => void) {
    this.update = update;
  }

  async start(context: common.UIAbilityContext): Promise<void> {
    if (this.recorder || this.closing) {
      return;
    }
    this.context = context;
    this.filesDir = context.filesDir;
    captureLog(this.filesDir, 'start requested');
    if (!canIUse('SystemCapability.Multimedia.Media.AVScreenCapture')) {
      this.update('设备不支持屏幕采集能力', false);
      return;
    }
    this.update('正在准备屏幕采集', true);
    try {
      this.output = `${this.filesDir}/screen-probe.mp4`;
      this.file = fs.openSync(this.output, fs.OpenMode.CREATE | fs.OpenMode.READ_WRITE | fs.OpenMode.TRUNC);
      this.recorder = await media.createAVScreenCaptureRecorder();
      this.recorder.on('stateChange', (state: media.AVScreenCaptureStateCode) => {
        captureLog(this.filesDir, `capture state ${state}`);
        if (state === media.AVScreenCaptureStateCode.SCREENCAPTURE_STATE_STARTED) {
          this.active = true;
          this.update('正在采集：请切换到卓易通游戏，20 秒后自动停止', true);
          this.beginBackground(context);
          this.timer = setTimeout(() => {
            captureLog(this.filesDir, '20 second timer fired');
            this.stop();
          }, 20000);
        } else if (state === media.AVScreenCaptureStateCode.SCREENCAPTURE_STATE_CANCELED ||
          state === media.AVScreenCaptureStateCode.SCREENCAPTURE_STATE_STOPPED_BY_USER ||
          state === media.AVScreenCaptureStateCode.SCREENCAPTURE_STATE_INTERRUPTED_BY_OTHER ||
          state === media.AVScreenCaptureStateCode.SCREENCAPTURE_STATE_STOPPED_BY_CALL ||
          state === media.AVScreenCaptureStateCode.SCREENCAPTURE_STATE_STOPPED_BY_USER_SWITCHES) {
          this.active = false;
          setTimeout(() => { this.finish(`采集已结束，状态码 ${state}`); }, 0);
        }
      });
      this.recorder.on('error', (error: BusinessError) => {
        captureLog(this.filesDir, `capture error ${error.code} ${error.message}`);
        setTimeout(() => { this.finish(`采集错误：${error.code} ${error.message}`); }, 0);
      });
      const config: media.AVScreenCaptureRecordConfig = {
        fd: this.file.fd,
        videoBitrate: 4000000
      };
      await this.recorder.init(config);
      await this.recorder.setMicEnabled(false);
      await this.recorder.setContentAutoRotation(true);
      this.update('请在系统弹窗选择采集范围并授权', true);
      await this.recorder.startRecording();
    } catch (error) {
      const failure = error as BusinessError;
      await this.finish(`启动失败：${failure.code} ${failure.message}`);
    }
  }

  private async beginBackground(context: common.UIAbilityContext): Promise<void> {
    try {
      const agent = await wantAgent.getWantAgent({
        wants: [{ bundleName: 'com.maameow.hmosprobe', abilityName: 'EntryAbility' }],
        operationType: wantAgent.OperationType.START_ABILITY,
        requestCode: 0,
        wantAgentFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
      });
      const request = new backgroundTaskManager.ContinuousTaskRequest();
      // Screen-record submode belongs to the multimedia mode; legacy recording mode accepts only normal submode.
      request.backgroundTaskModes = [backgroundTaskManager.BackgroundTaskMode.MODE_AV_PLAYBACK_AND_RECORD];
      request.backgroundTaskSubmodes = [
        backgroundTaskManager.BackgroundTaskSubmode.SUBMODE_SCREEN_RECORD_NORMAL_NOTIFICATION
      ];
      request.wantAgent = agent;
      const task = await backgroundTaskManager.startBackgroundRunning(context, request);
      const taskId = task.continuousTaskId;
      if (taskId === undefined) {
        await backgroundTaskManager.stopBackgroundRunning(context);
        throw new Error('No continuous task ID returned');
      }
      if (!this.active || this.closing) {
        await backgroundTaskManager.stopBackgroundRunning(context, taskId);
        return;
      }
      this.backgroundTaskId = taskId;
      captureLog(this.filesDir, `background capture task started ${this.backgroundTaskId}`);
    } catch (error) {
      const failure = error as BusinessError;
      captureLog(this.filesDir, `background task error ${failure.code} ${failure.message}`);
      await this.finish(`后台采集任务申请失败：${failure.code}`);
    }
  }

  async stop(): Promise<void> {
    if (!this.recorder || this.closing) {
      return;
    }
    await this.finish('采集完成');
  }

  private async finish(message: string): Promise<void> {
    if (this.closing) {
      return;
    }
    this.closing = true;
    captureLog(this.filesDir, `finish requested: ${message}`);
    if (this.timer !== -1) {
      clearTimeout(this.timer);
      this.timer = -1;
    }
    try {
      if (this.recorder) {
        if (this.active) {
          await this.recorder.stopRecording();
        }
      }
    } catch (error) {
      const failure = error as BusinessError;
      message += `；释放状态 ${failure.code}`;
    } finally {
      if (this.recorder) {
        try {
          await this.recorder.release();
        } catch (error) {
          const failure = error as BusinessError;
          message += `；资源释放失败 ${failure.code}`;
        }
      }
      this.recorder = undefined;
      this.active = false;
      if (this.context && this.backgroundTaskId !== -1) {
        try {
          await backgroundTaskManager.stopBackgroundRunning(this.context, this.backgroundTaskId);
          captureLog(this.filesDir, 'background capture task stopped');
        } catch (error) {
          const failure = error as BusinessError;
          captureLog(this.filesDir, `background task stop error ${failure.code}`);
          message += `；后台任务释放失败 ${failure.code}`;
        }
        this.backgroundTaskId = -1;
      }
      if (this.file) {
        try {
          fs.closeSync(this.file.fd);
        } catch {
          message += '；文件关闭失败';
        }
        this.file = undefined;
      }
      this.closing = false;
      try {
        const size = fs.statSync(this.output).size;
        captureLog(this.filesDir, `finished file bytes ${size}`);
        this.update(`${message}；文件 ${size} 字节`, false);
      } catch {
        this.update(message, false);
      }
    }
  }
}
