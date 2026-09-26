import dayjs from 'dayjs';

export const format = {
  date(value: Date | string | number): string {
    return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  },
};
