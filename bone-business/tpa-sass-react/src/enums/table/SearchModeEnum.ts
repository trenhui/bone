export enum SearchModeEnum {
  PRECISE = 1,
  FUZZY = 2,
}

export const SearchModeLabel = {
  [SearchModeEnum.PRECISE]: "精准匹配",
  [SearchModeEnum.FUZZY]: "模糊匹配",
};

export const SearchModeOptions = Object.entries(SearchModeLabel).map(
  ([value, label]) => ({
    value: Number(value),
    label,
  })
);

export const getSearchModeLabel = (value: number) => {
  return SearchModeLabel[value as keyof typeof SearchModeLabel];
};
