import { authenticateHdc } from '../HdcAuthProbe';
import { probeHdc } from '../HdcProbe';
import { GameTouchProbe } from '../GameTouchProbe';
import { captureLog } from '../CaptureLog';
import { testLocalTouch, testLocalSwipe } from '../TouchProbe';
import deviceInfo from '@ohos.deviceInfo';
import display from '@ohos.display';
import common from '@ohos.app.ability.common';
import { ScreenCaptureProbe } from '../ScreenCaptureProbe';
import { NativeFrameProbe } from '../NativeFrameProbe';
import { runRecognitionProbe, prepareRecognitionTemplates } from '../RecognitionProbe';
import frameProbe from 'libframeprobe.so';

@Entry
@Component
struct Index {
  @State hdcAddress: string = '127.0.0.1';
  @State hdcPort: string = '39499';
  @State hdcStatus: string = '尚未验证本机 HDC';
  @State hdcBusy: boolean = false;
  @State gameTouchStatus: string = '游戏测试坐标尚未准备';
  private gameTouch: GameTouchProbe = new GameTouchProbe((message: string) => {
    this.gameTouchStatus = message;
  });
  @State touchStatus: string = '尚未测试本机触控';
  @State touchBusy: boolean = false;
  @State sliderValue: number = 20;
  @State sliderEvents: number = 0;
  private sliderStartX: number = 0;
  private sliderEndX: number = 0;
  private sliderY: number = 0;
  private targetX: number = 0;
  private targetY: number = 0;
  @State screenInfo: string = '尚未读取';
  @State count: number = 0;
  @State captureStatus: string = '尚未采集';
  @State captureBusy: boolean = false;
  @State frameStatus: string = '尚未取帧';
  @State frameBusy: boolean = false;
  @State recognitionStatus: string = '尚未验证图像识别导航';
  @State coreStatus: string = '尚未检查 MaaCore';
  private frames: NativeFrameProbe = new NativeFrameProbe((message: string, busy: boolean) => {
    this.frameStatus = message;
    this.frameBusy = busy;
  });
  private capture: ScreenCaptureProbe = new ScreenCaptureProbe((message: string, busy: boolean) => {
    this.captureStatus = message;
    this.captureBusy = busy;
  });

  private refreshScreen(): void {
    try {
      const screen = display.getDefaultDisplaySync();
      this.screenInfo = `${screen.width} × ${screen.height}，旋转 ${screen.rotation}`;
    } catch {
      this.screenInfo = '读取屏幕信息失败';
    }
  }

  aboutToAppear(): void {
    this.refreshScreen();
  }

  private logTouch(message: string): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    captureLog(context.filesDir, message);
  }

  build() {
    Scroll() {
      Column({ space: 20 }) {
        Text('MAA 鸿蒙验证').fontSize(28).fontWeight(FontWeight.Bold)
        Text('原生图像识别与本机触控验证').fontSize(16)
        Button('识别首页并打开设置').enabled(!this.frameBusy && !this.captureBusy).onClick(async () => {
          const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
          try {
            await prepareRecognitionTemplates(context);
            this.frames.start(context, () => {
              runRecognitionProbe(context.filesDir, (message: string) => { this.recognitionStatus = message; });
            });
          } catch (error) { this.recognitionStatus = `模板准备失败：${(error as Error).message}`; }
        })
        Text(this.recognitionStatus).fontSize(14)
        Button('检查 MaaCore 加载').onClick(() => {
          try {
            this.coreStatus = frameProbe ? frameProbe.coreStatus() : '原生模块加载失败，请读取系统日志';
          } catch (error) {
            this.coreStatus = `MaaCore 检查失败：${(error as Error).message}`;
          }
          captureLog((this.getUIContext().getHostContext() as common.UIAbilityContext).filesDir, this.coreStatus);
        })
        Text(this.coreStatus).fontSize(14)
        TextInput({ text: this.hdcAddress, placeholder: '手机自身 IP 或 127.0.0.1' })
          .onChange((value: string) => { this.hdcAddress = value.trim(); })
        TextInput({ text: this.hdcPort, placeholder: '无线调试端口' }).type(InputType.Number)
          .onChange((value: string) => { this.hdcPort = value.trim(); })
        Button('验证本机 HDC 握手').enabled(!this.hdcBusy).onClick(async () => {
          this.hdcBusy = true;
          this.hdcStatus = '正在连接手机自身调试端口';
          try {
            this.hdcStatus = await probeHdc(this.hdcAddress, Number(this.hdcPort));
          } catch (error) {
            this.hdcStatus = `HDC 失败：${(error as Error).message} ${JSON.stringify(error)}`;
          } finally { this.hdcBusy = false; }
          this.logTouch(this.hdcStatus);
        })
        Button('本机 HDC 授权并验证 id').enabled(!this.hdcBusy).onClick(async () => {
          this.hdcBusy = true;
          try {
            this.hdcStatus = await authenticateHdc(this.hdcAddress, Number(this.hdcPort),
              (this.getUIContext().getHostContext() as common.UIAbilityContext).filesDir,
              (message: string) => { this.hdcStatus = message; this.logTouch(message); });
          } catch (error) {
            this.logTouch(`HDC stack=${(error as Error).stack}`);
            this.hdcStatus = `HDC 认证失败：${(error as Error).message} ${JSON.stringify(error)}`;
          } finally { this.hdcBusy = false; }
          this.logTouch(this.hdcStatus);
        })
        Button('本机重新激活触控服务').enabled(!this.hdcBusy).onClick(async () => {
          this.hdcBusy = true;
          try {
            this.hdcStatus = await authenticateHdc(this.hdcAddress, Number(this.hdcPort),
              (this.getUIContext().getHostContext() as common.UIAbilityContext).filesDir,
              (message: string) => { this.hdcStatus = message; this.logTouch(message); }, true);
          } catch (error) {
            this.hdcStatus = `本机激活失败：${(error as Error).message}`;
          } finally { this.hdcBusy = false; }
          this.logTouch(this.hdcStatus);
        })
        Button('启动新触控服务（无旧服务时）').enabled(!this.hdcBusy).onClick(async () => {
          this.hdcBusy = true;
          try {
            this.hdcStatus = await authenticateHdc(this.hdcAddress, Number(this.hdcPort),
              (this.getUIContext().getHostContext() as common.UIAbilityContext).filesDir,
              (message: string) => { this.hdcStatus = message; this.logTouch(message); }, true, true);
          } catch (error) {
            this.hdcStatus = `本机启动失败：${(error as Error).message}`;
          } finally { this.hdcBusy = false; }
          this.logTouch(this.hdcStatus);
        })
        Text(this.hdcStatus).fontSize(14)
        Text(`设备类型：${deviceInfo.deviceType}`).fontSize(16)
        Text(`SDK API：${deviceInfo.sdkApiVersion}`).fontSize(16)
        Text(`屏幕：${this.screenInfo}`).fontSize(16)
        Button('刷新屏幕信息').onClick(() => this.refreshScreen())
        Button(`交互测试：${this.count}`).onClick(() => { this.count++; this.logTouch(`touch-counter=${this.count}`); })
          .onAreaChange((_old: Area, area: Area) => {
            this.targetX = vp2px(Number(area.globalPosition.x) + Number(area.width) / 2);
            this.targetY = vp2px(Number(area.globalPosition.y) + Number(area.height) / 2);
          })
        Button('验证本机服务点击').enabled(!this.touchBusy).onClick(async () => {
          this.touchBusy = true;
          this.touchStatus = '正在连接本机触控服务';
          try {
            this.touchStatus = await testLocalTouch(this.targetX, this.targetY);
            this.logTouch(this.touchStatus);
          } catch (error) {
            this.touchStatus = `本机触控失败：${JSON.stringify(error)}`;
          } finally { this.touchBusy = false; }
        })
        Text(`拖动测试：${Math.round(this.sliderValue)}，事件 ${this.sliderEvents}`).fontSize(14)
        Slider({ value: this.sliderValue, min: 0, max: 100, step: 1 })
          .width('100%')
          .onAreaChange((_old: Area, area: Area) => {
            const left = Number(area.globalPosition.x);
            const width = Number(area.width);
            this.sliderStartX = vp2px(left + width * 0.2);
            this.sliderEndX = vp2px(left + width * 0.8);
            this.sliderY = vp2px(Number(area.globalPosition.y) + Number(area.height) / 2);
          })
          .onChange((value: number, mode: SliderChangeMode) => {
            this.sliderValue = value;
            this.sliderEvents++;
            this.logTouch(`touch-slider value=${value} mode=${mode}`);
          })
        Button('验证本机服务拖动').enabled(!this.touchBusy).onClick(async () => {
          this.touchBusy = true;
          this.sliderValue = 20;
          this.sliderEvents = 0;
          this.touchStatus = '正在拖动本页滑块';
          try {
            this.touchStatus = await testLocalSwipe(this.sliderStartX, this.sliderY, this.sliderEndX);
            this.logTouch(this.touchStatus);
          } catch (error) {
            this.touchStatus = `本机拖动失败：${JSON.stringify(error)}`;
            this.logTouch(this.touchStatus);
          } finally { this.touchBusy = false; }
        })
        Text(this.touchStatus).fontSize(14)
        Button('采集并测试游戏音量拖动').enabled(!this.frameBusy && !this.captureBusy).onClick(() => {
          const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
          this.gameTouchStatus = '请授权屏幕采集；后台任务就绪后开始 10 秒倒计时。';
          this.frames.start(context, () => { this.gameTouch.start(context.filesDir); });
        })
        Button('取消等待中的游戏测试').onClick(() => {
          const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
          this.gameTouch.cancel(context.filesDir);
        })
        Text(this.gameTouchStatus).fontSize(14)
        Text('录制屏幕与系统音频，麦克风关闭。授权后 20 秒自动停止，新采集覆盖上一次文件。')
          .fontSize(14).fontColor('#596579')
        Button('开始采集验证').enabled(!this.captureBusy && !this.frameBusy).onClick(() => {
          const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
          this.capture.start(context);
        })
        Button('停止采集').enabled(this.captureBusy).onClick(() => { this.capture.stop(); })
        Text(this.captureStatus).fontSize(14)
        Text('实时取帧只采集画面，不录制声音；授权后约 20 秒停止。').fontSize(14).fontColor('#596579')
        Button('开始实时取帧').enabled(!this.captureBusy && !this.frameBusy).onClick(() => {
          const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
          this.frames.start(context);
        })
        Button('停止实时取帧').enabled(this.frameBusy).onClick(() => { this.frames.stop(); })
        Text(this.frameStatus).fontSize(14)
        Text('触控服务处于实验验证阶段，MAA 核心尚未接入。').fontSize(14).fontColor('#596579')
      }.width('100%').padding(24)
    }.width('100%').height('100%').backgroundColor('#F5F7FA')
  }
}
