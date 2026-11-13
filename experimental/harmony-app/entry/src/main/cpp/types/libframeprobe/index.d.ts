export const start: (width: number, height: number, filesDir: string) => number;
export const stop: () => void;
export const status: () => string;
export const matchSettings: () => string;
export const coreStatus: () => string;
export const nativeClick: (x: number, y: number, width: number, height: number) => Promise<boolean>;
