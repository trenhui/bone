
export interface MainBlockProps {
  id?: string;
  code?: string;
  name?: string;
  eventTriggerList?: any[];
  body?: any[];
}

export const defaultMainBlockProps: Partial<MainBlockProps> = {
  id: "",
  code: "",
  name: "",
  eventTriggerList: [],
  body: [],
};
