export const format = {
  date(value: Date | string | number): string {
    return new Date(value).toLocaleString();
  },
};
