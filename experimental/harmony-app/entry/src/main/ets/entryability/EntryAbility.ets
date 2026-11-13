import UIAbility from '@ohos.app.ability.UIAbility';
import window from '@ohos.window';
import hilog from '@ohos.hilog';
import { BusinessError } from '@ohos.base';
import { captureLog } from '../CaptureLog';

export default class EntryAbility extends UIAbility {
  onForeground(): void {
    captureLog(this.context.filesDir, 'ability foreground');
  }

  onBackground(): void {
    captureLog(this.context.filesDir, 'ability background');
  }

  onDestroy(): void {
    captureLog(this.context.filesDir, 'ability destroyed');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    try {
      windowStage.loadContent('pages/Index', (error: BusinessError) => {
        if (error.code) {
          hilog.error(0, 'MAAHarmonyProbe', 'Page load failed: %{public}d', error.code);
        }
      });
    } catch (error) {
      hilog.error(0, 'MAAHarmonyProbe', 'Page load threw an exception');
    }
  }
}
