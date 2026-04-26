import { SizeEnum } from "./enums/SizeEnum";
import { LayoutEnum } from "./enums/LayoutEnum";
import { ThemeEnum } from "./enums/ThemeEnum";
import { LanguageEnum } from "./enums/LanguageEnum";

// 获取package.json信息
const getPkgInfo = () => {
  try {
    return {
      name: "tpa-sass-react",
      version: "1.0.0"
    };
  } catch {
    return {
      name: "tpa-sass-react",
      version: "1.0.0"
    };
  }
};

const pkg = getPkgInfo();
const mediaQueryList = typeof window !== "undefined" ? window.matchMedia("(prefers-color-scheme: dark)") : { matches: false };

const defaultSettings: AppSettings = {
  title: pkg.name,
  version: pkg.version,
  showSettings: true,
  tagsView: true,
  fixedHeader: true,
  sidebarLogo: true,
  layout: LayoutEnum.LEFT,
  theme: mediaQueryList.matches ? ThemeEnum.DARK : ThemeEnum.LIGHT,
  size: SizeEnum.DEFAULT,
  language: LanguageEnum.ZH_CN,
  themeColor: "#4080FF",
  watermarkEnabled: false,
  watermarkContent: pkg.name,
};

export default defaultSettings;
