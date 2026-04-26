import dayjs, { ConfigType } from "dayjs";

export const formatDate = (
  date: ConfigType,
  format = "YYYY-MM-DD HH:mm:ss"
) => {
  if (date === null || date === undefined) return "";

  return dayjs(date).isValid() ? dayjs(date).format(format) : date;
};
