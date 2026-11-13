import fs from '@ohos.file.fs';
import display from '@ohos.display';
import { testLocalSwipe } from './TouchProbe';
import { captureLog } from './CaptureLog';

interface GameTouchPlan {
  width: number;
  height: number;
  startX: number;
  endX: number;
  y: number;
}

// Debug-only plan supplied in our private files directory after inspecting the game screen.
export class GameTouchProbe {
  private timer: number = -1;
  private busy: boolean = false;
  private update: (message: string) => void;
  constructor(update: (message: string) => void) { this.update = update; }

  start(filesDir: string): void {
    if (this.busy) { return; }
    try {
      const plan = JSON.parse(fs.readTextSync(`${filesDir}/touch-game-plan.json`)) as GameTouchPlan;
      const values: number[] = [plan.width, plan.height, plan.startX, plan.endX, plan.y];
      if (!values.every((value: number) => Number.isFinite(value)) || plan.width <= 0 ||
        plan.height <= 0 || plan.startX <= 0 || plan.endX <= 0 ||
        plan.startX >= plan.width || plan.endX >= plan.width || plan.y <= 0 || plan.y >= plan.height) {
        throw new Error('测试坐标无效');
      }
      this.busy = true;
      this.update('10 秒后拖动游戏音量，再拖回原位置；请切回游戏设置。');
      captureLog(filesDir, `game-touch scheduled plan=${JSON.stringify(plan)}`);
      this.timer = setTimeout(async () => {
        this.timer = -1;
        let moved: boolean = false;
        try {
          const screen = display.getDefaultDisplaySync();
          if (screen.width !== plan.width || screen.height !== plan.height) {
            throw new Error(`屏幕尺寸变化，取消测试：${screen.width}x${screen.height}`);
          }
          captureLog(filesDir, 'game-touch forward begin');
          await testLocalSwipe(plan.startX, plan.y, plan.endX);
          moved = true;
          captureLog(filesDir, 'game-touch forward accepted');
          await new Promise<void>((resolve) => { setTimeout(resolve, 2000); });
        } catch (error) {
          captureLog(filesDir, `game-touch error=${`${(error as Error).message} ${JSON.stringify(error)}`}`);
          this.update(`游戏拖动失败：${`${(error as Error).message} ${JSON.stringify(error)}`}`);
        } finally {
          if (moved) {
            try {
              const screen = display.getDefaultDisplaySync();
              if (screen.width !== plan.width || screen.height !== plan.height) {
                throw new Error('方向变化，无法恢复，请手动恢复音量');
              }
              await testLocalSwipe(plan.endX, plan.y, plan.startX);
              captureLog(filesDir, 'game-touch restore accepted');
              this.update('游戏拖动及恢复指令已完成，请核对游戏画面。');
            } catch (error) {
              captureLog(filesDir, `game-touch restore error=${`${(error as Error).message} ${JSON.stringify(error)}`}`);
              this.update('音量恢复失败，请手动恢复音量。');
            }
          }
          this.busy = false;
        }
      }, 10000);
    } catch (error) {
      this.update(`游戏测试未启动：${`${(error as Error).message} ${JSON.stringify(error)}`}`);
    }
  }

  cancel(filesDir: string): void {
    if (this.timer < 0) { return; }
    clearTimeout(this.timer);
    this.timer = -1;
    this.busy = false;
    this.update('已取消等待中的游戏测试。');
    captureLog(filesDir, 'game-touch cancelled before execution');
  }
}
