export const configService = {
  get: <T>(_key: string, defaultValue?: T): T | undefined => defaultValue,
};
