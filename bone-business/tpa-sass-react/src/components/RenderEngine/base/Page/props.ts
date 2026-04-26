
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";

export interface PageProps {
  id?: string;
  name?: string;
  code?: string;
  displayMode?: DisplayModeEnum;
  bizInfo?: any;
  pageType?: number;
  body?: any[];
}

export const defaultPageProps: Partial<PageProps> = {
  id: "",
  name: "",
  code: "",
  displayMode: DisplayModeEnum.VIEW,
  bizInfo: {},
  pageType: 0,
  body: [],
};
