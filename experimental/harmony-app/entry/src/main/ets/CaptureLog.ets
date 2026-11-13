import fs from '@ohos.file.fs';
import hilog from '@ohos.hilog';

export function captureLog(filesDir: string, message: string): void {
  const line = `${new Date().toISOString()} ${message}\n`;
  hilog.info(0, 'MAAHarmonyProbe', '%{public}s', message);
  let file: fs.File | undefined = undefined;
  try {
    file = fs.openSync(`${filesDir}/capture-report.txt`,
      fs.OpenMode.CREATE | fs.OpenMode.WRITE_ONLY | fs.OpenMode.APPEND);
    fs.writeSync(file.fd, line);
  } catch {
    hilog.error(0, 'MAAHarmonyProbe', 'Cannot persist capture report');
  } finally {
    if (file) {
      try {
        fs.closeSync(file.fd);
      } catch {
        hilog.error(0, 'MAAHarmonyProbe', 'Cannot close capture report');
      }
    }
  }
}
